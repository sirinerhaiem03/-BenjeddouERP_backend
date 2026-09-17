package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.CommandeAchat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommandeAchatRepository extends JpaRepository<CommandeAchat, Long> {
    Optional<CommandeAchat> findByNumeroCommande(String numeroCommande);
    List<CommandeAchat> findByFournisseurId(Long fournisseurId);
    List<CommandeAchat> findByStatut(String statut);
}
