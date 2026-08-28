-- ══════════════════════════════════════════════════════════════
-- BENJEDDOU ERP — Export SQL
-- Base       : erp_ent_00000
-- Date       : 2026-08-11T13:06:41.405477100
-- Exporté par: superadmin
-- ══════════════════════════════════════════════════════════════

SET FOREIGN_KEY_CHECKS=0;
SET SQL_MODE='NO_AUTO_VALUE_ON_ZERO';
SET NAMES utf8mb4;


-- ─────────────────────────────────
-- Table: abonnements
-- ─────────────────────────────────
DROP TABLE IF EXISTS `abonnements`;
CREATE TABLE `abonnements` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `client_id` bigint(20) DEFAULT NULL,
  `type_plan` varchar(20) DEFAULT NULL,
  `prix` decimal(10,3) DEFAULT 0.000,
  `duree_mois` int(11) DEFAULT 1,
  `statut` varchar(20) DEFAULT 'EN_ATTENTE',
  `methode_paiement` varchar(30) DEFAULT NULL,
  `reference_paiement` varchar(100) DEFAULT NULL,
  `date_debut` datetime DEFAULT NULL,
  `date_fin` datetime DEFAULT NULL,
  `date_soumission` datetime DEFAULT current_timestamp(),
  `notes_admin` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ─────────────────────────────────
-- Table: audit_logs
-- ─────────────────────────────────
DROP TABLE IF EXISTS `audit_logs`;
CREATE TABLE `audit_logs` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `utilisateur_id` bigint(20) DEFAULT NULL,
  `nom_utilisateur` varchar(100) DEFAULT NULL,
  `action` varchar(60) NOT NULL,
  `resultat` varchar(20) DEFAULT NULL,
  `details` varchar(1000) DEFAULT NULL,
  `adresse_ip` varchar(60) DEFAULT NULL,
  `user_agent` varchar(500) DEFAULT NULL,
  `module` varchar(60) DEFAULT NULL,
  `ressource_id` bigint(20) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ─────────────────────────────────
-- Table: calculs_moteur
-- ─────────────────────────────────
DROP TABLE IF EXISTS `calculs_moteur`;
CREATE TABLE `calculs_moteur` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `reference` varchar(30) NOT NULL,
  `type_calcul` varchar(20) NOT NULL,
  `montant` decimal(15,3) NOT NULL,
  `date_debut` date NOT NULL,
  `date_fin` date NOT NULL,
  `nombre_jours` bigint(20) NOT NULL,
  `taux_unique` decimal(5,2) DEFAULT NULL,
  `resultat_total` decimal(15,2) NOT NULL,
  `module_erp` varchar(50) DEFAULT 'GENERAL',
  `libelle` varchar(300) DEFAULT NULL,
  `cree_par_id` bigint(20) DEFAULT NULL,
  `date_creation` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `reference` (`reference`),
  KEY `fk_cm_user` (`cree_par_id`),
  CONSTRAINT `fk_cm_user` FOREIGN KEY (`cree_par_id`) REFERENCES `utilisateurs` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `calculs_moteur` VALUES
(1, 'CM-20260731-0001', 'TAUX_UNIQUE', 50000.000, '2026-07-16', '2026-08-01', 17, 9.00, 209.59, 'GENERAL', '', 1, '2026-07-31 19:56:19'),
(2, 'CM-20260731-0002', 'TAUX_VARIABLE', 70000.000, '2026-07-21', '2026-08-20', 31, NULL, 371.58, 'GENERAL', '', 1, '2026-07-31 19:58:05'),
(3, 'CM-20260809-0001', 'TAUX_UNIQUE', 5000.000, '2024-01-01', '2026-12-31', 1096, 9.00, 1351.23, 'GENERAL', '', 1, '2026-08-09 22:44:10'),
(4, 'CM-20260810-0001', 'TAUX_UNIQUE', 50000.000, '2024-01-01', '2026-12-31', 1096, 9.00, 13512.33, 'GENERAL', '', 1, '2026-08-09 23:32:33');

-- ─────────────────────────────────
-- Table: clients
-- ─────────────────────────────────
DROP TABLE IF EXISTS `clients`;
CREATE TABLE `clients` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `nom` varchar(200) NOT NULL,
  `email` varchar(100) NOT NULL,
  `telephone` varchar(20) DEFAULT NULL,
  `adresse` varchar(500) DEFAULT NULL,
  `matricule_fiscale` varchar(50) DEFAULT NULL,
  `code_client` varchar(50) DEFAULT NULL,
  `type_client` varchar(20) DEFAULT 'ENTREPRISE',
  `plafond_credit` decimal(15,3) DEFAULT 0.000,
  `date_creation` timestamp NOT NULL DEFAULT current_timestamp(),
  `date_modification` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=69 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `clients` VALUES
