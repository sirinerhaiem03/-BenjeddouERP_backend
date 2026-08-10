package com.benjeddou.erp.service;

import com.benjeddou.erp.model.DocumentGenere;
import com.benjeddou.erp.model.DocumentGenere.StatutDocument;
import com.benjeddou.erp.model.VersionDocument;
import com.benjeddou.erp.model.Utilisateur;
import com.benjeddou.erp.repository.DocumentGenereRepository;
import com.benjeddou.erp.repository.VersionDocumentRepository;
import com.benjeddou.erp.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service de gestion des versions des documents générés.
 * Permet de sauvegarder, lister et restaurer des versions antérieures.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentVersionService {

    private final DocumentGenereRepository documentGenereRepository;
    private final VersionDocumentRepository versionDocumentRepository;
    private final UtilisateurRepository utilisateurRepository;

    /**
     * Sauvegarde la version courante d'un document avant modification.
     * À appeler avant chaque modification importante d'un document.
     */
    public VersionDocument sauvegarderVersion(Long documentId, String commentaire, String nomUtilisateur) {
        DocumentGenere doc = documentGenereRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document introuvable : " + documentId));

        Utilisateur modificateur = utilisateurRepository.findByNomUtilisateur(nomUtilisateur).orElse(null);

        VersionDocument version = VersionDocument.builder()
                .document(doc)
                .numeroVersion(doc.getVersion())
                .contenuBlob(doc.getContenuDocx().clone())
                .commentaire(commentaire)
                .modifiePar(modificateur)
                .build();

        VersionDocument saved = versionDocumentRepository.save(version);
        log.info("Version {} sauvegardée pour document {}", doc.getVersion(), documentId);

        // Incrémenter le numéro de version du document
        doc.setVersion(doc.getVersion() + 1);
        documentGenereRepository.save(doc);

        return saved;
    }

    /** Retourne l'historique des versions d'un document */
    public List<VersionDocument> listerVersions(Long documentId) {
        return versionDocumentRepository.findByDocumentIdOrderByNumeroVersionDesc(documentId);
    }

    /**
     * Restaure une version antérieure d'un document.
     * Sauvegarde d'abord la version actuelle avant de restaurer.
     */
    public DocumentGenere restaurerVersion(Long versionId, String nomUtilisateur) {
        VersionDocument version = versionDocumentRepository.findById(versionId)
                .orElseThrow(() -> new RuntimeException("Version introuvable : " + versionId));

        DocumentGenere doc = version.getDocument();

        // Sauvegarder la version actuelle avant restauration
        sauvegarderVersion(doc.getId(), "Sauvegarde avant restauration v" + version.getNumeroVersion(), nomUtilisateur);

        // Restaurer le contenu de la version sélectionnée
        doc.setContenuDocx(version.getContenuBlob().clone());
        doc.setContenuPdf(null); // Invalider le PDF — sera régénéré si nécessaire

        DocumentGenere restored = documentGenereRepository.save(doc);
        log.info("Document {} restauré à la version {}", doc.getId(), version.getNumeroVersion());
        return restored;
    }

    /** Nombre de versions d'un document */
    public int nombreVersions(Long documentId) {
        return versionDocumentRepository.countByDocumentId(documentId);
    }
}
