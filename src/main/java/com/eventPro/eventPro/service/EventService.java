package com.eventPro.eventPro.service;

import com.eventPro.eventPro.dto.event.EventRequest;
import com.eventPro.eventPro.dto.event.EventResponse;
import com.eventPro.eventPro.exception.BusinessRuleException;
import com.eventPro.eventPro.exception.ForbiddenOperationException;
import com.eventPro.eventPro.exception.ResourceNotFoundException;
import com.eventPro.eventPro.mapper.event.EventMapper;
import com.eventPro.eventPro.model.Event;
import com.eventPro.eventPro.model.Organisateur;
import com.eventPro.eventPro.model.enums.StatusEvenment;
import com.eventPro.eventPro.repository.CategorieBilletRepository;
import com.eventPro.eventPro.repository.EventRepository;
import com.eventPro.eventPro.repository.OrganisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final OrganisateurRepository organisateurRepository;
    private final EventMapper eventMapper;
    private final CategorieBilletRepository categorieBilletRepository;

    @Transactional
    public EventResponse create(EventRequest request){
        validateDates(request);
        Organisateur organisateur = findOrganisateur(request.organisateurId());

        Event event = eventMapper.toEntity(request);
        event.setStatut(StatusEvenment.DRAFT);
        event.setOrganisateur(organisateur);

        return eventMapper.toResponse(eventRepository.save(event));
    }

    @Transactional
    public EventResponse update(UUID id,EventRequest request){

        Event event = findEvent(id);
        checkOwner(event,request.organisateurId());
        if (event.getStatut() != StatusEvenment.DRAFT){
            throw new BusinessRuleException("seul l'event en draft peut etre modifier");
        }
        validateDates(request);
        eventMapper.updateEntity(request,event);
        return eventMapper.toResponse(event);
    }

    @Transactional
    public EventResponse publier(UUID id, UUID organisateurId) {
        Event event = findEvent(id);
        checkOwner(event, organisateurId);

        if (event.getStatut() != StatusEvenment.DRAFT) {
            throw new BusinessRuleException("Seul un evenement en draft peut etre publie");
        }
        // existing by category
        if (!categorieBilletRepository.existsByEventId(id)){
            throw new BusinessRuleException("l'evenment doit avoir au moins une categorie de billet");
        }

        event.setStatut(StatusEvenment.PUBLISHED);
        return eventMapper.toResponse(event);
    }
    @Transactional
    public EventResponse annuler(UUID id, UUID organisateurId) {
        Event event = findEvent(id);
        checkOwner(event, organisateurId);
        if (event.getStatut() == StatusEvenment.CANCELLED || event.getStatut() == StatusEvenment.COMPLETED) {
            throw new BusinessRuleException("cette evenement est deja annule ou terminer");
        }
        event.setStatut(StatusEvenment.CANCELLED);
        return eventMapper.toResponse(event);
    }

    @Transactional(readOnly = true)
    public EventResponse getById(UUID id){
        Event event = findEvent(id);
        if (event.getStatut() != StatusEvenment.PUBLISHED){
            throw new ResourceNotFoundException("evenment introuvable "+id);
        }
        return eventMapper.toResponse(event);
    }

    @Transactional(readOnly = true)
    public Page<EventResponse> listPublished(Pageable pageable){
        return eventRepository.findByStatut(StatusEvenment.PUBLISHED,pageable).map(eventMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<EventResponse> listByOrganisateur(UUID organisateurId, Pageable pageable) {
        if (!organisateurRepository.existsById(organisateurId)) {
            throw new ResourceNotFoundException("Organisateur introuvable : " + organisateurId);
        }
        return eventRepository.findByOrganisateurId(organisateurId, pageable)
                .map(eventMapper::toResponse);
    }

    // ---------- helpers ----------
    // find event
    private Event findEvent(UUID id){
        return eventRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("evenment introuvable "+id));
    }

    // find organisateur
    private Organisateur findOrganisateur(UUID id){
        return organisateurRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("organisateur introuvable "+id));
    }

    // check owner
    private void checkOwner(Event event,UUID organisateurId){
        if (!event.getOrganisateur().getId().equals(organisateurId)){
            throw new ForbiddenOperationException("vous n'etes pas l'organisateur de cette evenment");
        }
    }

    // validation des dates
    private void validateDates(EventRequest request) {
        if (request.dateDebut().isBefore(LocalDateTime.now())){
            throw new BusinessRuleException("L'evenement doit commencer dans le futur");
        }
        if (request.dateFin().isBefore(request.dateDebut())){
            throw new BusinessRuleException("La date de fin doit etre apres la date de debut");
        }
        if (!request.dateOuvertureVente().isBefore(request.dateClotureVente())){
            throw new BusinessRuleException("L'ouverture de la vente doit preceder sa cloture");
        }
        if (request.dateClotureVente().isAfter(request.dateDebut())){
            throw new BusinessRuleException("La vente doit se cloturer avant le debut de l'evenement");
        }
    }


}
