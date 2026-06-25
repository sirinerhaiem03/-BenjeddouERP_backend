package com.benjeddou.erp.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "devis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Devis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_devis", length = 50, unique = true, nullable = false)
    private String numeroDevis;

    @Column(name = "date_devis")
    @Builder.Default
    private LocalDateTime dateDevis = LocalDateTime.now();

    @Column(name = "date_validite")
    private LocalDateTime dateValidite;

    @Column(length = 20, nullable = false)
    @Builder.Default
    private String statut = "BROUILLON"; // BROUILLON, ENVOYE, ACCEPTE, REFUSE

    @Column(name = "montant_total", precision = 15, scale = 3, nullable = false)
    @Builder.Default
    private BigDecimal montantTotal = BigDecimal.ZERO;

    @Column(length = 500)
    private String notes;

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
