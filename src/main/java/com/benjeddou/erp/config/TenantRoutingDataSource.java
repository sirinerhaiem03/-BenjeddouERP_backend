package com.benjeddou.erp.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * TenantRoutingDataSource — Le cœur du routing multi-tenant.
 *
 * Extends AbstractRoutingDataSource de Spring qui maintient une Map<key, DataSource>.
 * Pour chaque opération JPA/JDBC, Spring appelle determineCurrentLookupKey()
 * qui retourne le nom du DataSource à utiliser pour CE thread.
 *
 * Résultat :
 * - Thread A (Entreprise erp_ent_00001) → toutes ses requêtes vont vers la base erp_ent_00001
 * - Thread B (Entreprise erp_ent_00002) → toutes ses requêtes vont vers la base erp_ent_00002
 * - Thread C (SuperAdmin)               → requêtes vers la base master benjeddou_erp
 * - Isolation physique totale garantie.
 */
public class TenantRoutingDataSource extends AbstractRoutingDataSource {

    /**
     * Retourne la clé du DataSource actif pour le thread courant.
     * La clé doit correspondre exactement à une entrée dans la Map de datasources
     * configurée dans TenantDataSourceConfig.
     *
     * @return schemaName (ex: "erp_ent_00001") ou null → utilise le DataSource par défaut (master)
     */
    @Override
    protected Object determineCurrentLookupKey() {
        String tenant = TenantContextHolder.getCurrentTenant();
        // Si pas de tenant défini → utilise la base master (default datasource)
        return tenant;
    }
}
