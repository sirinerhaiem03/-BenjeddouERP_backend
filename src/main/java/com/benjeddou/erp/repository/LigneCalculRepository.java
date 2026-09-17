package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.LigneCalcul;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LigneCalculRepository extends JpaRepository<LigneCalcul, Long> {

    /** Toutes les lignes d'un calcul, triées par numéro de ligne */
    List<LigneCalcul> findByCalculIdOrderByNumeroLigneAsc(Long calculId);

    /** Supprime toutes les lignes d'un calcul */
    void deleteByCalculId(Long calculId);
}
