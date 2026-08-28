package com.benjeddou.erp.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * TenantDataSourceConfig — Configure et gère le pool de DataSources multi-tenant.
 *
 * Responsabilités :
 * 1. Crée le DataSource master (base benjeddou_erp)
 * 2. Crée le TenantRoutingDataSource avec le master comme défaut
 * 3. Expose addTenantDataSource() pour ajouter dynamiquement de nouvelles bases
 *
 * Pattern : le TenantRoutingDataSource wraps une Map<schemaName, DataSource>
 * Spring/JPA utilise ce DataSource @Primary pour TOUTES les opérations de base de données.
 */
@Configuration
@Slf4j
public class TenantDataSourceConfig {

    @Value("${spring.datasource.url}")
    private String masterUrl;

    @Value("${spring.datasource.username}")
    private String masterUsername;

    @Value("${spring.datasource.password}")
    private String masterPassword;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    /** Map partagée des DataSources par schéma (thread-safe avec synchronisation) */
    private final Map<Object, Object> targetDataSources = new HashMap<>();

    /** Référence au TenantRoutingDataSource pour les mises à jour dynamiques */
    private TenantRoutingDataSource routingDataSource;

    /**
     * Crée le DataSource master — utilisé pour :
     * - Les opérations sur la base master (Utilisateurs, Entreprises, SuperAdmin)
     * - Les requêtes sans tenant défini (login, inscription)
     */
    @Bean(name = "masterDataSource")
    public DataSource masterDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(masterUrl);
        ds.setUsername(masterUsername);
        ds.setPassword(masterPassword);
        ds.setDriverClassName(driverClassName);
        ds.setPoolName("HikariPool-Master");
        ds.setMaximumPoolSize(10);
        ds.setMinimumIdle(2);
        ds.setConnectionTimeout(30000);
        ds.setIdleTimeout(600000);
        ds.setMaxLifetime(1800000);
        log.info("DataSource Master initialisé : {}", masterUrl);
        return ds;
    }

    /**
     * Crée le TenantRoutingDataSource — le DataSource @Primary de l'application.
     * Spring/JPA utilise CE bean pour toutes les opérations.
     * Il délègue automatiquement vers la bonne base via TenantContextHolder.
     */
    @Bean
    @Primary
    public DataSource dataSource() {
        routingDataSource = new TenantRoutingDataSource();

        // DataSource master = DataSource par défaut (quand aucun tenant n'est défini)
        DataSource master = masterDataSource();
        routingDataSource.setDefaultTargetDataSource(master);

        // Initialisation avec seulement la base master pour commencer
        // Les bases tenant seront ajoutées dynamiquement via addTenantDataSource()
        targetDataSources.put("master", master);
        routingDataSource.setTargetDataSources(new HashMap<>(targetDataSources));
        routingDataSource.afterPropertiesSet();

        log.info("TenantRoutingDataSource initialisé avec la base master.");
        return routingDataSource;
    }

    /**
     * Ajoute dynamiquement un DataSource pour un nouveau tenant.
     * Appelé par EntrepriseService.creerEntreprise() et au démarrage pour charger les tenants existants.
     *
     * @param schemaName  Identifiant du schéma (ex: "erp_ent_00001")
     * @param url         URL JDBC complète de la base tenant
     * @param username    Utilisateur MySQL
     * @param password    Mot de passe MySQL
     */
    public synchronized void addTenantDataSource(String schemaName, String url,
                                                  String username, String password) {
        // Si déjà enregistré avec les mêmes credentials → rien à faire
        if (targetDataSources.containsKey(schemaName)) {
            Object existing = targetDataSources.get(schemaName);
            // Si le nouveau user est 'root' et l'ancien était erp_user_XXXXX → forcer le remplacement
            boolean forceReplace = false;
            if (existing instanceof HikariDataSource existingDs) {
                String existingUser = existingDs.getUsername();
                if (existingUser != null && existingUser.startsWith("erp_user_") && "root".equals(username)) {
                    log.info("DataSource '{}' : remplacement erp_user → root (fix Aria)", schemaName);
                    existingDs.close();  // fermer l'ancien pool corrompu
                    forceReplace = true;
                } else if (!existingDs.isClosed()) {
                    log.debug("DataSource déjà enregistré pour : {}", schemaName);
                    return;
                }
            }
            if (!forceReplace) {
                log.debug("DataSource déjà enregistré pour : {}", schemaName);
                return;
            }
        }

        try {
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(url);
            ds.setUsername(username);
            ds.setPassword(password);
            ds.setDriverClassName(driverClassName);
            ds.setPoolName("HikariPool-" + schemaName);
            ds.setMaximumPoolSize(5);
            ds.setMinimumIdle(1);
            ds.setConnectionTimeout(30000);
            ds.setIdleTimeout(600000);
            ds.setMaxLifetime(1800000);

            targetDataSources.put(schemaName, ds);

            // Mise à jour du routing datasource — SANS redémarrage
            routingDataSource.setTargetDataSources(new HashMap<>(targetDataSources));
            routingDataSource.afterPropertiesSet();

            log.info("✓ DataSource ajouté pour tenant '{}' → user={}", schemaName, username);
        } catch (Exception e) {
            log.error("✗ Impossible d'ajouter le DataSource pour '{}' : {}", schemaName, e.getMessage());
        }
    }

    /**
     * Vérifie si un DataSource est déjà enregistré pour ce tenant.
     */
    public boolean tenantExists(String schemaName) {
        return targetDataSources.containsKey(schemaName);
    }
}
