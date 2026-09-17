package com.benjeddou.erp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "commandes_achat")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CommandeAchat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_commande", length = 50, unique = true, nullable = false)
    private String numeroCommande;

    @Column(name = "date_commande")
    @Builder.Default
    private LocalDateTime dateCommande = LocalDateTime.now();

    /** EN_ATTENTE, ENVOYEE, RECUE_PARTIELLE, RECUE_TOTALE, ANNULEE */
    @Column(length = 30, nullable = false)
    @Builder.Default
    private String statut = "EN_ATTENTE";

    @Column(name = "montant_total", precision = 15, scale = 3)
    @Builder.Default
    private BigDecimal montantTotal = BigDecimal.ZERO;

    @Column(length = 500)
    private String notes;

    @Column(name = "date_livraison_prevue")
    private LocalDateTime dateLivraisonPrevue;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fournisseur_id", nullable = false)
    private Fournisseur fournisseur;

    @OneToMany(mappedBy = "commandeAchat", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LigneCommandeAchat> lignes;

    @CreationTimestamp
    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;

    @UpdateTimestamp
    @Column(name = "date_modification")
    private LocalDateTime dateModification;
}
