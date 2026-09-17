package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.EcritureComptable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EcritureComptableRepository extends JpaRepository<EcritureComptable, Long> {
    List<EcritureComptable> findByTypeEcritureOrderByDateEcritureDesc(String typeEcriture);
    List<EcritureComptable> findByStatutOrderByDateEcritureDesc(String statut);
    List<EcritureComptable> findAllByOrderByDateEcritureDesc();
}
