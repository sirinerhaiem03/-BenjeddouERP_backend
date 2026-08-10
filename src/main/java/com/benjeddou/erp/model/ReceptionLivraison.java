package com.benjeddou.erp.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "receptions_livraison")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReceptionLivraison {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_reception", length = 50, unique = true)
    private String numeroReception;

    @Column(name = "date_reception")
    @Builder.Default
    private LocalDateTime dateReception = LocalDateTime.now();

    /** CONFORME, PARTIELLE, NON_CONFORME */
    @Column(length = 20)
    @Builder.Default
    private String statut = "CONFORME";

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "commande_achat_id", nullable = false)
    private CommandeAchat commandeAchat;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "produit_id")
    private Produit produit;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "entrepot_id")
    private Entrepot entrepot;

    @Column(name = "quantite_commandee")
    private Integer quantiteCommandee;

    @Column(name = "quantite_recue")
    private Integer quantiteRecue;

    @Column(length = 500)
    private String observations;

    @CreationTimestamp
    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;
}
