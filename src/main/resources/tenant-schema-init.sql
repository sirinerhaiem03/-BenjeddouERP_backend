-- ═══════════════════════════════════════════════════════════════════════════════
-- TENANT SCHEMA INIT — Script d'initialisation des tables métier
-- Exécuté automatiquement à la création de chaque base dédiée erp_ent_XXXXX
--
-- Architecture SaaS Multi-Database :
--   - Ce script s'exécute DANS la base dédiée (erp_ent_00001, erp_ent_00002...)
--   - La base centrale (benjeddou_erp) ne contient PAS ces tables
--   - Isolation physique totale entre entreprises
--
-- Tables incluses (données métier de l'entreprise cliente) :
--   1.  utilisateurs          — comptes internes de l'entreprise
--   2.  clients               — clients de l'entreprise
--   3.  fournisseurs          — fournisseurs de l'entreprise
--   4.  produits              — catalogue produits
--   5.  commandes             — commandes clients (ventes)
--   6.  ligne_commandes       — lignes des commandes
--   7.  devis                 — devis commerciaux
--   8.  ligne_devis           — lignes des devis
--   9.  factures              — factures de vente
--   10. ecritures_comptables  — comptabilité
--   11. commandes_achat       — commandes fournisseurs (achats)
--   12. lignes_commande_achat — lignes des commandes achat
--   13. receptions_livraison  — réceptions des livraisons
--   14. entrepots             — entrepôts de stockage
--   15. stock_entrepots       — stocks par entrepôt
--   16. mouvements_stock      — historique des mouvements de stock
--   17. inventaires           — inventaires physiques
--   18. ligne_inventaires     — lignes d'inventaire
--   19. codes_promo           — codes promotionnels
--   20. modeles_document      — modèles de documents Word/PDF
--   21. documents_generes     — documents générés (factures PDF, devis...)
-- ═══════════════════════════════════════════════════════════════════════════════

-- ─── 1. UTILISATEURS (membres internes de l'entreprise) ──────────────────────
CREATE TABLE IF NOT EXISTS `utilisateurs` (
    `id`                              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `nom_utilisateur`                 VARCHAR(50)  NOT NULL UNIQUE,
    `email`                           VARCHAR(500) NOT NULL UNIQUE COMMENT 'Chiffré AES-256-GCM',
    `mot_de_passe`                    VARCHAR(255) NOT NULL,
    `prenom`                          VARCHAR(50)  NULL,
    `nom`                             VARCHAR(50)  NULL,
    `actif`                           BOOLEAN      NOT NULL DEFAULT TRUE,
    `role`                            VARCHAR(20)  NOT NULL DEFAULT 'USER',
    `langue_preferee`                 VARCHAR(5)   DEFAULT 'fr',
    `telephone`                       VARCHAR(500) NULL COMMENT 'Chiffré AES-256-GCM',
    `societe`                         VARCHAR(200) NULL,
    `adresse`                         VARCHAR(500) NULL COMMENT 'Chiffré AES-256-GCM',
    `token_session`                   VARCHAR(512) NULL,
    `statut_compte`                   VARCHAR(20)  NOT NULL DEFAULT 'ACTIF'
                                      COMMENT 'ACTIF | EN_ATTENTE | REFUSE | VERROUILLE | SUSPENDU',
    `mode_trial`                      BOOLEAN      NOT NULL DEFAULT FALSE,
    `nb_utilisations`                 INT          NOT NULL DEFAULT 0,
    `nb_utilisations_max`             INT          NOT NULL DEFAULT 30,
    `trial_expires_at`                DATETIME     NULL,
    `doit_changer_mot_de_passe`       BOOLEAN      NOT NULL DEFAULT FALSE,
    `kyc_soumis`                      BOOLEAN      NOT NULL DEFAULT FALSE,
    `token_recuperation`              VARCHAR(255) NULL,
    `expiration_token_recuperation`   DATETIME     NULL,
    `tentatives_connexion`            INT          NOT NULL DEFAULT 0,
    `derniere_tentative_connexion`    DATETIME     NULL,
    `entreprise_id`                   BIGINT       NULL COMMENT 'Référence entreprise master',
    `entreprise_schema`               VARCHAR(100) NULL,
    `date_creation`                   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    `date_modification`               TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ─── 2. CLIENTS ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `clients` (
    `id`                BIGINT AUTO_INCREMENT PRIMARY KEY,
    `nom`               VARCHAR(100) NOT NULL,
    `email`             VARCHAR(500) NOT NULL UNIQUE COMMENT 'Chiffré AES-256-GCM',
    `telephone`         VARCHAR(500) NULL COMMENT 'Chiffré AES-256-GCM',
    `adresse`           VARCHAR(1000) NULL COMMENT 'Chiffré AES-256-GCM',
    `matricule_fiscale` VARCHAR(50)  NULL,
    `date_creation`     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    `date_modification` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ─── 3. FOURNISSEURS ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `fournisseurs` (
    `id`                BIGINT AUTO_INCREMENT PRIMARY KEY,
    `nom`               VARCHAR(100) NOT NULL,
    `email`             VARCHAR(500) NOT NULL UNIQUE COMMENT 'Chiffré AES-256-GCM',
    `telephone`         VARCHAR(500) NULL COMMENT 'Chiffré AES-256-GCM',
    `adresse`           VARCHAR(1000) NULL COMMENT 'Chiffré AES-256-GCM',
    `matricule_fiscale` VARCHAR(50)  NULL,
    `date_creation`     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    `date_modification` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ─── 4. PRODUITS ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `produits` (
    `id`                BIGINT AUTO_INCREMENT PRIMARY KEY,
    `nom`               VARCHAR(100)    NOT NULL,
    `reference`         VARCHAR(50)     NOT NULL UNIQUE,
    `description`       TEXT            NULL,
    `prix_unitaire`     DECIMAL(15,3)   NOT NULL COMMENT 'Prix de vente TND',
    `prix_achat`        DECIMAL(15,3)   NOT NULL COMMENT 'Prix d achat TND',
    `quantite_stock`    INT             DEFAULT 0,
    `seuil_stock_min`   INT             DEFAULT 5,
    `categorie`         VARCHAR(50)     NULL,
    `date_creation`     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    `date_modification` TIMESTAMP       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ─── 5. COMMANDES (ventes) ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `commandes` (
    `id`                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    `numero_commande`     VARCHAR(50)   NOT NULL UNIQUE,
    `date_commande`       DATETIME      DEFAULT CURRENT_TIMESTAMP,
    `statut`              VARCHAR(20)   NOT NULL DEFAULT 'EN_ATTENTE'
                          COMMENT 'EN_ATTENTE | PAYEE | ANNULEE',
    `montant_total`       DECIMAL(15,3) NOT NULL,
    `code_promo_applique` VARCHAR(50)   NULL,
    `remise_promo`        DECIMAL(15,3) DEFAULT 0.000,
    `client_id`           BIGINT        NULL,
    `date_creation`       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    `date_modification`   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT `fk_cmd_client` FOREIGN KEY (`client_id`) REFERENCES `clients`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ─── 6. LIGNES DE COMMANDES ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `ligne_commandes` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `quantite`      INT           NOT NULL,
    `prix_unitaire` DECIMAL(15,3) NOT NULL,
    `remise`        DECIMAL(5,2)  DEFAULT 0.00,
    `produit_id`    BIGINT        NOT NULL,
    `commande_id`   BIGINT        NOT NULL,
    CONSTRAINT `fk_lc_produit`   FOREIGN KEY (`produit_id`)  REFERENCES `produits`(`id`)   ON DELETE RESTRICT,
    CONSTRAINT `fk_lc_commande`  FOREIGN KEY (`commande_id`) REFERENCES `commandes`(`id`)  ON DELETE CASCADE
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ─── 7. DEVIS ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `devis` (
    `id`                BIGINT AUTO_INCREMENT PRIMARY KEY,
    `numero_devis`      VARCHAR(50)   NOT NULL UNIQUE,
    `date_devis`        DATETIME      DEFAULT CURRENT_TIMESTAMP,
    `date_validite`     DATETIME      NULL,
    `statut`            VARCHAR(20)   NOT NULL DEFAULT 'BROUILLON'
                        COMMENT 'BROUILLON | ENVOYE | ACCEPTE | REFUSE',
    `montant_total`     DECIMAL(15,3) DEFAULT 0.000,
    `notes`             VARCHAR(500)  NULL,
    `client_id`         BIGINT        NULL,
    `date_creation`     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    `date_modification` TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT `fk_devis_client` FOREIGN KEY (`client_id`) REFERENCES `clients`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ─── 8. LIGNES DE DEVIS ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `ligne_devis` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `quantite`      INT           NOT NULL,
    `prix_unitaire` DECIMAL(15,3) NOT NULL,
    `remise`        DECIMAL(5,2)  DEFAULT 0.00,
    `produit_id`    BIGINT        NOT NULL,
    `devis_id`      BIGINT        NOT NULL,
    CONSTRAINT `fk_ld_produit` FOREIGN KEY (`produit_id`) REFERENCES `produits`(`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_ld_devis`   FOREIGN KEY (`devis_id`)   REFERENCES `devis`(`id`)    ON DELETE CASCADE
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ─── 9. FACTURES ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `factures` (
    `id`                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    `numero_facture`       VARCHAR(50)   NOT NULL UNIQUE,
    `date_emission`        DATETIME      DEFAULT (NOW()),
    `date_echeance`        DATETIME      NULL,
    `montant_total`        DECIMAL(15,3) NOT NULL,
    `montant_tva`          DECIMAL(15,3) NOT NULL,
    `statut`               VARCHAR(20)   NOT NULL DEFAULT 'EN_ATTENTE'
                           COMMENT 'EN_ATTENTE | PAYEE | ANNULEE | IMPAYEE',
    `signature_numerique`  VARCHAR(512)  NULL,
    `commande_id`          BIGINT        NULL,
    `date_creation`        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    `date_modification`    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT `fk_fact_commande` FOREIGN KEY (`commande_id`) REFERENCES `commandes`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ─── 10. ÉCRITURES COMPTABLES ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `ecritures_comptables` (
    `id`               BIGINT AUTO_INCREMENT PRIMARY KEY,
    `numero_ecriture`  VARCHAR(50)   UNIQUE,
    `date_ecriture`    DATE          NOT NULL,
    `libelle`          VARCHAR(300)  NOT NULL,
    `type_ecriture`    VARCHAR(30)   DEFAULT 'AUTRE'
                       COMMENT 'VENTE | ACHAT | TRESORERIE | SALAIRE | CHARGE | AUTRE',
    `sens`             VARCHAR(10)   DEFAULT 'DEBIT' COMMENT 'DEBIT | CREDIT',
    `montant`          DECIMAL(15,3) DEFAULT 0.000,
    `compte_comptable` VARCHAR(10)   NULL COMMENT 'Ex: 411, 701, 512',
    `reference_piece`  VARCHAR(100)  NULL,
    `statut`           VARCHAR(15)   DEFAULT 'BROUILLON' COMMENT 'BROUILLON | VALIDE',
    `facture_id`       BIGINT        NULL,
    `date_creation`    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `fk_ec_facture` FOREIGN KEY (`facture_id`) REFERENCES `factures`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ─── 11. COMMANDES ACHAT (fournisseurs) ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS `commandes_achat` (
    `id`                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    `numero_commande`       VARCHAR(50)   NOT NULL UNIQUE,
    `date_commande`         DATETIME      DEFAULT CURRENT_TIMESTAMP,
    `statut`                VARCHAR(30)   NOT NULL DEFAULT 'EN_ATTENTE'
                            COMMENT 'EN_ATTENTE | ENVOYEE | RECUE_PARTIELLE | RECUE_TOTALE | ANNULEE',
    `montant_total`         DECIMAL(15,3) DEFAULT 0.000,
    `notes`                 VARCHAR(500)  NULL,
    `date_livraison_prevue` DATETIME      NULL,
    `fournisseur_id`        BIGINT        NOT NULL,
    `date_creation`         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    `date_modification`     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT `fk_ca_fournisseur` FOREIGN KEY (`fournisseur_id`) REFERENCES `fournisseurs`(`id`) ON DELETE RESTRICT
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ─── 12. LIGNES COMMANDE ACHAT ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `lignes_commande_achat` (
    `id`                BIGINT AUTO_INCREMENT PRIMARY KEY,
    `commande_achat_id` BIGINT        NOT NULL,
    `produit_id`        BIGINT        NULL,
    `designation`       VARCHAR(200)  NULL,
    `quantite`          INT           NOT NULL DEFAULT 1,
    `prix_unitaire`     DECIMAL(15,3) DEFAULT 0.000,
    `montant_ligne`     DECIMAL(15,3) DEFAULT 0.000,
    CONSTRAINT `fk_lca_commande` FOREIGN KEY (`commande_achat_id`) REFERENCES `commandes_achat`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_lca_produit`  FOREIGN KEY (`produit_id`)         REFERENCES `produits`(`id`)        ON DELETE SET NULL
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ─── 13. RÉCEPTIONS LIVRAISONS ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `receptions_livraison` (
    `id`                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    `numero_reception`    VARCHAR(50)  UNIQUE,
    `date_reception`      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `statut`              VARCHAR(20)  DEFAULT 'CONFORME'
                          COMMENT 'CONFORME | PARTIELLE | NON_CONFORME',
    `commande_achat_id`   BIGINT       NOT NULL,
    `produit_id`          BIGINT       NULL,
    `entrepot_id`         BIGINT       NULL,
    `quantite_commandee`  INT          NULL,
    `quantite_recue`      INT          NULL,
    `observations`        VARCHAR(500) NULL,
    `date_creation`       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ─── 14. ENTREPÔTS ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `entrepots` (
    `id`                BIGINT AUTO_INCREMENT PRIMARY KEY,
    `code`              VARCHAR(50)  NOT NULL UNIQUE,
    `nom`               VARCHAR(100) NOT NULL,
    `adresse`           VARCHAR(255) NULL,
    `description`       TEXT         NULL,
    `date_creation`     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    `date_modification` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Ajout FK différé (entrepots créé après) ─────────────────────────────────────
ALTER TABLE `receptions_livraison`
    ADD CONSTRAINT `fk_rl_commande_achat` FOREIGN KEY (`commande_achat_id`) REFERENCES `commandes_achat`(`id`) ON DELETE RESTRICT;
ALTER TABLE `receptions_livraison`
    ADD CONSTRAINT `fk_rl_produit` FOREIGN KEY (`produit_id`) REFERENCES `produits`(`id`) ON DELETE SET NULL;
ALTER TABLE `receptions_livraison`
    ADD CONSTRAINT `fk_rl_entrepot` FOREIGN KEY (`entrepot_id`) REFERENCES `entrepots`(`id`) ON DELETE SET NULL;

-- ─── 15. STOCK PAR ENTREPÔT ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `stock_entrepots` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `produit_id`  BIGINT NOT NULL,
    `entrepot_id` BIGINT NOT NULL,
    `quantite`    INT    DEFAULT 0,
    CONSTRAINT `fk_se_produit`   FOREIGN KEY (`produit_id`)  REFERENCES `produits`(`id`)   ON DELETE CASCADE,
    CONSTRAINT `fk_se_entrepot`  FOREIGN KEY (`entrepot_id`) REFERENCES `entrepots`(`id`)  ON DELETE CASCADE,
    UNIQUE KEY `uq_produit_entrepot` (`produit_id`, `entrepot_id`)
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ─── 16. MOUVEMENTS DE STOCK ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `mouvements_stock` (
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `produit_id`      BIGINT       NOT NULL,
    `entrepot_id`     BIGINT       NOT NULL,
    `type_mouvement`  VARCHAR(20)  NOT NULL
                      COMMENT 'ENTREE | SORTIE | CORRECTION | TRANSFERT',
    `quantite`        INT          NOT NULL,
    `description`     VARCHAR(255) NULL,
    `date_mouvement`  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    `utilisateur_id`  BIGINT       NULL,
    CONSTRAINT `fk_ms_produit`      FOREIGN KEY (`produit_id`)     REFERENCES `produits`(`id`)      ON DELETE RESTRICT,
    CONSTRAINT `fk_ms_entrepot`     FOREIGN KEY (`entrepot_id`)    REFERENCES `entrepots`(`id`)     ON DELETE RESTRICT,
    CONSTRAINT `fk_ms_utilisateur`  FOREIGN KEY (`utilisateur_id`) REFERENCES `utilisateurs`(`id`)  ON DELETE SET NULL
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ─── 17. INVENTAIRES ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `inventaires` (
    `id`                BIGINT AUTO_INCREMENT PRIMARY KEY,
    `code`              VARCHAR(50)  NOT NULL UNIQUE,
    `date_inventaire`   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    `entrepot_id`       BIGINT       NOT NULL,
    `statut`            VARCHAR(20)  NOT NULL DEFAULT 'EN_COURS'
                        COMMENT 'EN_COURS | VALIDE',
    `description`       VARCHAR(255) NULL,
    `date_creation`     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    `date_modification` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT `fk_inv_entrepot` FOREIGN KEY (`entrepot_id`) REFERENCES `entrepots`(`id`) ON DELETE RESTRICT
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ─── 18. LIGNES D'INVENTAIRE ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `ligne_inventaires` (
    `id`                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    `inventaire_id`       BIGINT NOT NULL,
    `produit_id`          BIGINT NOT NULL,
    `quantite_theorique`  INT    NOT NULL,
    `quantite_physique`   INT    NOT NULL,
    CONSTRAINT `fk_li_inventaire` FOREIGN KEY (`inventaire_id`) REFERENCES `inventaires`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_li_produit`    FOREIGN KEY (`produit_id`)    REFERENCES `produits`(`id`)    ON DELETE RESTRICT,
    UNIQUE KEY `uq_inventaire_produit` (`inventaire_id`, `produit_id`)
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ─── 19. CODES PROMO ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `codes_promo` (
    `id`                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    `code`                   VARCHAR(50)    NOT NULL UNIQUE,
    `description`            VARCHAR(255)   NULL,
    `type_remise`            VARCHAR(20)    NOT NULL DEFAULT 'POURCENTAGE'
                             COMMENT 'POURCENTAGE | MONTANT_FIXE',
    `valeur`                 DECIMAL(10,3)  NOT NULL,
    `montant_minimum`        DECIMAL(15,3)  DEFAULT 0.000,
    `plafond_remise`         DECIMAL(15,3)  NULL,
    `date_debut`             DATETIME       NULL,
    `date_fin`               DATETIME       NULL,
    `utilisations_max`       INT            NULL,
    `utilisations_actuelles` INT            NOT NULL DEFAULT 0,
    `actif`                  BOOLEAN        NOT NULL DEFAULT TRUE,
    `date_creation`          TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    `date_modification`      TIMESTAMP      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ─── 20. MODÈLES DE DOCUMENTS ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `modeles_document` (
    `id`                BIGINT AUTO_INCREMENT PRIMARY KEY,
    `nom`               VARCHAR(200)  NOT NULL,
    `description`       VARCHAR(500)  NULL,
    `type_document`     VARCHAR(50)   NOT NULL
                        COMMENT 'FACTURE | DEVIS | BON_LIVRAISON | CONTRAT | AUTRE',
    `langue`            VARCHAR(10)   NOT NULL DEFAULT 'fr',
    `contenu_html`      LONGTEXT      NULL,
    `contenu_blob`      LONGBLOB      NULL COMMENT 'Fichier .docx binaire',
    `actif`             BOOLEAN       NOT NULL DEFAULT TRUE,
    `cree_par_id`       BIGINT        NULL,
    `date_creation`     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    `date_modification` TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT `fk_md_cree_par` FOREIGN KEY (`cree_par_id`) REFERENCES `utilisateurs`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ─── 21. DOCUMENTS GÉNÉRÉS ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `documents_generes` (
    `id`               BIGINT AUTO_INCREMENT PRIMARY KEY,
    `modele_id`        BIGINT       NULL,
    `titre_document`   VARCHAR(255) NOT NULL,
    `contenu_docx`     LONGBLOB     NULL,
    `contenu_pdf`      LONGBLOB     NULL,
    `module_source`    VARCHAR(100) NULL
                       COMMENT 'COMMERCIAL | ACHATS | RH | COMPTABILITE',
    `entite_id`        BIGINT       NULL COMMENT 'ID de la facture, commande, devis associé',
    `langue`           VARCHAR(10)  DEFAULT 'fr',
    `statut`           VARCHAR(50)  DEFAULT 'GENERE'
                       COMMENT 'GENERE | SIGNE | ARCHIVE',
    `donnees_fusion`   TEXT         NULL COMMENT 'JSON des données utilisées',
    `signature_base64` TEXT         NULL,
    `date_signature`   DATETIME     NULL,
    `version`          INT          NOT NULL DEFAULT 1,
    `genere_par_id`    BIGINT       NULL,
    `date_generation`  DATETIME     NOT NULL,
    `date_modification` DATETIME    NULL,
    CONSTRAINT `fk_dg_modele`     FOREIGN KEY (`modele_id`)     REFERENCES `modeles_document`(`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_dg_genere_par` FOREIGN KEY (`genere_par_id`) REFERENCES `utilisateurs`(`id`)     ON DELETE SET NULL
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ─── ENTREPÔT PAR DÉFAUT ──────────────────────────────────────────────────────
INSERT IGNORE INTO `entrepots` (`code`, `nom`, `description`)
VALUES ('ENT-PRINCIPAL', 'Entrepôt Principal', 'Entrepôt principal créé automatiquement');

-- ─── CODES PROMO ──────────────────────────────────────────────────────────────
-- Entité Java : CodePromo.java  @Table(name = "codes_promo")
-- Manquante → 500 sur /api/promo → "Erreur chargement promotions"
CREATE TABLE IF NOT EXISTS `codes_promo` (
    `id`                      BIGINT          AUTO_INCREMENT PRIMARY KEY,
    `code`                    VARCHAR(50)     NOT NULL UNIQUE
                              COMMENT 'Code saisi par le client ex: SUMMER20',
    `description`             VARCHAR(255)    NULL,
    `type_remise`             VARCHAR(20)     NOT NULL DEFAULT 'POURCENTAGE'
                              COMMENT 'POURCENTAGE | MONTANT_FIXE',
    `valeur`                  DECIMAL(10,3)   NOT NULL
                              COMMENT 'Ex: 15 = 15%  ou  10 = 10 TND fixe',
    `montant_minimum`         DECIMAL(15,3)   NOT NULL DEFAULT 0.000
                              COMMENT 'Montant minimum commande pour activer',
    `plafond_remise`          DECIMAL(15,3)   NULL
                              COMMENT 'Plafond de remise pour les %. NULL = illimité',
    `date_debut`              DATETIME        NULL,
    `date_fin`                DATETIME        NULL,
    `utilisations_max`        INT             NULL
                              COMMENT 'NULL = illimité',
    `utilisations_actuelles`  INT             NOT NULL DEFAULT 0,
    `actif`                   TINYINT(1)      NOT NULL DEFAULT 1,
    `date_creation`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `date_modification`       DATETIME        NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ═══════════════════════════════════════════════════════════════════════════════
-- FIN DU SCRIPT D'INITIALISATION
-- Toutes les tables métier sont prêtes. L'entreprise peut commencer à utiliser
-- la plateforme immédiatement après la création de son espace.
-- ═══════════════════════════════════════════════════════════════════════════════

