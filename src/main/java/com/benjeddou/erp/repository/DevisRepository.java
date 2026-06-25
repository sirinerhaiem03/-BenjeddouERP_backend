package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.Devis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DevisRepository extends JpaRepository<Devis, Long> {
    List<Devis> findByClientId(Long clientId);
    List<Devis> findByStatut(String statut);
    boolean existsByNumeroDevis(String numeroDevis);
}
