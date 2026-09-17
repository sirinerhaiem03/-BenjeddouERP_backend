package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.Abonnement;
import com.benjeddou.erp.model.StatutAbonnement;
import com.benjeddou.erp.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AbonnementRepository extends JpaRepository<Abonnement, Long> {

    @Query("SELECT a FROM Abonnement a LEFT JOIN FETCH a.client WHERE a.client = :client ORDER BY a.dateSoumission DESC")
    List<Abonnement> findByClientOrderByDateSoumissionDesc(@Param("client") Utilisateur client);

    @Query("SELECT a FROM Abonnement a LEFT JOIN FETCH a.client WHERE a.statut = :statut ORDER BY a.dateSoumission DESC")
    List<Abonnement> findByStatutOrderByDateSoumissionDesc(@Param("statut") StatutAbonnement statut);

    @Query("SELECT a FROM Abonnement a LEFT JOIN FETCH a.client ORDER BY a.dateSoumission DESC")
    List<Abonnement> findAllByOrderByDateSoumissionDesc();

    Optional<Abonnement> findFirstByClientAndStatutOrderByDateDebutDesc(
            Utilisateur client, StatutAbonnement statut);
}
