-- ══════════════════════════════════════════════════════════════════════════════
-- tenant-schema.sql — Schéma complet erp_ent_00000 (base entreprise démo)
-- Exécuté automatiquement au démarrage par DatabaseInitializer
-- Idempotent : CREATE TABLE IF NOT EXISTS + ALTER TABLE ... ADD COLUMN IF NOT EXISTS
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────
-- TABLE : utilisateurs (employés de l'entreprise)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS utilisateurs (
    id                            BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom_utilisateur               VARCHAR(50)  NOT NULL UNIQUE,
    email                         VARCHAR(100) NOT NULL UNIQUE,
    mot_de_passe                  VARCHAR(255) NOT NULL,
    prenom                        VARCHAR(50)  NULL,
    nom                           VARCHAR(50)  NULL,
    actif                         BOOLEAN      DEFAULT TRUE,
    role                          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    langue_preferee               VARCHAR(5)   DEFAULT 'fr',
    statut_compte                 VARCHAR(20)  DEFAULT 'ACTIF',
    mode_trial                    BOOLEAN      DEFAULT FALSE,
    nb_utilisations               INT          DEFAULT 0,
    nb_utilisations_max           INT          DEFAULT 30,
    token_session                 VARCHAR(512) NULL,
    doit_changer_mot_de_passe     BOOLEAN      DEFAULT FALSE,
    telephone                     VARCHAR(20)  NULL,
    societe                       VARCHAR(200) NULL,
    adresse                       VARCHAR(500) NULL,
    kyc_soumis                    BOOLEAN      DEFAULT FALSE,
    trial_expires_at              DATETIME     NULL,
    token_recuperation            VARCHAR(255) NULL,
    expiration_token_recuperation DATETIME     NULL,
    entreprise_id                 BIGINT       NULL,
    entreprise_schema             VARCHAR(100) NULL,
    date_creation                 TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    date_modification             TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
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
    CONSTRAINT fk_rt_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateurs(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : clients
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS clients (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom               VARCHAR(200)  NOT NULL,
    email             VARCHAR(100)  NOT NULL UNIQUE,
    telephone         VARCHAR(20)   NULL,
    adresse           VARCHAR(500)  NULL,
    matricule_fiscale VARCHAR(50)   NULL,
    code_client       VARCHAR(50)   NULL,
    type_client       VARCHAR(20)   DEFAULT 'ENTREPRISE',
    plafond_credit    DECIMAL(15,3) DEFAULT 0.000,
    date_creation     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    date_modification TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : fournisseurs
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS fournisseurs (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom                  VARCHAR(200) NOT NULL,
    email                VARCHAR(100) NOT NULL UNIQUE,
    telephone            VARCHAR(20)  NULL,
    adresse              VARCHAR(500) NULL,
    matricule_fiscale    VARCHAR(50)  NULL,
    code_fournisseur     VARCHAR(50)  NULL,
    delai_paiement_jours INT          DEFAULT 30,
    date_creation        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    date_modification    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : produits
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS produits (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    reference         VARCHAR(50)   NOT NULL UNIQUE,
    designation       VARCHAR(300)  NOT NULL,
    description       TEXT          NULL,
    prix_achat        DECIMAL(15,3) DEFAULT 0.000,
    prix_vente        DECIMAL(15,3) NOT NULL DEFAULT 0.000,
    taux_tva          DECIMAL(5,2)  DEFAULT 19.00,
    categorie         VARCHAR(100)  NULL,
    unite             VARCHAR(20)   DEFAULT 'UNITE',
    stock_actuel      INT           DEFAULT 0,
    stock_minimum     INT           DEFAULT 0,
    actif             BOOLEAN       DEFAULT TRUE,
    date_creation     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    date_modification TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : devis
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS devis (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    numero_devis      VARCHAR(50)   NOT NULL UNIQUE,
    date_devis        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    date_validite     DATE          NULL,
    statut            VARCHAR(20)   NOT NULL DEFAULT 'BROUILLON',
    montant_ht        DECIMAL(15,3) NOT NULL DEFAULT 0.000,
    montant_tva       DECIMAL(15,3) NOT NULL DEFAULT 0.000,
    montant_total     DECIMAL(15,3) NOT NULL DEFAULT 0.000,
    remise_globale    DECIMAL(5,2)  DEFAULT 0.00,
    notes             TEXT          NULL,
    client_id         BIGINT        NULL,
    date_creation     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    date_modification TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_dv_client FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ligne_devis (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    devis_id      BIGINT        NOT NULL,
    produit_id    BIGINT        NOT NULL,
    quantite      INT           NOT NULL,
    prix_unitaire DECIMAL(15,3) NOT NULL,
    remise        DECIMAL(5,2)  DEFAULT 0.00,
    taux_tva      DECIMAL(5,2)  DEFAULT 19.00,
    CONSTRAINT fk_ldv_devis   FOREIGN KEY (devis_id)   REFERENCES devis(id)    ON DELETE CASCADE,
    CONSTRAINT fk_ldv_produit FOREIGN KEY (produit_id) REFERENCES produits(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : commandes (ventes)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS commandes (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    numero_commande   VARCHAR(50)   NOT NULL UNIQUE,
    date_commande     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    statut            VARCHAR(20)   NOT NULL DEFAULT 'EN_ATTENTE',
    montant_ht        DECIMAL(15,3) NOT NULL DEFAULT 0.000,
    montant_tva       DECIMAL(15,3) NOT NULL DEFAULT 0.000,
    montant_total     DECIMAL(15,3) NOT NULL DEFAULT 0.000,
    remise_globale    DECIMAL(5,2)  DEFAULT 0.00,
    notes             TEXT          NULL,
    client_id         BIGINT        NULL,
    devis_id          BIGINT        NULL,
    date_creation     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    date_modification TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_cmd_client FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE SET NULL,
    CONSTRAINT fk_cmd_devis  FOREIGN KEY (devis_id)  REFERENCES devis(id)   ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ligne_commandes (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    commande_id   BIGINT        NOT NULL,
    produit_id    BIGINT        NOT NULL,
    quantite      INT           NOT NULL,
    prix_unitaire DECIMAL(15,3) NOT NULL,
    remise        DECIMAL(5,2)  DEFAULT 0.00,
    taux_tva      DECIMAL(5,2)  DEFAULT 19.00,
    CONSTRAINT fk_lc_commande FOREIGN KEY (commande_id) REFERENCES commandes(id) ON DELETE CASCADE,
    CONSTRAINT fk_lc_produit  FOREIGN KEY (produit_id)  REFERENCES produits(id)  ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : factures
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS factures (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    numero_facture      VARCHAR(50)   NOT NULL UNIQUE,
    date_emission       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    date_echeance       DATE          NULL,
    montant_ht          DECIMAL(15,3) NOT NULL DEFAULT 0.000,
    montant_tva         DECIMAL(15,3) NOT NULL DEFAULT 0.000,
    montant_total       DECIMAL(15,3) NOT NULL DEFAULT 0.000,
    statut              VARCHAR(20)   NOT NULL DEFAULT 'EN_ATTENTE',
    signature_numerique VARCHAR(512)  NULL,
    notes               TEXT          NULL,
    commande_id         BIGINT        NULL,
    client_id           BIGINT        NULL,
    date_creation       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    date_modification   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_fact_commande FOREIGN KEY (commande_id) REFERENCES commandes(id) ON DELETE SET NULL,
    CONSTRAINT fk_fact_client   FOREIGN KEY (client_id)   REFERENCES clients(id)   ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : commandes_achat (achats fournisseurs)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS commandes_achat (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    numero_commande     VARCHAR(50)   NOT NULL UNIQUE,
    date_commande       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    statut              VARCHAR(20)   NOT NULL DEFAULT 'EN_ATTENTE',
    montant_ht          DECIMAL(15,3) NOT NULL DEFAULT 0.000,
    montant_tva         DECIMAL(15,3) NOT NULL DEFAULT 0.000,
    montant_total       DECIMAL(15,3) NOT NULL DEFAULT 0.000,
    notes               TEXT          NULL,
    fournisseur_id      BIGINT        NULL,
    date_livraison_prev DATE          NULL,
    date_creation       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    date_modification   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ca_fournisseur FOREIGN KEY (fournisseur_id) REFERENCES fournisseurs(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ligne_commandes_achat (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    commande_achat_id BIGINT        NOT NULL,
    produit_id        BIGINT        NOT NULL,
    quantite          INT           NOT NULL,
    prix_unitaire     DECIMAL(15,3) NOT NULL,
    taux_tva          DECIMAL(5,2)  DEFAULT 19.00,
    CONSTRAINT fk_lca_commande FOREIGN KEY (commande_achat_id) REFERENCES commandes_achat(id) ON DELETE CASCADE,
    CONSTRAINT fk_lca_produit  FOREIGN KEY (produit_id)        REFERENCES produits(id)        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : ecritures_comptables
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ecritures_comptables (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    numero_ecriture VARCHAR(50)   NOT NULL UNIQUE,
    date_ecriture   DATE          NOT NULL,
    type_ecriture   VARCHAR(50)   NOT NULL,
    libelle         VARCHAR(500)  NOT NULL,
    montant_debit   DECIMAL(15,3) DEFAULT 0.000,
    montant_credit  DECIMAL(15,3) DEFAULT 0.000,
    compte_debit    VARCHAR(20)   NULL,
    compte_credit   VARCHAR(20)   NULL,
    reference_doc   VARCHAR(100)  NULL,
    utilisateur_id  BIGINT        NULL,
    date_creation   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ec_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateurs(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : entrepots
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS entrepots (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom           VARCHAR(200) NOT NULL,
    adresse       VARCHAR(500) NULL,
    responsable   VARCHAR(200) NULL,
    date_creation TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : stock_entrepots
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS stock_entrepots (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    produit_id  BIGINT NOT NULL,
    entrepot_id BIGINT NOT NULL,
    quantite    INT    DEFAULT 0,
    CONSTRAINT fk_se_produit  FOREIGN KEY (produit_id)  REFERENCES produits(id)  ON DELETE CASCADE,
    CONSTRAINT fk_se_entrepot FOREIGN KEY (entrepot_id) REFERENCES entrepots(id) ON DELETE CASCADE,
    UNIQUE KEY uq_prod_entrepot (produit_id, entrepot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : mouvements_stock
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS mouvements_stock (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    produit_id     BIGINT       NOT NULL,
    entrepot_id    BIGINT       NULL,
    type_mouvement VARCHAR(20)  NOT NULL,
    quantite       INT          NOT NULL,
    reference_doc  VARCHAR(100) NULL,
    motif          VARCHAR(300) NULL,
    utilisateur_id BIGINT       NULL,
    date_mouvement TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ms_produit     FOREIGN KEY (produit_id)     REFERENCES produits(id)      ON DELETE CASCADE,
    CONSTRAINT fk_ms_entrepot    FOREIGN KEY (entrepot_id)    REFERENCES entrepots(id)     ON DELETE SET NULL,
    CONSTRAINT fk_ms_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateurs(id)  ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : inventaires
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS inventaires (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    reference         VARCHAR(50)  NOT NULL UNIQUE,
    statut            VARCHAR(20)  NOT NULL DEFAULT 'EN_COURS',
    entrepot_id       BIGINT       NULL,
    utilisateur_id    BIGINT       NULL,
    date_inventaire   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    date_creation     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    date_modification TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_inv_entrepot    FOREIGN KEY (entrepot_id)    REFERENCES entrepots(id)    ON DELETE SET NULL,
    CONSTRAINT fk_inv_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateurs(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ligne_inventaires (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    inventaire_id  BIGINT NOT NULL,
    produit_id     BIGINT NOT NULL,
    quantite_theo  INT    DEFAULT 0,
    quantite_reelle INT   DEFAULT 0,
    ecart          INT    DEFAULT 0,
    CONSTRAINT fk_li_inventaire FOREIGN KEY (inventaire_id) REFERENCES inventaires(id) ON DELETE CASCADE,
    CONSTRAINT fk_li_produit    FOREIGN KEY (produit_id)    REFERENCES produits(id)    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : receptions_livraison
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS receptions_livraison (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    numero_reception  VARCHAR(50)   NOT NULL UNIQUE,
    commande_achat_id BIGINT        NULL,
    fournisseur_id    BIGINT        NULL,
    entrepot_id       BIGINT        NULL,
    statut            VARCHAR(20)   NOT NULL DEFAULT 'EN_ATTENTE',
    notes             TEXT          NULL,
    date_reception    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    date_creation     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    date_modification TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_rl_commande    FOREIGN KEY (commande_achat_id) REFERENCES commandes_achat(id) ON DELETE SET NULL,
    CONSTRAINT fk_rl_fournisseur FOREIGN KEY (fournisseur_id)   REFERENCES fournisseurs(id)    ON DELETE SET NULL,
    CONSTRAINT fk_rl_entrepot    FOREIGN KEY (entrepot_id)      REFERENCES entrepots(id)       ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : modeles_documents
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS modeles_documents (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom               VARCHAR(200) NOT NULL,
    type_document     VARCHAR(50)  NOT NULL,
    contenu           LONGTEXT     NULL,
    actif             BOOLEAN      DEFAULT TRUE,
    date_creation     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    date_modification TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : documents_generes
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS documents_generes (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom_fichier     VARCHAR(300) NOT NULL,
    type_document   VARCHAR(50)  NOT NULL,
    format          VARCHAR(20)  DEFAULT 'PDF',
    contenu_blob    LONGBLOB     NULL,
    reference_doc   VARCHAR(100) NULL,
    modele_id       BIGINT       NULL,
    utilisateur_id  BIGINT       NULL,
    date_creation   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dg_modele      FOREIGN KEY (modele_id)      REFERENCES modeles_documents(id) ON DELETE SET NULL,
    CONSTRAINT fk_dg_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateurs(id)      ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : versions_documents
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS versions_documents (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id   BIGINT       NOT NULL,
    version       INT          NOT NULL DEFAULT 1,
    commentaire   VARCHAR(500) NULL,
    date_creation TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vd_document FOREIGN KEY (document_id) REFERENCES documents_generes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : documents_kyc
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS documents_kyc (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    utilisateur_id BIGINT       NOT NULL,
    type_doc       VARCHAR(50)  NOT NULL,
    nom_fichier    VARCHAR(300) NOT NULL,
    contenu_blob   LONGBLOB     NULL,
    statut         VARCHAR(20)  DEFAULT 'EN_ATTENTE',
    date_creation  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dk_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateurs(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : codes_promo
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS codes_promo (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    code             VARCHAR(50)   NOT NULL UNIQUE,
    description      VARCHAR(300)  NULL,
    type_remise      VARCHAR(20)   DEFAULT 'POURCENTAGE',
    valeur_remise    DECIMAL(10,3) NOT NULL,
    date_debut       DATE          NULL,
    date_fin         DATE          NULL,
    nb_utilisations_max INT        DEFAULT 1,
    nb_utilisations  INT           DEFAULT 0,
    actif            BOOLEAN       DEFAULT TRUE,
    date_creation    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    date_modification TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : theme_config (par utilisateur)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS theme_config (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    utilisateur_id      BIGINT       NULL UNIQUE,
    theme_preset        VARCHAR(20)  DEFAULT 'light',
    sidebar_position    VARCHAR(10)  DEFAULT 'left',
    animations_enabled  BOOLEAN      DEFAULT TRUE,
    logout_position     VARCHAR(20)  DEFAULT 'both',
    primary_color       VARCHAR(20)  DEFAULT '#f97316',
    accent_color        VARCHAR(20)  DEFAULT '#7c3aed',
    sidebar_color       VARCHAR(20)  DEFAULT '#0f172a',
    dark_mode           BOOLEAN      DEFAULT FALSE,
    date_modification   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_tc_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateurs(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : connexions_log — correspond à l'entité ConnexionLog.java
-- Nom EXACT de la table : connexions_log (avec 's' sur connexions)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS connexions_log (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    utilisateur_id        BIGINT       NULL,
    statut                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    session_token         VARCHAR(600) NULL,
    signalement_token     VARCHAR(100) NULL UNIQUE,
    est_signale           BOOLEAN      DEFAULT FALSE,
    date_signalement      DATETIME     NULL,
    adresse_ip            VARCHAR(50)  NULL,
    user_agent            VARCHAR(500) NULL,
    type_appareil         VARCHAR(50)  NULL,
    os                    VARCHAR(100) NULL,
    navigateur            VARCHAR(100) NULL,
    resolution            VARCHAR(20)  NULL,
    langue                VARCHAR(20)  NULL,
    fuseau_horaire        VARCHAR(60)  NULL,
    device_fingerprint    VARCHAR(100) NULL,
    appareil_connu        BOOLEAN      DEFAULT FALSE,
    type_reseau           VARCHAR(30)  NULL,
    niveau_risque         INT          DEFAULT 0,
    connexion_inhabituelle BOOLEAN     DEFAULT FALSE,
    pays                  VARCHAR(60)  NULL,
    region                VARCHAR(60)  NULL,
    ville                 VARCHAR(60)  NULL,
    latitude              DOUBLE       NULL,
    longitude             DOUBLE       NULL,
    fournisseur_internet  VARCHAR(100) NULL,
    succes                BOOLEAN      NOT NULL DEFAULT TRUE,
    date_connexion        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    date_deconnexion      DATETIME     NULL,
    motif_revocation      VARCHAR(200) NULL,
    CONSTRAINT fk_cl_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateurs(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : audit_logs — correspond à l'entité AuditLog.java
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS audit_logs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    utilisateur_id  BIGINT        NULL,
    nom_utilisateur VARCHAR(100)  NULL,
    action          VARCHAR(60)   NOT NULL,
    resultat        VARCHAR(20)   NULL,
    details         VARCHAR(1000) NULL,
    adresse_ip      VARCHAR(60)   NULL,
    user_agent      VARCHAR(500)  NULL,
    module          VARCHAR(60)   NULL,
    ressource_id    BIGINT        NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ╔══════════════════════════════════════════════════════════════════════╗
-- ║  ARCHITECTURE : periodes_taux N'EST PAS dans les bases tenant        ║
-- ║  Elle est CENTRALISÉE dans benjeddou_erp (base master)               ║
-- ║  Gérée EXCLUSIVEMENT par le Super Admin SaaS                         ║
-- ║  Accès via MasterTenantContext dans MoteurCalculService               ║
-- ╚══════════════════════════════════════════════════════════════════════╝

-- ─────────────────────────────────────────────────────────────
-- TABLE : calculs_moteur
-- Correspond exactement à l'entité CalculMoteur.java
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS calculs_moteur (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    reference      VARCHAR(30)   NOT NULL UNIQUE COMMENT 'CM-YYYYMMDD-XXXX',
    type_calcul    VARCHAR(20)   NOT NULL COMMENT 'TAUX_UNIQUE ou TAUX_VARIABLE',
    montant        DECIMAL(15,3) NOT NULL,
    date_debut     DATE          NOT NULL,
    date_fin       DATE          NOT NULL,
    nombre_jours   BIGINT        NOT NULL,
    taux_unique    DECIMAL(5,2)  NULL COMMENT 'Null pour TAUX_VARIABLE',
    resultat_total DECIMAL(15,2) NOT NULL,
    module_erp     VARCHAR(50)   DEFAULT 'GENERAL',
    libelle        VARCHAR(300)  NULL,
    cree_par_id    BIGINT        NULL,
    date_creation  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cm_utilisateur FOREIGN KEY (cree_par_id) REFERENCES utilisateurs(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE : lignes_calcul
-- Correspond exactement à l'entité LigneCalcul.java
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS lignes_calcul (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    calcul_id       BIGINT        NOT NULL,
    numero_ligne    INT           NOT NULL,
    date_debut      DATE          NOT NULL,
    date_fin        DATE          NOT NULL,
    nombre_jours    BIGINT        NOT NULL,
    taux            DECIMAL(5,2)  NOT NULL,
    montant_base    DECIMAL(15,3) NOT NULL,
    resultat_ligne  DECIMAL(15,2) NOT NULL,
    libelle_periode VARCHAR(200)  NULL,
    CONSTRAINT fk_lc_calcul FOREIGN KEY (calcul_id) REFERENCES calculs_moteur(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ══════════════════════════════════════════════════════════════
-- MIGRATIONS — Ajout colonnes manquantes pour bases existantes
-- MySQL 8 ne supporte pas IF NOT EXISTS sur ALTER TABLE ADD COLUMN
-- → chaque colonne est séparée ; l'erreur 1060 est ignorée par le code
-- ══════════════════════════════════════════════════════════════
ALTER TABLE utilisateurs ADD COLUMN entreprise_id BIGINT NULL;
ALTER TABLE utilisateurs ADD COLUMN entreprise_schema VARCHAR(100) NULL;
ALTER TABLE utilisateurs ADD COLUMN doit_changer_mot_de_passe BOOLEAN DEFAULT FALSE;
ALTER TABLE refresh_tokens ADD COLUMN revoque BOOLEAN DEFAULT FALSE;
ALTER TABLE refresh_tokens ADD COLUMN date_creation TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- ══════════════════════════════════════════════════════════════
-- TABLE : codes_promo
-- Entité Java : CodePromo.java  @Table(name = "codes_promo")
-- Manquante → 500 /api/promo → "Erreur chargement promotions"
-- ══════════════════════════════════════════════════════════════
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

-- ══════════════════════════════════════════════════════════════
-- TABLE : devis — ajout colonne manquante montant_total
-- Hibernate Devis.java @Column(name="montant_total") exige cette colonne
-- ══════════════════════════════════════════════════════════════
ALTER TABLE devis ADD COLUMN montant_total DECIMAL(15,3) NOT NULL DEFAULT 0.000;
UPDATE devis SET montant_total = montant_ttc WHERE montant_total = 0 AND montant_ttc > 0;

