package com.benjeddou.erp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Version archivée d'un document généré.
 * Chaque modification d'un DocumentGenere crée une entrée ici.
 * Permet de restaurer une version antérieure.
 */
@Entity
@Table(name = "versions_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Document parent dont cette entrée est une version */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private DocumentGenere document;

    /** Numéro de version (1, 2, 3, ...) */
    @Column(name = "numero_version", nullable = false)
    private Integer numeroVersion;

    /** Contenu binaire du .docx à cette version — exclu de la sérialisation JSON */
    @JsonIgnore
    @Lob
    @Column(name = "contenu_blob", columnDefinition = "LONGBLOB")
    private byte[] contenuBlob;

    /** Commentaire décrivant les modifications de cette version */
    @Column(columnDefinition = "TEXT")
    private String commentaire;

    /** Utilisateur ayant créé cette version */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modifie_par_id")
    private Utilisateur modifiePar;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
    }
}
