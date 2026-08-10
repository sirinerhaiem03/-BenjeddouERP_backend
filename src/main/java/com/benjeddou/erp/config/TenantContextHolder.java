package com.benjeddou.erp.config;

/**
 * TenantContextHolder — Stocke l'identifiant de la base de données active pour le thread courant.
 *
 * Architecture Multi-Tenant : chaque requête HTTP s'exécute dans son propre thread.
 * Ce ThreadLocal garantit qu'aucune requête ne peut interférer avec une autre entreprise.
 *
 * Cycle de vie :
 *   1. TenantFilter.doFilter() → set(schemaName)
 *   2. La requête s'exécute → toutes les requêtes JPA vont vers la bonne base
 *   3. TenantFilter.finally → clear() (prévient les fuites mémoire dans les pool de threads)
 */
public class TenantContextHolder {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    /** Définit le schéma actif pour le thread courant (ex: "erp_ent_001") */
    public static void setCurrentTenant(String tenant) {
        CURRENT_TENANT.set(tenant);
    }

    /** Retourne le schéma actif du thread courant. Null si aucun tenant défini (requête publique). */
    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    /** ⚠️ OBLIGATOIRE — appeler dans le finally du filter pour éviter les fuites mémoire */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
