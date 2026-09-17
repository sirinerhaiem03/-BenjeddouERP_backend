package com.benjeddou.erp.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * AuditLog — Journal des opérations critiques de la plateforme
 * Table : audit_logs
 *
 * Enregistre toutes les actions sensibles :
 *  - Connexions / déconnexions
 *  - Tentatives de connexion échouées (bruteforce)
 *  - Blocages par rate limiting
 *  - Modifications d'utilisateurs (rôles, statut, trial)
 *  - Suppression de données
 *  - Exports de documents
 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_user",      columnList = "utilisateur_id"),
    @Index(name = "idx_audit_action",    columnList = "action"),
    @Index(name = "idx_audit_date",      columnList = "created_at"),
    @Index(name = "idx_audit_ip",        columnList = "adresse_ip")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Qui a fait l'action (null = anonyme / attaque externe) */
    @Column(name = "utilisateur_id")
    private Long utilisateurId;

    @Column(name = "nom_utilisateur", length = 100)
    private String nomUtilisateur;

    /** Type d'action */
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 60)
    private ActionAudit action;

    /** Résultat de l'action */
    @Enumerated(EnumType.STRING)
    @Column(name = "resultat", length = 20)
    private ResultatAudit resultat;

    /** Détails libres sur l'action */
    @Column(name = "details", length = 1000)
    private String details;

    /** Adresse IP de la requête */
    @Column(name = "adresse_ip", length = 60)
    private String adresseIp;

    /** User-Agent du navigateur */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** Module ERP concerné */
    @Column(name = "module", length = 60)
    private String module;

    /** Identifiant de la ressource modifiée */
    @Column(name = "ressource_id")
    private Long ressourceId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    // ── Enum actions ─────────────────────────────────────────────────────
    public enum ActionAudit {
        // Auth
        LOGIN_SUCCESS, LOGIN_ECHEC, LOGOUT, TOKEN_REFRESH,
        RATE_LIMIT_BLOQUE,
        // Utilisateurs
        UTILISATEUR_CREE, UTILISATEUR_MODIFIE, UTILISATEUR_SUPPRIME,
        ROLE_MODIFIE, STATUT_MODIFIE, TRIAL_RESET, MOT_DE_PASSE_CHANGE,
        // Calcul
        CALCUL_TAUX_UNIQUE, CALCUL_TAUX_VARIABLE, CALCUL_SUPPRIME,
        // Documents
        DOCUMENT_EXPORTE, DOCUMENT_SUPPRIME, MODELE_CREE, MODELE_MODIFIE,
        // Administration
        PERIODE_TAUX_CREE, PERIODE_TAUX_MODIFIEE, PERIODE_TAUX_SUPPRIMEE,
        // Commandes & Factures
        COMMANDE_CREEE, COMMANDE_SUPPRIMEE,
        FACTURE_GENEREE, FACTURE_SUPPRIMEE, FACTURE_STATUT_MODIFIE,
        // Achats
        ACHAT_CREE, ACHAT_RECEPTIONNE,
        // Sécurité
        SESSION_REVOQUEE, COMPTE_BLOQUE, MODIFICATION
    }

    public enum ResultatAudit {
        SUCCES, ECHEC, BLOQUE
    }
}
