package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.ReceptionLivraison;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReceptionLivraisonRepository extends JpaRepository<ReceptionLivraison, Long> {
    List<ReceptionLivraison> findByCommandeAchatId(Long commandeAchatId);
    List<ReceptionLivraison> findByStatut(String statut);
}
