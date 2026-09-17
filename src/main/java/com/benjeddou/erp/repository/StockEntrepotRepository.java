package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.StockEntrepot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface StockEntrepotRepository extends JpaRepository<StockEntrepot, Long> {
    Optional<StockEntrepot> findByProduitIdAndEntrepotId(Long produitId, Long entrepotId);
    List<StockEntrepot> findByEntrepotId(Long entrepotId);
    List<StockEntrepot> findByProduitId(Long produitId);
}
