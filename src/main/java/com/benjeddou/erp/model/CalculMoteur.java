package com.benjeddou.erp.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * CalculMoteur — Enregistrement de chaque calcul effectué dans l'historique.
 * Supporte deux modes : TAUX_UNIQUE et TAUX_VARIABLE.
 * Pour TAUX_VARIABLE, les lignes de détail sont dans LigneCalcul.
 */
@Entity
@Table(name = "calculs_moteur")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CalculMoteur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Référence unique auto-générée : CM-YYYYMMDD-XXXX
     * ex: CM-20260709-0001
     */
    @Column(name = "reference", length = 30, unique = true, nullable = false)
    private String reference;

    /**
     * Type de calcul : TAUX_UNIQUE ou TAUX_VARIABLE
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type_calcul", length = 20, nullable = false)
    private TypeCalcul typeCalcul;

    /** Montant de base du calcul */
    @Column(name = "montant", nullable = false, precision = 15, scale = 3)
    private BigDecimal montant;

    /** Date de début de la période globale */
    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    /** Date de fin de la période globale */
    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    /** Nombre de jours total (inclusif : fin - début + 1) */
    @Column(name = "nombre_jours", nullable = false)
    private Long nombreJours;

    /**
     * Taux unique en % — seulement renseigné pour TAUX_UNIQUE.
     * Null pour TAUX_VARIABLE.
     */
    @Column(name = "taux_unique", precision = 5, scale = 2)
    private BigDecimal tauxUnique;

    /** Résultat total calculé — arrondi 2 décimales */
    @Column(name = "resultat_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal resultatTotal;

    /**
     * Module ERP d'origine : RH, COMPTABILITE, FINANCE, VENTES, ACHATS, TRESORERIE, etc.
     * Permet de filtrer l'historique par module.
     */
    @Column(name = "module_erp", length = 50)
    @Builder.Default
    private String moduleErp = "GENERAL";

    /** Libellé libre — description du calcul */
    @Column(name = "libelle", length = 300)
    private String libelle;

    /** Utilisateur ayant effectué le calcul */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cree_par_id")
    private Utilisateur creePar;

    /** Lignes de détail (pour TAUX_VARIABLE) */
    @OneToMany(mappedBy = "calcul", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<LigneCalcul> lignes = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;

    /** Enum des types de calcul supportés */
    public enum TypeCalcul {
        TAUX_UNIQUE,
        TAUX_VARIABLE
    }
}
