package com.benjeddou.erp.service;

import com.benjeddou.erp.model.RefreshToken;
import com.benjeddou.erp.model.Utilisateur;
import com.benjeddou.erp.repository.RefreshTokenRepository;
import com.benjeddou.erp.repository.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Service de gestion des Refresh Tokens — J3 Sécurité
 * Gère la création, validation et révocation des refresh tokens long-durée.
 *
 * ── Architecture Multi-Tenant ─────────────────────────────────────────────────
 * La table refresh_tokens existe dans CHAQUE base (master ET tenant) :
 *  - SuperAdmin (TenantContextHolder = null) → base master (benjeddou_erp)
 *  - Utilisateurs tenant (TenantContextHolder = "erp_ent_XXXXX") → base tenant
 *
 * Le routing est entièrement géré par TenantRoutingDataSource via TenantContextHolder.
 * On ne modifie JAMAIS le TenantContextHolder ici : le contexte du thread appelant
 * est toujours correct (défini par TenantFilter ou UserDetailsServiceImpl).
 *
 * ⚠️ Ancienne implémentation : elle forçait TenantContextHolder.clear() dans toutes
 * les méthodes, ce qui faisait chercher les utilisateurs tenant dans la base MASTER
 * → orElseThrow("Utilisateur introuvable") → RuntimeException → 500 lors du login.
 * Ce pattern a été supprimé.
 */
@Service
@Transactional
public class RefreshTokenService {

    @Value("${benjeddou.erp.refreshTokenDurationMs:604800000}")
    private Long refreshTokenDurationMs;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    /**
     * Crée un nouveau refresh token pour un utilisateur.
     * Révoque d'abord tous les anciens tokens (session unique).
     * Utilise la base courante du thread (master ou tenant selon le contexte).
     */
    public RefreshToken creerRefreshToken(Long utilisateurId) {
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable: " + utilisateurId));

        // Révoquer tous les anciens tokens (1 session active à la fois)
        refreshTokenRepository.revoquerTousParUtilisateur(utilisateur);

        RefreshToken refreshToken = RefreshToken.builder()
                .utilisateur(utilisateur)
                .token(UUID.randomUUID().toString())
                .dateExpiration(LocalDateTime.now().plusSeconds(refreshTokenDurationMs / 1000))
                .revoque(false)
                .dateCreation(LocalDateTime.now())
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Cherche un refresh token par sa valeur.
     * Utilise la base courante du thread.
     */
    public Optional<RefreshToken> trouverParToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    /**
     * Vérifie si un refresh token est encore valide (non expiré, non révoqué).
     * @throws RuntimeException si expiré ou révoqué
     */
    public RefreshToken verifierValidite(RefreshToken token) {
        if (Boolean.TRUE.equals(token.getRevoque())) {
            refreshTokenRepository.delete(token);
            throw new RuntimeException("Refresh token révoqué. Veuillez vous reconnecter.");
        }
        if (token.getDateExpiration().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(token);
            throw new RuntimeException("Refresh token expiré. Veuillez vous reconnecter.");
        }
        return token;
    }

    /**
     * Révoque tous les refresh tokens d'un utilisateur (logout complet).
     */
    public void revoquerTousLesTokens(Utilisateur utilisateur) {
        refreshTokenRepository.revoquerTousParUtilisateur(utilisateur);
    }

    /**
     * Supprime physiquement tous les tokens d'un utilisateur.
     */
    public void supprimerParUtilisateur(Utilisateur utilisateur) {
        refreshTokenRepository.deleteByUtilisateur(utilisateur);
    }
}
