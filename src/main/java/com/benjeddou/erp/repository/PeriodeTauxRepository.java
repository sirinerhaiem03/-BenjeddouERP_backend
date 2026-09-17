package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.PeriodeTaux;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface PeriodeTauxRepository extends JpaRepository<PeriodeTaux, Long> {

    /** Toutes les périodes actives, triées par date de début */
    List<PeriodeTaux> findByActifTrueOrderByDateDebutAsc();

    /**
     * Récupère toutes les périodes actives qui se chevauchent avec la plage [debut, fin].
     * Une période est applicable si elle commence avant la fin et se termine après le début.
     */
    @Query("SELECT p FROM PeriodeTaux p WHERE p.actif = true " +
           "AND p.dateDebut <= :dateFin AND p.dateFin >= :dateDebut " +
           "ORDER BY p.dateDebut ASC")
    List<PeriodeTaux> findPeriodesApplicables(
        @Param("dateDebut") LocalDate dateDebut,
        @Param("dateFin") LocalDate dateFin
    );

    /**
     * Vérifie si une période en chevauchement existe déjà (pour validation avant ajout).
     * Exclut la période d'ID donné (utile pour update).
     */
    @Query("SELECT COUNT(p) > 0 FROM PeriodeTaux p WHERE p.actif = true " +
           "AND p.id <> :excludeId " +
           "AND p.dateDebut <= :dateFin AND p.dateFin >= :dateDebut")
    boolean existsChevauchement(
        @Param("dateDebut") LocalDate dateDebut,
        @Param("dateFin") LocalDate dateFin,
        @Param("excludeId") Long excludeId
    );
}
