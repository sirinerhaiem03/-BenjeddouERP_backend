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

    @Value("${spring.datasource.password}")
    private String masterPassword;

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

            // Si l'utilisateur a un entrepriseSchema → router vers sa base entreprise
            if (user.getEntrepriseSchema() != null && !user.getEntrepriseSchema().isBlank()) {
                chargerTenantSiNecessaire(user.getEntrepriseSchema());
                TenantContextHolder.setCurrentTenant(user.getEntrepriseSchema());
                log.info("Tenant défini pour '{}' : {}", username, user.getEntrepriseSchema());
            }
            // Sinon → SuperAdmin → base master (pas de TenantContext)

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

            try {
                // ► Recherche directement via JDBC (contourne le routage JPA)
                Utilisateur found = chercherUtilisateurJDBC(url, user, pass, username);

                if (found != null) {
                    log.info("Utilisateur '{}' trouvé dans '{}' (rôle: {})", username, schema, found.getRole());

                    // Charger le DataSource pour les futures requêtes JPA de cet utilisateur
                    chargerTenantSiNecessaire(schema);
                    TenantContextHolder.setCurrentTenant(schema);

                    return UserDetailsImpl.build(found);
                }

            } catch (Exception e) {
                log.warn("Erreur lors de la recherche dans '{}' : {}", schema, e.getMessage());
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
