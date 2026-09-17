package com.benjeddou.erp.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "commandes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Commande {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_commande", length = 50, unique = true, nullable = false)
    private String numeroCommande;

    @Column(name = "date_commande")
    @Builder.Default
    private LocalDateTime dateCommande = LocalDateTime.now();

    @Column(length = 20, nullable = false)
    @Builder.Default
    private String statut = "EN_ATTENTE"; // EN_ATTENTE, PAYEE, ANNULEE

    @Column(name = "montant_total", precision = 15, scale = 3, nullable = false)
    private BigDecimal montantTotal;

    /** Code promo appliqué à cette commande (null si aucun) */
    @Column(name = "code_promo_applique", length = 50)
    private String codePromoApplique;

    /** Montant de la remise promo déduite (0 si aucun code) */
    @Column(name = "remise_promo", precision = 15, scale = 3)
    @Builder.Default
    private BigDecimal remisePromo = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "client_id")
    private Client client;

    @CreationTimestamp
    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;

    @UpdateTimestamp
    @Column(name = "date_modification")
    private LocalDateTime dateModification;
}
