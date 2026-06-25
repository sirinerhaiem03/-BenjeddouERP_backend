package com.benjeddou.erp.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "factures")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Facture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_facture", length = 50, unique = true, nullable = false)
    private String numeroFacture;

    @Column(name = "date_emission")
    @Builder.Default
    private LocalDateTime dateEmission = LocalDateTime.now();

    @Column(name = "date_echeance")
    private LocalDateTime dateEcheance;

    @Column(name = "montant_total", precision = 15, scale = 3, nullable = false)
    private BigDecimal montantTotal;

    @Column(name = "montant_tva", precision = 15, scale = 3, nullable = false)
    private BigDecimal montantTva;

    @Column(length = 20, nullable = false)
    @Builder.Default
    private String statut = "EN_ATTENTE"; // EN_ATTENTE, PAYEE, ANNULEE, IMPAYEE

    @Column(name = "signature_numerique", length = 512)
    private String signatureNumerique;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "commande_id")
    private Commande commande;

    @CreationTimestamp
    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;

    @UpdateTimestamp
    @Column(name = "date_modification")
    private LocalDateTime dateModification;
}
