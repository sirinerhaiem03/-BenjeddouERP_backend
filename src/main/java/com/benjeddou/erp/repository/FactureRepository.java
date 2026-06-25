package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.Facture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface FactureRepository extends JpaRepository<Facture, Long> {
    Optional<Facture> findByNumeroFacture(String numeroFacture);
    Optional<Facture> findByCommandeId(Long commandeId);
    List<Facture> findByStatut(String statut);
}
