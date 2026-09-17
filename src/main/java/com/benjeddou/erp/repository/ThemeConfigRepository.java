package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.ThemeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ThemeConfigRepository extends JpaRepository<ThemeConfig, Long> {
    // La configuration est toujours en id=1 (singleton)
    // On utilise findById(1L) directement depuis le service
}
