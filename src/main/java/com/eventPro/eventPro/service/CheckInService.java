package com.eventPro.eventPro.service;

import com.eventPro.eventPro.dto.ticket.CheckInRequest;
import com.eventPro.eventPro.dto.ticket.TicketResponse;
import com.eventPro.eventPro.dto.ticket.VerificationResponse;
import com.eventPro.eventPro.exception.ResourceNotFoundException;
import com.eventPro.eventPro.exception.TicketAlreadyUsedException;
import com.eventPro.eventPro.exception.TicketInvalidException;
import com.eventPro.eventPro.mapper.ticket.TicketMapper;
import com.eventPro.eventPro.model.Billet;
import com.eventPro.eventPro.model.enums.StatusEvenment;
import com.eventPro.eventPro.model.enums.StatutBillet;
import com.eventPro.eventPro.repository.AgentControleRepository;
import com.eventPro.eventPro.repository.BilletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckInService {

    private final BilletRepository billetRepository;
    private final AgentControleRepository agentControleRepository;
    private final TicketMapper ticketMapper;

    @Transactional(readOnly = true)
    public VerificationResponse verifier(String code, UUID agentId) {
        checkAgent(agentId);
        return decrire(findBillet(normaliser(code)));
    }

    @Transactional
    public VerificationResponse composter(CheckInRequest request) {
        checkAgent(request.agentId());
        String code = request.code();

        Billet avant = findBillet(code);
        if (evenementAnnule(avant)) {
            throw new TicketInvalidException("Accès refusé : l'événement a été annulé");
        }

        int composte = billetRepository.composter(code, LocalDateTime.now());
        Billet billet = findBillet(code);   // état réel après l'UPDATE

        if (composte == 0) {
            if (billet.getStatut() == StatutBillet.USED) {
                throw new TicketAlreadyUsedException("Billet déja utilisé le " + billet.getDateControle());
            }
            throw new TicketInvalidException("Accés refusé : billet annulé");
        }
        return new VerificationResponse(true, "Accés autorisé : billet composté", ticketMapper.toResponse(billet));
    }

    // ---------- helpers ----------
    private void checkAgent(UUID agentId) {
        if (!agentControleRepository.existsById(agentId)) {
            throw new ResourceNotFoundException("Agent introuvable : " + agentId);
        }
    }

    private Billet findBillet(String code) {
        return billetRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Billet introuvable : " + code));
    }

    private String normaliser(String code) {
        return code.strip().toUpperCase();
    }

    private boolean evenementAnnule(Billet billet) {
        return billet.getLigneReservation().getReservation().getEvent().getStatut() == StatusEvenment.CANCELLED;
    }

    private VerificationResponse decrire(Billet billet) {
        TicketResponse ticket = ticketMapper.toResponse(billet);
        if (evenementAnnule(billet)) {
            return new VerificationResponse(false, "Accés refusé : l'événement a été annulé", ticket);
        }
        return switch (billet.getStatut()) {
            case VALID -> new VerificationResponse(true, "Billet valide", ticket);
            case USED -> new VerificationResponse(false, "Billet déja utilisé le " + billet.getDateControle(), ticket);
            case CANCELLED -> new VerificationResponse(false, "Billet annulé", ticket);
        };
    }
}