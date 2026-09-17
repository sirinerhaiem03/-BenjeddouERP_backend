package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.CodePromo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface CodePromoRepository extends JpaRepository<CodePromo, Long> {
    Optional<CodePromo> findByCode(String code);
    boolean existsByCode(String code);
    List<CodePromo> findByActifTrue();
}
