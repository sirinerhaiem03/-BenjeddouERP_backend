package com.benjeddou.erp.repository;

import com.benjeddou.erp.model.RefreshToken;
import com.benjeddou.erp.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * Repository pour les Refresh Tokens — J3 Sécurité
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** Recherche un refresh token valide par sa valeur */
    Optional<RefreshToken> findByToken(String token);

    /** Supprime tous les refresh tokens d'un utilisateur (logout complet) */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.utilisateur = :utilisateur")
    void deleteByUtilisateur(Utilisateur utilisateur);

    /** Révoque tous les tokens non expirés d'un utilisateur (nouvelle connexion) */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoque = true WHERE rt.utilisateur = :utilisateur AND rt.revoque = false")
    void revoquerTousParUtilisateur(Utilisateur utilisateur);

    /** Compte les tokens actifs d'un utilisateur */
    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.utilisateur = :utilisateur AND rt.revoque = false")
    long compterTokensActifs(Utilisateur utilisateur);
}
