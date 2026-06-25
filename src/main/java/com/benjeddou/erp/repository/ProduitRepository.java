package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.Produit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface ProduitRepository extends JpaRepository<Produit, Long> {
    Optional<Produit> findByReference(String reference);
    Boolean existsByReference(String reference);
    List<Produit> findByCategorie(String categorie);
}
