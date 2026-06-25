package com.benjeddou.erp.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "documents_kyc")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentKyc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    /** Type de document : CNI, PASSEPORT, REGISTRE_COMMERCE, PATENTE */
    @Column(name = "type_document", length = 50)
    private String typeDocument;

    /** Nom du fichier original */
    @Column(name = "nom_fichier", length = 255)
    private String nomFichier;

    /** Type MIME du fichier (image/jpeg, application/pdf, etc.) */
    @Column(name = "content_type", length = 100)
    private String contentType;

    /** Contenu binaire du fichier stocké en base (BLOB) */
    @Lob
    @Basic(fetch = FetchType.EAGER)
    @Column(name = "contenu_fichier", columnDefinition = "LONGBLOB")
    private byte[] contenuFichier;

    /** Statut de vérification : EN_ATTENTE, VALIDE, REFUSE */
    @Column(name = "statut_verification", length = 20)
    @Builder.Default
    private String statutVerification = "EN_ATTENTE";

    @CreationTimestamp
    @Column(name = "date_soumission", updatable = false)
    private LocalDateTime dateSoumission;

    @Column(name = "commentaire_admin", length = 500)
    private String commentaireAdmin;
}
