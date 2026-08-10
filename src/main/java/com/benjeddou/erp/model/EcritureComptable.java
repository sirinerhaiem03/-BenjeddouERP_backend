package com.benjeddou.erp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "ecritures_comptables")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EcritureComptable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_ecriture", length = 50, unique = true)
    private String numeroEcriture;

    @Column(name = "date_ecriture", nullable = false)
    private LocalDate dateEcriture;

    @Column(length = 300, nullable = false)
    private String libelle;

    /** VENTE, ACHAT, TRESORERIE, SALAIRE, CHARGE, AUTRE */
    @Column(length = 30)
    @Builder.Default
    private String typeEcriture = "AUTRE";

    /** DEBIT ou CREDIT */
    @Column(length = 10)
    @Builder.Default
    private String sens = "DEBIT";

    @Column(precision = 15, scale = 3)
    @Builder.Default
    private BigDecimal montant = BigDecimal.ZERO;

    /** Compte comptable (ex: 411, 701, 512...) */
    @Column(name = "compte_comptable", length = 10)
    private String compteComptable;

    /** Référence liée (numéro facture, commande, etc.) */
    @Column(name = "reference_piece", length = 100)
    private String referencePiece;

    /** BROUILLON, VALIDE */
    @Column(length = 15)
    @Builder.Default
    private String statut = "BROUILLON";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facture_id")
    @JsonIgnoreProperties({"lignesCommande", "commande", "hibernateLazyInitializer"})
    private Facture facture;

    @CreationTimestamp
    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;
}
