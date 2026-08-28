package com.benjeddou.erp.config;

import org.springframework.core.task.TaskDecorator;

/**
 * TenantAwareTaskDecorator — Propagation du TenantContext vers les threads asynchrones.
 *
 * Problème résolu :
 *   - TenantContextHolder utilise un ThreadLocal.
 *   - Quand Spring @Async dispatche une méthode vers un thread du pool,
 *     le nouveau thread a son propre ThreadLocal vide.
 *   - Résultat : TenantContextHolder.getCurrentTenant() retourne null dans @Async.
 *   - Conséquence : connexionLogRepository, refreshTokenRepository, etc.
 *     vont tous chercher dans la base MASTER (au lieu du tenant), table introuvable → 500.
 *
 * Solution :
 *   Ce TaskDecorator capture la valeur du TenantContext dans le thread appelant (HTTP),
 *   puis la propage vers le thread async AVANT l'exécution, et la nettoie APRÈS.
 *
 * Utilisé par AsyncConfig pour configurer tous les threads @Async de l'application.
 */
public class TenantAwareTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // Capturer le tenant dans le thread HTTP (appelant) — AVANT le dispatch
        String currentTenant = TenantContextHolder.getCurrentTenant();

        return () -> {
            try {
                // Injecter le tenant dans le thread async
                if (currentTenant != null) {
                    TenantContextHolder.setCurrentTenant(currentTenant);
                }
                runnable.run();
            } finally {
                // OBLIGATOIRE : nettoyer le ThreadLocal pour éviter les fuites mémoire
                // dans le pool de threads (les threads sont réutilisés)
                TenantContextHolder.clear();
            }
        };
    }
}
