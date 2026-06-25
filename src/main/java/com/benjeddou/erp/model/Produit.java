package com.benjeddou.erp.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "produits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Produit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, nullable = false)
    private String nom;

    @Column(length = 50, unique = true, nullable = false)
    private String reference;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "prix_unitaire", precision = 15, scale = 3, nullable = false)
    private BigDecimal prixUnitaire;

    @Column(name = "prix_achat", precision = 15, scale = 3, nullable = false)
    private BigDecimal prixAchat;

    @Column(name = "quantite_stock")
    @Builder.Default
    private Integer quantiteStock = 0;

    @Column(name = "seuil_stock_min")
    @Builder.Default
    private Integer seuilStockMin = 5;

    @Column(length = 50)
    private String categorie;

    @CreationTimestamp
    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;

    @UpdateTimestamp
    @Column(name = "date_modification")
    private LocalDateTime dateModification;
}
