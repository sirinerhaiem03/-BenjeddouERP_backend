package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.LigneDevis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LigneDevisRepository extends JpaRepository<LigneDevis, Long> {
    List<LigneDevis> findByDevisId(Long devisId);
    void deleteByDevisId(Long devisId);
}
