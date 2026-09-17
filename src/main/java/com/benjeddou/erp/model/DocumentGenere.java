package com.benjeddou.erp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Document Word généré automatiquement à partir d'un modèle et de données métier.
 * Stocke à la fois le .docx et le PDF généré.
 * Lié à une entité métier (facture, commande, devis, etc.) via entiteId.
 */
@Entity
@Table(name = "documents_generes")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentGenere {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Modèle utilisé pour la génération */
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "creePar", "contenuBlob"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modele_id")
    private ModeleDocument modele;

    /** Titre descriptif du document généré */
    @Column(name = "titre_document", nullable = false, length = 255)
    private String titreDocument;

    /** Contenu binaire du fichier .docx généré — exclu de la sérialisation JSON */
    @JsonIgnore
    @Lob
    @Column(name = "contenu_docx", columnDefinition = "LONGBLOB")
    private byte[] contenuDocx;

    /** Contenu binaire du PDF généré — exclu de la sérialisation JSON */
    @JsonIgnore
    @Lob
    @Column(name = "contenu_pdf", columnDefinition = "LONGBLOB")
    private byte[] contenuPdf;

    /**
     * Module ERP source.
     * Ex: COMMERCIAL, ACHATS, RH, COMPTABILITE
     */
    @Column(name = "module_source", length = 100)
    private String moduleSource;

    /**
     * ID de l'entité métier associée.
     * Ex: l'ID de la facture, de la commande, du devis, etc.
     */
    @Column(name = "entite_id")
    private Long entiteId;

    /** Langue du document généré */
    @Column(length = 10)
    @Builder.Default
    private String langue = "fr";

    /**
     * Statut du document.
     * GENERE → SIGNE → ARCHIVE
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    @Builder.Default
    private StatutDocument statut = StatutDocument.GENERE;

    /**
     * Données utilisées pour la fusion (JSON).
     * Ex: {"nom_client": "SOTRAPIL", "montant": "1500.00"}
     * Utile pour régénération ou audit.
     */
    @Column(name = "donnees_fusion", columnDefinition = "TEXT")
    private String donneesFusion;

    /**
     * Image de signature électronique en base64.
     * Appliquée sur le PDF lors de la signature.
     */
    @Column(name = "signature_base64", columnDefinition = "TEXT")
    private String signatureBase64;

    /** Date et position de signature */
    @Column(name = "date_signature")
    private LocalDateTime dateSignature;

    /** Numéro de version courant de ce document */
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    /** Utilisateur qui a généré ce document */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "genere_par_id")
    private Utilisateur generePar;

    @Column(name = "date_generation", nullable = false, updatable = false)
    private LocalDateTime dateGeneration;

    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    @PrePersist
    protected void onCreate() {
        dateGeneration = LocalDateTime.now();
        dateModification = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = LocalDateTime.now();
    }

    /** Statuts possibles d'un document généré */
    public enum StatutDocument {
        GENERE, SIGNE, ARCHIVE
    }
}
