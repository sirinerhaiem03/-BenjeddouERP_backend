package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.Entreprise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * EntrepriseRepository — Accède uniquement à la BASE MASTER (benjeddou_erp).
 * Ce repository ne doit JAMAIS être routé vers une base tenant.
 */
@Repository
public interface EntrepriseRepository extends JpaRepository<Entreprise, Long> {

    /** Retrouve une entreprise par son schéma MySQL (ex: erp_ent_00001) */
    Optional<Entreprise> findBySchemaName(String schemaName);

    /** Retrouve toutes les entreprises actives */
    List<Entreprise> findByStatut(Entreprise.StatutEntreprise statut);

    /** Retrouve l'entreprise d'un utilisateur via son adminId */
    Optional<Entreprise> findByAdminId(Long adminId);

    /** Vérifie si un schéma existe déjà (pour éviter les doublons) */
    boolean existsBySchemaName(String schemaName);

    /** Retrouve par email de contact */
    Optional<Entreprise> findByEmailContact(String email);
}
