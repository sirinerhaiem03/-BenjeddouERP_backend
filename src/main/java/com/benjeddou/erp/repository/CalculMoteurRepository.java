package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.CalculMoteur;
import com.benjeddou.erp.model.CalculMoteur.TypeCalcul;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CalculMoteurRepository extends JpaRepository<CalculMoteur, Long> {

    Optional<CalculMoteur> findByReference(String reference);

    /** Historique paginé — tous les calculs, du plus récent au plus ancien */
    Page<CalculMoteur> findAllByOrderByDateCreationDesc(Pageable pageable);

    /** Historique filtré par type */
    Page<CalculMoteur> findByTypeCalculOrderByDateCreationDesc(TypeCalcul typeCalcul, Pageable pageable);

    /** Historique filtré par module ERP */
    Page<CalculMoteur> findByModuleErpIgnoreCaseOrderByDateCreationDesc(String moduleErp, Pageable pageable);

    /** Historique par utilisateur */
    Page<CalculMoteur> findByCreePar_IdOrderByDateCreationDesc(Long userId, Pageable pageable);

    /** Historique par utilisateur ET par type de calcul */
    Page<CalculMoteur> findByCreePar_IdAndTypeCalculOrderByDateCreationDesc(Long userId, TypeCalcul typeCalcul, Pageable pageable);

    /** Recherche par référence ou libellé */
    @Query("SELECT c FROM CalculMoteur c WHERE " +
           "LOWER(c.reference) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(c.libelle) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "ORDER BY c.dateCreation DESC")
    Page<CalculMoteur> rechercher(@Param("q") String q, Pageable pageable);

    /** Recherche par référence ou libellé FILTRÉE par type */
    @Query("SELECT c FROM CalculMoteur c WHERE c.typeCalcul = :type AND (" +
           "LOWER(c.reference) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(c.libelle) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "ORDER BY c.dateCreation DESC")
    Page<CalculMoteur> rechercherParType(@Param("q") String q,
                                         @Param("type") TypeCalcul type,
                                         Pageable pageable);

    /**
     * Recherche isolée par utilisateur — ISOLATION MULTI-TENANT
     * Garantit qu'un utilisateur ne peut rechercher que DANS SES propres calculs.
     */
    @Query("SELECT c FROM CalculMoteur c WHERE c.creePar.id = :userId AND (" +
           "LOWER(c.reference) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(c.libelle) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "ORDER BY c.dateCreation DESC")
    Page<CalculMoteur> rechercherParUtilisateur(@Param("q") String q,
                                                @Param("userId") Long userId,
                                                Pageable pageable);

    /**
     * Recherche isolée par utilisateur ET type — ISOLATION MULTI-TENANT
     */
    @Query("SELECT c FROM CalculMoteur c WHERE c.creePar.id = :userId AND c.typeCalcul = :type AND (" +
           "LOWER(c.reference) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(c.libelle) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "ORDER BY c.dateCreation DESC")
    Page<CalculMoteur> rechercherParUtilisateurEtType(@Param("q") String q,
                                                       @Param("userId") Long userId,
                                                       @Param("type") TypeCalcul type,
                                                       Pageable pageable);

    /** Dernier numéro séquentiel du jour pour la référence CM-YYYYMMDD-XXXX */
    @Query("SELECT COUNT(c) FROM CalculMoteur c WHERE c.reference LIKE CONCAT('CM-', :dateStr, '-%')")
    long countByDateStr(@Param("dateStr") String dateStr);

    /** Calculs sur une période donnée */
    List<CalculMoteur> findByDateDebutGreaterThanEqualAndDateFinLessThanEqual(
        LocalDate debut, LocalDate fin
    );
}
