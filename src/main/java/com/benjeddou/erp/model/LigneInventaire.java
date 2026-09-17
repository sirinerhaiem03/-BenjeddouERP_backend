package com.benjeddou.erp.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ligne_inventaires", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"inventaire_id", "produit_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LigneInventaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventaire_id", nullable = false)
    private Inventaire inventaire;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "produit_id", nullable = false)
    private Produit produit;

    @Column(name = "quantite_theorique", nullable = false)
    private Integer quantiteTheorique;

    @Column(name = "quantite_physique", nullable = false)
    private Integer quantitePhysique;
}
