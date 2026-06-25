package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.Abonnement;
import com.benjeddou.erp.model.StatutAbonnement;
import com.benjeddou.erp.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AbonnementRepository extends JpaRepository<Abonnement, Long> {

    List<Abonnement> findByClientOrderByDateSoumissionDesc(Utilisateur client);

    List<Abonnement> findByStatutOrderByDateSoumissionDesc(StatutAbonnement statut);

    List<Abonnement> findAllByOrderByDateSoumissionDesc();

    Optional<Abonnement> findFirstByClientAndStatutOrderByDateDebutDesc(
            Utilisateur client, StatutAbonnement statut);
}