(1, 'Sotrapil S.A.', 'contact@sotrapil.com.tn', '+216 71 900 100', 'Zone Industrielle, Rades, Ben Arous', '1234567MAM000', 'CLI-001', 'ENTREPRISE', 50000.000, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(2, 'Societe El-Bouniane', 'info@elbouniane.com.tn', '+216 71 800 200', 'Charguia II, Ariana, Tunisie', '9876543KXM000', 'CLI-002', 'ENTREPRISE', 30000.000, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(3, 'Tunisie Telecom', 'business@tunisietelecom.tn', '+216 71 100 100', 'Rue Asdrubal, El Menzah, Tunis', '0011223TTL000', 'CLI-003', 'ENTREPRISE', 80000.000, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(4, 'Alpha Invest SARL', 'contact@alphainvest.tn', '+216 73 250 300', 'Avenue Taïeb Mhiri, Sfax, Tunisie', '5566778AIS000', 'CLI-004', 'ENTREPRISE', 25000.000, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(5, 'Medina Group', 'dg@medinagroup.com.tn', '+21652026019', 'Centre Urbain Nord, Tunis, Tunisie', '3344556MGR000', 'CLI-005', 'ENTREPRISE', 40000.000, '2026-07-30 21:09:39', '2026-08-06 23:14:16'),
(6, 'Delta Corp Tunisie', 'info@deltacorp.tn', '+216 74 330 440', 'Route de Sfax Km 4, Gabes, Tunisie', '7788990DCT000', 'CLI-006', 'ENTREPRISE', 20000.000, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(7, 'Carthage SA', 'direction@carthage-sa.tn', '+216 71 760 880', 'Les Berges du Lac II, Tunis, Tunisie', '2233445CSA000', 'CLI-007', 'ENTREPRISE', 60000.000, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(8, 'Nord Finance Group', 'contact@nordfinance.tn', '+216 70 200 300', 'Rue du Lac Biwa, Berges du Lac, Tunis', '6677889NFG000', 'CLI-008', 'ENTREPRISE', 35000.000, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(9, 'Sahel Pro Services', 'admin@sahelpro.tn', '+216 73 450 600', 'Route de Sousse, Monastir, Tunisie', '4455667SPS000', 'CLI-009', 'ENTREPRISE', 15000.000, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(10, 'M. Karim Belhadj', 'karim.belhadj@gmail.com', '+216 98 123 456', '12 Rue Ibn Khaldoun, La Marsa, Tunis', NULL, 'CLI-010', 'PARTICULIER', 5000.000, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(11, 'Belhadj', 'ENC:cp6WWvRffF2IDWcBpQexx89DYlXNiSq+N8BMUdQmpuYQ4KxlW2e+83Mv2IOahRNL', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 13:19:35', '2026-08-05 13:19:35'),
(12, 'Belhadj', 'ENC:An1DuURWNpGO9/ZqZP/S5F3gwaXz813SB0tI1N3Q3oVA/1/iGZJb75fl9V5ZOXJN', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 13:19:36', '2026-08-05 13:19:36'),
(13, 'Belhadj', 'ENC:WUcFXl5BYxEU1c09d8y7riCIB5KW9xHiyhR0BAYJ1qxYzy9iGmKFuDSpKbx1I/t0', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 13:19:37', '2026-08-05 13:19:37'),
(14, 'Belhadj', 'ENC:fnb85m+C64hzll+lF9VpS6xX8G4PV04uq2pWOBZBnvSd22QWRkA80gVH9ys2K1Xo', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 13:19:37', '2026-08-05 13:19:37'),
(15, 'Belhadj', 'ENC:ymhuYHIikYyvF/Ol6CHmznQ6Hd8/Ksiy/piwnvrhN4ZbamIkNuxwoTuS03ImQ7iw', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 13:19:43', '2026-08-05 13:19:43'),
(16, 'Belhadj', 'ENC:ICvjezLivAS1fGp/TDOeKMnLhSagN8qX2gjYhZVBdMCKLIDLsFDBQi2IHICZSpDu', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 13:19:45', '2026-08-05 13:19:45'),
(17, 'Belhadj', 'ENC:iMeLYkCNadAis7SK51jkQiPu2p8HKvTwuEcevq41DhEgqgiaO8gEeRbFB44yFRY7', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 13:19:47', '2026-08-05 13:19:47'),
(18, 'Belhadj', 'ENC:n3FPrTsdHilz7PA/U+kDaPtRFiDOA6JZ87R4gEKAjPGTiVKbATgaT7NdUsgU0Ymo', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 13:19:55', '2026-08-05 13:19:55'),
(19, 'Belhadj', 'ENC:i3cmogBrehMXNHOQe6BOvJBidcObOTZ9xPx7rf6uV8SyhHh2jhXzQVOJoYFBKG8Y', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 13:19:56', '2026-08-05 13:19:56'),
(20, 'Belhadj', 'ENC:0d63esKXCUN7lQ9NN5sVc8ohUIngjrIAGpjWJGwFaMDvCHtdhrRXx/HG0k7sa0fW', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 13:20:31', '2026-08-05 13:20:31'),
(21, 'Belhadj', 'ENC:rcPM2HNwaygSy/c0Ip4gGncAL7Rk7Y0NcOHaNCRkMkb05ZRhvqVTTr7pEFB05KS5', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 13:20:31', '2026-08-05 13:20:31'),
(22, 'Belhadj', 'ENC:cx118s3MyIVSbNuRO/nt7t43xDvUVBYlt6T7cZXwuvCv4v6mNFVuJ6n3bBZX9Qlb', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 13:20:32', '2026-08-05 13:20:32'),
(23, 'Belhadj', 'ENC:Sf7HFH9tLnnf04viMv5C7tVO/U7q3wrBZEgKUNvHtVU5ReCSPObq/RnYA0tCmNmp', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 13:20:32', '2026-08-05 13:20:32'),
(24, 'Belhadj', 'ENC:ZFWkxJYR30xfuja0/ENUcK1d3JzS3+ypy5q8UETorUqd+MsVDHIUXQoU4J18M5QG', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 13:20:33', '2026-08-05 13:20:33'),
(25, 'Belhadj', 'ENC:UAAsYGDtFXFdqGTUawtCYspI1Y/6mljQUqibZtwJI6Aph/gWmJUsHmce2oNOSQ/C', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 13:20:43', '2026-08-05 13:20:43'),
(26, 'Belhadj', 'ENC:UysPRStwoMohcDhc6hhQB/jalh3B/KmtRWchuacaGgsOEulzB/H5S3cg+ol7aiTQ', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 13:20:43', '2026-08-05 13:20:43'),
(27, 'Belhadj', 'ENC:osahM3frBuhrlE3fRCXfvemZMlY6qEXJhBw1voUk34+J/DzK+E0wL4tnVXmz1cmy', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 13:20:44', '2026-08-05 13:20:44'),
(28, 'Belhadj', 'ENC:/MZrl4FrzbP9W+c5J4m3bl9BLOa7w4FpAmvhXztIimsMnY1hsxRz6thDN3MljcC8', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 13:20:44', '2026-08-05 13:20:44'),
(29, 'Belhadj', 'ENC:sAorEoOavd++GZFqhCWFSbk8bvyO9NjtJ1P7Y3jIDFCkdqCltZgUt9HvZUWQrt1F', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 13:20:45', '2026-08-05 13:20:45'),
(30, 'Belhadj', 'ENC:K4wsTUpPVT5BlHBQfDpacvkgdz6q8kSvpT03XcvmFy4fVjzxEYsDDzx/bKpAvVx6', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 13:20:54', '2026-08-05 13:20:54'),
(31, 'Belhadj', 'ENC:GldTbfnWdqPxcXVoOgsoBU5FuSzhgCKPKWuvoGFcEwqGkdajSjRPEIn9If6SWUIC', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 13:20:56', '2026-08-05 13:20:56'),
(32, 'Belhadj', 'ENC:7kuKTs5fXW/GkMH/y393mUQ1Ulz47nD1BdS9nyhM6gUezkoNVSfsK9OurabCC7bT', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 13:20:57', '2026-08-05 13:20:57'),
(33, 'Belhadj', 'ENC:RRv2Z4JHwCW0XCJdBIvecQxacIMXBpt/lMf2dbnJlgF4NsFc6VPHt/ytzOZy5NGk', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 13:20:58', '2026-08-05 13:20:58'),
(34, 'Belhadj', 'ENC:JP+cDYhWi++1knhqSYXghcwdQgZOlh/QrkJ0ZrCm5HTvCrwdSLPixasIhvsY3VrG', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 13:20:59', '2026-08-05 13:20:59'),
(35, 'Belhadj', 'ENC:BnSXQ2yJiKhTbaH68KAObIQziDgaJR8HEEGgBLHKcBa9i5FivwEcgZpPo4OoiZU7', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 13:21:00', '2026-08-05 13:21:00'),
(36, 'Belhadj', 'ENC:jtaK83BgJQRIX/AEBPP23co11EwYRHnPHlvxAySaXrmX5xQkA5VLrL92DEqzllYT', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 14:13:03', '2026-08-05 14:13:03'),
(37, 'Belhadj', 'ENC:lPoglQan5ZP2wKDfrxClbyqbbjDUwvqtaZBVA0gf9wWCT6lEa7ZDrDcYkxJNxXcG', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 14:13:17', '2026-08-05 14:13:17'),
(38, 'Belhadj', 'ENC:xSY3dsE7DdVVtPZvRuAFglZ+FvX0NwhaKixpyCQ+IvLq2yM8QRIulKHtLI9go8WR', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 14:13:17', '2026-08-05 14:13:17'),
(39, 'Belhadj', 'ENC:BzXODG+NDaZ1aw5zCg3jHZns8nMu2swhzZgdQ58zwBE7rqjzNLCrDK4yv+VpzATt', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 14:13:20', '2026-08-05 14:13:20'),
(40, 'Belhadj', 'ENC:eky/bV/ExQgVwsxUtmlvesouAE1vhMtBK25VI7SSrzaLjaO2Z+GB9UcWlrW8C0O/', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 15:18:31', '2026-08-05 15:18:31'),
(41, 'Belhadj', 'ENC:7gTUYhQzIB/eHqmct42iw+6Uhb+tJjUd3Liq/2e1RCACKbitcdS0InrPRA5Mlksz', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 15:18:32', '2026-08-05 15:18:32'),
(42, 'Belhadj', 'ENC:xLifQIKz+eoihVxemOj8TPy5J23O30CF/PRHfCnrhK9wS0AkirWGKoQrctx3FpLI', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 15:18:34', '2026-08-05 15:18:34'),
(43, 'Belhadj', 'ENC:YuiACGlkxzOobGZ1PKttg8DoYgyNBMrp7hbT/q9Hw2xMKqPqc9kRQYO317KaJ01Y', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 15:18:37', '2026-08-05 15:18:37'),
(44, 'Belhadj', 'ENC:jMK7eOgCQEoNrHFNKBJGKmtnsJJn8Qyp3LIVtq3o7shGSlgi9y0n0mOMzDChfv1a', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 15:25:14', '2026-08-05 15:25:14'),
(45, 'Belhadj', 'ENC:+GwPtoTdtx43Lp1bPgeeEg3JkURFuj2CEu6k+g3WHjhAte5fRk9JT57/kWDHIt7S', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 15:26:57', '2026-08-05 15:26:57'),
(46, 'Belhadj', 'ENC:8hCtlmXJyNBBoNKqVd6mYAFJ9o5KSEq+dMLxNW6xXqJV31vmMGQjxHKOtaSSnNT/', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 15:43:40', '2026-08-05 15:43:40'),
(47, 'Belhadj', 'ENC:TSWRy5fLmPAM67esr/pcz6ejnZhxxPux95FQYwMMOYlvVxHnB3SXPf8b0cZzKkVH', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 15:43:43', '2026-08-05 15:43:43'),
(48, 'Belhadj', 'ENC:UHax7hnGzDcr/AW1t0AEjrlKUFI91/9HqGMiy1TmY8hE3qt6Y3wrrO5rw04TGV0N', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 15:43:43', '2026-08-05 15:43:43'),
(49, 'Belhadj', 'ENC:fG6KBOv5QPsbPeHOrQnDtM8Cg6h9k8q897e+X5DvyPlesWM4nVoteFcK2j8V/dxT', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 15:43:44', '2026-08-05 15:43:44'),
(50, 'Belhadj', 'ENC:NlbDbIMeayZnzkHdEI/90gR76G0TWFUnXF5RG6kEPSUJQ8743HWu2SvqeIgCinkI', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 16:16:36', '2026-08-05 16:16:36'),
(51, 'Belhadj', 'ENC:eMw/9Fhh3oj3SSRscNP9+Jxwc1k97VZ3H8Lea0lkv4MMHcAJRr4Adyt+Z85ILlS0', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 16:16:38', '2026-08-05 16:16:38'),
(52, 'Belhadj', 'ENC:P5ClFu4gzpSkBoiFyNK7GXZeblFNPHh0xWIKQpSQkJBVF4w5r6b91CC3C42xgmQA', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 16:16:38', '2026-08-05 16:16:38'),
(53, 'Belhadj', 'ENC:hmnoiRU0f13O+z6vEUS8onxKWy6TigQ2iU8aVKnijNZJsSiTHeLdQdk8UOfCJaKz', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 16:16:40', '2026-08-05 16:16:40'),
(54, 'Belhadj', 'ENC:3H7e0aYvUBrsoDakbI5oXsX5uwUTDwf7pmozoRddxhLWcfdzq1K/7m0qsyrqWm5+', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 16:16:40', '2026-08-05 16:16:40'),
(55, 'Belhadj', 'ENC:Np88vRMbFVwnHOCc9W+N4rZkj6ZwFpamabDzgtIB8vpyPw/W29w35g1T1OSbwFBG', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 16:24:27', '2026-08-05 16:24:27'),
(56, 'Belhadj', 'ENC:bjloGAQNrd2hxl/TGsnVuDhYLcr1ERUaDr15nFQ9bhN9hFknRGLv6iLQ99YaukDq', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 16:24:27', '2026-08-05 16:24:27'),
(57, 'Belhadj', 'ENC:TTH65bKnqPatuqnSKJXT7Px5U31tpC7zZI42xqyLEPbjQVP5A6wi0YogyEdiG6rt', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 16:24:27', '2026-08-05 16:24:27'),
(58, 'Belhadj', 'ENC:hrj2ICy7MQ/1+8mCGK3hJGYQpe3uP5gM1nJf2zOtZqcQiDnCk9+7m+XqFQ18X5JP', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 16:24:28', '2026-08-05 16:24:28'),
(59, 'Belhadj', 'ENC:xwWV83WaCUDHfLI7XW05cVkHy8BIvSPVfqi1dpDMibivD2ciXDcIFreVb8KtFEcZ', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 16:24:29', '2026-08-05 16:24:29'),
(60, 'Belhadj', 'ENC:+4gWvGN91IFNfm/NDytb3XYbXlLaJ5VBemw4HoP8aX1hdfuIyjIgSGZtBzpxrXYO', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 16:25:37', '2026-08-05 16:25:37'),
(61, 'Belhadj', 'ENC:AV6y5+BXfbIqTe6MNnH4N8t8HBTCj2mztP4EoYONSzzToDAfn+gmWP7UJwYmVyii', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 16:25:38', '2026-08-05 16:25:38'),
(62, 'Belhadj', 'ENC:QiaViUEScOeb/nrcVJiE5zrK6VI/2rJpd69KsvXHXF80efaPuPaTf4HG6A7qFmuh', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 16:25:40', '2026-08-05 16:25:40'),
(63, 'Belhadj', 'ENC:AVJ/f8F6ul4k6gmiPxJC2nRl8lI2Zf3/SaXPF8dflpwDL1g9ML6Eq+THmEEwMSwj', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 16:25:41', '2026-08-05 16:25:41'),
(64, 'Belhadj', 'ENC:4KHQIpphlHJ6+XlzjJW+wYARJ4oNDEZuFeKtALLojeVSJGGPa9v9yfIk5DvFbfa8', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 17:00:34', '2026-08-05 17:00:34'),
(65, 'Belhadj', 'ENC:4NBc4aVJ/qafRMB+baOwn5e/av1sAr4T9UBYRZ4xlB6t91VpLQ3vQmBDpt08f0bY', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 17:00:36', '2026-08-05 17:00:36'),
(66, 'Belhadj', 'ENC:BH53pGo0lGJ57qDJPMcV1ZNZ3M+/7dh7KNoHJJ9+JLPauzBeqjVI8kJerlaBaxkn', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 17:00:37', '2026-08-05 17:00:37'),
(67, 'Belhadj', 'ENC:qSZIufdlfEkrsA2EMunZrR+BDekTUp+zKUL52ayBx8UcYTnkHdEIakdtV2bTRcS5', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 17:00:38', '2026-08-05 17:00:38'),
(68, 'Belhadj', 'ENC:SWh0p2+c/yc18KtvJCoxxHHNH50pcTz2FLqcLdtvrfFFvEeaG2aFi46t+223zvZy', NULL, NULL, NULL, NULL, 'ENTREPRISE', 0.000, '2026-08-05 17:00:39', '2026-08-05 17:00:39');

-- ─────────────────────────────────
-- Table: codes_promo
-- ─────────────────────────────────
DROP TABLE IF EXISTS `codes_promo`;
CREATE TABLE `codes_promo` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `code` varchar(50) NOT NULL,
  `description` varchar(300) DEFAULT NULL,
  `type_remise` varchar(20) DEFAULT 'POURCENTAGE',
  `valeur_remise` decimal(10,3) NOT NULL,
  `date_debut` date DEFAULT NULL,
  `date_fin` date DEFAULT NULL,
  `nb_utilisations_max` int(11) DEFAULT 1,
  `nb_utilisations` int(11) DEFAULT 0,
  `actif` tinyint(1) DEFAULT 1,
  `date_creation` timestamp NOT NULL DEFAULT current_timestamp(),
  `date_modification` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ─────────────────────────────────
-- Table: commandes
-- ─────────────────────────────────
DROP TABLE IF EXISTS `commandes`;
CREATE TABLE `commandes` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `numero_commande` varchar(50) NOT NULL,
  `date_commande` timestamp NOT NULL DEFAULT current_timestamp(),
  `statut` varchar(20) NOT NULL DEFAULT 'EN_ATTENTE',
  `montant_ht` decimal(15,3) NOT NULL DEFAULT 0.000,
  `montant_tva` decimal(15,3) NOT NULL DEFAULT 0.000,
  `montant_total` decimal(15,3) NOT NULL DEFAULT 0.000,
  `remise_globale` decimal(5,2) DEFAULT 0.00,
  `notes` text DEFAULT NULL,
  `client_id` bigint(20) DEFAULT NULL,
  `devis_id` bigint(20) DEFAULT NULL,
  `date_creation` timestamp NOT NULL DEFAULT current_timestamp(),
  `date_modification` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `code_promo_applique` varchar(50) DEFAULT NULL,
  `remise_promo` decimal(15,3) DEFAULT 0.000,
  PRIMARY KEY (`id`),
  UNIQUE KEY `numero_commande` (`numero_commande`),
  KEY `fk_cmd_client` (`client_id`),
  KEY `fk_cmd_devis` (`devis_id`),
  CONSTRAINT `fk_cmd_client` FOREIGN KEY (`client_id`) REFERENCES `clients` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_cmd_devis` FOREIGN KEY (`devis_id`) REFERENCES `devis` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `commandes` VALUES
(1, 'CMD-2026-001', '2026-01-15 08:00:00', 'LIVRE', 15462.185, 2937.815, 18400.000, 0.00, 'Livraison urgente Sotrapil', 1, 1, '2026-07-30 21:09:39', '2026-07-30 21:09:39', NULL, 0.000),
(2, 'CMD-2026-002', '2026-02-02 09:30:00', 'EN_COURS', 8403.361, 1596.639, 10000.000, 5.00, 'Bureautique El-Bouniane', 2, 2, '2026-07-30 21:09:39', '2026-07-30 21:09:39', NULL, 0.000),
(3, 'CMD-2026-003', '2026-02-10 10:00:00', 'EN_ATTENTE', 5042.017, 957.983, 6000.000, 0.00, 'Commande electrique Alpha Invest', 4, NULL, '2026-07-30 21:09:39', '2026-07-30 21:09:39', NULL, 0.000),
(4, 'CMD-2026-004', '2026-02-25 14:00:00', 'LIVRE', 21008.403, 3991.597, 25000.000, 2.00, 'Serveurs Carthage SA', 7, 4, '2026-07-30 21:09:39', '2026-07-30 21:09:39', NULL, 0.000),
(5, 'CMD-2026-005', '2026-03-01 11:00:00', 'LIVRE', 2016.807, 383.193, 2400.000, 0.00, 'Cablage Medina Group', 5, NULL, '2026-07-30 21:09:39', '2026-07-30 21:09:39', NULL, 0.000),
(6, 'CMD-2026-006', '2026-03-10 09:00:00', 'EN_COURS', 4201.681, 798.319, 5000.000, 0.00, 'WiFi Tunisie Telecom', 3, NULL, '2026-07-30 21:09:39', '2026-07-30 21:09:39', NULL, 0.000),
(7, 'CMD-2026-007', '2026-04-01 08:30:00', 'EN_ATTENTE', 6722.689, 1277.311, 8000.000, 0.00, 'Infrastructure Sahel Pro', 9, NULL, '2026-07-30 21:09:39', '2026-07-30 21:09:39', NULL, 0.000),
(8, 'CMD-2026-008', '2026-04-10 10:00:00', 'ANNULE', 1680.672, 319.328, 2000.000, 0.00, 'Commande annulee Delta Corp', 6, NULL, '2026-07-30 21:09:39', '2026-07-30 21:09:39', NULL, 0.000),
(9, 'CMD-20260802-830CB0', '2026-08-02 22:18:27', 'EN_ATTENTE', 0.000, 0.000, 3200.000, 0.00, NULL, 5, NULL, '2026-08-02 22:18:27', '2026-08-02 22:18:27', NULL, 0.000),
(10, 'CMD-20260802-E3695D', '2026-08-02 22:18:56', 'EN_ATTENTE', 0.000, 0.000, 2200.000, 0.00, NULL, 5, NULL, '2026-08-02 22:18:56', '2026-08-02 22:18:56', NULL, 0.000),
(11, 'CMD-20260802-3D1755', '2026-08-02 22:19:38', 'PAYEE', 0.000, 0.000, 45.000, 0.00, NULL, 5, NULL, '2026-08-02 22:19:38', '2026-08-06 23:12:49', NULL, 0.000),
(12, 'CMD-20260805-4C3D4E', '2026-08-05 16:24:52', 'EN_ATTENTE', 0.000, 0.000, 3200.000, 0.00, NULL, 5, NULL, '2026-08-05 16:24:52', '2026-08-05 16:24:52', NULL, 0.000),
(13, 'CMD-20260805-7670B9', '2026-08-05 16:25:05', 'EN_ATTENTE', 0.000, 0.000, 350.000, 0.00, NULL, 5, NULL, '2026-08-05 16:25:05', '2026-08-05 16:25:05', NULL, 0.000),
(14, 'CMD-20260805-BFD5A6', '2026-08-05 16:34:15', 'EN_ATTENTE', 0.000, 0.000, 12500.000, 0.00, NULL, 5, NULL, '2026-08-05 16:34:15', '2026-08-05 16:34:15', NULL, 0.000),
(15, 'CMD-20260805-B4847A', '2026-08-05 16:54:22', 'EN_ATTENTE', 0.000, 0.000, 890.000, 0.00, NULL, 5, NULL, '2026-08-05 16:54:22', '2026-08-05 16:54:22', NULL, 0.000),
(16, 'CMD-20260805-671B6C', '2026-08-05 17:40:10', 'EN_ATTENTE', 0.000, 0.000, 950.000, 0.00, NULL, 5, NULL, '2026-08-05 17:40:10', '2026-08-05 17:40:10', NULL, 0.000),
(17, 'CMD-20260806-1DFD0A', '2026-08-06 21:50:32', 'EN_ATTENTE', 0.000, 0.000, 8500.000, 0.00, NULL, 5, NULL, '2026-08-06 21:50:32', '2026-08-06 21:50:32', NULL, 0.000);

-- ─────────────────────────────────
-- Table: commandes_achat
-- ─────────────────────────────────
DROP TABLE IF EXISTS `commandes_achat`;
CREATE TABLE `commandes_achat` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `numero_commande` varchar(50) NOT NULL,
  `date_commande` timestamp NOT NULL DEFAULT current_timestamp(),
  `statut` varchar(20) NOT NULL DEFAULT 'EN_ATTENTE',
  `montant_ht` decimal(15,3) NOT NULL DEFAULT 0.000,
  `montant_tva` decimal(15,3) NOT NULL DEFAULT 0.000,
  `montant_total` decimal(15,3) NOT NULL DEFAULT 0.000,
  `notes` text DEFAULT NULL,
  `fournisseur_id` bigint(20) DEFAULT NULL,
  `date_livraison_prev` date DEFAULT NULL,
  `date_creation` timestamp NOT NULL DEFAULT current_timestamp(),
  `date_modification` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `numero_commande` (`numero_commande`),
  KEY `fk_ca_fournisseur` (`fournisseur_id`),
  CONSTRAINT `fk_ca_fournisseur` FOREIGN KEY (`fournisseur_id`) REFERENCES `fournisseurs` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `commandes_achat` VALUES
(1, 'ACH-2026-001', '2026-01-05 08:00:00', 'RECEPTIONNE', 12605.042, 2394.958, 15000.000, 'Reappro reseau Cisco', 1, '2026-01-15', '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(2, 'ACH-2026-002', '2026-01-08 09:00:00', 'RECEPTIONNE', 5042.017, 957.983, 6000.000, 'Points acces WiFi Netgear', 2, '2026-01-20', '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(3, 'ACH-2026-003', '2026-02-01 10:00:00', 'EN_COURS', 7563.025, 1436.975, 9000.000, 'Serveurs et NAS Dell', 4, '2026-02-20', '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(4, 'ACH-2026-004', '2026-02-15 11:00:00', 'RECEPTIONNE', 2521.008, 478.992, 3000.000, 'Postes de travail HP', 5, '2026-03-01', '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(5, 'ACH-2026-005', '2026-03-01 09:00:00', 'EN_ATTENTE', 3361.345, 638.655, 4000.000, 'UPS et protection APC', 6, '2026-03-20', '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(6, 'ACH-2026-006', '2026-03-10 10:00:00', 'EN_COURS', 1848.739, 351.261, 2200.000, 'Materiaux electriques Legrand', 3, '2026-03-25', '2026-07-30 21:09:39', '2026-07-30 21:09:39');

-- ─────────────────────────────────
-- Table: connexion_logs
-- ─────────────────────────────────
DROP TABLE IF EXISTS `connexion_logs`;
CREATE TABLE `connexion_logs` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `utilisateur_id` bigint(20) DEFAULT NULL,
  `email_tente` varchar(100) DEFAULT NULL,
  `succes` tinyint(1) NOT NULL DEFAULT 0,
  `adresse_ip` varchar(45) DEFAULT NULL,
  `user_agent` varchar(500) DEFAULT NULL,
  `date_connexion` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `fk_cl_utilisateur` (`utilisateur_id`),
  CONSTRAINT `fk_cl_utilisateur` FOREIGN KEY (`utilisateur_id`) REFERENCES `utilisateurs` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ─────────────────────────────────
-- Table: connexions_log
-- ─────────────────────────────────
DROP TABLE IF EXISTS `connexions_log`;
CREATE TABLE `connexions_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `utilisateur_id` bigint(20) NOT NULL,
  `statut` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `session_token` varchar(600) DEFAULT NULL,
  `signalement_token` varchar(100) DEFAULT NULL,
  `est_signale` tinyint(1) DEFAULT 0,
  `date_signalement` datetime DEFAULT NULL,
  `adresse_ip` varchar(50) DEFAULT NULL,
  `user_agent` varchar(500) DEFAULT NULL,
  `type_appareil` varchar(50) DEFAULT NULL,
  `os` varchar(100) DEFAULT NULL,
  `navigateur` varchar(100) DEFAULT NULL,
  `resolution` varchar(20) DEFAULT NULL,
  `langue` varchar(20) DEFAULT NULL,
  `fuseau_horaire` varchar(60) DEFAULT NULL,
  `device_fingerprint` varchar(100) DEFAULT NULL,
  `appareil_connu` tinyint(1) DEFAULT 0,
  `type_reseau` varchar(30) DEFAULT NULL,
  `niveau_risque` int(11) DEFAULT 0,
  `connexion_inhabituelle` tinyint(1) DEFAULT 0,
  `pays` varchar(60) DEFAULT NULL,
  `region` varchar(60) DEFAULT NULL,
  `ville` varchar(60) DEFAULT NULL,
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `fournisseur_internet` varchar(100) DEFAULT NULL,
  `succes` tinyint(1) NOT NULL DEFAULT 1,
  `date_connexion` datetime DEFAULT current_timestamp(),
  `date_deconnexion` datetime DEFAULT NULL,
  `motif_revocation` varchar(200) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `signalement_token` (`signalement_token`)
) ENGINE=InnoDB AUTO_INCREMENT=116 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `connexions_log` VALUES
(1, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTQ1MDY2MSwiZXhwIjoxNzg1NDUxNTYxfQ.CnuPTnp0vRI45OvNblhBq0ihI-83-h6JdJL_L58CDkU', 'b427b1ab-5e55-418e-9e2e-8e7273f5de07', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36 Edg/150.0.0.0', 'PC', 'Windows 10/11', 'Edge 150.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '2b8e32484b420545637115c749f065877275214d96ba7c6475e0ba36330c7355', false, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-07-30 22:31:02', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(2, 2, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjb21tZXJjaWFsIiwiaWF0IjoxNzg1NDUwNzg5LCJleHAiOjE3ODU0NTE2ODl9.FTuMnpAc8kTWBl-bGcqu1w3DFL18KN2sVwRqKTh7928', 'e3fe3890-5f26-448a-9285-8c081418ce60', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36 Edg/150.0.0.0', 'PC', 'Windows 10/11', 'Edge 150.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '2b8e32484b420545637115c749f065877275214d96ba7c6475e0ba36330c7355', false, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-07-30 22:33:09', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(3, 3, 'ACTIVE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjb21wdGFibGUiLCJpYXQiOjE3ODU0NTA4MTUsImV4cCI6MTc4NTQ1MTcxNX0.A_TLB_FkvZdrfiMUXsLoNt0WJdWhpMoa2pENolw6cmk', '839b9342-a816-4924-84a4-44e25395f534', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36 Edg/150.0.0.0', 'PC', 'Windows 10/11', 'Edge 150.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '2b8e32484b420545637115c749f065877275214d96ba7c6475e0ba36330c7355', false, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-07-30 22:33:35', NULL, NULL),
(4, 4, 'ACTIVE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzdG9jayIsImlhdCI6MTc4NTQ1MDk2NSwiZXhwIjoxNzg1NDUxODY1fQ.6RQNGdfzExDttlCQtG-8e_HQ0VHhOlIzdH0hgc0gpYE', '972171e6-7106-4a09-950b-99f7c8fe8f67', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36 Edg/150.0.0.0', 'PC', 'Windows 10/11', 'Edge 150.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '2b8e32484b420545637115c749f065877275214d96ba7c6475e0ba36330c7355', false, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-07-30 22:36:06', NULL, NULL),
(5, 5, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjbGllbnRfZGVtbyIsImlhdCI6MTc4NTQ1MTA4NywiZXhwIjoxNzg1NDUxOTg3fQ.rG2Gr4ysiTMiyAXjOHENNS9xkdTGA67NzkaJnrMcPVw', '995c90ae-dc88-4e5a-b56b-29d9f7c877ec', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36 Edg/150.0.0.0', 'PC', 'Windows 10/11', 'Edge 150.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '2b8e32484b420545637115c749f065877275214d96ba7c6475e0ba36330c7355', false, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-07-30 22:38:07', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(6, 2, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjb21tZXJjaWFsIiwiaWF0IjoxNzg1NDUyNzA4LCJleHAiOjE3ODU0NTM2MDh9.fLX7-h8_-6RCFqCFUthoRdyPIl585iiceNdEGCN6Xkw', '7c37ace5-49a4-4c62-a1dc-f1d277cc8560', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36 Edg/150.0.0.0', 'PC', 'Windows 10/11', 'Edge 150.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '5150f8872c1326ce80bbfcd1b3d271116c0a1906f44e58c220f4a7b9c384104a', false, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-07-30 23:05:08', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(7, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTQ1Mjc2MiwiZXhwIjoxNzg1NDUzNjYyfQ.rr92Vm9wXIMPBgf0MwXF6Iuhb_B7QbSKmtQy2c1t7Qc', '1b91df1f-c0bf-4bfd-9589-7e9c8a2d79c0', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36 Edg/150.0.0.0', 'PC', 'Windows 10/11', 'Edge 150.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '5150f8872c1326ce80bbfcd1b3d271116c0a1906f44e58c220f4a7b9c384104a', false, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-07-30 23:06:03', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(8, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTUyNzQ5MCwiZXhwIjoxNzg1NTI4MzkwfQ.r20MIg2Wu62w22wCLsoHfAO14vncmxIdg6XDqF9RCkc', '7d0ec95f-5149-4a68-977b-a6f6798d443a', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36 Edg/150.0.0.0', 'PC', 'Windows 10/11', 'Edge 150.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '5150f8872c1326ce80bbfcd1b3d271116c0a1906f44e58c220f4a7b9c384104a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-07-31 19:51:31', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(9, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTUyOTgwNCwiZXhwIjoxNzg1NTMwNzA0fQ.LFnx4b5I0iBTYbW_hoJ-MbkLfuHQOnXAON1DLfqqwXM', '0b3b6b49-96a2-4902-84bf-a4f9d5b7a1db', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36 Edg/150.0.0.0', 'PC', 'Windows 10/11', 'Edge 150.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '5150f8872c1326ce80bbfcd1b3d271116c0a1906f44e58c220f4a7b9c384104a', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-07-31 20:30:04', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(10, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTUzMjk1MiwiZXhwIjoxNzg1NTMzODUyfQ.u7jRva24Uz2p9_GWq347KBTSn6H6B0uxETFd0cTSrFI', 'a74e2fa3-534e-4a3a-9694-d8487db87bdc', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36 Edg/150.0.0.0', 'PC', 'Windows 10/11', 'Edge 150.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '5150f8872c1326ce80bbfcd1b3d271116c0a1906f44e58c220f4a7b9c384104a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-07-31 21:22:32', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(11, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTUzNTY1NywiZXhwIjoxNzg1NTM2NTU3fQ.YlpQoicRgG1LspFjSA2RIsmo1qI9Iu6_x_hfcN_8zOw', '789226d1-1cc9-4c8b-b8a2-9a214b962a05', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36 Edg/150.0.0.0', 'PC', 'Windows 10/11', 'Edge 150.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '5150f8872c1326ce80bbfcd1b3d271116c0a1906f44e58c220f4a7b9c384104a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-07-31 22:07:37', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(12, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTUzNjAxMSwiZXhwIjoxNzg1NTM2OTExfQ.ox-FidCx1_I_VWzRYjL2BrWL9vrekaqzaHIOfLD9RrI', '1fe36142-5553-402b-8ac4-73b67bd8a418', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36 Edg/150.0.0.0', 'PC', 'Windows 10/11', 'Edge 150.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '5150f8872c1326ce80bbfcd1b3d271116c0a1906f44e58c220f4a7b9c384104a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-07-31 22:13:31', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(13, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTUzNjA2MCwiZXhwIjoxNzg1NTM2OTYwfQ.isDweKRciI7TWp5x0V7qb1RlazWdGkJRIma-lfDpW0c', '31b959b3-1bfd-4209-b31f-ac62023a0319', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36 Edg/150.0.0.0', 'PC', 'Windows 10/11', 'Edge 150.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '5150f8872c1326ce80bbfcd1b3d271116c0a1906f44e58c220f4a7b9c384104a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-07-31 22:14:20', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(14, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTYyMjk0NCwiZXhwIjoxNzg1NjIzODQ0fQ.DKxsGDLmvxGRDYqqZb9UcMQEwIamQgyt3iq7YYlmtPU', '7b50654f-edd7-4dca-9f6e-52983d15bc97', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36 Edg/150.0.0.0', 'PC', 'Windows 10/11', 'Edge 150.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '2821a70d116df4cd0681c3c28fef40de54ddaa6d3363340b830e974abeacf01e', false, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-01 22:22:24', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(15, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTYyNjMwMSwiZXhwIjoxNzg1NjI3MjAxfQ.M-1sdadzW1ksK4ira6w_aqYeZC8JJmzzX-1shSkHG5s', '03305c46-8631-41c7-b324-28fb4b90d0a4', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36 Edg/150.0.0.0', 'PC', 'Windows 10/11', 'Edge 150.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '2821a70d116df4cd0681c3c28fef40de54ddaa6d3363340b830e974abeacf01e', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-01 23:18:22', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(16, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTYzMDM4NywiZXhwIjoxNzg1NjMxMjg3fQ.BAC-lAazj61D38xrg0YbnGILFKMvsIG9AGVT6UmwhFQ', '7a124b2e-a0c2-4607-9060-6954acf47332', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36 Edg/150.0.0.0', 'PC', 'Windows 10/11', 'Edge 150.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '2821a70d116df4cd0681c3c28fef40de54ddaa6d3363340b830e974abeacf01e', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-02 00:26:27', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(17, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTYzMTQyMCwiZXhwIjoxNzg1NjMyMzIwfQ.ZLmpjmHf12tGVxMfxJI2we0MMVy2a9U71vu5LplSHyc', '572fc0ee-b3ba-4db4-a1c4-493230e02cf8', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36 Edg/150.0.0.0', 'PC', 'Windows 10/11', 'Edge 150.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '2821a70d116df4cd0681c3c28fef40de54ddaa6d3363340b830e974abeacf01e', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-02 00:43:40', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(18, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTYzMTUzOSwiZXhwIjoxNzg1NjMyNDM5fQ.E8k0vdOIOtbr8M429iCzCl9fGg889yULOlQ5xm2TW0Q', '55e7e9e6-5e29-4c2b-8bfd-f9f7dbc2ffec', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36 Edg/150.0.0.0', 'PC', 'Windows 10/11', 'Edge 150.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '2821a70d116df4cd0681c3c28fef40de54ddaa6d3363340b830e974abeacf01e', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-02 00:45:40', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(19, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTcwODY0MCwiZXhwIjoxNzg1NzA5NTQwfQ.aayJh-8CmveEgTJeaA9_S6bougUZxbgjApnq7TPgFqI', '37ca9a4a-48cb-42fb-b10c-8a9bc7d56deb', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', false, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-02 22:10:40', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(20, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTcwOTUzNywiZXhwIjoxNzg1NzEwNDM3fQ.F7xOjD1LR8b8ucbD3kVo4D30ExwAgu8xU5nTSHuVyqA', 'bf2827ef-dade-4aa0-aa20-9aa91dd6c9e0', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-02 22:25:37', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(21, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTcxMDQ3OSwiZXhwIjoxNzg1NzExMzc5fQ.Y70EGiNAyewaok7DrqMp9RjsOuGSDl_ayupvm-7gpVk', 'f5287ff2-d87d-4274-bdd3-a2af6331a5fa', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-02 22:41:19', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(22, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTcxMDkxOCwiZXhwIjoxNzg1NzExODE4fQ.RwJg7l272DnlqwuvrJ_rPbesoR3DltME4GPZw5sVTdc', '563e4b88-d9f9-4bc1-aae7-d09b60f6d93b', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-02 22:48:38', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(23, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTcxMTk3OSwiZXhwIjoxNzg1NzEyODc5fQ.fG3lzJ-tWcWQ_R2NqbYfl-Rn7EKnSB3ynNNoytpBOoI', '4f7b05b0-6b27-4d79-af37-346bbe17ae6a', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-02 23:06:19', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(24, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTcxMzA3MSwiZXhwIjoxNzg1NzEzOTcxfQ.12L1s0Uefya3i45K6bXCFGY3qvby-P_RtbTvucQYy6U', '9a910292-c2fc-4b54-bdd3-398ef2909264', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-02 23:24:32', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(25, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTc1OTY2MywiZXhwIjoxNzg1NzYwNTYzfQ.TAVZ72U1j_FPG4Q9ye43f8QoEUwxzd93njUMQvRUTl8', '81da88ec-dde6-4361-b9a6-914120262c99', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36', 'PC', 'Windows 10/11', 'Chrome 150.0.0.0', '1536x864', 'fr-FR', 'Africa/Tunis', 'f25f8060efcb67bf4ea1e54241109ee4360c183460a228bf4c2fd96fb2e96a6e', false, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-03 12:21:05', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(26, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTc2NjI0MywiZXhwIjoxNzg1NzY3MTQzfQ.CT9AQaksOhn-buMgYOzClf0M6fV_VR0Y6ZNw8isfscM', '20ff373b-b6b6-4bd8-93da-9d90eeefea5d', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-03 14:10:44', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(27, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTc3NDU5MCwiZXhwIjoxNzg1Nzc1NDkwfQ.rOMiqrhMVKuKkrAVTQd1vrqUA1vlZLqapjDbXYNy8rQ', '1321611b-4ed2-451a-929d-5f7d6a048e90', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-03 16:29:50', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(28, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTc3NDYwNywiZXhwIjoxNzg1Nzc1NTA3fQ.zxv5m-Sy_5NRWEMV1cOXM1llBJbbJc61zTyZHGkOKuc', 'c1feb6b0-0744-4696-b593-741a27daa18a', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-03 16:30:07', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(29, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTc3NzQ2NSwiZXhwIjoxNzg1Nzc4MzY1fQ.zY0kM_jfCc1yW2ATSSsMJi1nm-lNq974mD7FP_izs04', '7b4eea61-c7a2-4984-a05f-583ba7d955b6', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-03 17:17:45', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(30, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTc3ODc2MywiZXhwIjoxNzg1Nzc5NjYzfQ.oI8W--9FJmTX_19TKc976jvKqBd60SND-kOGNwtf8Tg', '4e6caa77-f718-42c4-b89b-8d0da9263676', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-03 17:39:23', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(31, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTc5MDg3OCwiZXhwIjoxNzg1NzkxNzc4fQ.OfcwWoc_Gt5l4azEU4MxVTFRcY0xUBjBtgUFOO_NUgQ', 'c50e23ab-ec93-4263-b2fb-2b23a8c818aa', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-03 21:01:18', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(32, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTc5MDg5MCwiZXhwIjoxNzg1NzkxNzkwfQ.F7iXHsKxRIwVlDMGD_YeSrks2O_SBwBQkROl3cYZNXg', '5b63d704-64b9-45e8-987a-13f11fa10f0c', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-03 21:01:30', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(33, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTc5MTIxNiwiZXhwIjoxNzg1NzkyMTE2fQ.Gh6flBsU5WZZBywuIaFID6oo8lJFRmN_deBnO8gbrwI', '989c468d-df31-440e-9b81-390deb512a11', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-03 21:06:56', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(34, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTc5MTU1NSwiZXhwIjoxNzg1NzkyNDU1fQ.Ch6zz1rmoD8f64OSbMmZa-Fz4qojEjAOSjFIz6oxUl8', '9a9335da-e574-40cc-857b-a4362deef848', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-03 21:12:35', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(35, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTg4MTA0NSwiZXhwIjoxNzg1ODgxOTQ1fQ.sUW93jsGgpIVwfDJdWJXPRI9wFri6D_DTI6ReW8xREE', '2b51cc0a-c85a-4a22-a771-4fa656f46114', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-04 22:04:05', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(36, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTg4MjQ2MiwiZXhwIjoxNzg1ODgzMzYyfQ.kSMeObNXCtBt5xvkao62vnvA4-UJr9-2IJLeuxaoqcE', '704b8aab-90c3-4d41-8602-933c1a1132d8', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-04 22:27:42', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(37, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTg4MzAzOSwiZXhwIjoxNzg1ODgzOTM5fQ.uH9GpmMv0hxj5D5RdOZdoLJ2fQMMDLZJQ-hENE4G4YU', 'c07d1417-583a-4df3-88bc-fe6eac7ded35', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-04 22:37:19', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(38, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTkzNDM4MCwiZXhwIjoxNzg1OTM1MjgwfQ.qr7PHD04VOGkeAFZPMYa9ozDMOUVWh0vxm5G0Q2e-Tg', '35879ffa-51d1-4f56-837b-3bfc3942d791', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 12:53:00', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(39, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTkzNDcwNywiZXhwIjoxNzg1OTM1NjA3fQ.IBoeaMX9JVaFX_nPnBhUcnp_Z9VvldWjIyQ38Mcj4do', 'd890c4dd-7f5f-4328-83af-9585ca80de15', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 12:58:27', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(40, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTkzNTE5NiwiZXhwIjoxNzg1OTM2MDk2fQ.O6ius9xi7Z-HbP1ZheMbjqV2jWOJzEmnMQDPuDPZha8', '997c3dae-5028-4e8a-834b-d38045c198db', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 13:06:36', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(41, 5, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjbGllbnRfZGVtbyIsImlhdCI6MTc4NTkzNTk0MywiZXhwIjoxNzg1OTM2ODQzfQ.M8dKUnxmeT1oipb00RCb-z3tYyw0-TYdGxm342oPmZA', '34380c47-41e7-42e2-a70b-15fbd094fdc2', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', false, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 13:19:03', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(42, 5, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjbGllbnRfZGVtbyIsImlhdCI6MTc4NTkzNzQzOSwiZXhwIjoxNzg1OTM4MzM5fQ.8T1i9PwAx_mc_HaYm3Yj4KoOrl9wj-5HhKTb4J9DSvM', 'fb63a415-d42c-498f-8b27-c449a337ff8c', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 13:43:59', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(43, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTkzNzU1MCwiZXhwIjoxNzg1OTM4NDUwfQ.kClqdthSgoySChTirWkbncT6jnNpQNbUz9uJJoEaiBY', '37df9879-329a-4ae2-8610-43df71412879', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 13:45:50', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(44, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTkzOTA2MSwiZXhwIjoxNzg1OTM5OTYxfQ.py5iHMyjNW0txxUOGhb6YBat4DQLePeIL4yUq48lfCs', '17df5d99-07b0-407d-8a9e-b499d56d3000', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 14:11:01', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(45, 5, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjbGllbnRfZGVtbyIsImlhdCI6MTc4NTkzOTE3OSwiZXhwIjoxNzg1OTQwMDc5fQ.qOt8aOUzBR4MtlNEwmLW65JUxuTe3iN5PnxHdWeVAGg', 'c36e4224-3c96-4b4c-b1c8-9e364cabd128', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 14:12:59', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(46, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTkzOTYxOSwiZXhwIjoxNzg1OTQwNTE5fQ.yZnEp_7-R6dhcrKm2ACrajdegpawiiPST0gFwJyrANA', 'd9a0001d-e813-48af-99dc-13db815e6f8a', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 14:20:19', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(47, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTk0MzA2OSwiZXhwIjoxNzg1OTQzOTY5fQ.mgmjNLbIaxX9w4aKYt54ODLVtzapuQ8CnRGC9ub5Rzo', 'aef15c33-af71-4c4a-ab64-8a49908b90d1', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 15:17:49', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(48, 5, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjbGllbnRfZGVtbyIsImlhdCI6MTc4NTk0MzEwNSwiZXhwIjoxNzg1OTQ0MDA1fQ.ANLymIURmtsO9rrtChXr6-d_TUf38w8SODohcjTUYUA', 'f56a8db9-206c-4067-9b0c-8de6dba5333f', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 15:18:25', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(49, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTk0MzM4MiwiZXhwIjoxNzg1OTQ0MjgyfQ.KPvbdHBA424uOd3O_MOoIJItQnlxhgTX3FhXaMFsWF4', 'fcfeee73-6000-47ec-87d4-c78448fd3570', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 15:23:02', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(50, 5, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjbGllbnRfZGVtbyIsImlhdCI6MTc4NTk0MzUwOSwiZXhwIjoxNzg1OTQ0NDA5fQ.mQWXvsMa30iFzzqGG2rGIame4pnJ4WBuNFWTjqcaKAc', 'b53d899c-adc6-45db-98f0-b617781e7a37', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 15:25:09', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(51, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTk0MzUzMywiZXhwIjoxNzg1OTQ0NDMzfQ.cD6OmiCrP4pMIxiX-0-U1nv120qgj4fZG2yl7vdAQ-0', 'e4b4ee1b-7dba-43c8-805e-6d72306d112a', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 15:25:33', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(52, 5, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjbGllbnRfZGVtbyIsImlhdCI6MTc4NTk0MzYxNSwiZXhwIjoxNzg1OTQ0NTE1fQ.3hhd0LqxAajkJdSef7o6eMw9TgqP8c_qK1mA4ZHhOag', 'd16f5793-3340-4343-aa05-91719201c13f', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 15:26:55', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(53, 5, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjbGllbnRfZGVtbyIsImlhdCI6MTc4NTk0NDYxOCwiZXhwIjoxNzg1OTQ1NTE4fQ.UwzaL5nBRFkchLUqY7AVXzF8D_Q73l9SvY2icQkF7ig', '908140e2-84c5-482e-927b-f6a24643d5c0', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 15:43:38', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(54, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTk0NDgwMSwiZXhwIjoxNzg1OTQ1NzAxfQ.riSi0bvE6ig5K0o0cybNuB_UvVIslzx-SESgyVzYhcs', '38b61e4e-1c76-44fa-b2e9-c101a8c7acf6', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36', 'PC', 'Windows 10/11', 'Chrome 150.0.0.0', '1536x864', 'fr-FR', 'Africa/Tunis', 'f25f8060efcb67bf4ea1e54241109ee4360c183460a228bf4c2fd96fb2e96a6e', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 15:46:42', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(55, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTk0NTgzMywiZXhwIjoxNzg1OTQ2NzMzfQ.GMzVuFfR3kQUo4wzEInhDN-2mG9CMpG5tDbHCmq5iFM', '36ff9116-31d0-46c3-8ac3-a4943702c1ea', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36', 'PC', 'Windows 10/11', 'Chrome 150.0.0.0', '1536x864', 'fr-FR', 'Africa/Tunis', 'f25f8060efcb67bf4ea1e54241109ee4360c183460a228bf4c2fd96fb2e96a6e', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 16:03:53', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(56, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTk0NjU2MCwiZXhwIjoxNzg1OTQ3NDYwfQ.DHTtnb3mhoAC8BJUNkUIacbOYhQmCvIQPYPlFRRfnbM', '3fdb3164-e1dd-47f6-a39b-cf1336c034b1', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 16:16:00', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(57, 5, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjbGllbnRfZGVtbyIsImlhdCI6MTc4NTk0NjU5MywiZXhwIjoxNzg1OTQ3NDkzfQ.pdVWh6zp0NetoWN9TlVvyoMjclR-_dUBy7RD5xZrj6c', '13771015-f8f0-4a8d-a8cb-c6cb742e9675', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 16:16:33', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(58, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTk0NjczNCwiZXhwIjoxNzg1OTQ3NjM0fQ.WoVpAU2uXoToK-Hjejdt-uGSII7qHyTmMW3PGAszxoM', '6a42a9bc-61bb-46f1-83fa-b5d2dfeb3aca', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36', 'PC', 'Windows 10/11', 'Chrome 150.0.0.0', '1536x864', 'fr-FR', 'Africa/Tunis', 'f25f8060efcb67bf4ea1e54241109ee4360c183460a228bf4c2fd96fb2e96a6e', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 16:18:54', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(59, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTk0NzA3NywiZXhwIjoxNzg1OTQ3OTc3fQ.8ABvb14rXEGUB5bt5ZgFdUAoePT8PnTUKmhbRRuQ4ew', '881ec2a9-098e-42cd-a055-5a25db7cbafc', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 16:24:37', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(60, 5, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjbGllbnRfZGVtbyIsImlhdCI6MTc4NTk0NzEzNCwiZXhwIjoxNzg1OTQ4MDM0fQ.JukZodmPWw7OO5yKWveA1pdOyfkLtURj2Km7we5ps8s', '51291fbe-6e1b-4bcd-9c88-daaa538bd924', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 16:25:34', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(61, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTk0NzYwOSwiZXhwIjoxNzg1OTQ4NTA5fQ.lD_T40DQZ-G6dIx5XNfuC7atYdjqHb9qk7I0WD12Cn0', '9913e94e-fe76-4ad5-ab9a-4d417a799779', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 16:33:30', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(62, 5, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjbGllbnRfZGVtbyIsImlhdCI6MTc4NTk0NzcwMiwiZXhwIjoxNzg1OTQ4NjAyfQ.jlT-ZM1e6vv804PTmn2oBTnosMXypz8jOPJ60795W5I', '36939b26-334b-4e6d-a454-be35e248ae90', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 16:35:02', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(63, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTk0ODM1NCwiZXhwIjoxNzg1OTQ5MjU0fQ.S6kX_cJpspalCFRpDClzS3TIfP3rH9Umj60nq4JKT2s', '04089349-4891-402c-809d-9b177426be7a', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36', 'PC', 'Windows 10/11', 'Chrome 150.0.0.0', '1536x864', 'fr-FR', 'Africa/Tunis', 'f25f8060efcb67bf4ea1e54241109ee4360c183460a228bf4c2fd96fb2e96a6e', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 16:45:54', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(64, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTk0ODQ4NCwiZXhwIjoxNzg1OTQ5Mzg0fQ.K_P-aZ_4eKlQZR2CvQ68PZnlTbheg697XNZE3Zi6ppo', '000246ea-acdd-4845-a35f-d4cbb8b57da5', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36', 'PC', 'Windows 10/11', 'Chrome 150.0.0.0', '1536x864', 'fr-FR', 'Africa/Tunis', 'f25f8060efcb67bf4ea1e54241109ee4360c183460a228bf4c2fd96fb2e96a6e', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 16:48:04', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(65, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTk0ODY3OSwiZXhwIjoxNzg1OTQ5NTc5fQ.pG1L7h_SUqmSOczPfcL-5eogIjwErtG0LY6PEHWbxL8', '3b4ac5df-37fc-452d-a2b6-06c67544ec5d', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36', 'PC', 'Windows 10/11', 'Chrome 150.0.0.0', '1536x864', 'fr-FR', 'Africa/Tunis', 'f25f8060efcb67bf4ea1e54241109ee4360c183460a228bf4c2fd96fb2e96a6e', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 16:51:19', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(66, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTk0ODg0MiwiZXhwIjoxNzg1OTQ5NzQyfQ.eORQyHsbscj1gVbDtlfi6A8ZdFIejRRFplYu283Lojc', 'b46c6d10-f950-41c0-b2de-1608ac7dddde', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 16:54:02', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(67, 5, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjbGllbnRfZGVtbyIsImlhdCI6MTc4NTk0ODg5MywiZXhwIjoxNzg1OTQ5NzkzfQ.UTlPPOOnfTVyBjVFgu2vd-noA9Pb40UMRkDmqkJa8B4', '644db760-6e11-401b-bdab-97cf172c7207', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 16:54:53', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(68, 5, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjbGllbnRfZGVtbyIsImlhdCI6MTc4NTk1MDk1NCwiZXhwIjoxNzg1OTUxODU0fQ.fNlJYmknFd9K71ZFicixqPVTM9HlnwRY2YfGmzEf2LM', '632d0846-3208-462b-aaab-fd21cbb56f96', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 17:29:14', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(69, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NTk1MTU5MSwiZXhwIjoxNzg1OTUyNDkxfQ.lnH54iE0v-wOqcAc_2ADRbGSEd5RQa5qUig_qNW5EK0', 'bfd5b4ce-434d-4f5a-a175-88fbde232561', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 17:39:51', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(70, 5, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjbGllbnRfZGVtbyIsImlhdCI6MTc4NTk1MTY0NSwiZXhwIjoxNzg1OTUyNTQ1fQ.wHYceQ_5VO6rWNk3DvnvGKWZtWq-rGM1Q8Rk_Rr8rj0', '5cef07e2-a01e-4f8d-aadc-577fdf25479e', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 17:40:45', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(71, 5, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjbGllbnRfZGVtbyIsImlhdCI6MTc4NTk1MjcwMywiZXhwIjoxNzg1OTUzNjAzfQ.PggjAksTGX7FlLya4XJizVpVCoXkJxELphZRjmtWsCs', '0c61bf0a-e881-450f-825a-51b920583f87', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 17:58:23', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(72, 5, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjbGllbnRfZGVtbyIsImlhdCI6MTc4NTk1MzY4NiwiZXhwIjoxNzg1OTU0NTg2fQ.CguTePnzo6EmvouOvNHbfgU3zylzhgX5l2-liXJjxEs', '393cbc31-ddfd-4f83-8ff2-8b4ca0a70e1d', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 18:14:46', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(73, 5, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjbGllbnRfZGVtbyIsImlhdCI6MTc4NTk1NTYzMiwiZXhwIjoxNzg1OTU2NTMyfQ.cXWSrjWi2RInkY94fCODuhfOwGeUc1iN0UN928Xh2_I', 'e1be8a55-74e6-4abd-a11b-6cc0bc113e70', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 18:47:12', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(74, 5, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjbGllbnRfZGVtbyIsImlhdCI6MTc4NTk1NjA0MSwiZXhwIjoxNzg1OTU2OTQxfQ.uDWnhluRiVk37V4xtXabJLMwv7C9UTSopbAz1TIQzrA', 'ccfad944-b64d-403e-b490-5c780fe83932', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 18:54:01', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(75, 5, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjbGllbnRfZGVtbyIsImlhdCI6MTc4NTk1OTU0NywiZXhwIjoxNzg1OTYwNDQ3fQ.bnzkhr9owevXS0XHBHrqzU0R5PfkiJW0BVW1w5bkF2M', 'd71b91ba-cc8d-4109-aa1b-e68c91d322bb', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 19:52:27', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(76, 5, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjbGllbnRfZGVtbyIsImlhdCI6MTc4NTk1OTkzMiwiZXhwIjoxNzg1OTYwODMyfQ.dao3dRpe9zqlH5nINgXRd3X9DsZ7yG0w-fzmWZlCC-o', 'fb14bca5-467a-4b8f-9409-883de4f4f99c', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-05 19:58:52', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(77, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjAzNTIzNCwiZXhwIjoxNzg2MDM2MTM0fQ.HgKbB-r9bERGqjCY5Xj33XcwtdhWCsCSthvkgY-3qF4', '8f5593d3-d63d-4f7a-bca6-2d93e0b55650', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'b78b0dc50fc61d75d0795873cc095e78a45026293c7fc2df7f34852cf1b19dfd', false, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-06 16:53:55', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(78, 2, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjb21tZXJjaWFsIiwiaWF0IjoxNzg2MDM1NDcwLCJleHAiOjE3ODYwMzYzNzB9.eZIzseZkRAnSeB1wacPAz7r8NK4IrBMDuI0rsq8oBPU', 'faf9d114-93fe-498d-b0a7-8dcea92cfd59', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'b78b0dc50fc61d75d0795873cc095e78a45026293c7fc2df7f34852cf1b19dfd', false, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-06 16:57:50', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(79, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjAzNjM1NCwiZXhwIjoxNzg2MDM3MjU0fQ.5GTQn15reg1r4xU0LgbHt2t9WUjsBVz2i7x_2xoaMqQ', 'a7f2ed24-04e5-4fc2-9917-c51117904384', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'b78b0dc50fc61d75d0795873cc095e78a45026293c7fc2df7f34852cf1b19dfd', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-06 17:12:34', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(80, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjAzNjUwNSwiZXhwIjoxNzg2MDM3NDA1fQ.zA5Yzk2lXsSubyTPLmRBocDIxDLrH7fn6sTo09BKRs8', '6e8eba70-9b55-4926-a66a-f60f97fef53c', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'b78b0dc50fc61d75d0795873cc095e78a45026293c7fc2df7f34852cf1b19dfd', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-06 17:15:06', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(81, 5, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjbGllbnRfZGVtbyIsImlhdCI6MTc4NjA0ODE5MSwiZXhwIjoxNzg2MDQ5MDkxfQ.h8UCOFYvajPnz7wprl0AEse5IqD3BVcz5-mBJjZDjoI', '9f03a277-017f-423d-ab98-09fedaa23e77', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'b78b0dc50fc61d75d0795873cc095e78a45026293c7fc2df7f34852cf1b19dfd', false, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-06 20:29:51', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(82, 5, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjbGllbnRfZGVtbyIsImlhdCI6MTc4NjA0OTIxNCwiZXhwIjoxNzg2MDUwMTE0fQ.vrIn99XmlWiLJs0KJ26kFQMnwmqVy1jId8D5spdyAaM', 'fb81f513-6ee2-4f23-a52b-e8d797746eb3', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'b78b0dc50fc61d75d0795873cc095e78a45026293c7fc2df7f34852cf1b19dfd', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-06 20:46:54', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(83, 5, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjbGllbnRfZGVtbyIsImlhdCI6MTc4NjA1MDI4NywiZXhwIjoxNzg2MDUxMTg3fQ.Pa-rdk9eKs7i3Xyf7LEXwmwDj5hyo76_mPSDNOlbTN4', '66387ef1-3672-4149-825e-01d138feee89', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'b78b0dc50fc61d75d0795873cc095e78a45026293c7fc2df7f34852cf1b19dfd', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-06 21:04:47', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(84, 5, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjbGllbnRfZGVtbyIsImlhdCI6MTc4NjA1MTQ2MiwiZXhwIjoxNzg2MDUyMzYyfQ.8R4GB7i3tsZzms2HabXq8Jz0-XiGHzQd8jrthwQmw5w', '34546f52-4303-4064-9735-f3a65d07c953', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'b78b0dc50fc61d75d0795873cc095e78a45026293c7fc2df7f34852cf1b19dfd', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-06 21:24:22', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(85, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjA1MjI5MywiZXhwIjoxNzg2MDUzMTkzfQ.33VXSnhna_wVecL_OCwbFiLb5-FzO2o9FqzoDhOs_Ks', '9dd29333-b725-4078-91b4-b2eb8b0e16aa', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-06 21:38:13', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(86, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjA1MzI2NywiZXhwIjoxNzg2MDU0MTY3fQ.4iRtN96bL_Ju-rlAyeScS3UkrPL-GfBylpLbKU_TObM', '74f6e8a3-23bd-475f-b5bc-dec7cb0f69f3', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-06 21:54:27', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(87, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjA1NDQ2NywiZXhwIjoxNzg2MDU1MzY3fQ.pBBr2NIo00EnmGj2wLUTIw2StymA9Pcf9h4RGQVu_4E', 'f03675ea-84b0-46a4-8d27-1fbeada4fbd1', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-06 22:14:27', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(88, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjA1NTQ3MCwiZXhwIjoxNzg2MDU2MzcwfQ.Bjb5sx4YV-T_CQtMzulU94OmF0uu_Us4cOWYCkJCmJE', '551b2699-c958-4094-b6d4-71dba7d92726', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-06 22:31:10', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(89, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjA1NzQ4NywiZXhwIjoxNzg2MDU4Mzg3fQ.Pf5LtsNd8QsoskYJuJMr-FfafU_rNuOMZIOmtyllG_Q', '3941d21e-d3b8-4eef-8827-20f9f3bb9acd', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-06 23:04:47', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(90, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjA1NzgzOSwiZXhwIjoxNzg2MDU4NzM5fQ.R1ye8HdgZWHjehx9wfeQjv5j4qKgBUS2YdkxOQd--Ss', 'f29e7aab-1ec4-4f49-ac70-57ed13e1f207', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-06 23:10:39', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(91, 5, 'ACTIVE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjbGllbnRfZGVtbyIsImlhdCI6MTc4NjA1NzkzOCwiZXhwIjoxNzg2MDU4ODM4fQ.eX-kGO3LUP1FuFY6GptfJUsFkwoKwZ_iFJQDq3JVfoM', 'e50b43b2-95cb-426c-aa19-fc40e839df5e', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-06 23:12:18', NULL, NULL),
(92, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjEyNjE5MCwiZXhwIjoxNzg2MTI3MDkwfQ.ikx_-eRYcTI2bnz2PN-v57vIyIv9zRpSYQDgql2-6ug', 'dd184cab-16aa-4669-9fde-b094f0ef09f4', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-07 18:09:50', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(93, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjEzNDY5MiwiZXhwIjoxNzg2MTM1NTkyfQ.z6Iw59FT-CVREdDJO-BKvDzvpYjgzocegnpArF5HkCA', '7a0308ca-7cb0-40e9-a9ec-3737f85beb70', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', 'd708873b50b313d90cffe39de881d452e46f7db082a4e45762395b47ba61a10a', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-07 20:31:32', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(94, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjEzNjU0OCwiZXhwIjoxNzg2MTM3NDQ4fQ.BuhWSrEAETpfunqpKpRHPsINDZH0CuybOub59oKG-qw', '01fd6843-6230-44bf-9683-5cb9f5809f3e', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '53b75bbc6c891bfdb56e8972303b2786dcbcfe8ca59249920497f9f31c26c93e', false, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-07 21:02:28', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(95, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjIyNTI5NSwiZXhwIjoxNzg2MjI2MTk1fQ.wT-wk8Cj3Gqn9rxN3JrBTY8jwkkvo77ARem4xhXdpmE', 'c99d6235-f2e4-4ad4-90bc-3f57006eccfc', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '030939225990d9f5804f88eea2e6d74e46347c2f7fe4422203f79ded857139bd', false, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-08 21:41:35', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(96, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjIyOTA2OSwiZXhwIjoxNzg2MjI5OTY5fQ.fxgdiz0T5XWqOHqWqpy3uV-vTqxlPXkgOCfRCN-AyaQ', '5b2b008b-c967-403b-8f2a-1cae4cab4fbe', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '030939225990d9f5804f88eea2e6d74e46347c2f7fe4422203f79ded857139bd', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-08 22:44:29', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(97, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjI5MTk3OSwiZXhwIjoxNzg2MjkyODc5fQ.NYR6PZhbROSUfnw5j2DHKYmTv-tb3URk_yn9ZNxqLW0', 'b245aca9-8fa6-4407-8732-aad674001633', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '030939225990d9f5804f88eea2e6d74e46347c2f7fe4422203f79ded857139bd', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-09 16:12:59', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(98, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjI5MzU2MywiZXhwIjoxNzg2Mjk0NDYzfQ.tMP2l1-aoxoW9s2HUHXoD8GFZlZTvHEAKqO9qu6GDpE', '84f00135-2b13-463d-a001-50af7781cf8a', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '030939225990d9f5804f88eea2e6d74e46347c2f7fe4422203f79ded857139bd', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-09 16:39:23', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(99, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjI5NTY5MSwiZXhwIjoxNzg2Mjk2NTkxfQ.u85WZlXvBVUEkjwNSY2C5NlG-AtSdHauTGVASYKIYZk', 'da7f13e8-4f61-4608-a41c-ac1565622374', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '030939225990d9f5804f88eea2e6d74e46347c2f7fe4422203f79ded857139bd', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-09 17:14:51', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(100, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjI5Njg0NiwiZXhwIjoxNzg2Mjk3NzQ2fQ.g6XQzQUwZ-M3_eKdOrheqsqpjTK4nP9jI2a80qHVedU', 'a550f78a-3e95-49ca-8088-722ff035763c', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '030939225990d9f5804f88eea2e6d74e46347c2f7fe4422203f79ded857139bd', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-09 17:34:06', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(101, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjI5Nzk4NSwiZXhwIjoxNzg2Mjk4ODg1fQ.DIbDulIHIa6ynol94YCdwRiigs47bVJBJzk8tgRN81w', '785a9879-3e74-4d86-a8cd-dc8ae3dcf1bb', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '030939225990d9f5804f88eea2e6d74e46347c2f7fe4422203f79ded857139bd', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-09 17:53:05', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(102, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjMwODUzMiwiZXhwIjoxNzg2MzA5NDMyfQ.abM02XlsEh3EZjWwGgSOIWqlEfT1N0zXDwO2ZO6qwgs', '4b7f6d76-3c6a-43ae-9833-f5ffa0a6e43d', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '030939225990d9f5804f88eea2e6d74e46347c2f7fe4422203f79ded857139bd', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-09 20:48:52', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(103, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjMxMDY1NiwiZXhwIjoxNzg2MzExNTU2fQ.b7IcmLwjp4B774rbFOvuPnqG_ng1Sjx42f2SPwaXcwc', 'ade7f9e5-4826-47a9-9712-bc89421cf870', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '030939225990d9f5804f88eea2e6d74e46347c2f7fe4422203f79ded857139bd', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-09 21:24:16', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(104, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjMxMDY3NSwiZXhwIjoxNzg2MzExNTc1fQ.3Sfj8KN7P_c-UE-FMlpxt1BJWtH4qBbxKqCidsHPII0', 'b1e43933-227d-463d-bbaa-34e22ffef63e', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '030939225990d9f5804f88eea2e6d74e46347c2f7fe4422203f79ded857139bd', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-09 21:24:35', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(105, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjMxMDY4OCwiZXhwIjoxNzg2MzExNTg4fQ.ohU6YjAw5OhqzcgqMfVYqHfCAtkLDAnyhQe8g-ZMDhA', '7c143ac5-d98b-4726-83cb-6aa7f0ab981f', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '030939225990d9f5804f88eea2e6d74e46347c2f7fe4422203f79ded857139bd', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-09 21:24:48', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(106, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjMxMDc1MCwiZXhwIjoxNzg2MzExNjUwfQ.lDTERnxSArVYcGKY_Sc1Fpqsptz8_cJy5Fpe8RYwmCw', 'fca05146-16c1-4bab-b266-02bb3da30421', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '030939225990d9f5804f88eea2e6d74e46347c2f7fe4422203f79ded857139bd', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-09 21:25:50', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(107, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjMxMDc2NiwiZXhwIjoxNzg2MzExNjY2fQ.5_XeMvNkSTLxvglUgvHsUd6HyyFM75RupNFXI8Pf4uQ', '3d77c421-cc1a-48f7-9e18-8e1958a30b06', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '030939225990d9f5804f88eea2e6d74e46347c2f7fe4422203f79ded857139bd', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-09 21:26:06', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(108, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjMxMzE0OSwiZXhwIjoxNzg2MzE0MDQ5fQ.y-K27Y0lQN43c_Aceuwu8sXg7LKniWPw1nR_qaB_NIU', 'a46f7629-bb6c-44da-824a-4b885b0f158c', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '030939225990d9f5804f88eea2e6d74e46347c2f7fe4422203f79ded857139bd', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-09 22:05:49', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(109, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjMxMzM0NiwiZXhwIjoxNzg2MzE0MjQ2fQ.szqirzq_MCwaJ6a38dIRKuQELG_CuWMJZaXcralaNkE', 'c768e4ab-fd91-491d-a5cb-bd39c6044467', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '030939225990d9f5804f88eea2e6d74e46347c2f7fe4422203f79ded857139bd', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-09 22:09:06', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(110, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjMxMzk1NSwiZXhwIjoxNzg2MzE0ODU1fQ.tWWjSfqY8Kadc7UGNa2p_YIqNu7E5VcZamf64zqhs9U', '623e7a10-595c-4f3c-97a1-ae5159adebcf', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '030939225990d9f5804f88eea2e6d74e46347c2f7fe4422203f79ded857139bd', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-09 22:19:15', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(111, 2, 'ACTIVE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjb21tZXJjaWFsIiwiaWF0IjoxNzg2MzE0MDIwLCJleHAiOjE3ODYzMTQ5MjB9._5T5VhaDwbaQuj_D0lPa6WIRKS5IY2x-k3XmNIvdDAE', 'f0309a85-ccf5-4074-92fa-bd08762a41f3', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '030939225990d9f5804f88eea2e6d74e46347c2f7fe4422203f79ded857139bd', false, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-09 22:20:20', NULL, NULL),
(112, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjMxNTM0NSwiZXhwIjoxNzg2MzE2MjQ1fQ.0blPMnI-WH4nOP3DqRUoLa7E2KfAm1RtppR8GU0EQ8Y', 'a164832d-7f83-4c01-91af-ffba8f7ac248', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '030939225990d9f5804f88eea2e6d74e46347c2f7fe4422203f79ded857139bd', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-09 22:42:25', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(113, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjMxNjg4NCwiZXhwIjoxNzg2MzE3Nzg0fQ.keisheYrhV2izNvU6KdzbJgVZ2P6lggZknXsN7QEaOk', 'fa76a200-8404-4035-b0fb-818098fed8fd', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '030939225990d9f5804f88eea2e6d74e46347c2f7fe4422203f79ded857139bd', true, 'Inconnu', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-09 23:08:04', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(114, 1, 'REVOQUEE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjMxODEzMiwiZXhwIjoxNzg2MzE5MDMyfQ.xYdFKtqJ-U2oV8ymNlVQLGojVhZTKnbFvKwMcobBquI', 'b7a9b782-79b9-46a1-9531-2ae936b2ae63', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '030939225990d9f5804f88eea2e6d74e46347c2f7fe4422203f79ded857139bd', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-09 23:28:52', NULL, 'Nouvelle connexion détectée depuis 127.0.0.1'),
(115, 1, 'ACTIVE', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjMxODMyMCwiZXhwIjoxNzg2MzE5MjIwfQ.CovRzPHnhyWNdjRZ_s_5bmIwr0oxoy5R-JnvVU5pkVI', '4d75391c-b663-4b0d-9417-39e1d27a8b84', false, NULL, '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0', 'PC', 'Windows 10/11', 'Edge 151.0.0.0', '1536x864', 'fr', 'Africa/Tunis', '030939225990d9f5804f88eea2e6d74e46347c2f7fe4422203f79ded857139bd', true, 'Haut débit', 0, false, NULL, NULL, NULL, NULL, NULL, NULL, true, '2026-08-09 23:32:00', NULL, NULL);

-- ─────────────────────────────────
-- Table: devis
-- ─────────────────────────────────
DROP TABLE IF EXISTS `devis`;
CREATE TABLE `devis` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `numero_devis` varchar(50) NOT NULL,
  `date_devis` timestamp NOT NULL DEFAULT current_timestamp(),
  `date_validite` date DEFAULT NULL,
  `statut` varchar(20) NOT NULL DEFAULT 'BROUILLON',
  `montant_ht` decimal(15,3) NOT NULL DEFAULT 0.000,
  `montant_tva` decimal(15,3) NOT NULL DEFAULT 0.000,
  `montant_ttc` decimal(15,3) NOT NULL DEFAULT 0.000,
  `remise_globale` decimal(5,2) DEFAULT 0.00,
  `notes` text DEFAULT NULL,
  `client_id` bigint(20) DEFAULT NULL,
  `commercial_id` bigint(20) DEFAULT NULL,
  `date_creation` timestamp NOT NULL DEFAULT current_timestamp(),
  `date_modification` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `montant_total` decimal(15,3) NOT NULL DEFAULT 0.000,
  PRIMARY KEY (`id`),
  UNIQUE KEY `numero_devis` (`numero_devis`),
  KEY `fk_devis_client` (`client_id`),
  KEY `fk_devis_commercial` (`commercial_id`),
  CONSTRAINT `fk_devis_client` FOREIGN KEY (`client_id`) REFERENCES `clients` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_devis_commercial` FOREIGN KEY (`commercial_id`) REFERENCES `utilisateurs` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `devis` VALUES
(1, 'DEV-2026-001', '2026-01-10 09:00:00', '2026-02-10', 'ACCEPTE', 15462.185, 2937.815, 18400.000, 0.00, 'Infrastructure reseau complete - Sotrapil', 1, 2, '2026-07-30 21:09:39', '2026-08-02 23:33:44', 18400.000),
(2, 'DEV-2026-002', '2026-01-18 10:30:00', '2026-02-17', 'ACCEPTE', 8403.361, 1596.639, 10000.000, 5.00, 'Equipement bureautique - El-Bouniane', 2, 2, '2026-07-30 21:09:39', '2026-08-05 16:16:13', 10000.000),
(3, 'DEV-2026-003', '2026-02-05 14:00:00', '2026-03-05', 'BROUILLON', 5042.017, 957.983, 6000.000, 0.00, 'UPS et protection electrique - Alpha Invest', 4, 2, '2026-07-30 21:09:39', '2026-08-02 23:33:44', 6000.000),
(4, 'DEV-2026-004', '2026-02-20 09:30:00', '2026-03-20', 'ACCEPTE', 21008.403, 3991.597, 25000.000, 2.00, 'Mise a niveau serveurs - Carthage SA', 7, 2, '2026-07-30 21:09:39', '2026-08-02 23:33:44', 25000.000),
(5, 'DEV-2026-005', '2026-03-01 11:00:00', '2026-04-01', 'REFUSE', 3361.345, 638.655, 4000.000, 0.00, 'Cablage reseau - Delta Corp', 6, 2, '2026-07-30 21:09:39', '2026-08-02 23:33:44', 4000.000),
(6, 'DEV-2026-006', '2026-03-15 10:00:00', '2026-04-14', 'ACCEPTE', 12605.042, 2394.958, 15000.000, 0.00, 'Infrastructure datacenter - Nord Finance Group', 8, 2, '2026-07-30 21:09:39', '2026-08-05 16:16:13', 15000.000),
(7, 'DR-197228', '2026-08-05 14:13:17', NULL, 'ACCEPTE', 0.000, 0.000, 0.000, 0.00, '[DEMANDE_CLIENT]
Objet: dgd
Urgence: NORMAL
Remarques: 
Lignes: [{designation=dgdg, quantite=1, unite=pièce, description=}]', 37, NULL, '2026-08-05 14:13:17', '2026-08-05 16:16:12', 0.000),
(8, 'DEV-20260805-F98201', '2026-08-05 15:24:52', '2026-09-02', 'ACCEPTE', 0.000, 0.000, 0.000, 0.00, 'bonjourr', 5, NULL, '2026-08-05 15:24:52', '2026-08-05 16:16:11', 2850.000),
(9, 'DEV-20260805-8C2E3B', '2026-08-05 16:14:41', '2026-09-02', 'ACCEPTE', 0.000, 0.000, 0.000, 0.00, '', 5, NULL, '2026-08-05 16:14:41', '2026-08-05 16:16:10', 2850.000),
(10, 'DEV-20260805-73DC47', '2026-08-05 16:33:50', '2026-09-02', 'ACCEPTE', 0.000, 0.000, 0.000, 0.00, '', 5, NULL, '2026-08-05 16:33:50', '2026-08-05 16:33:59', 3100.000);

-- ─────────────────────────────────
-- Table: documents_generes
-- ─────────────────────────────────
DROP TABLE IF EXISTS `documents_generes`;
CREATE TABLE `documents_generes` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `nom_fichier` varchar(300) NOT NULL,
  `type_document` varchar(50) NOT NULL,
  `format` varchar(20) DEFAULT 'PDF',
  `contenu_blob` longblob DEFAULT NULL,
  `reference_doc` varchar(100) DEFAULT NULL,
  `modele_id` bigint(20) DEFAULT NULL,
  `utilisateur_id` bigint(20) DEFAULT NULL,
  `date_creation` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `fk_dg_modele` (`modele_id`),
  KEY `fk_dg_utilisateur` (`utilisateur_id`),
  CONSTRAINT `fk_dg_modele` FOREIGN KEY (`modele_id`) REFERENCES `modeles_documents` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_dg_utilisateur` FOREIGN KEY (`utilisateur_id`) REFERENCES `utilisateurs` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ─────────────────────────────────
-- Table: documents_kyc
-- ─────────────────────────────────
DROP TABLE IF EXISTS `documents_kyc`;
CREATE TABLE `documents_kyc` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `utilisateur_id` bigint(20) NOT NULL,
  `type_doc` varchar(50) NOT NULL,
  `nom_fichier` varchar(300) NOT NULL,
  `contenu_blob` longblob DEFAULT NULL,
  `statut` varchar(20) DEFAULT 'EN_ATTENTE',
  `date_creation` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `fk_dk_utilisateur` (`utilisateur_id`),
  CONSTRAINT `fk_dk_utilisateur` FOREIGN KEY (`utilisateur_id`) REFERENCES `utilisateurs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ─────────────────────────────────
-- Table: ecritures_comptables
-- ─────────────────────────────────
DROP TABLE IF EXISTS `ecritures_comptables`;
CREATE TABLE `ecritures_comptables` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `numero_ecriture` varchar(50) NOT NULL,
  `date_ecriture` date NOT NULL,
  `type_ecriture` varchar(50) NOT NULL,
  `libelle` varchar(500) NOT NULL,
  `montant_debit` decimal(15,3) DEFAULT 0.000,
  `montant_credit` decimal(15,3) DEFAULT 0.000,
  `compte_debit` varchar(20) DEFAULT NULL,
  `compte_credit` varchar(20) DEFAULT NULL,
  `reference_doc` varchar(100) DEFAULT NULL,
  `utilisateur_id` bigint(20) DEFAULT NULL,
  `date_creation` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `numero_ecriture` (`numero_ecriture`),
  KEY `fk_ec_utilisateur` (`utilisateur_id`),
  CONSTRAINT `fk_ec_utilisateur` FOREIGN KEY (`utilisateur_id`) REFERENCES `utilisateurs` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `ecritures_comptables` VALUES
(1, 'ECR-2026-001', '2026-01-20', 'VENTE', 'Facture FAC-2026-001 Sotrapil Reseau', 18400.000, 0.000, '411', '707', 'FAC-2026-001', 3, '2026-07-30 21:09:39'),
(2, 'ECR-2026-002', '2026-01-20', 'TVA', 'TVA collectee FAC-2026-001 Sotrapil', 0.000, 2937.815, '411', '4457', 'FAC-2026-001', 3, '2026-07-30 21:09:39'),
(3, 'ECR-2026-003', '2026-02-01', 'ACHAT', 'Facture fournisseur Cisco ACH-2026-001', 15000.000, 0.000, '601', '401', 'ACH-2026-001', 3, '2026-07-30 21:09:39'),
(4, 'ECR-2026-004', '2026-02-01', 'TVA', 'TVA deductible achat Cisco', 2394.958, 0.000, '4456', '401', 'ACH-2026-001', 3, '2026-07-30 21:09:39'),
(5, 'ECR-2026-005', '2026-02-10', 'VENTE', 'Facture FAC-2026-002 El-Bouniane Bureautique', 10000.000, 0.000, '411', '707', 'FAC-2026-002', 3, '2026-07-30 21:09:39'),
(6, 'ECR-2026-006', '2026-02-10', 'TVA', 'TVA collectee FAC-2026-002 El-Bouniane', 0.000, 1596.639, '411', '4457', 'FAC-2026-002', 3, '2026-07-30 21:09:39'),
(7, 'ECR-2026-007', '2026-03-01', 'VENTE', 'Facture FAC-2026-003 Carthage SA Serveurs', 25000.000, 0.000, '411', '707', 'FAC-2026-003', 3, '2026-07-30 21:09:39'),
(8, 'ECR-2026-008', '2026-03-01', 'TVA', 'TVA collectee FAC-2026-003 Carthage SA', 0.000, 3991.597, '411', '4457', 'FAC-2026-003', 3, '2026-07-30 21:09:39'),
(9, 'ECR-2026-009', '2026-03-05', 'ENCAISSEMENT', 'Paiement Sotrapil FAC-2026-001', 0.000, 18400.000, '512', '411', 'FAC-2026-001', 3, '2026-07-30 21:09:39'),
(10, 'ECR-2026-010', '2026-03-20', 'ENCAISSEMENT', 'Paiement Carthage SA FAC-2026-003', 0.000, 25000.000, '512', '411', 'FAC-2026-003', 3, '2026-07-30 21:09:39'),
(11, 'ECR-2026-011', '2026-04-01', 'VENTE', 'Facture FAC-2026-005 Tunisie Telecom WiFi', 5000.000, 0.000, '411', '707', 'FAC-2026-005', 3, '2026-07-30 21:09:39'),
(12, 'ECR-2026-012', '2026-04-15', 'VENTE', 'Facture FAC-2026-006 Alpha Invest Electrique', 6000.000, 0.000, '411', '707', 'FAC-2026-006', 3, '2026-07-30 21:09:39');

-- ─────────────────────────────────
-- Table: entrepots
-- ─────────────────────────────────
DROP TABLE IF EXISTS `entrepots`;
CREATE TABLE `entrepots` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `code` varchar(50) NOT NULL,
  `nom` varchar(100) NOT NULL,
  `adresse` varchar(255) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `date_creation` timestamp NOT NULL DEFAULT current_timestamp(),
  `date_modification` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `entrepots` VALUES
(1, 'ENT-TUNIS', 'Entrepot Principal Tunis', 'Zone Industrielle Charguia I, Tunis', 'Entrepot principal - Materiel reseau et serveurs', '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(2, 'ENT-SFAX', 'Depot Sfax', 'Route de Gabes Km 3, Sfax', 'Depot regional Sud - Electricite et cablage', '2026-07-30 21:09:39', '2026-07-30 21:09:39');

-- ─────────────────────────────────
-- Table: factures
-- ─────────────────────────────────
DROP TABLE IF EXISTS `factures`;
CREATE TABLE `factures` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `numero_facture` varchar(50) NOT NULL,
  `date_emission` timestamp NOT NULL DEFAULT current_timestamp(),
  `date_echeance` date DEFAULT NULL,
  `montant_ht` decimal(15,3) NOT NULL DEFAULT 0.000,
  `montant_tva` decimal(15,3) NOT NULL DEFAULT 0.000,
  `montant_total` decimal(15,3) NOT NULL DEFAULT 0.000,
  `statut` varchar(20) NOT NULL DEFAULT 'EN_ATTENTE',
  `signature_numerique` varchar(512) DEFAULT NULL,
  `notes` text DEFAULT NULL,
  `commande_id` bigint(20) DEFAULT NULL,
  `client_id` bigint(20) DEFAULT NULL,
  `date_creation` timestamp NOT NULL DEFAULT current_timestamp(),
  `date_modification` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `numero_facture` (`numero_facture`),
  KEY `fk_fact_commande` (`commande_id`),
  KEY `fk_fact_client` (`client_id`),
  CONSTRAINT `fk_fact_client` FOREIGN KEY (`client_id`) REFERENCES `clients` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_fact_commande` FOREIGN KEY (`commande_id`) REFERENCES `commandes` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `factures` VALUES
(1, 'FAC-2026-001', '2026-01-20 10:00:00', '2026-02-20', 15462.185, 2937.815, 18400.000, 'PAYEE', NULL, 'Facture reseau Sotrapil', 1, 1, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(2, 'FAC-2026-002', '2026-02-05 09:00:00', '2026-03-05', 8403.361, 1596.639, 10000.000, 'EN_ATTENTE', NULL, 'Facture bureautique El-Bouniane', 2, 2, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(3, 'FAC-2026-003', '2026-03-01 11:00:00', '2026-04-01', 21008.403, 3991.597, 25000.000, 'PAYEE', NULL, 'Facture serveurs Carthage SA', 4, 7, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(4, 'FAC-2026-004', '2026-03-05 14:00:00', '2026-04-05', 2016.807, 383.193, 2400.000, 'PAYEE', NULL, 'Facture cablage Medina Group', 5, 5, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(5, 'FAC-2026-005', '2026-04-01 09:00:00', '2026-05-01', 4201.681, 798.319, 5000.000, 'EN_ATTENTE', NULL, 'Facture WiFi Tunisie Telecom', 6, 3, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(6, 'FAC-2026-006', '2026-04-15 10:00:00', '2026-05-15', 5042.017, 957.983, 6000.000, 'EN_RETARD', NULL, 'Facture electrique Alpha Invest', 3, 4, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(7, 'FAC-20260802-5FDB32', '2026-08-02 22:18:27', '2026-09-01', 0.000, 608.000, 3808.000, 'EN_ATTENTE', 'SIG-5E547EA2-C437-4956-B08F-6B634A0B7148', NULL, 9, NULL, '2026-08-02 22:18:27', '2026-08-02 22:18:27'),
(8, 'FAC-20260802-8C7416', '2026-08-02 22:18:56', '2026-08-31', 0.000, 418.000, 2618.000, 'PAYEE', 'SIG-FDC33A97-D104-4493-B03B-2304CA025617', NULL, 10, NULL, '2026-08-02 22:18:56', '2026-08-04 22:16:07'),
(9, 'FAC-20260802-7A5F0F', '2026-08-02 22:19:38', '2026-08-31', 0.000, 8.550, 53.550, 'PAYEE', 'SIG-D000089E-8140-499C-B57A-A54B3762CE4A', NULL, 11, NULL, '2026-08-02 22:19:38', '2026-08-06 23:12:49'),
(10, 'FAC-20260805-605A65', '2026-08-05 16:24:52', '2026-09-04', 0.000, 608.000, 3808.000, 'EN_ATTENTE', 'SIG-7F4EFC6F-2EEB-484E-8799-4A225E5A60EE', NULL, 12, NULL, '2026-08-05 16:24:53', '2026-08-05 16:24:53'),
(11, 'FAC-20260805-95F651', '2026-08-05 16:25:05', '2026-09-04', 0.000, 66.500, 416.500, 'EN_ATTENTE', 'SIG-1AF3FBB4-E7A5-40A0-A859-FF77FB6CABC9', NULL, 13, NULL, '2026-08-05 16:25:05', '2026-08-05 16:25:05'),
(12, 'FAC-20260805-91F388', '2026-08-05 16:34:15', '2026-09-04', 0.000, 2375.000, 14875.000, 'EN_ATTENTE', 'SIG-048D7F7C-58E2-4343-9A46-4387AC72B1EA', NULL, 14, NULL, '2026-08-05 16:34:15', '2026-08-05 16:34:15'),
(13, 'FAC-20260805-627405', '2026-08-05 16:54:23', '2026-09-04', 0.000, 169.100, 1059.100, 'EN_ATTENTE', 'SIG-7C706D6C-78A4-435C-81E3-3A287B452F51', NULL, 15, NULL, '2026-08-05 16:54:23', '2026-08-05 16:54:23'),
(14, 'FAC-20260805-0AC8F5', '2026-08-05 17:40:10', '2026-09-03', 0.000, 180.500, 1130.500, 'PAYEE', 'SIG-289386E9-63F0-446D-9064-423A7273279E', NULL, 16, NULL, '2026-08-05 17:40:10', '2026-08-06 17:03:58'),
(15, 'FAC-20260806-3EDD83', '2026-08-06 21:50:32', '2026-09-05', 0.000, 1615.000, 10115.000, 'EN_ATTENTE', 'SIG-DF628F22-7516-4088-8B4B-9F776C99E05D', NULL, 17, NULL, '2026-08-06 21:50:32', '2026-08-06 21:50:32');

-- ─────────────────────────────────
-- Table: fournisseurs
-- ─────────────────────────────────
DROP TABLE IF EXISTS `fournisseurs`;
CREATE TABLE `fournisseurs` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `nom` varchar(200) NOT NULL,
  `email` varchar(100) NOT NULL,
  `telephone` varchar(20) DEFAULT NULL,
  `adresse` varchar(500) DEFAULT NULL,
  `matricule_fiscale` varchar(50) DEFAULT NULL,
  `code_fournisseur` varchar(50) DEFAULT NULL,
  `delai_paiement_jours` int(11) DEFAULT 30,
  `date_creation` timestamp NOT NULL DEFAULT current_timestamp(),
  `date_modification` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `fournisseurs` VALUES
(1, 'Cisco Systems Tunisia', 'sales.tn@cisco.com', '+216 71 710 000', 'Immeuble Bayrem, Charguia I, Tunis', 'US-CISCO-001', 'FOU-001', 60, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(2, 'Netgear Maghreb', 'maghreb@netgear.com', '+216 71 800 500', 'Ariana Technopole, Ariana, Tunisie', 'FR-NETGEAR-02', 'FOU-002', 45, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(3, 'Legrand Tunisie', 'contact.tunisie@legrand.com', '+216 71 180 500', 'Zone Industrielle, Charguia I, Tunis', '0012345LAM000', 'FOU-003', 30, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(4, 'Dell Technologies TN', 'tunisie@dell.com', '+216 71 900 400', 'Rue du Lac Malaren, Berges du Lac, Tunis', 'US-DELL-TN-04', 'FOU-004', 45, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(5, 'HP Tunisia', 'hptunisia@hp.com', '+216 71 860 700', 'Centre Urbain Nord, Tunis, Tunisie', 'US-HP-TN-0005', 'FOU-005', 30, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(6, 'APC Schneider Tunisie', 'apc.tunisie@schneider.com', '+216 71 340 600', 'Route de Bizerte Km 12, Tunis', 'FR-SCHN-TN-06', 'FOU-006', 45, '2026-07-30 21:09:39', '2026-07-30 21:09:39');

-- ─────────────────────────────────
-- Table: inventaires
-- ─────────────────────────────────
DROP TABLE IF EXISTS `inventaires`;
CREATE TABLE `inventaires` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `code` varchar(50) NOT NULL,
  `date_inventaire` timestamp NOT NULL DEFAULT current_timestamp(),
  `entrepot_id` bigint(20) NOT NULL,
  `statut` varchar(20) NOT NULL DEFAULT 'EN_COURS',
  `description` varchar(255) DEFAULT NULL,
  `date_creation` timestamp NOT NULL DEFAULT current_timestamp(),
  `date_modification` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  KEY `fk_inv_entrepot` (`entrepot_id`),
  CONSTRAINT `fk_inv_entrepot` FOREIGN KEY (`entrepot_id`) REFERENCES `entrepots` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `inventaires` VALUES
(1, 'INV-2026-001', '2026-03-31 08:00:00', 1, 'TERMINE', 'Inventaire T1 2026 - Entrepot Tunis', '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(2, 'INV-2026-002', '2026-03-31 09:00:00', 2, 'TERMINE', 'Inventaire T1 2026 - Depot Sfax', '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(3, 'INV-4904588C', '2026-08-07 21:08:23', 1, 'EN_COURS', NULL, '2026-08-07 21:08:23', '2026-08-07 21:08:23'),
(4, 'INV-A2865BE6', '2026-08-07 21:08:39', 1, 'EN_COURS', 'klk', '2026-08-07 21:08:39', '2026-08-07 21:08:39'),
(5, 'INV-E907F004', '2026-08-07 21:09:03', 1, 'EN_COURS', 'klk,', '2026-08-07 21:09:03', '2026-08-07 21:09:03');

-- ─────────────────────────────────
-- Table: ligne_commandes
-- ─────────────────────────────────
DROP TABLE IF EXISTS `ligne_commandes`;
CREATE TABLE `ligne_commandes` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `commande_id` bigint(20) NOT NULL,
  `produit_id` bigint(20) NOT NULL,
  `quantite` int(11) NOT NULL,
  `prix_unitaire` decimal(15,3) NOT NULL,
  `remise` decimal(5,2) DEFAULT 0.00,
  `taux_tva` decimal(5,2) DEFAULT 19.00,
  PRIMARY KEY (`id`),
  KEY `fk_lc_commande` (`commande_id`),
  KEY `fk_lc_produit` (`produit_id`),
  CONSTRAINT `fk_lc_commande` FOREIGN KEY (`commande_id`) REFERENCES `commandes` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_lc_produit` FOREIGN KEY (`produit_id`) REFERENCES `produits` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `ligne_commandes` VALUES
(1, 1, 1, 3, 2850.000, 0.00, 19.00),
(2, 1, 3, 5, 890.000, 0.00, 19.00),
(3, 1, 4, 3, 120.000, 0.00, 19.00),
(4, 2, 8, 3, 2200.000, 5.00, 19.00),
(5, 2, 9, 1, 3100.000, 5.00, 19.00),
(6, 2, 10, 2, 950.000, 5.00, 19.00),
(7, 3, 11, 2, 1850.000, 0.00, 19.00),
(8, 3, 12, 4, 350.000, 0.00, 19.00),
(9, 4, 5, 2, 8500.000, 2.00, 19.00),
(10, 4, 6, 1, 12500.000, 2.00, 19.00),
(11, 5, 4, 10, 120.000, 0.00, 19.00),
(12, 5, 14, 10, 85.000, 0.00, 19.00),
(13, 6, 3, 4, 890.000, 0.00, 19.00),
(14, 7, 15, 1, 2400.000, 0.00, 19.00),
(15, 7, 7, 2, 2100.000, 0.00, 19.00),
(16, 8, 2, 1, 3200.000, 0.00, 19.00),
(17, 9, 2, 1, 3200.000, 0.00, 19.00),
(18, 10, 8, 1, 2200.000, 0.00, 19.00),
(19, 11, 13, 1, 45.000, 0.00, 19.00),
(20, 12, 2, 1, 3200.000, 0.00, 19.00),
(21, 13, 12, 1, 350.000, 0.00, 19.00),
(22, 14, 6, 1, 12500.000, 0.00, 19.00),
(23, 15, 3, 1, 890.000, 0.00, 19.00),
(24, 16, 10, 1, 950.000, 0.00, 19.00),
(25, 17, 5, 1, 8500.000, 0.00, 19.00);

-- ─────────────────────────────────
-- Table: ligne_commandes_achat
-- ─────────────────────────────────
DROP TABLE IF EXISTS `ligne_commandes_achat`;
CREATE TABLE `ligne_commandes_achat` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `commande_achat_id` bigint(20) NOT NULL,
  `produit_id` bigint(20) NOT NULL,
  `quantite` int(11) NOT NULL,
  `prix_unitaire` decimal(15,3) NOT NULL,
  `taux_tva` decimal(5,2) DEFAULT 19.00,
  PRIMARY KEY (`id`),
  KEY `fk_lca_commande` (`commande_achat_id`),
  KEY `fk_lca_produit` (`produit_id`),
  CONSTRAINT `fk_lca_commande` FOREIGN KEY (`commande_achat_id`) REFERENCES `commandes_achat` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_lca_produit` FOREIGN KEY (`produit_id`) REFERENCES `produits` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `ligne_commandes_achat` VALUES
(1, 1, 1, 3, 2100.000, 19.00),
(2, 1, 2, 2, 2400.000, 19.00),
(3, 1, 15, 1, 1750.000, 19.00),
(4, 2, 3, 8, 650.000, 19.00),
(5, 3, 5, 1, 6500.000, 19.00),
(6, 3, 7, 3, 1550.000, 19.00),
(7, 4, 8, 3, 1650.000, 19.00),
(8, 5, 11, 3, 1350.000, 19.00),
(9, 6, 12, 10, 220.000, 19.00),
(10, 6, 13, 20, 28.000, 19.00);

-- ─────────────────────────────────
-- Table: ligne_devis
-- ─────────────────────────────────
DROP TABLE IF EXISTS `ligne_devis`;
CREATE TABLE `ligne_devis` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `devis_id` bigint(20) NOT NULL,
  `produit_id` bigint(20) NOT NULL,
  `quantite` int(11) NOT NULL,
  `prix_unitaire` decimal(15,3) NOT NULL,
  `remise` decimal(5,2) DEFAULT 0.00,
  `taux_tva` decimal(5,2) DEFAULT 19.00,
  PRIMARY KEY (`id`),
  KEY `fk_ld_devis` (`devis_id`),
  KEY `fk_ld_produit` (`produit_id`),
  CONSTRAINT `fk_ld_devis` FOREIGN KEY (`devis_id`) REFERENCES `devis` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ld_produit` FOREIGN KEY (`produit_id`) REFERENCES `produits` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `ligne_devis` VALUES
(1, 1, 1, 3, 2850.000, 0.00, 19.00),
(2, 1, 3, 5, 890.000, 0.00, 19.00),
(3, 1, 4, 3, 120.000, 0.00, 19.00),
(4, 2, 8, 3, 2200.000, 5.00, 19.00),
(5, 2, 9, 1, 3100.000, 5.00, 19.00),
(6, 2, 10, 2, 950.000, 5.00, 19.00),
(7, 3, 11, 2, 1850.000, 0.00, 19.00),
(8, 3, 12, 4, 350.000, 0.00, 19.00),
(9, 4, 5, 2, 8500.000, 2.00, 19.00),
(10, 4, 6, 1, 12500.000, 2.00, 19.00),
(11, 5, 4, 15, 120.000, 0.00, 19.00),
(12, 5, 14, 20, 85.000, 0.00, 19.00),
(13, 6, 15, 2, 2400.000, 0.00, 19.00),
(14, 6, 7, 3, 2100.000, 0.00, 19.00),
(15, 6, 2, 1, 3200.000, 0.00, 19.00),
(16, 8, 1, 1, 2850.000, 0.00, 19.00),
(17, 9, 1, 1, 2850.000, 0.00, 19.00),
(18, 10, 9, 1, 3100.000, 0.00, 19.00);

-- ─────────────────────────────────
-- Table: ligne_inventaires
-- ─────────────────────────────────
DROP TABLE IF EXISTS `ligne_inventaires`;
CREATE TABLE `ligne_inventaires` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `inventaire_id` bigint(20) NOT NULL,
  `produit_id` bigint(20) NOT NULL,
  `quantite_theorique` int(11) NOT NULL,
  `quantite_physique` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_inventaire_produit` (`inventaire_id`,`produit_id`),
  KEY `fk_li_produit` (`produit_id`),
  CONSTRAINT `fk_li_inventaire` FOREIGN KEY (`inventaire_id`) REFERENCES `inventaires` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_li_produit` FOREIGN KEY (`produit_id`) REFERENCES `produits` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=64 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `ligne_inventaires` VALUES
(1, 1, 1, 10, 10),
(2, 1, 2, 6, 6),
(3, 1, 3, 20, 19),
(4, 1, 4, 35, 35),
(5, 1, 5, 4, 4),
(6, 1, 6, 3, 3),
(7, 1, 7, 8, 8),
(8, 1, 8, 15, 15),
(9, 1, 9, 10, 10),
(10, 1, 10, 14, 14),
(11, 1, 11, 6, 6),
(12, 2, 3, 5, 5),
(13, 2, 12, 15, 14),
(14, 2, 13, 60, 60),
(15, 2, 14, 15, 15),
(16, 3, 1, 10, 10),
(17, 3, 2, 6, 6),
(18, 3, 3, 20, 20),
(19, 3, 4, 35, 35),
(20, 3, 5, 4, 4),
(21, 3, 6, 3, 3),
(22, 3, 7, 8, 8),
(23, 3, 8, 15, 15),
(24, 3, 9, 10, 10),
(25, 3, 10, 14, 14),
(26, 3, 11, 6, 6),
(27, 3, 12, 15, 15),
(28, 3, 13, 60, 60),
(29, 3, 14, 25, 25),
(30, 3, 15, 3, 3),
(31, 3, 16, 0, 0),
(32, 4, 1, 10, 10),
(33, 4, 2, 6, 6),
(34, 4, 3, 20, 20),
(35, 4, 4, 35, 35),
(36, 4, 5, 4, 4),
(37, 4, 6, 3, 3),
(38, 4, 7, 8, 8),
(39, 4, 8, 15, 15),
(40, 4, 9, 10, 10),
(41, 4, 10, 14, 14),
(42, 4, 11, 6, 6),
(43, 4, 12, 15, 15),
(44, 4, 13, 60, 60),
(45, 4, 14, 25, 25),
(46, 4, 15, 3, 3),
(47, 4, 16, 0, 0),
(48, 5, 1, 10, 10),
(49, 5, 2, 6, 6),
(50, 5, 3, 20, 20),
(51, 5, 4, 35, 35),
(52, 5, 5, 4, 4),
(53, 5, 6, 3, 3),
(54, 5, 7, 8, 8),
(55, 5, 8, 15, 15),
(56, 5, 9, 10, 10),
(57, 5, 10, 14, 14),
(58, 5, 11, 6, 6),
(59, 5, 12, 15, 15),
(60, 5, 13, 60, 60),
(61, 5, 14, 25, 25),
(62, 5, 15, 3, 3),
(63, 5, 16, 0, 0);

-- ─────────────────────────────────
-- Table: lignes_calcul
-- ─────────────────────────────────
DROP TABLE IF EXISTS `lignes_calcul`;
CREATE TABLE `lignes_calcul` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `calcul_id` bigint(20) NOT NULL,
  `numero_ligne` int(11) NOT NULL,
  `date_debut` date NOT NULL,
  `date_fin` date NOT NULL,
  `nombre_jours` bigint(20) NOT NULL,
  `taux` decimal(5,2) NOT NULL,
  `montant_base` decimal(15,3) NOT NULL,
  `resultat_ligne` decimal(15,2) NOT NULL,
  `libelle_periode` varchar(200) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_lc_calcul` (`calcul_id`),
  CONSTRAINT `fk_lc_calcul` FOREIGN KEY (`calcul_id`) REFERENCES `calculs_moteur` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `lignes_calcul` VALUES
(1, 2, 1, '2026-07-21', '2026-08-20', 31, 6.25, 70000.000, 371.58, 'BCT Taux Directeur S2 2026');

-- ─────────────────────────────────
-- Table: modeles_documents
-- ─────────────────────────────────
DROP TABLE IF EXISTS `modeles_documents`;
CREATE TABLE `modeles_documents` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `nom` varchar(200) NOT NULL,
  `type_document` varchar(50) NOT NULL,
  `contenu` longtext DEFAULT NULL,
  `actif` tinyint(1) DEFAULT 1,
  `date_creation` timestamp NOT NULL DEFAULT current_timestamp(),
  `date_modification` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ─────────────────────────────────
-- Table: mouvements_stock
-- ─────────────────────────────────
DROP TABLE IF EXISTS `mouvements_stock`;
CREATE TABLE `mouvements_stock` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `produit_id` bigint(20) NOT NULL,
  `entrepot_id` bigint(20) NOT NULL,
  `type_mouvement` varchar(20) NOT NULL,
  `quantite` int(11) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `reference_doc` varchar(100) DEFAULT NULL,
  `date_mouvement` timestamp NOT NULL DEFAULT current_timestamp(),
  `utilisateur_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_ms_produit` (`produit_id`),
  KEY `fk_ms_entrepot` (`entrepot_id`),
  KEY `fk_ms_utilisateur` (`utilisateur_id`),
  CONSTRAINT `fk_ms_entrepot` FOREIGN KEY (`entrepot_id`) REFERENCES `entrepots` (`id`),
  CONSTRAINT `fk_ms_produit` FOREIGN KEY (`produit_id`) REFERENCES `produits` (`id`),
  CONSTRAINT `fk_ms_utilisateur` FOREIGN KEY (`utilisateur_id`) REFERENCES `utilisateurs` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `mouvements_stock` VALUES
(1, 1, 1, 'ENTREE', 5, 'Reception commande Cisco', 'ACH-2026-001', '2026-07-30 21:09:39', 4),
(2, 2, 1, 'ENTREE', 3, 'Reception commande Cisco', 'ACH-2026-001', '2026-07-30 21:09:39', 4),
(3, 15, 1, 'ENTREE', 2, 'Reception baie brassage', 'ACH-2026-001', '2026-07-30 21:09:39', 4),
(4, 3, 1, 'ENTREE', 10, 'Reception WiFi Netgear', 'ACH-2026-002', '2026-07-30 21:09:39', 4),
(5, 5, 1, 'ENTREE', 2, 'Reception serveur Dell', 'ACH-2026-003', '2026-07-30 21:09:39', 4),
(6, 7, 1, 'ENTREE', 4, 'Reception NAS Synology', 'ACH-2026-003', '2026-07-30 21:09:39', 4),
(7, 8, 1, 'ENTREE', 5, 'Reception PC HP', 'ACH-2026-004', '2026-07-30 21:09:39', 4),
(8, 1, 1, 'SORTIE', 3, 'Livraison Sotrapil - switches', 'CMD-2026-001', '2026-07-30 21:09:39', 2),
(9, 3, 1, 'SORTIE', 5, 'Livraison Sotrapil - WiFi', 'CMD-2026-001', '2026-07-30 21:09:39', 2),
(10, 4, 1, 'SORTIE', 3, 'Livraison Sotrapil - cables', 'CMD-2026-001', '2026-07-30 21:09:39', 2),
(11, 5, 1, 'SORTIE', 2, 'Livraison Carthage SA - serveurs', 'CMD-2026-004', '2026-07-30 21:09:39', 2),
(12, 6, 1, 'SORTIE', 1, 'Livraison Carthage SA - HP DL380', 'CMD-2026-004', '2026-07-30 21:09:39', 2),
(13, 4, 1, 'SORTIE', 10, 'Livraison Medina Group', 'CMD-2026-005', '2026-07-30 21:09:39', 2),
(14, 14, 2, 'SORTIE', 10, 'Livraison cables elec Medina', 'CMD-2026-005', '2026-07-30 21:09:39', 2);

-- ─────────────────────────────────
-- Table: periodes_taux
-- ─────────────────────────────────
DROP TABLE IF EXISTS `periodes_taux`;
CREATE TABLE `periodes_taux` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `date_debut` date NOT NULL,
  `date_fin` date NOT NULL,
  `taux` decimal(5,2) NOT NULL,
  `libelle` varchar(200) DEFAULT NULL,
  `actif` tinyint(1) NOT NULL DEFAULT 1,
  `date_creation` timestamp NOT NULL DEFAULT current_timestamp(),
  `date_modification` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `periodes_taux` VALUES
(1, '2024-01-01', '2024-06-30', 8.00, 'BCT Taux Directeur S1 2024', true, '2026-07-31 20:55:51', '2026-07-31 20:55:51'),
(2, '2024-07-01', '2024-12-31', 7.50, 'BCT Taux Directeur S2 2024', true, '2026-07-31 20:55:51', '2026-07-31 20:55:51'),
(3, '2025-01-01', '2025-06-30', 7.00, 'BCT Taux Directeur S1 2025', true, '2026-07-31 20:55:51', '2026-07-31 20:55:51'),
(4, '2025-07-01', '2025-12-31', 6.75, 'BCT Taux Directeur S2 2025', true, '2026-07-31 20:55:51', '2026-07-31 20:55:51'),
(5, '2026-01-01', '2026-06-30', 6.50, 'BCT Taux Directeur S1 2026', true, '2026-07-31 20:55:51', '2026-07-31 20:55:51'),
(6, '2026-07-01', '2026-12-31', 6.25, 'BCT Taux Directeur S2 2026', true, '2026-07-31 20:55:51', '2026-07-31 20:55:51');

-- ─────────────────────────────────
-- Table: produits
-- ─────────────────────────────────
DROP TABLE IF EXISTS `produits`;
CREATE TABLE `produits` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `nom` varchar(200) NOT NULL,
  `reference` varchar(100) NOT NULL,
  `description` text DEFAULT NULL,
  `prix_unitaire` decimal(15,3) NOT NULL,
  `prix_achat` decimal(15,3) NOT NULL,
  `taux_tva` decimal(5,2) DEFAULT 19.00,
  `quantite_stock` int(11) DEFAULT 0,
  `seuil_stock_min` int(11) DEFAULT 5,
  `categorie` varchar(100) DEFAULT NULL,
  `unite` varchar(20) DEFAULT 'Pièce',
  `actif` tinyint(1) DEFAULT 1,
  `date_creation` timestamp NOT NULL DEFAULT current_timestamp(),
  `date_modification` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `reference` (`reference`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `produits` VALUES
(1, 'Switch Cisco Catalyst 2960', 'CISCO-SW-2960', 'Switch 24 ports Gigabit gere, VLAN, QoS', 2850.000, 2100.000, 19.00, 15, 3, 'Reseau', 'Piece', true, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(2, 'Routeur Cisco ASA 5506', 'CISCO-RT-5506', 'Firewall/Routeur entreprise, 8 ports GE', 3200.000, 2400.000, 19.00, 8, 2, 'Reseau', 'Piece', true, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(3, 'Point acces WiFi Netgear', 'NTGR-AP-WAX630', 'WiFi 6 Triband, PoE+, gestion centralisee', 890.000, 650.000, 19.00, 25, 5, 'Reseau', 'Piece', true, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(4, 'Cable RJ45 Cat6 100m', 'CAB-RJ45-C6-100', 'Cable reseau blinde Cat6 bobine 100 metres', 120.000, 80.000, 19.00, 50, 10, 'Cablage', 'Bobine', true, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(5, 'Serveur Dell PowerEdge R350', 'DELL-SRV-R350', 'Serveur rack 1U, Xeon E-2300, 32GB RAM, 2TB SSD', 8500.000, 6500.000, 19.00, 5, 1, 'Serveurs', 'Piece', true, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(6, 'Serveur HP ProLiant DL380', 'HP-SRV-DL380', 'Serveur 2U, 2x Xeon Silver, 64GB RAM, 4TB SAS', 12500.000, 9500.000, 19.00, 3, 1, 'Serveurs', 'Piece', true, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(7, 'NAS Synology DS923+', 'SYN-NAS-DS923', 'NAS 4 baies, 8GB RAM, 10GbE, extensible', 2100.000, 1550.000, 19.00, 10, 2, 'Stockage', 'Piece', true, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(8, 'PC Dell OptiPlex 7010', 'DELL-PC-7010', 'Tour, Core i7-13700, 16GB, 512GB SSD, Win11 Pro', 2200.000, 1650.000, 19.00, 20, 5, 'Postes de travail', 'Piece', true, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(9, 'Laptop HP EliteBook 840 G9', 'HP-LT-EB840G9', '14 FHD IPS, Core i7-1265U, 16GB, 512GB SSD', 3100.000, 2350.000, 19.00, 12, 3, 'Postes de travail', 'Piece', true, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(10, 'Ecran Dell 27 4K', 'DELL-MON-U2722D', '27 IPS 4K UHD, USB-C 90W, reglable en hauteur', 950.000, 680.000, 19.00, 18, 4, 'Postes de travail', 'Piece', true, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(11, 'Onduleur APC Smart-UPS 1500', 'APC-UPS-SMT1500', 'UPS line-interactive 1500VA/1000W, LCD, RS232', 1850.000, 1350.000, 19.00, 8, 2, 'Energie', 'Piece', true, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(12, 'Tableau electrique 18 modules', 'LEG-TAB-18M', 'Coffret saillie Legrand 18 modules, equipe', 350.000, 220.000, 19.00, 30, 8, 'Electricite', 'Piece', true, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(13, 'Disjoncteur 20A Legrand', 'LEG-DIS-20A', 'Disjoncteur differentiel 20A/30mA type A', 45.000, 28.000, 19.00, 100, 20, 'Electricite', 'Piece', true, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(14, 'Cable electrique 2.5mm 100m', 'CAB-ELEC-25-100', 'Cable souple H07V-K 2.5mm rouge bobine 100m', 85.000, 55.000, 19.00, 40, 10, 'Cablage', 'Bobine', true, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(15, 'Baie brassage 19 42U', 'BAI-19-42U', 'Rack 19 pouces 42U, 600x1000mm, avec PDU 8 prises', 2400.000, 1750.000, 19.00, 4, 1, 'Reseau', 'Piece', true, '2026-07-30 21:09:39', '2026-07-30 21:09:39'),
(16, 'eert', '9564g', NULL, 100.000, 700.000, 19.00, 0, 3, 'INFORMATIQUE', 'Pièce', true, '2026-08-06 17:28:22', '2026-08-06 17:28:22');

-- ─────────────────────────────────
-- Table: receptions_livraison
-- ─────────────────────────────────
DROP TABLE IF EXISTS `receptions_livraison`;
CREATE TABLE `receptions_livraison` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `numero_reception` varchar(50) NOT NULL,
  `commande_achat_id` bigint(20) DEFAULT NULL,
  `fournisseur_id` bigint(20) DEFAULT NULL,
  `entrepot_id` bigint(20) DEFAULT NULL,
  `statut` varchar(20) NOT NULL DEFAULT 'EN_ATTENTE',
  `notes` text DEFAULT NULL,
  `date_reception` timestamp NOT NULL DEFAULT current_timestamp(),
  `date_creation` timestamp NOT NULL DEFAULT current_timestamp(),
  `date_modification` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `numero_reception` (`numero_reception`),
  KEY `fk_rl_commande` (`commande_achat_id`),
  KEY `fk_rl_fournisseur` (`fournisseur_id`),
  KEY `fk_rl_entrepot` (`entrepot_id`),
  CONSTRAINT `fk_rl_commande` FOREIGN KEY (`commande_achat_id`) REFERENCES `commandes_achat` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_rl_entrepot` FOREIGN KEY (`entrepot_id`) REFERENCES `entrepots` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_rl_fournisseur` FOREIGN KEY (`fournisseur_id`) REFERENCES `fournisseurs` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ─────────────────────────────────
-- Table: refresh_tokens
-- ─────────────────────────────────
DROP TABLE IF EXISTS `refresh_tokens`;
CREATE TABLE `refresh_tokens` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `token` varchar(512) NOT NULL,
  `utilisateur_id` bigint(20) NOT NULL,
  `date_expiration` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `revoque` tinyint(1) DEFAULT 0,
  `date_creation` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `token` (`token`),
  KEY `fk_rt_utilisateur` (`utilisateur_id`),
  CONSTRAINT `fk_rt_utilisateur` FOREIGN KEY (`utilisateur_id`) REFERENCES `utilisateurs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=116 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `refresh_tokens` VALUES
(1, 'a1f8eb29-7c6d-4f34-b397-44492ca0bad4', 1, '2026-07-30 23:33:00', true, '2026-07-30 22:31:02'),
(2, 'd2f55e6b-b604-4899-b40e-08026c3f56e6', 2, '2026-07-30 23:33:22', true, '2026-07-30 22:33:09'),
(3, 'ee1fbfcc-cb16-47f7-ac17-57879575a6cb', 3, '2026-08-06 22:33:35', false, '2026-07-30 22:33:35'),
(4, '53d361ce-f223-4d27-baa1-ff315bdbd958', 4, '2026-07-30 23:37:56', true, '2026-07-30 22:36:05'),
(5, '01da9f5c-1c3b-4f79-8961-b092ef1ef745', 5, '2026-08-05 14:19:03', true, '2026-07-30 22:38:07'),
(6, '7fac47a7-34c6-4025-8f9f-7d7e95e0f8a7', 2, '2026-07-31 00:05:53', true, '2026-07-30 23:05:08'),
(7, 'dc3c60f7-71cf-4d5b-928d-60817deb9a8c', 1, '2026-07-31 00:06:22', true, '2026-07-30 23:06:02'),
(8, 'cb359508-9afd-4957-8f21-79f445530707', 1, '2026-07-31 21:30:04', true, '2026-07-31 19:51:30'),
(9, '367e4e2d-9684-40b6-ad2a-98f4de8ba736', 1, '2026-07-31 22:22:32', true, '2026-07-31 20:30:04'),
(10, 'ec1d19a9-99a5-4d43-88b3-b21380209557', 1, '2026-07-31 23:07:37', true, '2026-07-31 21:22:32'),
(11, 'f9af2aff-2c98-4a27-817d-bd0ba11e3302', 1, '2026-07-31 23:08:50', true, '2026-07-31 22:07:37'),
(12, '25e29858-e9c7-4883-b05c-d9f50814a176', 1, '2026-07-31 23:13:37', true, '2026-07-31 22:13:31'),
(13, '988f9275-8105-469f-9e83-d64af12f528f', 1, '2026-08-01 23:22:24', true, '2026-07-31 22:14:20'),
(14, '9623e079-43ab-419a-99f1-4433008574cd', 1, '2026-08-02 00:18:21', true, '2026-08-01 22:22:24'),
(15, 'cc299f94-4f19-4710-bfaf-b68d799966f8', 1, '2026-08-02 00:18:57', true, '2026-08-01 23:18:21'),
(16, '3cdbb01d-202a-4a0f-af1a-6ea67c37e42f', 1, '2026-08-02 01:43:40', true, '2026-08-02 00:26:27'),
(17, '4fcba671-f6d9-43db-b3dd-7f61e4b2f656', 1, '2026-08-02 01:44:02', true, '2026-08-02 00:43:40'),
(18, '7e280df5-ee17-4c8a-9afd-3988371fe3b5', 1, '2026-08-02 01:45:46', true, '2026-08-02 00:45:40'),
(19, 'cb56f98a-c753-4bc5-ae12-db21523c9aba', 1, '2026-08-02 23:25:37', true, '2026-08-02 22:10:40'),
(20, '7248ad9c-a917-49f2-8586-945cff93bcc2', 1, '2026-08-02 23:41:19', true, '2026-08-02 22:25:37'),
(21, '10da92b7-8658-4f1f-b31e-65472a1bbed6', 1, '2026-08-02 23:48:38', true, '2026-08-02 22:41:19'),
(22, '336c0abc-f596-44bb-a3c9-50ce000a306e', 1, '2026-08-03 00:06:19', true, '2026-08-02 22:48:38'),
(23, '7a73e79b-be9e-4ef5-89ef-cbbb7f5e1928', 1, '2026-08-03 00:24:31', true, '2026-08-02 23:06:19'),
(24, '62742d67-ba67-4d20-9ece-ce23e4b5fe97', 1, '2026-08-03 13:21:04', true, '2026-08-02 23:24:31'),
(25, 'eaab9d4b-5ee5-4ea2-8ec7-dbdc9874ede3', 1, '2026-08-03 15:10:44', true, '2026-08-03 12:21:04'),
(26, '5f74de0e-99d1-4229-bb7f-d6e657d7bece', 1, '2026-08-03 17:29:50', true, '2026-08-03 14:10:44'),
(27, '008a04a3-c225-4401-aad6-2336821d65be', 1, '2026-08-03 17:30:07', true, '2026-08-03 16:29:50'),
(28, 'cf184100-5b77-4f72-9039-a4a0f5046113', 1, '2026-08-03 17:30:13', true, '2026-08-03 16:30:07'),
(29, 'd9db3c4d-6f5a-4069-996b-d6dae8deb270', 1, '2026-08-03 18:39:23', true, '2026-08-03 17:17:45'),
(30, '1732bf80-0f97-4acd-a254-aa91c80c3f8e', 1, '2026-08-03 18:39:43', true, '2026-08-03 17:39:23'),
(31, '06f30161-7cf9-42d6-acc4-e7b00c5d476e', 1, '2026-08-03 22:01:27', true, '2026-08-03 21:01:18'),
(32, 'b373ac91-cf63-4895-ae81-e4a95b498a23', 1, '2026-08-03 22:01:34', true, '2026-08-03 21:01:30'),
(33, 'ad103b74-b3d3-4f68-894f-541602b70077', 1, '2026-08-03 22:07:09', true, '2026-08-03 21:06:56'),
(34, '65b757af-ff9b-4840-afd5-f7acf9aa6ea7', 1, '2026-08-03 22:24:28', true, '2026-08-03 21:12:35'),
(35, '9c6b6e64-31c3-449b-ac39-c8579d3407d8', 1, '2026-08-04 23:27:42', true, '2026-08-04 22:04:05'),
(36, '0a08bf57-4cce-4003-9f56-e75fcad4a843', 1, '2026-08-04 23:37:19', true, '2026-08-04 22:27:42'),
(37, 'c62f8491-0b3e-4ae6-bdd4-100e32aadd41', 1, '2026-08-05 13:53:00', true, '2026-08-04 22:37:19'),
(38, 'e044c448-7851-43cf-a0dd-32f689c03b01', 1, '2026-08-05 13:58:27', true, '2026-08-05 12:53:00'),
(39, 'ce29174e-714a-42cf-8027-030ce67f90dc', 1, '2026-08-05 14:06:36', true, '2026-08-05 12:58:27'),
(40, '32c92883-674a-4cfb-af44-1a0f5a7345d0', 1, '2026-08-05 14:45:50', true, '2026-08-05 13:06:36'),
(41, '50f1ff9c-6a78-4d80-9281-61e1919885f0', 5, '2026-08-05 14:43:59', true, '2026-08-05 13:19:03'),
(42, '5b74b9df-d8ab-4e34-80ad-8a8531d80bb1', 5, '2026-08-05 14:45:47', true, '2026-08-05 13:43:59'),
(43, '28c71087-b946-47d2-b65f-04dfda2fdc72', 1, '2026-08-05 15:11:01', true, '2026-08-05 13:45:50'),
(44, '7e899cc2-4a7f-470c-bcd9-546f423dc310', 1, '2026-08-05 15:12:39', true, '2026-08-05 14:11:01'),
(45, 'ab373e19-9e0a-43bf-99cf-e9be983c325e', 5, '2026-08-05 15:20:14', true, '2026-08-05 14:12:59'),
(46, 'a6f9e6a9-a91a-41d3-8bff-e8731d8f1e00', 1, '2026-08-05 16:17:49', true, '2026-08-05 14:20:19'),
(47, 'a19a9aac-fe85-4d1c-bc6f-ff9c192d67e9', 1, '2026-08-05 16:18:13', true, '2026-08-05 15:17:49'),
(48, 'cac2137b-a029-4fb2-b834-5ca51cb684f6', 5, '2026-08-05 16:22:50', true, '2026-08-05 15:18:25'),
(49, '67d11c29-8104-46c4-9702-cfd179066632', 1, '2026-08-05 16:24:58', true, '2026-08-05 15:23:02'),
(50, 'ff9c451d-57d2-45de-99a9-2b6ba2d837d4', 5, '2026-08-05 16:25:31', true, '2026-08-05 15:25:09'),
(51, '795059f5-918f-41c6-a350-b1e3b95dd61a', 1, '2026-08-05 16:26:38', true, '2026-08-05 15:25:33'),
(52, 'efa38f03-0c3b-494d-aaf5-47dc3b981b33', 5, '2026-08-05 16:43:38', true, '2026-08-05 15:26:55'),
(53, '7f0168cf-248a-4f94-aaaa-3f26e89d5a2c', 5, '2026-08-05 17:16:33', true, '2026-08-05 15:43:38'),
(54, '450dd58e-b5df-40e7-869a-72cdd2c306bb', 1, '2026-08-05 17:03:53', true, '2026-08-05 15:46:41'),
(55, '25b4bbe9-9c51-402a-b546-8b5c7d041861', 1, '2026-08-05 17:16:00', true, '2026-08-05 16:03:53'),
(56, '563ac3aa-9b80-41f3-b179-ad8d20657f83', 1, '2026-08-05 17:16:17', true, '2026-08-05 16:16:00'),
(57, 'c4e8b49d-b964-4d81-a3fd-da5ecb62b953', 5, '2026-08-05 17:24:34', true, '2026-08-05 16:16:33'),
(58, '4ebe9198-9e95-4721-a6c5-60885df3d0d3', 1, '2026-08-05 17:24:37', true, '2026-08-05 16:18:54'),
(59, '0afe6925-9e96-4f21-8880-60a4801523d6', 1, '2026-08-05 17:24:54', true, '2026-08-05 16:24:37'),
(60, '05a5af0e-39ff-40d8-8757-77b12fd19251', 5, '2026-08-05 17:33:24', true, '2026-08-05 16:25:34'),
(61, 'd2ca2bc9-a1e2-4779-a52e-2f25fbf2cd0b', 1, '2026-08-05 17:34:31', true, '2026-08-05 16:33:30'),
(62, 'a8c50b3c-1194-4d1b-9948-ba84015af120', 5, '2026-08-05 17:54:53', true, '2026-08-05 16:35:02'),
(63, '236dedc3-76ff-4e38-bcc2-7dc32b8b5e47', 1, '2026-08-05 17:46:39', true, '2026-08-05 16:45:54'),
(64, 'f9025225-20c3-4388-b1b4-0ff681d6049f', 1, '2026-08-05 17:49:54', true, '2026-08-05 16:48:04'),
(65, '10c6455a-fe52-45a4-a96b-34e99f91f045', 1, '2026-08-05 17:54:02', true, '2026-08-05 16:51:19'),
(66, '9e432817-02bc-4b22-a121-349c4216d6ad', 1, '2026-08-05 17:54:36', true, '2026-08-05 16:54:02'),
(67, 'ad50dba2-36de-4834-a12d-80ad321c678b', 5, '2026-08-05 18:29:14', true, '2026-08-05 16:54:53'),
(68, 'b4dfe832-572d-40cb-b161-7910d9fb86c8', 5, '2026-08-05 18:39:46', true, '2026-08-05 17:29:14'),
(69, 'afca1d79-5527-4dcf-9bb9-a56efd5b265a', 1, '2026-08-05 18:40:27', true, '2026-08-05 17:39:51'),
(70, 'f7f22652-524c-47cb-ae09-6befee2e0b92', 5, '2026-08-05 18:58:23', true, '2026-08-05 17:40:45'),
(71, '3ad2269a-7ea4-409a-a5fa-d949a18edded', 5, '2026-08-05 19:14:46', true, '2026-08-05 17:58:23'),
(72, '75eec49e-621c-44c1-a07a-d2633b6b0bd5', 5, '2026-08-05 19:47:12', true, '2026-08-05 18:14:46'),
(73, '0cbf2193-9a2a-4ac8-b61a-aaeeb95614b5', 5, '2026-08-05 19:53:25', true, '2026-08-05 18:47:12'),
(74, '4106ebc4-2f89-4689-a95a-99e2b51e5d0e', 5, '2026-08-05 19:54:41', true, '2026-08-05 18:54:01'),
(75, 'e25cf5e3-f816-455b-bcb4-893fd44ffc13', 5, '2026-08-05 20:56:27', true, '2026-08-05 19:52:27'),
(76, 'c8393819-91b3-46c5-8d9f-ca21b0a2d316', 5, '2026-08-06 21:29:51', true, '2026-08-05 19:58:52'),
(77, 'b486f67e-3d5a-4df1-8a64-82ea6355fa18', 1, '2026-08-06 17:57:43', true, '2026-08-06 16:53:54'),
(78, 'd47a1b36-85a2-4cf8-ac5e-9eb58b348de3', 2, '2026-08-09 23:20:20', true, '2026-08-06 16:57:50'),
(79, '06934d30-1c02-4917-8958-3cd1fe24eb1b', 1, '2026-08-06 18:15:06', true, '2026-08-06 17:12:34'),
(80, '0fe580cd-9a7d-48a6-88f5-ba8e9f08a3c1', 1, '2026-08-06 22:38:13', true, '2026-08-06 17:15:06'),
(81, '6e38cf81-bde4-4faa-952e-fd30bf8ba2e8', 5, '2026-08-06 21:46:54', true, '2026-08-06 20:29:51'),
(82, 'd8a66248-e09f-412b-a94e-190422a5b436', 5, '2026-08-06 22:04:47', true, '2026-08-06 20:46:54'),
(83, '5f2c11e4-528b-41af-8450-bb12c9982bcd', 5, '2026-08-06 22:24:22', true, '2026-08-06 21:04:47'),
(84, '804aedb1-18f9-4111-a2ac-db7b36db4871', 5, '2026-08-06 22:38:08', true, '2026-08-06 21:24:22'),
(85, 'edbae8be-3fad-4e21-b8bb-7785c631543c', 1, '2026-08-06 22:54:27', true, '2026-08-06 21:38:13'),
(86, 'fbe320a8-a9c2-499a-ae53-5cdc2d6f3ce9', 1, '2026-08-06 23:14:27', true, '2026-08-06 21:54:27'),
(87, '754a9c16-771e-40b6-8547-6e011255b899', 1, '2026-08-06 23:31:10', true, '2026-08-06 22:14:27'),
(88, 'c6da17a8-3d76-4ad4-b112-638cafe8c837', 1, '2026-08-07 00:04:47', true, '2026-08-06 22:31:10'),
(89, '82f09c75-37fb-4cfb-b197-192db6f190fe', 1, '2026-08-07 00:10:18', true, '2026-08-06 23:04:47'),
(90, '2ba02218-cb83-4b0c-a61f-1bd27bae7095', 1, '2026-08-07 00:12:09', true, '2026-08-06 23:10:39'),
(91, 'e3a8105b-3a96-420c-8e8e-3090bb54bfcf', 5, '2026-08-07 00:13:12', true, '2026-08-06 23:12:18'),
(92, '56e71d81-b06c-44e5-a496-f009462e0434', 1, '2026-08-07 21:31:32', true, '2026-08-07 18:09:50'),
(93, '6df9ab9f-62ad-4472-b9c2-ac479918f762', 1, '2026-08-07 21:39:57', true, '2026-08-07 20:31:32'),
(94, '6c638da1-e830-4a1b-ad04-9517be9bf330', 1, '2026-08-08 22:41:35', true, '2026-08-07 21:02:28'),
(95, '01fddae8-bb17-442a-a462-4e3e648dbc82', 1, '2026-08-08 23:44:29', true, '2026-08-08 21:41:35'),
(96, '7e7bc05b-c687-47a6-ad4a-e1c0c0a15a50', 1, '2026-08-09 17:12:59', true, '2026-08-08 22:44:29'),
(97, '8212093e-a0ba-48bf-87f7-78b355e46ddc', 1, '2026-08-09 17:39:23', true, '2026-08-09 16:12:59'),
(98, 'c58602de-82a3-442f-9a2b-9e36161108ba', 1, '2026-08-09 18:14:51', true, '2026-08-09 16:39:23'),
(99, '5df62160-0daa-4f55-b03a-7e2d3749913a', 1, '2026-08-09 18:34:06', true, '2026-08-09 17:14:51'),
(100, 'c937045b-3288-469f-8cc3-55c0880e4670', 1, '2026-08-09 18:53:05', true, '2026-08-09 17:34:06'),
(101, '0305c4c4-9abe-40be-a945-5712f38ffb1f', 1, '2026-08-09 21:48:52', true, '2026-08-09 17:53:05'),
(102, '7c277a98-1494-4bee-8915-7f8c6286387c', 1, '2026-08-09 21:49:29', true, '2026-08-09 20:48:52'),
(103, '22afbd8e-ffc1-4574-ace2-0b6ed496b088', 1, '2026-08-09 22:24:35', true, '2026-08-09 21:24:16'),
(104, '29ed6b70-84b0-4366-a771-3774be8584e3', 1, '2026-08-09 22:24:48', true, '2026-08-09 21:24:35'),
(105, 'dda748ca-e4f4-4edb-a545-3df582a76acb', 1, '2026-08-09 22:25:38', true, '2026-08-09 21:24:48'),
(106, 'b88c4de2-13cd-4ece-8fba-ba40cb8335ac', 1, '2026-08-09 22:25:59', true, '2026-08-09 21:25:50'),
(107, 'e6e0b413-b987-4573-9352-89c3e27ecefc', 1, '2026-08-09 23:05:49', true, '2026-08-09 21:26:06'),
(108, 'a6b258f6-772a-454d-890b-a7a4184733a9', 1, '2026-08-09 23:05:56', true, '2026-08-09 22:05:49'),
(109, '06b8a674-f901-4b16-affc-6d0f1607d3b8', 1, '2026-08-09 23:09:21', true, '2026-08-09 22:09:06'),
(110, 'd2f7afac-f6d2-47d5-9947-46c1c41fdc27', 1, '2026-08-09 23:19:25', true, '2026-08-09 22:19:15'),
(111, 'af8cc46e-f25a-4884-8f69-857ac64b40c9', 2, '2026-08-09 23:20:25', true, '2026-08-09 22:20:20'),
(112, '98babeb7-8bca-4db7-9887-b1b16e92eb96', 1, '2026-08-10 00:08:04', true, '2026-08-09 22:42:25'),
(113, '22f99b6b-bedb-4349-87bd-ac7fe9b8d0b6', 1, '2026-08-10 00:16:41', true, '2026-08-09 23:08:04'),
(114, '02c567a6-e3cd-4379-b78e-863c96621f5b', 1, '2026-08-10 00:30:27', true, '2026-08-09 23:28:52'),
(115, '34956fac-c294-4e19-9e25-869ef5830421', 1, '2026-08-16 23:32:00', false, '2026-08-09 23:32:00');

-- ─────────────────────────────────
-- Table: stock_entrepots
-- ─────────────────────────────────
DROP TABLE IF EXISTS `stock_entrepots`;
CREATE TABLE `stock_entrepots` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `produit_id` bigint(20) NOT NULL,
  `entrepot_id` bigint(20) NOT NULL,
  `quantite` int(11) DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_produit_entrepot` (`produit_id`,`entrepot_id`),
  KEY `fk_se_entrepot` (`entrepot_id`),
  CONSTRAINT `fk_se_entrepot` FOREIGN KEY (`entrepot_id`) REFERENCES `entrepots` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_se_produit` FOREIGN KEY (`produit_id`) REFERENCES `produits` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `stock_entrepots` VALUES
(1, 1, 1, 10),
(2, 2, 1, 6),
(3, 3, 1, 20),
(4, 4, 1, 35),
(5, 5, 1, 4),
(6, 6, 1, 3),
(7, 7, 1, 8),
(8, 8, 1, 15),
(9, 9, 1, 10),
(10, 10, 1, 14),
(11, 11, 1, 6),
(12, 12, 1, 15),
(13, 13, 1, 60),
(14, 14, 1, 25),
(15, 15, 1, 3),
(16, 3, 2, 5),
(17, 4, 2, 15),
(18, 12, 2, 15),
(19, 13, 2, 40),
(20, 14, 2, 15);

-- ─────────────────────────────────
-- Table: theme_config
-- ─────────────────────────────────
DROP TABLE IF EXISTS `theme_config`;
CREATE TABLE `theme_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `utilisateur_id` bigint(20) NOT NULL,
  `theme_preset` varchar(20) DEFAULT 'light',
  `sidebar_position` varchar(10) DEFAULT 'left',
  `animations_enabled` tinyint(1) DEFAULT 1,
  `logout_position` varchar(20) DEFAULT 'both',
  `date_modification` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `accent_color` varchar(20) DEFAULT '#ea580c',
  PRIMARY KEY (`id`),
  UNIQUE KEY `utilisateur_id` (`utilisateur_id`),
  CONSTRAINT `fk_tc_utilisateur` FOREIGN KEY (`utilisateur_id`) REFERENCES `utilisateurs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ─────────────────────────────────
-- Table: utilisateurs
-- ─────────────────────────────────
DROP TABLE IF EXISTS `utilisateurs`;
CREATE TABLE `utilisateurs` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `nom_utilisateur` varchar(50) NOT NULL,
  `email` varchar(100) NOT NULL,
  `mot_de_passe` varchar(255) NOT NULL,
  `prenom` varchar(50) DEFAULT NULL,
  `nom` varchar(50) DEFAULT NULL,
  `actif` tinyint(1) DEFAULT 1,
  `role` varchar(20) NOT NULL DEFAULT 'USER',
  `langue_preferee` varchar(5) DEFAULT 'fr',
  `statut_compte` varchar(20) DEFAULT 'ACTIF',
  `mode_trial` tinyint(1) DEFAULT 0,
  `nb_utilisations` int(11) DEFAULT 0,
  `nb_utilisations_max` int(11) DEFAULT 30,
  `token_session` varchar(512) DEFAULT NULL,
  `doit_changer_mot_de_passe` tinyint(1) DEFAULT 0,
  `telephone` varchar(20) DEFAULT NULL,
  `societe` varchar(200) DEFAULT NULL,
  `adresse` varchar(500) DEFAULT NULL,
  `kyc_soumis` tinyint(1) DEFAULT 0,
  `trial_expires_at` datetime DEFAULT NULL,
  `token_recuperation` varchar(255) DEFAULT NULL,
  `expiration_token_recuperation` datetime DEFAULT NULL,
  `date_creation` timestamp NOT NULL DEFAULT current_timestamp(),
  `date_modification` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `entreprise_id` bigint(20) DEFAULT NULL,
  `entreprise_schema` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `nom_utilisateur` (`nom_utilisateur`),
  UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=251 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `utilisateurs` VALUES
(1, 'admin', 'admin@benjeddou.com', '$2a$12$3nmkjE/PzSEcKannuvWSXOMqb58.8Emrwivph8kUYTYziYSV1MLOS', 'Admin', 'BenJeddou', true, 'ADMIN', 'fr', 'ACTIF', false, 0, 30, 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NjMxODMyMCwiZXhwIjoxNzg2MzE5MjIwfQ.CovRzPHnhyWNdjRZ_s_5bmIwr0oxoy5R-JnvVU5pkVI', false, NULL, NULL, NULL, false, NULL, NULL, NULL, '2026-07-30 21:09:38', '2026-08-09 23:32:00', NULL, NULL),
(2, 'commercial', 'commercial@benjeddou.com', '$2a$12$3nmkjE/PzSEcKannuvWSXOMqb58.8Emrwivph8kUYTYziYSV1MLOS', 'Mehdi', 'Trabelsi', true, 'COMMERCIAL', 'fr', 'ACTIF', false, 0, 30, NULL, false, NULL, NULL, NULL, false, NULL, NULL, NULL, '2026-07-30 21:09:38', '2026-08-09 22:20:25', NULL, NULL),
(3, 'comptable', 'comptable@benjeddou.com', '$2a$12$3nmkjE/PzSEcKannuvWSXOMqb58.8Emrwivph8kUYTYziYSV1MLOS', 'Sarra', 'Mansouri', true, 'COMPTABLE', 'fr', 'ACTIF', false, 0, 30, 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjb21wdGFibGUiLCJpYXQiOjE3ODU0NTA4MTUsImV4cCI6MTc4NTQ1MTcxNX0.A_TLB_FkvZdrfiMUXsLoNt0WJdWhpMoa2pENolw6cmk', false, NULL, NULL, NULL, false, NULL, NULL, NULL, '2026-07-30 21:09:38', '2026-08-02 01:17:08', NULL, NULL),
(4, 'stock', 'stock@benjeddou.com', '$2a$12$3nmkjE/PzSEcKannuvWSXOMqb58.8Emrwivph8kUYTYziYSV1MLOS', 'Youssef', 'Chaabane', true, 'STOCK', 'fr', 'ACTIF', false, 0, 30, NULL, false, NULL, NULL, NULL, false, NULL, NULL, NULL, '2026-07-30 21:09:38', '2026-08-02 01:17:08', NULL, NULL),
(5, 'client_demo', 'client@benjeddou.com', '$2a$12$FtK2iscA.QwhKgszF0RLHeKnkf0PvEhbBK4KaotIlOOLybOcPAkKa', 'Karim', 'Belhadj', true, 'CLIENT', 'fr', 'ACTIF', false, 0, 30, NULL, false, '52026019', NULL, NULL, false, NULL, NULL, NULL, '2026-07-30 21:09:38', '2026-08-06 23:13:12', NULL, NULL);

-- ─────────────────────────────────
-- Table: versions_documents
-- ─────────────────────────────────
DROP TABLE IF EXISTS `versions_documents`;
CREATE TABLE `versions_documents` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `document_id` bigint(20) NOT NULL,
  `version` int(11) NOT NULL DEFAULT 1,
  `commentaire` varchar(500) DEFAULT NULL,
  `date_creation` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `fk_vd_document` (`document_id`),
  CONSTRAINT `fk_vd_document` FOREIGN KEY (`document_id`) REFERENCES `documents_generes` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


SET FOREIGN_KEY_CHECKS=1;
-- FIN EXPORT
