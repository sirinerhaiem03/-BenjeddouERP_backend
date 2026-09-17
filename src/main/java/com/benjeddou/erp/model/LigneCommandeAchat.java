package com.benjeddou.erp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "lignes_commande_achat")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LigneCommandeAchat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_achat_id", nullable = false)
    private CommandeAchat commandeAchat;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "produit_id")
    private Produit produit;

    @Column(name = "designation", length = 200)
    private String designation;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantite = 1;

    @Column(name = "prix_unitaire", precision = 15, scale = 3)
    @Builder.Default
    private BigDecimal prixUnitaire = BigDecimal.ZERO;

    @Column(name = "montant_ligne", precision = 15, scale = 3)
    @Builder.Default
    private BigDecimal montantLigne = BigDecimal.ZERO;
}
