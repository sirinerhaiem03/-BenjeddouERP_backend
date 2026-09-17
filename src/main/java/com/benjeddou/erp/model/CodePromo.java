package com.benjeddou.erp.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "codes_promo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodePromo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Le code unique que le client saisit (ex: SUMMER20) */
    @Column(name = "code", length = 50, unique = true, nullable = false)
    private String code;

    @Column(length = 255)
    private String description;

    /**
     * Type de remise :
     * POURCENTAGE → valeur = % (ex: 15 = 15%)
     * MONTANT_FIXE → valeur = TND (ex: 10 = 10 TND)
     */
    @Column(name = "type_remise", length = 20, nullable = false)
    @Builder.Default
    private String typeRemise = "POURCENTAGE"; // POURCENTAGE | MONTANT_FIXE

    @Column(name = "valeur", precision = 10, scale = 3, nullable = false)
    private BigDecimal valeur;

    /** Montant minimum de commande pour activer ce code */
    @Column(name = "montant_minimum", precision = 15, scale = 3)
    @Builder.Default
    private BigDecimal montantMinimum = BigDecimal.ZERO;

    /** Plafond de remise (pour les %, ex : max 50 TND). Null = illimité */
    @Column(name = "plafond_remise", precision = 15, scale = 3)
    private BigDecimal plafondRemise;

    @Column(name = "date_debut")
    private LocalDateTime dateDebut;

    @Column(name = "date_fin")
    private LocalDateTime dateFin;

    /** Nombre maximum d'utilisations (null = illimité) */
    @Column(name = "utilisations_max")
    private Integer utilisationsMax;

    /** Compteur d'utilisations réelles */
    @Column(name = "utilisations_actuelles", nullable = false)
    @Builder.Default
    private Integer utilisationsActuelles = 0;

    /** Actif/désactivé manuellement */
    @Column(nullable = false)
    @Builder.Default
    private Boolean actif = true;

    @CreationTimestamp
    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;

    @UpdateTimestamp
    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    // ── Méthodes métier ────────────────────────────────────────────────────────

    /** Calcule la remise applicable sur un montant donné */
    public BigDecimal calculerRemise(BigDecimal montantCommande) {
        BigDecimal remise;
        if ("POURCENTAGE".equals(typeRemise)) {
            remise = montantCommande.multiply(valeur).divide(BigDecimal.valueOf(100));
            // Appliquer le plafond si défini
            if (plafondRemise != null && remise.compareTo(plafondRemise) > 0) {
                remise = plafondRemise;
            }
        } else {
            remise = valeur;
        }
        // La remise ne peut pas dépasser le montant de la commande
        return remise.min(montantCommande).setScale(3, java.math.RoundingMode.HALF_UP);
    }

    /** Vérifie si ce code est utilisable (toutes conditions) */
    public boolean estValide(BigDecimal montantCommande) {
        if (!Boolean.TRUE.equals(actif)) return false;
        LocalDateTime now = LocalDateTime.now();
        if (dateDebut != null && now.isBefore(dateDebut)) return false;
        if (dateFin != null && now.isAfter(dateFin)) return false;
        if (utilisationsMax != null && utilisationsActuelles >= utilisationsMax) return false;
        if (montantMinimum != null && montantCommande.compareTo(montantMinimum) < 0) return false;
        return true;
    }

    /** Statut lisible */
    public String getStatutCalcule() {
        if (!Boolean.TRUE.equals(actif)) return "DESACTIVE";
        LocalDateTime now = LocalDateTime.now();
        if (dateFin != null && now.isAfter(dateFin)) return "EXPIRE";
        if (dateDebut != null && now.isBefore(dateDebut)) return "PLANIFIE";
        if (utilisationsMax != null && utilisationsActuelles >= utilisationsMax) return "EPUISE";
        return "ACTIF";
    }
}
