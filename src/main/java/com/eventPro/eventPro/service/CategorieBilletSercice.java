package com.eventPro.eventPro.service;

import com.eventPro.eventPro.dto.categorie.CategorieBilletRequest;
import com.eventPro.eventPro.dto.categorie.CategorieBilletResponse;
import com.eventPro.eventPro.exception.BusinessRuleException;
import com.eventPro.eventPro.exception.ForbiddenOperationException;
import com.eventPro.eventPro.exception.ResourceNotFoundException;
import com.eventPro.eventPro.mapper.categorie.CategorieBilletMapper;
import com.eventPro.eventPro.model.CategorieBillet;
import com.eventPro.eventPro.model.Event;
import com.eventPro.eventPro.model.enums.StatusEvenment;
import com.eventPro.eventPro.repository.CategorieBilletRepository;
import com.eventPro.eventPro.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategorieBilletSercice {

    private final EventRepository eventRepository;
    private final CategorieBilletMapper categorieBilletMapper;
    private final CategorieBilletRepository categorieBilletRepository;

    @Transactional
    public CategorieBilletResponse create(UUID eventId, CategorieBilletRequest request){

        Event event = findEvent(eventId);
        checkOwner(event,request.organisateurId());
        checkEventActive(event);
        validate(request);
        checkNomUnique(eventId,request.nom());

        CategorieBillet categorie = categorieBilletMapper.toEntity(request);
        categorie.setEvent(event);
        return categorieBilletMapper.toResponse(categorieBilletRepository.save(categorie));
    }

    @Transactional(readOnly = true)
    public List<CategorieBilletResponse> listByEvent(UUID eventId){
        Event event = findEvent(eventId);
        if (event.getStatut() != StatusEvenment.PUBLISHED){
            throw new ResourceNotFoundException("evenment introuvable : "+ eventId);
        }
        return categorieBilletRepository.findByEventId(eventId).stream().map(categorieBilletMapper::toResponse).toList();
    }

    @Transactional
    public CategorieBilletResponse update(UUID id,CategorieBilletRequest request){
        CategorieBillet categorie = findCategorie(id);
        Event event = categorie.getEvent();
        checkOwner(event,request.organisateurId());
        checkEventActive(event);
        validate(request);
        if (!categorie.getNom().equalsIgnoreCase(request.nom())){
            checkNomUnique(event.getId(),request.nom());
        }
        categorieBilletMapper.updateEntity(request,categorie);
        return categorieBilletMapper.toResponse(categorie);
    }

    @Transactional
    public void delete(UUID id , UUID organisateurId){
        CategorieBillet categorie = findCategorie(id);
        Event event = categorie.getEvent();
        checkOwner(event,organisateurId);
        checkEventActive(event);

        if (event.getStatut() == StatusEvenment.PUBLISHED && categorieBilletRepository.countByEventId(event.getId()) <= 1){
            throw new BusinessRuleException("impossible de delete la dernier categorie d'un event publier");
        }
        categorieBilletRepository.delete(categorie);
    }

    // ---------- helpers ----------

    private Event findEvent(UUID id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("evenement introuvable : " + id));
    }

    private CategorieBillet findCategorie(UUID id) {
        return categorieBilletRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categorie introuvable : " + id));
    }

    private void checkOwner(Event event,UUID organisateurId){
        if (!event.getOrganisateur().getId().equals(organisateurId)){
            throw new ForbiddenOperationException("vous n'este pas l'organiateur de cette event !!");
        }
    }

    private void checkEventActive(Event event){
        if (event.getStatut() == StatusEvenment.CANCELLED || event.getStatut() == StatusEvenment.COMPLETED){
            throw new BusinessRuleException("impossible de gerer les categories d'un event annuller ou terminer !!");
        }
    }

    private void validate(CategorieBilletRequest request){
        if (request.quotaMaxParClient() > request.quantiteTotale()){
            throw new BusinessRuleException("le quota par client ne peut pad depasser la qauntite total !!");
        }
    }

    private void checkNomUnique(UUID eventId,String nom){
        if (categorieBilletRepository.existsByEventIdAndNomIgnoreCase(eventId,nom)){
            throw new BusinessRuleException("une category nommer \"" + nom + "\" exists deja pour cet event");
        }
    }


}
