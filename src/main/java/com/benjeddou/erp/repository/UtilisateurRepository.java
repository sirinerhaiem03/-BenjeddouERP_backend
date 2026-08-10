package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {
    Optional<Utilisateur> findByNomUtilisateur(String nomUtilisateur);
    Optional<Utilisateur> findByEmail(String email);
    Optional<Utilisateur> findFirstByTelephone(String telephone);
    Optional<Utilisateur> findByTokenRecuperation(String tokenRecuperation);
    Boolean existsByNomUtilisateur(String nomUtilisateur);
    Boolean existsByEmail(String email);

    /**
     * Resolution universelle : cherche par username, email ou telephone.
     * Utilise pour la connexion sans selection de role.
     */
    @Query("SELECT u FROM Utilisateur u WHERE " +
           "u.nomUtilisateur = :identifiant OR " +
           "u.email = :identifiant OR " +
           "u.telephone = :identifiant")
    Optional<Utilisateur> findByIdentifiant(@Param("identifiant") String identifiant);
}
