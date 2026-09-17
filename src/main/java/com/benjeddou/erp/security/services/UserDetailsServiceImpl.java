package com.benjeddou.erp.security.services;

import com.benjeddou.erp.config.TenantContextHolder;
import com.benjeddou.erp.config.TenantDataSourceConfig;
import com.benjeddou.erp.model.Entreprise;
import com.benjeddou.erp.model.Role;
import com.benjeddou.erp.model.StatutCompte;
import com.benjeddou.erp.model.Utilisateur;
import com.benjeddou.erp.repository.EntrepriseRepository;
import com.benjeddou.erp.repository.UtilisateurRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;

/**
 * UserDetailsServiceImpl — Authentification Multi-Tenant
 *
 * Stratégie de résolution de l'identifiant :
 *
 * 1. On cherche d'abord dans la base MASTER (benjeddou_erp) → SuperAdmin
 * 2. Si non trouvé ET si on est en /api/auth/login → on cherche dans TOUTES
 *    les bases entreprises actives jusqu'à trouver l'utilisateur
 *
 * Flux login :
 *   - superadmin → trouvé dans benjeddou_erp → entrepriseSchema = null → base master
 *   - admin       → pas dans benjeddou_erp → cherche dans erp_ent_00000 → routé vers erp_ent_00000
 *   - commercial  → idem admin
 */
