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
        Client client = findClient(request.clientId()); // le client
        Event event = findEvent(eventId); // l'evénment
        LocalDateTime now = LocalDateTime.now();  // la date actuel

        checkEventOpenForSale(event,now); // validation de statu (!pub) , dateOuvertire, dateCloture
        checkNoDuplicateCategorie(request.lignes()); // check if duplication une categoerie throw

        // Recuperer les ids des categories de billets demandees   (pour retrouver les categories dans la base de donnees)
        List<UUID> ids = request.lignes().stream().map(LigneReservationRequest::categorieBilletId).toList();
        // charger categoriesBillets
        Map<UUID, CategorieBillet> categories = lockCategories(event,ids);
        //create Reservation
        Reservation reservation = Reservation.builder()
                .reference(generateReference())
                .statut(StatutReservation.PENDING_PAYMENT)
                .dateCreation(now)
                .dateExpiration(now.plusMinutes(holdMinutes))
                .client(client)
                .event(event)
                .build();


        BigDecimal brut = BigDecimal.ZERO;  // initial total de tous les billes
        for (LigneReservationRequest ligne : request.lignes()){
            CategorieBillet categorie = categories.get(ligne.categorieBilletId());

            checkStock(categorie,ligne.quantite(),now);  // check if possible  de passer cette quantity
            checkQuota(categorie,client.getId(),ligne.quantite(),now); //verifie que le client ne depass pas ma limite d'achat autorise par cette categorie

            BigDecimal sousTotal = categorie.getPrixUnitaire().multiply(BigDecimal.valueOf(ligne.quantite())); // sous total
            reservation.getLignes().add(LigneReservation.builder() // create ligne
                    .reservation(reservation)
                    .categorieBillet(categorie)
                    .quantite(ligne.quantite())
                    .prixUnitaire(categorie.getPrixUnitaire())
                    .sousTotal(sousTotal)
                    .build());
            brut = brut.add(sousTotal); // total final
        }
        BigDecimal reduction = BigDecimal.ZERO; // la reduction initial
        if (request.promoCode() != null){ // si client saisir le code promo
            Promotion promotion = applyPromotion(event.getId(),request.promoCode());
            reservation.setPromotion(promotion);
            reduction = promotion.calculerReduction(brut); // reduction
        }
        reservation.setMontantTotal(brut.subtract(reduction));   // montant total de reservation

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
        Reservation reservation = reservationRepository.findByIdForUpdate(id)
                .orElseThrow(()-> new ResourceNotFoundException("Reservation introuvable : " + id));
                //findReservation(id);
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

    // expired
    @Transactional
    public boolean expirer(UUID id) {
        Reservation reservation = reservationRepository.findByIdForUpdate(id).orElse(null);
        if (reservation == null) {
            return false;
        }
        // deja payee, annulee, ou pas encore expiree
        if (reservation.getStatut() != StatutReservation.PENDING_PAYMENT
                || !reservation.getDateExpiration().isBefore(LocalDateTime.now())) {
            return false;
        }
        reservation.setStatut(StatutReservation.EXPIRED);
        if (reservation.getPromotion() != null) {
            promotionRepository.devrementUtilisation(reservation.getPromotion().getId());
        }
        return true;
    }

    //
    @Transactional
    public boolean annulerPourEvenementAnnule(UUID id) {
        Reservation reservation = reservationRepository.findByIdForUpdate(id).orElse(null);
        if (reservation == null
                || reservation.getStatut() != StatutReservation.PENDING_PAYMENT
                || reservation.getEvent().getStatut() != StatusEvenment.CANCELLED) {
            return false;
        }
        reservation.setStatut(StatutReservation.CANCELLED);
        if (reservation.getPromotion() != null) {
            promotionRepository.devrementUtilisation(reservation.getPromotion().getId());
        }
        return true;
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

    // est-ce disponible
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

    // check if not duplicate une categorie
    private void checkNoDuplicateCategorie(List<LigneReservationRequest> lignes){
        Set<UUID> seen = new HashSet<>();
        for (LigneReservationRequest ligne : lignes){
            if (!seen.add(ligne.categorieBilletId())) {
                throw new BusinessRuleException("Une categorie ne peut apparaitre qu'une seule fois");
            }
        }
    }

    private Map<UUID,CategorieBillet> lockCategories(Event event,List<UUID> ids){
        // charger les categoriesBellits
        List<CategorieBillet> found = categorieBilletRepository.findAllByIdForUpdate(ids);
        // check size
        if (found.size() != ids.size()){
            throw new ResourceNotFoundException("Une ou plusieurs categories sont introuvables");
        }

        // verification des categories dans event
        for (CategorieBillet categorie : found) {
            if (!categorie.getEvent().getId().equals(event.getId())) {
                throw new BusinessRuleException("La categorie " + categorie.getNom() + " n'appartient pas a cet evenement");
            }
        }
        return found.stream().collect(Collectors.toMap(CategorieBillet::getId, Function.identity()));
    }


    private void checkStock(CategorieBillet categorie, int quantite, LocalDateTime now){
        long pris = ligneReservationRepository.sumQuantiteActive(categorie.getId(),now); // total de quantity active
        long disponible = categorie.getQuantiteTotale() - pris; // total disponible
        if (quantite > disponible){ // check if logique de reserver cet quantity
            throw new BusinessRuleException("Stock insuffisant pour \"" + categorie.getNom()
                    + "\" (disponible : " + Math.max(disponible, 0) + ")");
        }
    }


    private void checkQuota(CategorieBillet categorie,UUID clientId,int quanite,LocalDateTime now){
        long dejaPris = ligneReservationRepository.sumQuantiteActiveByClient(categorie.getId(),clientId,now); // uantity par client
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
