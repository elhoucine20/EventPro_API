# EventPro API --- Cahier des charges

> **Projet :** Socle technique et base de données --- API de
> billetterie\
> **Stack cible :** Java 17, Spring Boot 3.2.x, PostgreSQL 15+\
> **Architecture :** Architecture en couches (Package-by-Layer)

**Principe directeur :** vendre des billets sans jamais dépasser la
capacité disponible. Les réservations bloquent temporairement les places
pendant 10 minutes, les paiements sont simulés avec vérification stricte
et chaque billet ne peut être composté qu'une seule fois.

------------------------------------------------------------------------

## Sommaire

-   [1. Présentation du projet](#1-présentation-du-projet)
-   [2. Objectifs fonctionnels et
    pédagogiques](#2-objectifs-fonctionnels-et-pédagogiques)
-   [3. Technologies imposées](#3-technologies-imposées)
-   [4. Fonctionnement général](#4-fonctionnement-général)
-   [5. Rôles et responsabilités](#5-rôles-et-responsabilités)
-   [6. User Stories](#6-user-stories-besoins-par-acteur)
-   [7. Règles métier et gestion des
    places](#7-règles-métier-et-gestion-des-places)
-   [8. Règles de remboursement](#8-règles-de-remboursement)
-   [9. Calcul des promotions](#9-calcul-des-promotions)
-   [10. Entités, attributs, méthodes et
    relations](#10-entités-attributs-méthodes-et-relations)
-   [11. Associations et
    multiplicités](#11-cartographie-formelle-des-associations-et-multiplicités)
-   [12. Architecture du
    projet](#12-arborescence-et-architecture-du-projet)
-   [13. DTOs et gestion des erreurs
    HTTP](#13-dtos-et-gestion-des-erreurs-http)
-   [14. Automatisation et stratégie de
    tests](#14-automatisation-et-stratégie-de-tests)

------------------------------------------------------------------------

## 1. Présentation du projet

EventPro est une API REST simple et robuste permettant de gérer des
événements et de vendre des billets en ligne.

Le parcours utilisateur est direct :

1.  Consulter les événements.
2.  Bloquer temporairement des billets pendant 10 minutes.
3.  Appliquer éventuellement un code promotionnel.
4.  Simuler et confirmer le paiement.
5.  Générer les billets électroniques avec un code unique destiné au
    contrôle à l'entrée.

## 2. Objectifs fonctionnels et pédagogiques

Le projet vise à mettre en pratique une architecture en couches standard
:

-   Controller
-   Service
-   Repository
-   DTO
-   Mapper
-   Entity

Les règles métier doivent rester claires et centralisées. La gestion du
stock des places est assurée en base de données, avec un déverrouillage
automatique des réservations non payées à l'aide d'une tâche planifiée.

## 3. Technologies imposées

  -----------------------------------------------------------------------
  Composant               Technologie / version   Utilisation
  ----------------------- ----------------------- -----------------------
  Langage et framework    Java 17, Spring Boot    Développement de l'API
                          3.2.x, Spring Web (MVC) REST

  Accès aux données       Spring Data JPA,        Persistance et ORM
                          Hibernate ORM 6.4.x     

  Base de données         PostgreSQL 15+          Transactions ACID et
                                                  verrous pessimistes
                                                  `PESSIMISTIC_WRITE`

  Validation              Jakarta Bean            Validation des DTOs
                          Validation, Hibernate   
                          Validator 8.x           

  Conversion Entity / DTO MapStruct               Mapping déclaratif,
                                                  sans réflexion au
                                                  runtime

  Construction des objets Lombok `@Builder`       Construction fiable des
                                                  DTOs et entités

  Monnaie et calculs      MAD, `BigDecimal`       Calculs monétaires
                                                  précis

  Documentation API       Springdoc OpenAPI 2.x / Documentation
                          Swagger UI              interactive

  Tests                   Apache Maven, JUnit 5,  Tests unitaires et
                          Mockito, Testcontainers d'intégration
                          (bonus)                 

  Tâches automatiques     Spring Scheduling,      Expiration des
                          `@Scheduled`            réservations et
                                                  libération des places
  -----------------------------------------------------------------------

## 4. Fonctionnement général

Le parcours d'achat suit les étapes suivantes :

1.  Le client choisit un événement publié.
2.  Il crée une réservation qui bloque les places pendant 10 minutes.
3.  Il peut saisir un code promotionnel.
4.  Il confirme son achat via une simulation de paiement.
5.  Les billets définitifs sont générés avec le statut `VALID`.

## 5. Rôles et responsabilités

  -----------------------------------------------------------------------
  Acteur                              Responsabilités
  ----------------------------------- -----------------------------------
  **ADMIN**                           Consulte la liste globale des
                                      utilisateurs ; crée les codes
                                      promotionnels généraux ; supervise
                                      l'historique des réservations ;
                                      gère les annulations
                                      exceptionnelles.

  **ORGANIZER**                       Crée, met à jour et publie ses
                                      événements ; configure les
                                      catégories de billets et leurs prix
                                      ; consulte le total de ses ventes
                                      et le nombre de billets vendus ;
                                      annule un événement en cas
                                      d'imprévu.

  **CLIENT**                          Consulte le catalogue des
                                      événements publiés ; réserve des
                                      places (bloquées pendant 10
                                      minutes) ; paie sa réservation et
                                      télécharge ses billets ; demande un
                                      remboursement en cas d'annulation.

  **AGENT**                           Scanne et composte les billets à
                                      l'entrée ; vérifie si un billet est
                                      valide ou déjà scanné ; refuse
                                      l'accès si le billet est invalide ;
                                      enregistre la date et l'heure du
                                      passage.
  -----------------------------------------------------------------------

## 6. User Stories (besoins par acteur)

### Client et achats

-   **US1 :** En tant que client, je veux voir la liste des événements
    et les places restantes.
-   **US2 :** En tant que client, je veux réserver des places bloquées
    pendant 10 minutes.
-   **US3 :** En tant que client, je veux saisir un code promotionnel
    pour payer moins cher.
-   **US4 :** En tant que client, je veux payer et obtenir mes billets
    électroniques.
-   **US5 :** En tant que client, je veux annuler mon achat avant
    l'événement et être remboursé.

### Organisateur, agent et administrateur

-   **US6 :** En tant qu'organisateur, je veux créer un événement avec
    ses tarifs et le publier.
-   **US7 :** En tant qu'organisateur, je veux voir combien de billets
    ont été vendus pour mon événement.
-   **US8 :** En tant qu'organisateur, je veux annuler mon événement si
    nécessaire avec remboursement automatique.
-   **US9 :** En tant qu'agent, je veux scanner les billets et empêcher
    un second passage.
-   **US10 :** En tant qu'administrateur, je veux superviser l'ensemble
    des opérations.

## 7. Règles métier et gestion des places

### 7.1 Calcul des places disponibles

La formule de calcul est :

``` text
placesDisponibles = quantiteTotale - (placesReservees + placesVendues)
```

Si la quantité demandée dépasse le nombre de places disponibles, la
demande est rejetée.

### 7.2 Verrouillage pessimiste

Lors de la création d'une réservation, un verrou exclusif pessimiste
(`PESSIMISTIC_WRITE`, via `SELECT ... FOR UPDATE`) bloque la catégorie
de billet concernée pendant la transaction.

Ce mécanisme évite la survente lorsque plusieurs clients tentent de
réserver simultanément les dernières places.

### 7.3 Réservation temporaire

-   À sa création, la réservation reçoit le statut `PENDING_PAYMENT`.
-   Les places sont bloquées pendant **10 minutes**.
-   Si aucun paiement n'est effectué dans ce délai, la réservation
    expire.
-   Les places bloquées sont alors restituées et redeviennent
    disponibles.

### 7.4 Confirmation de l'achat

Dès que le paiement est validé :

-   Les places passent de « réservées » à « vendues ».
-   Les billets sont générés.
-   Les billets reçoivent le statut `VALID`.

### 7.5 Contrôle d'accès

Chaque billet ne peut être composté qu'une seule fois :

-   Premier scan : transition du statut `VALID` vers `USED`.
-   Tout scan ultérieur : retour d'une erreur HTTP `409 Conflict`.

## 8. Règles de remboursement

Le remboursement dépend du délai entre la demande et la date de début de
l'événement (`dateDebut`).

  ------------------------------------------------------------------------
  Moment de la demande                Taux remboursé Impact sur les
                                                     billets et le stock
  --------------------- ---------------------------- ---------------------
  48 heures ou plus                            100 % Les billets passent à
  avant l'événement                                  `CANCELLED` et les
  (`≥ 48h`)                                          places redeviennent
                                                     vendables.

  Moins de 48 heures                             0 % Aucun remboursement.
  avant l'événement                                  Les billets restent
  (`< 48h`)                                          `VALID` pour le
                                                     client.

  Annulation par                   100 % automatique Tous les acheteurs
  l'organisateur                                     sont intégralement
                                                     remboursés et les
                                                     billets sont
                                                     invalidés.
  ------------------------------------------------------------------------

## 9. Calcul des promotions

Deux types de réductions sont prévus.

### Réduction en pourcentage (`PERCENTAGE`)

``` text
Remise = SousTotal × Valeur / 100
```

### Réduction à montant fixe (`FIXED_AMOUNT`)

``` text
Remise = Valeur
```

La remise fixe ne peut pas dépasser le sous-total.

### Montant final

``` text
Total = SousTotal - Remise
```

Le montant final ne peut jamais être inférieur à `0 MAD`.

Les montants monétaires sont manipulés avec `BigDecimal`.

## 10. Entités, attributs, méthodes et relations

### 10.1 Modèle des entités

  --------------------------------------------------------------------------------------------------------------------------------
  Entité / classe        Attributs essentiels              Méthodes de domaine                       Relations directes
  ---------------------- --------------------------------- ----------------------------------------- -----------------------------
  **Utilisateur          `id (Long)`, `nom (String)`,      `seConnecter()`, `seDeconnecter()`        Classe mère abstraite
  (abstraite)**          `email (String)`                                                            

  **Administrateur**     Hérite de `Utilisateur`           `gererUtilisateurs(): void`               Hérite de `Utilisateur`

  **Organisateur**       Hérite de `Utilisateur`,          `creerEvenement()`, `modifierEvenement()` `1 → 0..* Evenement`,
                         `nomOrganisation (String)`                                                  `1 → 0..* Promotion`

  **Client**             Hérite de `Utilisateur`,          `rechercherEvenements()`,                 `1 → 0..* Reservation`
                         `telephone (String)`              `reserverBillets()`                       

  **AgentControle**      Hérite de `Utilisateur`           `verifierBillet(code: String): boolean`   Hérite de `Utilisateur`

  **Evenement**          `id (Long)`, `titre`,             `publier()`, `annuler()`,                 `* → 1 Organisateur`,
                         `description`, `lieu`,            `verifierDisponibilite()`                 `1 → 1..* CategorieBillet`
                         `dateDebut`, `dateFin`,                                                     
                         `dateOuvertureVente`,                                                       
                         `dateClotureVente`, `statut`                                                

  **CategorieBillet**    `id`, `nom`,                      `verifierStock(quantite: int): boolean`   `* → 1 Evenement`,
                         `prixUnitaire (BigDecimal)`,                                                `1 → 0..* Billet`
                         `quantiteTotale`,                                                           
                         `quotaMaxParClient`                                                         

  **Promotion**          `id`, `code`, `typeReduction`,    `estValide(): boolean`,                   `* → 1 Organisateur`,
                         `valeurReduction (BigDecimal)`,   `calculerReduction(m: BigDecimal)`        `0..1 → 0..* Reservation`
                         `dateDebut`, `dateFin`,                                                     
                         `limiteUtilisation`,                                                        
                         `nombreUtilisations`                                                        

  **Reservation**        `id`, `reference`,                `calculerTotal()`, `confirmer()`,         `* → 1 Client`, `0..1 Promo`,
                         `dateCreation`, `dateExpiration`, `annuler()`, `expirer()`                  `1 → 1..* LigneReservation`
                         `statut`,                                                                   
                         `montantTotal (BigDecimal)`                                                 

  **LigneReservation**   `id`, `quantite`,                 `calculerSousTotal(): BigDecimal`         `* → 1 Reservation`,
                         `prixUnitaire (BigDecimal)`,                                                `* → 1 CategorieBillet`
                         `sousTotal (BigDecimal)`                                                    

  **Paiement**           `id`, `reference`,                `payer(): boolean`,                       `* → 1 Reservation`,
                         `montant (BigDecimal)`,           `rembourser(m: BigDecimal)`               `1 → 0..* Remboursement`
                         `datePaiement`, `statut`                                                    

  **Remboursement**      `id`, `montant (BigDecimal)`,     `demander()`, `approuver()`, `refuser()`  `* → 1 Paiement`
                         `motif`, `dateDemande`, `statut`                                            

  **Billet**             `id`, `code`, `dateEmission`,     `valider(): boolean`,                     `* → 1 LigneReservation`,
                         `dateControle`, `statut`          `controler(): boolean`                    `* → 1 CategorieBillet`
  --------------------------------------------------------------------------------------------------------------------------------

### 10.2 Énumérations associées

  -----------------------------------------------------------------------
  Énumération                         Valeurs
  ----------------------------------- -----------------------------------
  `StatutEvenement`                   `DRAFT`, `PUBLISHED`, `COMPLETED`,
                                      `CANCELLED`

  `TypeReduction`                     `PERCENTAGE`, `FIXED_AMOUNT`

  `StatutReservation`                 `PENDING_PAYMENT`, `CONFIRMED`,
                                      `EXPIRED`, `CANCELLED`, `REFUNDED`

  `StatutPaiement`                    `PENDING`, `SUCCESS`, `FAILED`

  `StatutRemboursement`               `PENDING`, `APPROVED`, `REJECTED`

  `StatutBillet`                      `VALID`, `USED`, `CANCELLED`
  -----------------------------------------------------------------------

## 11. Cartographie formelle des associations et multiplicités

### 11.1 Héritage et gestion événementielle

-   **Héritage polymorphique direct :** `Administrateur`,
    `Organisateur`, `Client` et `AgentControle` étendent la classe mère
    abstraite `Utilisateur`.
-   **Organisation (`1 → 0..*`) :** un organisateur gère zéro à
    plusieurs événements et crée zéro à plusieurs promotions.
-   **Composition tarifaire (`1 → 1..*`) :** un événement propose
    obligatoirement une ou plusieurs catégories de billets.

### 11.2 Réservation, paiement et compostage

-   **Panier (`1 → 1..*`) :** une réservation regroupe une ou plusieurs
    lignes de réservation. Chaque ligne pointe vers une catégorie de
    billets.
-   **Règlement et litiges (`1 → 0..*`) :** une réservation est
    acquittée via un paiement, qui peut faire l'objet de demandes de
    remboursement.
-   **Génération unitaire (`1 → 0..*`) :** chaque ligne de réservation
    confirmée engendre autant d'instances uniques de billets que la
    quantité achetée.

## 12. Arborescence et architecture du projet

Architecture **Package-by-Layer** prévue :

``` text
src/main/java/com/eventpro/
├── controller/       # Endpoints REST (Event, Reservation, Payment, Ticket, CheckIn)
├── service/          # Logique métier et verrous (Event, Reservation, Payment, Ticket, Promo)
├── repository/       # Spring Data JPA (Event, CategorieBillet, Reservation, Paiement, Billet)
├── entity/            # Entités JPA (Utilisateur, Evenement, CategorieBillet, Promotion, Reservation, etc.)
├── dto/               # DTOs Request/Response indépendants, assemblés via @Builder
├── mapper/            # MapStruct (EventMapper, ReservationMapper, PaymentMapper, TicketMapper)
├── scheduler/         # @Scheduled : expiration des réservations après 10 minutes
├── advice/            # @RestControllerAdvice global, ErrorResponse construit via Builder
└── exception/         # Exceptions métier (InsufficientStockException, ReservationExpiredException, etc.)
```

### Responsabilités des couches

-   **Controller :** expose les endpoints REST et reçoit les requêtes
    HTTP.
-   **Service :** porte les règles métier, orchestre les opérations et
    applique les règles de concurrence.
-   **Repository :** assure l'accès aux données via Spring Data JPA.
-   **Entity :** représente les objets persistés en base de données.
-   **DTO :** définit les structures de données d'entrée et de sortie de
    l'API.
-   **Mapper :** convertit les entités en DTOs et inversement avec
    MapStruct.
-   **Scheduler :** recherche et expire automatiquement les réservations
    non payées.
-   **Advice :** centralise la gestion des exceptions HTTP.
-   **Exception :** contient les exceptions spécifiques aux règles
    métier.

## 13. DTOs et gestion des erreurs HTTP

### 13.1 DTOs de requête (Request DTO)

  -----------------------------------------------------------------------
  DTO                                 Données attendues
  ----------------------------------- -----------------------------------
  `ReservationRequest`                ID de l'événement, lignes
                                      (catégorie et quantité), code
                                      promotionnel optionnel

  `PaymentRequest`                    Clé d'idempotence, montant

  `EventCreateRequest`                Titre, lieu, dates et catégories
  -----------------------------------------------------------------------

### 13.2 DTOs de réponse (Response DTO)

  -----------------------------------------------------------------------
  DTO                                 Données renvoyées
  ----------------------------------- -----------------------------------
  `ReservationResponse`               Référence, échéance de 10 minutes,
                                      statut et montants

  `TicketResponse`                    Code unique, statut d'accès et
                                      porteur
  -----------------------------------------------------------------------

**Construction :** les DTOs sont construits avec le pattern Builder,
notamment via Lombok `@Builder`.

### 13.3 Codes HTTP et erreurs

  --------------------------------------------------------------------------------------
  Code HTTP                     Erreur / réponse                 Situation
  ----------------------------- -------------------------------- -----------------------
  `200 OK` / `201 Created`      `SuccessResponse<T>` / DTO       L'opération a réussi :
                                nominal                          lecture de données ou
                                                                 création d'une
                                                                 ressource.

  `400 Bad Request`             `ValidationException`            Informations
                                                                 incorrectes, par
                                                                 exemple quantité
                                                                 négative ou dates
                                                                 illogiques.

  `404 Not Found`               `ResourceNotFoundException`      L'événement, le billet
                                                                 ou la réservation
                                                                 demandée n'existe pas.

  `409 Conflict`                `InsufficientStockException`     Il n'y a plus assez de
                                                                 places libres pour
                                                                 valider la réservation.

  `409 Conflict`                `TicketAlreadyUsedException`     Le billet présenté a
                                                                 déjà été scanné
                                                                 (`USED`).

  `409 Conflict`                `IdempotencyConflictException`   La même clé de paiement
                                                                 est réutilisée avec un
                                                                 panier différent.

  `409 Conflict`                `ReservationExpiredException`    Le paiement arrive trop
                                                                 tard : le délai de 10
                                                                 minutes est dépassé.

  `500 Internal Server Error`   Exception système imprévue       Erreur serveur
                                                                 inattendue. Le message
                                                                 ne doit pas dévoiler le
                                                                 code source.
  --------------------------------------------------------------------------------------

### 13.4 Gestion globale des erreurs

La gestion des exceptions est centralisée avec `@RestControllerAdvice`
dans `GlobalExceptionHandler`.

L'objet JSON standard d'erreur comprend :

``` json
{
  "timestamp": "...",
  "status": 400,
  "error": "...",
  "message": "...",
  "path": "...",
  "fieldErrors": []
}
```

Les valeurs ci-dessus illustrent la structure attendue ; elles sont
renseignées selon l'erreur réellement rencontrée.

## 14. Automatisation et stratégie de tests

### 14.1 Expiration automatique des réservations

Une tâche planifiée avec `@Scheduled` :

1.  Recherche les réservations `PENDING_PAYMENT` dont le délai de 10
    minutes est dépassé.
2.  Les fait passer au statut `EXPIRED`.
3.  Restitue les places correspondantes afin qu'elles soient de nouveau
    disponibles.

### 14.2 Tests unitaires et tests d'intégration

La stratégie de tests comprend :

-   **JUnit 5 :** structure et exécution des tests.
-   **Mockito :** tests unitaires des services et des règles métier.
-   **Testcontainers PostgreSQL (bonus) :** tests d'intégration avec une
    vraie instance PostgreSQL conteneurisée.
-   **Tests métier :** calculs de prix, réductions et remboursements.
-   **Tests de concurrence :** vérifier que deux réservations
    concurrentes ne peuvent pas vendre la dernière place simultanément.

### 14.3 Exécution des tests

``` bash
mvn test
```

------------------------------------------------------------------------

## Règles essentielles à respecter

1.  **Aucune survente :** le stock doit être protégé par des
    transactions et un verrou pessimiste.
2.  **Réservation temporaire :** les places sont bloquées pendant 10
    minutes maximum sans paiement.
3.  **Paiement contrôlé :** le paiement doit être vérifié avant la
    confirmation de la réservation.
4.  **Billets uniques :** chaque billet possède un code unique et ne
    peut être utilisé qu'une seule fois.
5.  **Remboursement encadré :** remboursement client à 100 % uniquement
    à 48 heures ou plus de l'événement ; annulation organisateur
    remboursée intégralement.
6.  **Calculs monétaires précis :** utiliser `BigDecimal` et la devise
    MAD.
7.  **Erreurs cohérentes :** centraliser les réponses d'erreur avec
    `@RestControllerAdvice`.
8.  **Stock libéré automatiquement :** les réservations non payées
    doivent expirer et rendre leurs places disponibles.
