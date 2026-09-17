package com.benjeddou.erp.service;

import com.benjeddou.erp.config.TenantContextHolder;
import com.benjeddou.erp.model.AuditLog;
import com.benjeddou.erp.model.AuditLog.ActionAudit;
import com.benjeddou.erp.model.AuditLog.ResultatAudit;
import com.benjeddou.erp.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AuditService — Deux responsabilités :
 *
 * 1. AUDIT LOG : journalisation asynchrone de toutes les actions critiques
 * 2. RATE LIMITING : protection bruteforce sur le login (sans dépendance externe)
 *    → 5 tentatives échouées max en 15 minutes par IP
 *    → après blocage : fenêtre de 15 min reset automatiquement
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    // ── Rate Limiting (in-memory, thread-safe) ───────────────────────────
    private static final int MAX_TENTATIVES = 5;
    private static final long FENETRE_MS    = 5 * 60 * 1000L;  // 5 minutes

    /** Map IP → [compteur, timestamp première tentative] */
    private final ConcurrentHashMap<String, long[]> tentativesParIp = new ConcurrentHashMap<>();

    // ═════════════════════════════════════════════════════════════════════
    // RATE LIMITING
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Vérifie si l'IP est bloquée (trop de tentatives).
     * @return true si bloquée
     */
    public boolean estBloquee(String ip) {
        long[] data = tentativesParIp.get(ip);
        if (data == null) return false;

        long count     = data[0];
        long firstTime = data[1];
        long now       = System.currentTimeMillis();

        if (now - firstTime > FENETRE_MS) {
            tentativesParIp.remove(ip);
            return false;
        }
        return count >= MAX_TENTATIVES;
    }

    /**
     * Enregistre un échec de connexion et retourne les secondes restantes si bloqué.
     * @return secondes avant déblocage (0 si pas encore bloqué)
     */
    public long enregistrerEchecEtVerifier(String ip) {
        long now = System.currentTimeMillis();
        tentativesParIp.compute(ip, (k, data) -> {
            if (data == null || now - data[1] > FENETRE_MS) {
                return new long[]{1, now};
            }
            data[0]++;
            return data;
        });

        long[] data = tentativesParIp.get(ip);
        if (data != null && data[0] >= MAX_TENTATIVES) {
            long elapsed   = now - data[1];
            long remaining = FENETRE_MS - elapsed;
            return Math.max(remaining / 1000, 0);
        }
        return 0;
    }

    /**
     * Réinitialise le compteur après un login réussi.
     */
    public void resetCompteurIp(String ip) {
        tentativesParIp.remove(ip);
    }

    /**
     * Retourne le nombre de tentatives restantes pour une IP.
     */
    public int tentativesRestantes(String ip) {
        long[] data = tentativesParIp.get(ip);
        if (data == null) return MAX_TENTATIVES;
        long now = System.currentTimeMillis();
        if (now - data[1] > FENETRE_MS) return MAX_TENTATIVES;
        return (int) Math.max(0, MAX_TENTATIVES - data[0]);
    }

    // ═════════════════════════════════════════════════════════════════════
    // AUDIT LOG — méthodes de journalisation
    // ═════════════════════════════════════════════════════════════════════

    /** Journalise de manière asynchrone pour ne pas bloquer la requête HTTP */
    @Async
    public void log(ActionAudit action, ResultatAudit resultat, String details,
                    Long utilisateurId, String nomUtilisateur,
                    String ip, String userAgent, String module, Long ressourceId) {
        // Forcer la base master : audit_logs est dans benjeddou_erp, jamais dans un tenant
        TenantContextHolder.clear();
        try {
            auditLogRepository.save(AuditLog.builder()
                .action(action)
                .resultat(resultat)
                .details(details)
                .utilisateurId(utilisateurId)
                .nomUtilisateur(nomUtilisateur)
                .adresseIp(ip)
                .userAgent(userAgent != null ? userAgent.substring(0, Math.min(userAgent.length(), 500)) : null)
                .module(module)
                .ressourceId(ressourceId)
                .build());
        } catch (Exception e) {
            log.warn("[Audit] Échec enregistrement log: {}", e.getMessage());
        }
    }

    // ── Surcharges pratiques ──────────────────────────────────────────────

    public void log(ActionAudit action, ResultatAudit resultat, String details,
                    HttpServletRequest request) {
        log(action, resultat, details, null, null,
            extractIp(request), extractUa(request), null, null);
    }

    public void log(ActionAudit action, ResultatAudit resultat, String details,
                    Long userId, String username, HttpServletRequest request) {
        log(action, resultat, details, userId, username,
            extractIp(request), extractUa(request), null, null);
    }

    public void log(ActionAudit action, ResultatAudit resultat, String details,
                    Long userId, String username, HttpServletRequest request,
                    String module, Long ressourceId) {
        log(action, resultat, details, userId, username,
            extractIp(request), extractUa(request), module, ressourceId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    public static String extractIp(HttpServletRequest request) {
        if (request == null) return "unknown";
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) ip = "127.0.0.1";
        return ip;
    }

    public static String extractUa(HttpServletRequest request) {
        if (request == null) return "unknown";
        return request.getHeader("User-Agent");
    }

    public void enregistrerAction(String nomUtilisateur, String action, String details) {
        try {
            log(ActionAudit.MODIFICATION, ResultatAudit.SUCCES, details, null, nomUtilisateur, "127.0.0.1", "System", "ADMIN", null);
        } catch (Exception ignored) {}
    }
}
