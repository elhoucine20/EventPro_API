package com.eventPro.eventPro.service;

import com.eventPro.eventPro.dto.ligneReservation.LigneReservationRequest;
import com.eventPro.eventPro.dto.reservation.ReservationRequest;
import com.eventPro.eventPro.dto.reservation.ReservationResponse;
import com.eventPro.eventPro.exception.BusinessRuleException;
import com.eventPro.eventPro.exception.ForbiddenOperationException;
import com.eventPro.eventPro.exception.ResourceNotFoundException;
import com.eventPro.eventPro.mapper.reservation.ReservationMapper;
import com.eventPro.eventPro.model.*;
import com.eventPro.eventPro.model.enums.StatusEvenment;
import com.eventPro.eventPro.model.enums.StatutReservation;
import com.eventPro.eventPro.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final LigneReservationRepository ligneReservationRepository;
    private final ClientRepository clientRepository;
    private final EventRepository eventRepository;
    private final CategorieBilletRepository categorieBilletRepository;
    private final PromotionRepository promotionRepository;
    private final ReservationMapper reservationMapper;

    @Value("${app.reservation.hold-minutes}")
    private long holdMinutes;


    @Transactional
    public ReservationResponse create(UUID eventId, ReservationRequest request){
        Client client = findClient(request.clientId());
        Event event = findEvent(eventId);
        LocalDateTime now = LocalDateTime.now();

        checkEventOpenForSale(event,now); // validation de statu (!pub) , dateOuvertire, dateCloture
        checkNoDuplicateCategorie(request.lignes()); // duplication de categoerie

        List<UUID> ids = request.lignes().stream().map(LigneReservationRequest::categorieBilletId).toList();
        Map<UUID, CategorieBillet> categories = lockCategories(event,ids);
        Reservation reservation = Reservation.builder()
                .reference(generateReference())
                .statut(StatutReservation.PENDING_PAYMENT)
                .dateCreation(now)
                .dateExpiration(now.plusMinutes(holdMinutes))
                .client(client)
                .event(event)
                .build();

        BigDecimal brut = BigDecimal.ZERO;
        for (LigneReservationRequest ligne : request.lignes()){
            CategorieBillet categorie = categories.get(ligne.categorieBilletId());

            checkStock(categorie,ligne.quantite(),now);
            checkQuota(categorie,client.getId(),ligne.quantite(),now);

            BigDecimal sousTotal = categorie.getPrixUnitaire().multiply(BigDecimal.valueOf(ligne.quantite()));
            reservation.getLignes().add(LigneReservation.builder()
                    .reservation(reservation)
                    .categorieBillet(categorie)
                    .quantite(ligne.quantite())
                    .prixUnitaire(categorie.getPrixUnitaire())
                    .sousTotal(sousTotal)
                    .build());
            brut = brut.add(sousTotal);
        }
        BigDecimal reduction = BigDecimal.ZERO;
        if (request.promoCode() != null){
            Promotion promotion = applyPromotion(event.getId(),request.promoCode());
            reservation.setPromotion(promotion);
            reduction = promotion.calculerReduction(brut);
        }
        reservation.setMontantTotal(brut.subtract(reduction));   // subtract

        return  reservationMapper.toResponse(reservationRepository.save(reservation));
    }

    // get reservation by id
    @Transactional(readOnly = true)
    public ReservationResponse getById(UUID id , UUID clientId){
        Reservation reservation = findReservation(id);
        checkOwner(reservation,clientId);
        return reservationMapper.toResponse(reservation);
    }

    // historique des reservation de client
    @Transactional
    public Page<ReservationResponse> listByClient(UUID clientId, Pageable pageable){
        if (!clientRepository.existsById(clientId)){
            throw new ResourceNotFoundException("Client Introuvable "+clientId);
        }
        return reservationRepository.findByClientId(clientId,pageable).map(reservationMapper::toResponse);
    }

    // annulation de reservation que ne paye pas
    @Transactional
    public ReservationResponse annuler(UUID id , UUID clientId){
        Reservation reservation = findReservation(id);
        checkOwner(reservation, clientId);

        if (reservation.getStatut() != StatutReservation.PENDING_PAYMENT){
            throw new BusinessRuleException("seule une reservation en ettente de paiment peut etre annuler");
        }
        reservation.setStatut(StatutReservation.CANCELLED);
        if (reservation.getPromotion() != null){
            promotionRepository.devrementUtilisation(reservation.getPromotion().getId());
        }
        return reservationMapper.toResponse(reservation);
    }


    // heapers
    private Client findClient(UUID id){
        return clientRepository.findById(id).orElseThrow(()-> new ResourceNotFoundException("Client introuvable : " + id));
    }

    private Event findEvent(UUID id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("evenement introuvable : " + id));
    }

    private Reservation findReservation(UUID id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation introuvable : " + id));
    }

    private void checkOwner(Reservation reservation ,UUID clientId){
        if (!reservation.getClient().getId().equals(clientId)){
            throw new ForbiddenOperationException("Cette reservation ne vous appartient pas");
        }
    }

    private void checkEventOpenForSale(Event event,LocalDateTime now){
        if (event.getStatut() != StatusEvenment.PUBLISHED){
            throw new ResourceNotFoundException("evenement introuvable : " + event.getId());
        }
        if (now.isBefore(event.getDateOuvertureVente())) {
            throw new BusinessRuleException("La vente n'a pas encore commence");
        }
        if (now.isAfter(event.getDateClotureVente())) {
            throw new BusinessRuleException("La vente est cloturée");
        }
    }

    private void checkNoDuplicateCategorie(List<LigneReservationRequest> lignes){
        Set<UUID> seen = new HashSet<>();
        for (LigneReservationRequest ligne : lignes){
            if (!seen.add(ligne.categorieBilletId())) {
                throw new BusinessRuleException("Une categorie ne peut apparaitre qu'une seule fois");
            }
        }
    }

    private Map<UUID,CategorieBillet> lockCategories(Event event,List<UUID> ids){
        List<CategorieBillet> found = categorieBilletRepository.findAllByIdForUpdate(ids);
        if (found.size() != ids.size()){
            throw new ResourceNotFoundException("Une ou plusieurs categories sont introuvables");
        }

        for (CategorieBillet categorie : found) {
            if (!categorie.getEvent().getId().equals(event.getId())) {
                throw new BusinessRuleException("La categorie \"" + categorie.getNom() + "\" n'appartient pas a cet evenement");
            }
        }
        return found.stream().collect(Collectors.toMap(CategorieBillet::getId, Function.identity()));
    }


    private void checkStock(CategorieBillet categorie, int quantite, LocalDateTime now){
        long pris = ligneReservationRepository.sumQuantiteActive(categorie.getId(),now);
        long disponible = categorie.getQuantiteTotale() - pris;
        if (quantite > disponible){
            throw new BusinessRuleException("Stock insuffisant pour \"" + categorie.getNom()
                    + "\" (disponible : " + Math.max(disponible, 0) + ")");
        }
    }


    private void checkQuota(CategorieBillet categorie,UUID clientId,int quanite,LocalDateTime now){
        long dejaPris = ligneReservationRepository.sumQuantiteActiveByClient(categorie.getId(),clientId,now);
        if (dejaPris + quanite > categorie.getQuotaMaxParClient()){
            throw new BusinessRuleException("Quota depasser pour \"" + categorie.getNom()
                    + "\" (maximum " + categorie.getQuotaMaxParClient() + " par client, deja " + dejaPris + ")");
        }
    }

    private Promotion applyPromotion(UUID eventId,String code){
        Promotion promotion = promotionRepository.findByEventIdAndCode(eventId,code).orElseThrow(()-> new BusinessRuleException("code promo invalide"));
        if (!promotion.estValid())
            throw new BusinessRuleException("code promo expire ou puisé");
        if (promotionRepository.incrementUtulisation(promotion.getId())==0)
            throw new BusinessRuleException("code promo expire");

        return promotion;
    }

    private String generateReference(){
        String reference;
        do {
            reference = "REF-"+UUID.randomUUID().toString().replace("-","").substring(0,10).toUpperCase();
        }while (reservationRepository.existsByReference(reference));
        return reference;
    }

}
