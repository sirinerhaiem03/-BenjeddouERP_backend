package com.benjeddou.erp.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "mouvements_stock")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MouvementStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "produit_id", nullable = false)
    private Produit produit;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "entrepot_id", nullable = false)
    private Entrepot entrepot;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_mouvement", length = 20, nullable = false)
    private TypeMouvement typeMouvement;

    @Column(nullable = false)
    private Integer quantite;

    @Column(length = 255)
    private String description;

    @CreationTimestamp
    @Column(name = "date_mouvement", updatable = false)
    private LocalDateTime dateMouvement;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur utilisateur;
}
