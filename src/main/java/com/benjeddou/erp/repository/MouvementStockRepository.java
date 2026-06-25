package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.MouvementStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MouvementStockRepository extends JpaRepository<MouvementStock, Long> {
    List<MouvementStock> findByProduitId(Long produitId);
    List<MouvementStock> findByEntrepotId(Long entrepotId);
    List<MouvementStock> findAllByOrderByDateMouvementDesc();
}
