package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.ModeleDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModeleDocumentRepository extends JpaRepository<ModeleDocument, Long> {

    /** Trouve tous les modèles actifs */
    List<ModeleDocument> findByActifTrue();

    /** Trouve les modèles actifs par module source */
    List<ModeleDocument> findByModuleSourceAndActifTrue(String moduleSource);

    /** Trouve les modèles actifs par catégorie */
    List<ModeleDocument> findByCategorieAndActifTrue(String categorie);

    /** Trouve les modèles actifs par langue */
    List<ModeleDocument> findByLangueAndActifTrue(String langue);

    /** Recherche combinée module + catégorie */
    List<ModeleDocument> findByModuleSourceAndCategorieAndActifTrue(String moduleSource, String categorie);

    /** Compte les modèles actifs par module */
    @Query("SELECT m.moduleSource, COUNT(m) FROM ModeleDocument m WHERE m.actif = true GROUP BY m.moduleSource")
    List<Object[]> countByModule();
}