@Service
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    UtilisateurRepository utilisateurRepository;

    @Autowired
    EntrepriseRepository entrepriseRepository;

    @Autowired
    TenantDataSourceConfig tenantDataSourceConfig;

    @Value("${spring.datasource.username}")
    private String masterUsername;

    @Value("${spring.datasource.password:}")
    private String masterPassword;

    @Value("${spring.datasource.url}")
    private String masterDbUrl; // utilisé pour construire l'URL root vers les bases tenant

    /**
     * Résolution Multi-Tenant de l'identifiant de connexion.
     *
     * L'identifiant peut être : nom_utilisateur, email ou téléphone.
     *
     * Étape 1 : Cherche dans la base master (benjeddou_erp)
     *   → Trouve le SuperAdmin → entrepriseSchema null → base master utilisée
     *
     * Étape 2 : Si non trouvé → cherche dans chaque base entreprise active
     *   → Trouve l'admin/commercial/comptable... → set TenantContext vers sa base
     */
    @Override
    // NE PAS mettre @Transactional ici : cela bloquerait le routage vers le tenant
    // (JPA ouvrirait la connexion master en début de transaction et ne changerait plus)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        // ──────────────────────────────────────────────────────
        // ÉTAPE 1 : Chercher dans la base MASTER (benjeddou_erp)
        // TenantContextHolder est null ici → JPA utilise la base master
        // ──────────────────────────────────────────────────────
        Optional<Utilisateur> userInMaster = utilisateurRepository.findByIdentifiant(username);

        if (userInMaster.isPresent()) {
            Utilisateur user = userInMaster.get();
            log.debug("Utilisateur '{}' trouvé dans la base master (rôle: {})", username, user.getRole());

            // ═══════════════════════════════════════════════════════════════
            // RÈGLE ABSOLUE DU ROUTAGE MULTI-TENANT :
            //
            // Si l'enregistrement master a entrepriseSchema NON NULL
            // → c'est un admin tenant dont l'entrée master a été créée
            //   accidentellement (login JPA save avec mauvais contexte).
            // → Son rôle réel vient UNIQUEMENT de la base tenant.
            // → Ne JAMAIS lui accorder le rôle SUPERADMIN depuis master.
            //
            // Si entrepriseSchema est NULL → c'est un vrai compte master
            // (SuperAdmin, CLIENT trial…). Son rôle master est la vérité.
            // ═══════════════════════════════════════════════════════════════

            // 1a. Vrai compte master (pas de tenant attaché) → rôle master
            if (user.getEntrepriseSchema() == null || user.getEntrepriseSchema().isBlank()) {
                if (user.getRole() == Role.SUPERADMIN) {
                    TenantContextHolder.clear();
                    return UserDetailsImpl.build(user);
                }
                // Autre rôle master (CLIENT trial, etc.) → connexion master
                TenantContextHolder.clear();
                return UserDetailsImpl.build(user);
            }

            // 1b. L'enregistrement master a un entrepriseSchema
            //     → Admin/Commercial/etc. d'un tenant.
            //     Lire son rôle RÉEL depuis la base tenant (JAMAIS depuis master).
            {
                String schema = user.getEntrepriseSchema();
                chargerTenantSiNecessaire(schema);

                // CRITIQUE : lire entreprise AVANT de setter le contexte tenant.
                // La table 'entreprises' est dans la base MASTER uniquement.
                Optional<Entreprise> entOpt = entrepriseRepository.findBySchemaName(schema);

                // Setter le contexte tenant
                TenantContextHolder.setCurrentTenant(schema);
                log.info("Tenant défini pour '{}' : {}", username, schema);

                if (entOpt.isPresent()) {
                    Entreprise ent = entOpt.get();
                    String url = ent.getDbUrl() != null ? ent.getDbUrl()
                        : "jdbc:mysql://localhost:3306/" + schema + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
                    String dbUser = ent.getDbUsername() != null ? ent.getDbUsername() : masterUsername;
                    String dbPass = ent.getDbPassword() != null ? ent.getDbPassword() : masterPassword;
                    try {
                        Utilisateur tenantUser = chercherUtilisateurJDBC(url, dbUser, dbPass, username);
                        if (tenantUser == null) {
                            // Fallback root
                            String rootUrl = masterDbUrl.replaceFirst("//([^/]+)/[^?]+", "//" + "$1" + "/" + schema);
                            tenantUser = chercherUtilisateurJDBC(rootUrl, masterUsername, masterPassword != null ? masterPassword : "", username);
                        }
                        if (tenantUser != null) {
                            tenantUser.setEntrepriseSchema(schema);
                            tenantUser.setEntrepriseId(ent.getId());

                            // Vérifier que le rôle tenant n'est pas SUPERADMIN (protection)
                            // Un admin tenant ne peut JAMAIS être SUPERADMIN
                            if (tenantUser.getRole() == Role.SUPERADMIN) {
                                log.warn("[SÉCURITÉ] Utilisateur '{}' a le rôle SUPERADMIN dans la base tenant '{}' !"
                                       + " C'est une anomalie — rôle forcé à ADMIN.", username, schema);
                                tenantUser.setRole(Role.ADMIN);
                            }

                            // Synchronisation hash master ↔ tenant
                            String hashMaster = user.getMotDePasse();
                            String hashTenant = tenantUser.getMotDePasse();
                            if (hashMaster != null && !hashMaster.equals(hashTenant)) {
                                log.warn("[SYNC] Hash désynchronisé pour '{}' (master ≠ tenant) — synchronisation automatique...", username);
                                try {
                                    String sqlSync = "UPDATE utilisateurs SET mot_de_passe = ? WHERE nom_utilisateur = ? OR email = ?";
                                    try (Connection syncConn = DriverManager.getConnection(url, dbUser, dbPass);
                                         PreparedStatement syncPs = syncConn.prepareStatement(sqlSync)) {
                                        syncPs.setString(1, hashMaster);
                                        syncPs.setString(2, username);
                                        syncPs.setString(3, username);
                                        int rows = syncPs.executeUpdate();
                                        log.info("[SYNC] Hash synchronisé dans tenant '{}' pour '{}' ({} ligne(s))", schema, username, rows);
                                    }
                                    tenantUser.setMotDePasse(hashMaster);
                                } catch (Exception syncEx) {
                                    log.warn("[SYNC] Synchronisation hash échouée pour '{}' : {} — utilisation hash master", username, syncEx.getMessage());
                                    tenantUser.setMotDePasse(hashMaster);
                                }
                            }

                            log.info("Utilisateur tenant '{}' authentifié via '{}' (rôle: {})",
                                     username, schema, tenantUser.getRole());
                            return UserDetailsImpl.build(tenantUser);
                        }
                    } catch (Exception e) {
                        log.warn("Recherche tenant JDBC échouée pour {}: {}", username, e.getMessage());
                    }
                }

                // Non trouvé dans le tenant → fallback sur l'enregistrement master
                // (en forcéant le rôle à ADMIN si c'était SUPERADMIN par erreur)
                log.warn("[TENANT] Utilisateur '{}' introuvable dans '{}' via JDBC → fallback master", username, schema);
                TenantContextHolder.clear();
                if (user.getRole() == Role.SUPERADMIN && user.getEntrepriseSchema() != null) {
                    log.warn("[SÉCURITÉ] Rôle master SUPERADMIN pour un admin tenant '{}' → forcé à ADMIN", username);
                    user.setRole(Role.ADMIN);
                }
            }

            return UserDetailsImpl.build(user);
        }

        // ──────────────────────────────────────────────────────
        // ÉTAPE 2 : Non trouvé dans master → chercher dans les bases entreprises
        // ──────────────────────────────────────────────────────
        log.debug("'{}' non trouvé dans master → recherche dans les bases entreprises...", username);

        List<Entreprise> entreprisesActives = entrepriseRepository.findByStatut(
                Entreprise.StatutEntreprise.ACTIVE);

        for (Entreprise entreprise : entreprisesActives) {
            String schema = entreprise.getSchemaName();
            if (schema == null || schema.isBlank()) continue;

            String url  = entreprise.getDbUrl();
            String user = entreprise.getDbUsername();
            String pass = entreprise.getDbPassword() != null ? entreprise.getDbPassword() : "";

            if (url == null || url.isBlank()) continue;

            // Tentative 1 : connexion avec l'user dédié erp_user_XXXXX
            Utilisateur found = null;
            try {
                found = chercherUtilisateurJDBC(url, user, pass, username);
            } catch (Exception e) {
                log.warn("Recherche avec user dédié dans '{}' échouée ({}). Tentative root...", schema, e.getMessage());
            }

            // Tentative 2 (fallback root) — si erp_user_XXXXX n'a pas les droits (Aria corrompu)
            if (found == null) {
                try {
                    String rootUrl = masterDbUrl
                        .replaceFirst("//([^/]+)/[^?]+", "//" + "$1" + "/" + schema);
                    found = chercherUtilisateurJDBC(rootUrl, masterUsername, masterPassword != null ? masterPassword : "", username);
                    if (found != null) {
                        log.info("Utilisateur '{}' trouvé dans '{}' via ROOT (fallback Aria)", username, schema);
                    }
                } catch (Exception e) {
                    log.warn("Recherche ROOT dans '{}' échouée : {}", schema, e.getMessage());
                }
            }

            if (found != null) {
                log.info("Utilisateur '{}' trouvé dans '{}' (rôle: {})", username, schema, found.getRole());
                found.setEntrepriseSchema(schema);
                found.setEntrepriseId(entreprise.getId());

                // Charger le DataSource pour les futures requêtes JPA de cet utilisateur
                // Si erp_user n'a pas les droits, on charge le DataSource root pour le routage JPA
                if (!tenantDataSourceConfig.tenantExists(schema)) {
                    String rootUrl = masterDbUrl
                        .replaceFirst("//([^/]+)/[^?]+", "//" + "$1" + "/" + schema);
                    // Essayer d'abord avec l'user dédié, puis root
                    try {
                        tenantDataSourceConfig.addTenantDataSource(schema, url, user, pass);
                        log.debug("DataSource chargé pour '{}' avec user dédié", schema);
                    } catch (Exception e) {
                        log.warn("DataSource user dédié échoué pour '{}', chargement root", schema);
                        tenantDataSourceConfig.addTenantDataSource(schema, rootUrl, masterUsername, masterPassword != null ? masterPassword : "");
                    }
                }
                TenantContextHolder.setCurrentTenant(schema);

                return UserDetailsImpl.build(found);
            }
        }

        // Non trouvé nulle part
        TenantContextHolder.clear();
        throw new UsernameNotFoundException(
            "Aucun compte trouvé pour l'identifiant : " + username);
    }

    /**
     * Charge le DataSource d'un tenant dans le pool HikariCP si pas encore fait.
     */
    private void chargerTenantSiNecessaire(String schema) {
        if (!tenantDataSourceConfig.tenantExists(schema)) {
            Optional<Entreprise> entOpt = entrepriseRepository.findBySchemaName(schema);
            entOpt.ifPresent(ent -> {
                String url = ent.getDbUrl() != null ? ent.getDbUrl()
                    : "jdbc:mysql://localhost:3306/" + schema + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
                String user = ent.getDbUsername() != null ? ent.getDbUsername() : masterUsername;
                String pass = ent.getDbPassword() != null ? ent.getDbPassword() : masterPassword;
                tenantDataSourceConfig.addTenantDataSource(schema, url, user, pass);
                log.info("DataSource chargé pour tenant '{}'", schema);
            });
        }
    }

    /**
     * Cherche un utilisateur directement dans la base tenant via JDBC (sans JPA).
     * Évite le problème du routage JPA bloqué par @Transactional du contexte appelant.
     *
     * @return Utilisateur hydraté ou null si non trouvé
     */
    private Utilisateur chercherUtilisateurJDBC(String url, String dbUser, String dbPass, String identifiant) throws Exception {
        String sql = """
            SELECT id, nom_utilisateur, email, mot_de_passe, prenom, nom,
                   actif, role, statut_compte, langue_preferee,
                   doit_changer_mot_de_passe
            FROM utilisateurs
            WHERE nom_utilisateur = ? OR email = ?
            LIMIT 1
            """;

        try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, identifiant);
            ps.setString(2, identifiant);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                Utilisateur u = new Utilisateur();
                u.setId(rs.getLong("id"));
                u.setNomUtilisateur(rs.getString("nom_utilisateur"));
                u.setEmail(rs.getString("email"));
                u.setMotDePasse(rs.getString("mot_de_passe"));
                u.setPrenom(rs.getString("prenom"));
                u.setNom(rs.getString("nom"));
                u.setActif(rs.getBoolean("actif"));
                u.setLanguePreferee(rs.getString("langue_preferee"));
                u.setDoitChangerMotDePasse(rs.getBoolean("doit_changer_mot_de_passe"));

                // Rôle
                String roleStr = rs.getString("role");
                if (roleStr != null) {
                    try { u.setRole(Role.valueOf(roleStr)); } catch (Exception ignored) {}
                }

                // Statut
                String statutStr = rs.getString("statut_compte");
                if (statutStr != null) {
                    try { u.setStatutCompte(StatutCompte.valueOf(statutStr)); } catch (Exception ignored) {}
                }

                return u;
            }
        }
    }
}
