package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.Commande;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface CommandeRepository extends JpaRepository<Commande, Long> {
    Optional<Commande> findByNumeroCommande(String numeroCommande);
    List<Commande> findByClientId(Long clientId);
    List<Commande> findByStatut(String statut);
}
