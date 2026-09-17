-- ══════════════════════════════════════════════════════════════════════════════
-- master-schema.sql — Schéma complet de benjeddou_erp (base SaaS Master)
-- Exécuté automatiquement au démarrage par DatabaseInitializer
-- Toutes les instructions utilisent CREATE TABLE IF NOT EXISTS (idempotent)
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────
-- TABLE : utilisateurs (SuperAdmin SaaS uniquement)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS utilisateurs (
    id                            BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom_utilisateur               VARCHAR(50)  NOT NULL UNIQUE,
    email                         VARCHAR(100) NOT NULL UNIQUE,
    mot_de_passe                  VARCHAR(255) NOT NULL,
    prenom                        VARCHAR(50)  NULL,
    nom                           VARCHAR(50)  NULL,
    actif                         BOOLEAN      DEFAULT TRUE,
    role                          VARCHAR(20)  NOT NULL DEFAULT 'SUPERADMIN',
    langue_preferee               VARCHAR(5)   DEFAULT 'fr',
    token_session                 VARCHAR(512) NULL,
    token_recuperation            VARCHAR(255) NULL,
    expiration_token_recuperation DATETIME     NULL,
    entreprise_id                 BIGINT       NULL,
    entreprise_schema             VARCHAR(100) NULL,
    statut_compte                 VARCHAR(20)  DEFAULT 'ACTIF',
    mode_trial                    BOOLEAN      DEFAULT FALSE,
    nb_utilisations               INT          DEFAULT 0,
    nb_utilisations_max           INT          DEFAULT 30,
    doit_changer_mot_de_passe     BOOLEAN      DEFAULT FALSE,
    telephone                     VARCHAR(20)  NULL,
    societe                       VARCHAR(200) NULL,
    adresse                       VARCHAR(500) NULL,
    kyc_soumis                    BOOLEAN      DEFAULT FALSE,
    trial_expires_at              DATETIME     NULL,
    date_creation                 TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    date_modification             TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : entreprises
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS entreprises (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom               VARCHAR(200) NOT NULL,
    schema_name       VARCHAR(100) NOT NULL UNIQUE,
    db_url            VARCHAR(500) NULL,
    db_username       VARCHAR(100) NULL,
    db_password       VARCHAR(255) NULL,
    email_contact     VARCHAR(200) NULL,
    telephone         VARCHAR(30)  NULL,
    adresse           VARCHAR(500) NULL,
    matricule_fiscale VARCHAR(50)  NULL,
    pays              VARCHAR(50)  DEFAULT 'Tunisie',
    admin_email       VARCHAR(200) NULL,
    admin_id          BIGINT       NULL,
    statut            VARCHAR(20)  DEFAULT 'ACTIVE',
    date_inscription  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    date_modification TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : abonnements
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS abonnements (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    entreprise_id           BIGINT        NOT NULL,
    plan                    VARCHAR(30)   NOT NULL DEFAULT 'STARTER',
    statut                  VARCHAR(20)   NOT NULL DEFAULT 'TRIAL',
    date_debut              DATE          NOT NULL,
    date_fin                DATE          NULL,
    prix_mensuel            DECIMAL(10,3) DEFAULT 0.000,
    nb_connexions_trial     INT           DEFAULT 30,
    nb_connexions_utilisees INT           DEFAULT 0,
    date_creation           TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    date_modification       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_abo_ent FOREIGN KEY (entreprise_id) REFERENCES entreprises(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : licences
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS licences (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    entreprise_id       BIGINT       NOT NULL,
    cle_licence         VARCHAR(100) NOT NULL UNIQUE,
    type_licence        VARCHAR(30)  DEFAULT 'STANDARD',
    nb_utilisateurs_max INT          DEFAULT 5,
    date_emission       DATE         NULL,
    date_expiration     DATE         NULL,
    actif               BOOLEAN      DEFAULT TRUE,
    date_creation       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lic_ent FOREIGN KEY (entreprise_id) REFERENCES entreprises(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : periodes_taux (taux fixes gérés par SuperAdmin)
-- IMPORTANT : colonnes doivent correspondre à l'entité PeriodeTaux.java
-- Formule : Résultat = Montant × (Taux / 100) × (Jours / 365)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS periodes_taux (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    date_debut        DATE          NOT NULL,
    date_fin          DATE          NOT NULL,
    taux              DECIMAL(5,2)  NOT NULL COMMENT 'Taux en % — ex: 9.75 = 9,75%',
    libelle           VARCHAR(200)  NULL,
    actif             BOOLEAN       NOT NULL DEFAULT TRUE,
    date_creation     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    date_modification TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : audit_logs — correspond à l'entité AuditLog.java
-- ATTENTION : utilisateur_id est une colonne simple (pas de FK) !
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS audit_logs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    utilisateur_id  BIGINT       NULL,
    nom_utilisateur VARCHAR(100) NULL,
    action          VARCHAR(60)  NOT NULL,
    resultat        VARCHAR(20)  NULL,
    details         VARCHAR(1000) NULL,
    adresse_ip      VARCHAR(60)  NULL,
    user_agent      VARCHAR(500) NULL,
    module          VARCHAR(60)  NULL,
    ressource_id    BIGINT       NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : connexion_logs
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS connexion_logs (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    utilisateur_id BIGINT       NULL,
    entreprise_id  BIGINT       NULL,
    email_tente    VARCHAR(100) NULL,
    succes         BOOLEAN      NOT NULL DEFAULT FALSE,
    adresse_ip     VARCHAR(45)  NULL,
    user_agent     VARCHAR(500) NULL,
    date_connexion TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cl_u FOREIGN KEY (utilisateur_id) REFERENCES utilisateurs(id) ON DELETE SET NULL,
    CONSTRAINT fk_cl_e FOREIGN KEY (entreprise_id)  REFERENCES entreprises(id)  ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : parametres_plateforme
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS parametres_plateforme (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    cle               VARCHAR(100) NOT NULL UNIQUE,
    valeur            TEXT         NULL,
    description       VARCHAR(500) NULL,
    date_modification TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : refresh_tokens
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    token           VARCHAR(512) NOT NULL UNIQUE,
    utilisateur_id  BIGINT       NOT NULL,
    date_expiration TIMESTAMP    NOT NULL,
    revoque         BOOLEAN      DEFAULT FALSE,
    date_creation   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rt_u FOREIGN KEY (utilisateur_id) REFERENCES utilisateurs(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : theme_config (SuperAdmin)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS theme_config (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    utilisateur_id      BIGINT       NULL UNIQUE,
    primary_color       VARCHAR(20)  DEFAULT '#f97316',
    accent_color        VARCHAR(20)  DEFAULT '#7c3aed',
    sidebar_color       VARCHAR(20)  DEFAULT '#0f172a',
    dark_mode           BOOLEAN      DEFAULT FALSE,
    theme_preset        VARCHAR(20)  DEFAULT 'light',
    sidebar_position    VARCHAR(10)  DEFAULT 'left',
    animations_enabled  BOOLEAN      DEFAULT TRUE,
    logout_position     VARCHAR(20)  DEFAULT 'both',
    date_modification   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- Colonnes optionnelles à ajouter si absentes (migrations)
-- ─────────────────────────────────────────────────────────────
-- Ces ALTER ignorent silencieusement si la colonne existe déjà
-- (MySQL 8.0+ : IF NOT EXISTS sur ALTER TABLE)

-- ─────────────────────────────────────────────────────────────
-- DONNÉES INITIALES : Périodes et Taux BCT Tunisie
-- Centralisés dans benjeddou_erp — partagés avec tous les tenants
-- Le Super Admin peut modifier ces valeurs depuis son interface
-- ─────────────────────────────────────────────────────────────
INSERT IGNORE INTO periodes_taux (date_debut, date_fin, taux, libelle, actif, date_creation) VALUES
('2024-01-01', '2024-06-30',  8.00, 'Taux BCT S1 2024 — Banque Centrale de Tunisie',  TRUE, NOW()),
('2024-07-01', '2024-12-31',  7.50, 'Taux BCT S2 2024 — Banque Centrale de Tunisie',  TRUE, NOW()),
('2025-01-01', '2025-06-30',  7.00, 'Taux BCT S1 2025 — Banque Centrale de Tunisie',  TRUE, NOW()),
('2025-07-01', '2025-12-31',  6.75, 'Taux BCT S2 2025 — Banque Centrale de Tunisie',  TRUE, NOW()),
('2026-01-01', '2026-06-30',  6.50, 'Taux BCT S1 2026 — Banque Centrale de Tunisie',  TRUE, NOW()),
('2026-07-01', '2026-12-31',  6.25, 'Taux BCT S2 2026 — Banque Centrale de Tunisie',  TRUE, NOW());

-- ─────────────────────────────────────────────────────────────
-- TABLE : codes_promo (master + chaque tenant)
-- La table existe dans la base master car le routing tenant
-- peut ne pas être actif pour certains utilisateurs (schema NULL).
-- CodePromo.java @Table(name="codes_promo")
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `codes_promo` (
    `id`                     BIGINT        AUTO_INCREMENT PRIMARY KEY,
    `code`                   VARCHAR(50)   NOT NULL UNIQUE,
    `description`            VARCHAR(255)  NULL,
    `type_remise`            VARCHAR(20)   NOT NULL DEFAULT 'POURCENTAGE',
    `valeur`                 DECIMAL(10,3) NOT NULL,
    `montant_minimum`        DECIMAL(15,3) NOT NULL DEFAULT 0.000,
    `plafond_remise`         DECIMAL(15,3) NULL,
    `date_debut`             DATETIME      NULL,
    `date_fin`               DATETIME      NULL,
    `utilisations_max`       INT           NULL,
    `utilisations_actuelles` INT           NOT NULL DEFAULT 0,
    `actif`                  TINYINT(1)    NOT NULL DEFAULT 1,
    `date_creation`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `date_modification`      DATETIME      NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : documents_kyc (SuperAdmin / KYC verification)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS documents_kyc (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    utilisateur_id      BIGINT       NOT NULL,
    type_document       VARCHAR(50)  NULL,
    nom_fichier         VARCHAR(255) NULL,
    content_type        VARCHAR(100) NULL,
    contenu_fichier     LONGBLOB     NULL,
    statut_verification VARCHAR(20)  DEFAULT 'EN_ATTENTE',
    date_soumission     DATETIME     NULL,
    commentaire_admin   VARCHAR(500) NULL,
    CONSTRAINT fk_dk_user FOREIGN KEY (utilisateur_id) REFERENCES utilisateurs(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


