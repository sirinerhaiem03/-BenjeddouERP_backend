package com.benjeddou.erp.model;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * LigneCalcul — Détail d'une sous-période pour le mode TAUX_VARIABLE.
 * Chaque ligne représente un segment de la période globale avec son propre taux.
 * Calcul de la ligne : montantBase × (taux / 100) × (nombreJours / 365)
 */
@Entity
@Table(name = "lignes_calcul")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class LigneCalcul {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Calcul parent */
    @JsonIgnoreProperties({"lignes", "creePar", "hibernateLazyInitializer", "handler"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calcul_id", nullable = false)
    private CalculMoteur calcul;

    /** Numéro d'ordre de la ligne dans le calcul (1, 2, 3...) */
    @Column(name = "numero_ligne", nullable = false)
    private Integer numeroLigne;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    /** Nombre de jours de cette sous-période */
    @Column(name = "nombre_jours", nullable = false)
    private Long nombreJours;

    /** Taux applicable sur cette période (en %) */
    @Column(name = "taux", nullable = false, precision = 5, scale = 2)
    private BigDecimal taux;

    /** Montant de base utilisé pour ce calcul (= montant principal) */
    @Column(name = "montant_base", nullable = false, precision = 15, scale = 3)
    private BigDecimal montantBase;

    /** Résultat de cette ligne : montantBase × (taux/100) × (nombreJours/365) */
    @Column(name = "resultat_ligne", nullable = false, precision = 15, scale = 2)
    private BigDecimal resultatLigne;

    /** Libellé de la période source (référence PeriodeTaux.libelle) */
    @Column(name = "libelle_periode", length = 200)
    private String libellePeriode;
}
