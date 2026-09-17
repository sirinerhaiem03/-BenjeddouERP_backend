package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.Entrepot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface EntrepotRepository extends JpaRepository<Entrepot, Long> {
    Optional<Entrepot> findByCode(String code);
    Boolean existsByCode(String code);
}
