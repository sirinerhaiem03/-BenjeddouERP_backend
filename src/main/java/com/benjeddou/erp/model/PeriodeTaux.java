package com.benjeddou.erp.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * PeriodeTaux — Base de référence des périodes et taux.
 * Gérée par l'administrateur. Utilisée par le moteur de calcul (mode taux variables).
 * Formule : Résultat = Montant × (Taux / 100) × (Nombre de jours / 365)
 */
@Entity
@Table(name = "periodes_taux")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PeriodeTaux {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    /**
     * Taux en pourcentage — ex: 9.75 signifie 9,75%
     * Precision 5,2 : max 999.99%
     */
    @Column(name = "taux", nullable = false, precision = 5, scale = 2)
    private BigDecimal taux;

    @Column(name = "libelle", length = 200)
    private String libelle;

    /** Si false, la période est désactivée et non utilisée dans les calculs */
    @Column(name = "actif", nullable = false)
    @Builder.Default
    private boolean actif = true;

    @CreationTimestamp
    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;

    @UpdateTimestamp
    @Column(name = "date_modification")
    private LocalDateTime dateModification;
}
