package com.benjeddou.erp.service;

import com.benjeddou.erp.config.TenantContextHolder;
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
 * ⚠️ IMPORTANT — Base master obligatoire :
 * La table refresh_tokens existe UNIQUEMENT dans la base master (benjeddou_erp).
 * Toutes les méthodes forcent le contexte master en effaçant temporairement
 * le tenant avant chaque opération repository, puis le restaurent.
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
     */
    public RefreshToken creerRefreshToken(Long utilisateurId) {
        String savedTenant = TenantContextHolder.getCurrentTenant();
        TenantContextHolder.clear(); // ← force base master
        try {
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
        } finally {
            if (savedTenant != null) TenantContextHolder.setCurrentTenant(savedTenant);
        }
    }

    /**
     * Cherche un refresh token par sa valeur.
     */
    public Optional<RefreshToken> trouverParToken(String token) {
        String savedTenant = TenantContextHolder.getCurrentTenant();
        TenantContextHolder.clear();
        try {
            return refreshTokenRepository.findByToken(token);
        } finally {
            if (savedTenant != null) TenantContextHolder.setCurrentTenant(savedTenant);
        }
    }

    /**
     * Vérifie si un refresh token est encore valide (non expiré, non révoqué).
     * @throws RuntimeException si expiré ou révoqué
     */
    public RefreshToken verifierValidite(RefreshToken token) {
        String savedTenant = TenantContextHolder.getCurrentTenant();
        TenantContextHolder.clear();
        try {
            if (Boolean.TRUE.equals(token.getRevoque())) {
                refreshTokenRepository.delete(token);
                throw new RuntimeException("Refresh token révoqué. Veuillez vous reconnecter.");
            }
            if (token.getDateExpiration().isBefore(LocalDateTime.now())) {
                refreshTokenRepository.delete(token);
                throw new RuntimeException("Refresh token expiré. Veuillez vous reconnecter.");
            }
            return token;
        } finally {
            if (savedTenant != null) TenantContextHolder.setCurrentTenant(savedTenant);
        }
    }

    /**
     * Révoque tous les refresh tokens d'un utilisateur (logout complet).
     */
    public void revoquerTousLesTokens(Utilisateur utilisateur) {
        String savedTenant = TenantContextHolder.getCurrentTenant();
        TenantContextHolder.clear();
        try {
            refreshTokenRepository.revoquerTousParUtilisateur(utilisateur);
        } finally {
            if (savedTenant != null) TenantContextHolder.setCurrentTenant(savedTenant);
        }
    }

    /**
     * Supprime physiquement tous les tokens d'un utilisateur.
     */
    public void supprimerParUtilisateur(Utilisateur utilisateur) {
        String savedTenant = TenantContextHolder.getCurrentTenant();
        TenantContextHolder.clear();
        try {
            refreshTokenRepository.deleteByUtilisateur(utilisateur);
        } finally {
            if (savedTenant != null) TenantContextHolder.setCurrentTenant(savedTenant);
        }
    }
}
