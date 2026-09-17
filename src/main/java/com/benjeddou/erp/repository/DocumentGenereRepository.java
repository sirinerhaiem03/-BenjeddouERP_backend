package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.DocumentGenere;
import com.benjeddou.erp.model.DocumentGenere.StatutDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentGenereRepository extends JpaRepository<DocumentGenere, Long> {

    /** Tous les documents d'un utilisateur */
    List<DocumentGenere> findByGenereParIdOrderByDateGenerationDesc(Long userId);

    /** Documents liés à une entité métier (factureId, commandeId, etc.) */
    List<DocumentGenere> findByModuleSourceAndEntiteId(String moduleSource, Long entiteId);

    /** Documents par statut */
    List<DocumentGenere> findByStatutOrderByDateGenerationDesc(StatutDocument statut);

    /** Documents d'un module donné */
    List<DocumentGenere> findByModuleSourceOrderByDateGenerationDesc(String moduleSource);

    /** Documents générés depuis un modèle donné */
    List<DocumentGenere> findByModeleIdOrderByDateGenerationDesc(Long modeleId);
}
