package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.Inventaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface InventaireRepository extends JpaRepository<Inventaire, Long> {
    Optional<Inventaire> findByCode(String code);
    Boolean existsByCode(String code);
    List<Inventaire> findAllByOrderByDateInventaireDesc();
}
