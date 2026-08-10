package com.benjeddou.erp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Modèle de document Word (.docx) uploadé par l'administrateur.
 * Contient des placeholders de type {{nom_client}}, {{montant}}, etc.
 * Utilisé comme base pour la génération automatique de documents.
 */
@Entity
@Table(name = "modeles_documents")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModeleDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nom du modèle (ex: "Facture standard FR", "Contrat de prestation") */
    @Column(nullable = false, length = 255)
    private String nom;

    /** Description métier du modèle */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Catégorie du document.
     * Ex: FACTURE, DEVIS, CONTRAT, BON_COMMANDE, BON_LIVRAISON, ATTESTATION, RAPPORT
     */
    @Column(length = 100)
    private String categorie;

    /**
     * Langue principale du modèle.
     * Ex: fr, ar, en
     */
    @Column(length = 10)
    private String langue;

    /** Contenu binaire du fichier .docx — exclu de la sérialisation JSON (téléchargement via endpoint dédié) */
    @JsonIgnore
    @Lob
    @Column(name = "contenu_blob", columnDefinition = "LONGBLOB")
    private byte[] contenuBlob;

    /**
     * Liste des placeholders détectés dans le modèle, stockée en JSON.
     * Ex: ["{{nom_client}}", "{{date_facture}}", "{{montant_total}}"]
     */
    @Column(name = "placeholders", columnDefinition = "TEXT")
    private String placeholders;

    /**
     * Module ERP source auquel ce modèle est associé.
     * Ex: COMMERCIAL, ACHATS, RH, COMPTABILITE, GLOBAL
     */
    @Column(name = "module_source", length = 100)
    private String moduleSource;

    /** Indique si le modèle est actif et disponible pour génération */
    @Column(nullable = false)
    @Builder.Default
    private Boolean actif = true;

    /** Nom original du fichier uploadé */
    @Column(name = "nom_fichier_original", length = 255)
    private String nomFichierOriginal;

    /** Taille du fichier en octets */
    @Column(name = "taille_fichier")
    private Long tailleFichier;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cree_par_id")
    private Utilisateur creePar;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        dateModification = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = LocalDateTime.now();
    }
}
