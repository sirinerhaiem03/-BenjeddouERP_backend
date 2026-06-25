package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {
    Optional<Utilisateur> findByNomUtilisateur(String nomUtilisateur);
    Optional<Utilisateur> findByEmail(String email);
    Optional<Utilisateur> findByTokenRecuperation(String tokenRecuperation);
    Boolean existsByNomUtilisateur(String nomUtilisateur);
    Boolean existsByEmail(String email);
}
