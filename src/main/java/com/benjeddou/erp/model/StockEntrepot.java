package com.benjeddou.erp.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "stock_entrepots", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"produit_id", "entrepot_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockEntrepot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "produit_id", nullable = false)
    private Produit produit;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "entrepot_id", nullable = false)
    private Entrepot entrepot;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantite = 0;
}
