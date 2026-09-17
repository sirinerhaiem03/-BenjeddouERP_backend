package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.VersionDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VersionDocumentRepository extends JpaRepository<VersionDocument, Long> {

    /** Toutes les versions d'un document, triées par numéro de version */
    List<VersionDocument> findByDocumentIdOrderByNumeroVersionDesc(Long documentId);

    /** Dernière version d'un document */
    VersionDocument findTopByDocumentIdOrderByNumeroVersionDesc(Long documentId);

    /** Nombre de versions d'un document */
    int countByDocumentId(Long documentId);
}
