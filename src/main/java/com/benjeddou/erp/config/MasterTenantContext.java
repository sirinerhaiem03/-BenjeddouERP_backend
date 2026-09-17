package com.benjeddou.erp.config;

import java.util.function.Supplier;

/**
 * MasterTenantContext — Utilitaire pour exécuter une opération JPA
 * EXCLUSIVEMENT sur la base master (benjeddou_erp), quel que soit le tenant courant.
 *
 * Contexte d'utilisation :
 *   - Lecture/écriture de la table periodes_taux (gérée globalement par le SuperAdmin)
 *   - Accès aux données centralisées partagées entre tous les tenants
 *
 * Principe :
 *   Le TenantRoutingDataSource lit TenantContextHolder.getCurrentTenant() à chaque
 *   ouverture de connexion JPA. En basculant temporairement vers "master" avant l'appel JPA,
 *   Spring/Hibernate utilisera automatiquement la base centrale.
 *
 * Exemple d'utilisation :
 *   List<PeriodeTaux> periodes = MasterTenantContext.run(
 *       () -> periodeTauxRepository.findAll()
 *   );
 */
public final class MasterTenantContext {

    private MasterTenantContext() { /* utilitaire statique */ }

    /**
     * Exécute un bloc de code dans le contexte master (benjeddou_erp).
     * Le contexte tenant précédent est restauré après l'appel (même en cas d'erreur).
     *
     * @param operation Lambda retournant un résultat
     * @param <T>       Type de retour
     * @return Résultat de l'opération
     */
    public static <T> T run(Supplier<T> operation) {
        final String previousTenant = TenantContextHolder.getCurrentTenant();
        try {
            // Basculer vers la base master
            TenantContextHolder.setCurrentTenant("master");
            return operation.get();
        } finally {
            // Restaurer le contexte tenant précédent
            if (previousTenant != null) {
                TenantContextHolder.setCurrentTenant(previousTenant);
            } else {
                TenantContextHolder.clear();
            }
        }
    }

    /**
     * Exécute un bloc de code sans valeur de retour dans le contexte master.
     *
     * @param operation Lambda void
     */
    public static void runVoid(Runnable operation) {
        run(() -> {
            operation.run();
            return null;
        });
    }
}
