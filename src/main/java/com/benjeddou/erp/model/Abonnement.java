package com.benjeddou.erp.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Représente un abonnement souscrit par un client.
 */
@Entity
@Table(name = "abonnements")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Abonnement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Client propriétaire de cet abonnement */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Utilisateur client;

    /** Type de plan : MENSUEL, TRIMESTRIEL, ANNUEL */
    @Enumerated(EnumType.STRING)
    @Column(name = "type_plan", length = 20, nullable = false)
    private TypePlanAbonnement typePlan;

    /** Prix payé en DT */
    @Column(name = "prix", precision = 10, scale = 3)
    private BigDecimal prix;

    /** Durée en mois */
    @Column(name = "duree_mois")
    private Integer dureeMois;

    /** Statut : EN_ATTENTE, VALIDE, ACTIF, EXPIRE, ANNULE */
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", length = 20)
    @Builder.Default
    private StatutAbonnement statut = StatutAbonnement.EN_ATTENTE;

    /** Méthode de paiement : CARTE, VIREMENT, CHEQUE, ESPECES */
    @Column(name = "methode_paiement", length = 30)
    private String methodePaiement;

    /** Référence de la transaction / numéro de chèque */
    @Column(name = "reference_paiement", length = 100)
    private String referencePaiement;

    /** Date de début d'activité */
    @Column(name = "date_debut")
    private LocalDateTime dateDebut;

    /** Date d'expiration */
    @Column(name = "date_fin")
    private LocalDateTime dateFin;

    /** Notes admin */
    @Column(name = "notes_admin", length = 500)
    private String notesAdmin;

    @CreationTimestamp
    @Column(name = "date_soumission", updatable = false)
    private LocalDateTime dateSoumission;
}
