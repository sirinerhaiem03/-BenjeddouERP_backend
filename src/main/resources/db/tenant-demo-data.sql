-- ══════════════════════════════════════════════════════════════════════════════
-- tenant-demo-data.sql — Données démo réalistes pour erp_ent_00000
-- Exécuté par DatabaseInitializer uniquement si les tables sont vides
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────
-- CLIENTS (10 entreprises tunisiennes réalistes)
-- ─────────────────────────────────────────────────────────────
INSERT IGNORE INTO clients (nom, email, telephone, adresse, matricule_fiscale, code_client, type_client, plafond_credit) VALUES
('Société Industrielle Sahem', 'contact@sahem.tn', '+216 71 234 567', 'Rue de l''Industrie, Zone Industrielle La Charguia, Tunis', '0123456A/P/M/000', 'CLI-001', 'ENTREPRISE', 50000.000),
('Groupe Alimentaire Tunisien', 'gat@groupealimenaire.tn', '+216 73 567 890', 'Avenue Habib Bourguiba, Sfax 3000', '9876543B/P/M/000', 'CLI-002', 'ENTREPRISE', 80000.000),
('Chantiers Navals Ben Arous', 'info@cnba.tn', '+216 71 440 220', 'Zone Portuaire, Ben Arous 2013', '5678901C/P/M/000', 'CLI-003', 'ENTREPRISE', 120000.000),
('Cabinet Juridique Maître Tlili', 'cabinet@tlili-avocats.tn', '+216 71 889 100', '15 Rue de Marseille, Tunis 1000', '1122334D/P/A/000', 'CLI-004', 'PARTICULIER', 15000.000),
('Hôtel El Mouradi Hammamet', 'reservation@elmouradi.tn', '+216 72 280 700', 'Route Touristique Hammamet, Nabeul 8050', '4455667E/P/M/000', 'CLI-005', 'ENTREPRISE', 200000.000),
('Pharmacie Centrale Sousse', 'pharmacie.centrale@gmail.com', '+216 73 123 456', 'Avenue de la République, Sousse 4000', '7788990F/P/A/000', 'CLI-006', 'PARTICULIER', 10000.000),
('Ferme Agricole Ben Salah', 'bensal.agri@gmail.com', '+216 74 567 123', 'Route de Kairouan, Enfidha 4030', '3344556G/P/A/000', 'CLI-007', 'PARTICULIER', 25000.000),
('Imprimerie Moderne Ariana', 'imprimerie.moderne@tn.net', '+216 71 760 344', 'Zone Industrielle Borj Louzir, Ariana 2037', '6677889H/P/M/000', 'CLI-008', 'ENTREPRISE', 40000.000),
('École Privée Avenir Brillant', 'direction@avenir-brillant.tn', '+216 71 456 789', 'Rue des Jasmins, La Marsa 2070', '2233445I/P/M/000', 'CLI-009', 'ENTREPRISE', 30000.000),
('Clinique Santé Plus Bizerte', 'clinique@santeplus.tn', '+216 72 431 200', 'Avenue Farhat Hached, Bizerte 7000', '8899001J/P/M/000', 'CLI-010', 'ENTREPRISE', 60000.000);

-- ─────────────────────────────────────────────────────────────
-- FOURNISSEURS (8 fournisseurs réalistes)
-- ─────────────────────────────────────────────────────────────
INSERT IGNORE INTO fournisseurs (nom, email, telephone, adresse, matricule_fiscale, code_fournisseur, delai_paiement_jours) VALUES
('SOTACIB — Ciments de Tunisie', 'commercial@sotacib.tn', '+216 71 270 600', 'Route de Béja, Jendouba 8100', '1111111A/P/M/000', 'FRN-001', 30),
('Tunisair Catering Services', 'fournisseurs@tunisair-catering.tn', '+216 71 840 000', 'Aéroport Tunis-Carthage, Tunis 1080', '2222222B/P/M/000', 'FRN-002', 45),
('STEG — Société Tunisienne Électricité', 'facturation@steg.com.tn', '+216 71 341 311', '38 Rue Kemal Atatürk, Tunis 1002', '3333333C/P/M/000', 'FRN-003', 15),
('Léoni Wiring Systems Tunisia', 'supply@leoni.tn', '+216 78 400 000', 'Zone Industrielle Mégrine, Ben Arous 2033', '4444444D/P/M/000', 'FRN-004', 60),
('Office National de l''Artisanat', 'ona.fournisseur@artisanat.tn', '+216 71 350 966', 'Avenue de la Liberté, Tunis 1002', '5555555E/P/M/000', 'FRN-005', 30),
('Poulina Group Holding', 'achats@poulina.tn', '+216 71 788 850', 'Les Berges du Lac, Tunis 1053', '6666666F/P/M/000', 'FRN-006', 30),
('SPDI — Société de Production et Distribution', 'commercial@spdi.tn', '+216 73 450 200', 'Route de Sakiet Ezzit, Sfax 3052', '7777777G/P/M/000', 'FRN-007', 45),
('Maghreb Steel', 'ventes@maghrebsteel.tn', '+216 72 648 100', 'Zone Industrielle Grombalia, Nabeul 8030', '8888888H/P/M/000', 'FRN-008', 30);

-- ─────────────────────────────────────────────────────────────
-- PRODUITS (20 articles réalistes)
-- ─────────────────────────────────────────────────────────────
INSERT IGNORE INTO produits (reference, designation, description, prix_achat, prix_vente, taux_tva, categorie, unite, stock_actuel, stock_minimum) VALUES
('PRD-001', 'Ordinateur Portable HP ProBook 450', 'HP ProBook 450 G9 — Core i5, 8 Go RAM, SSD 512 Go, 15.6 pouces', 1200.000, 1850.000, 19.00, 'INFORMATIQUE', 'UNITE', 15, 3),
('PRD-002', 'Imprimante Laser HP LaserJet Pro', 'HP LaserJet Pro M404n — Monochrome, Réseau, 38 ppm', 650.000, 980.000, 19.00, 'INFORMATIQUE', 'UNITE', 8, 2),
('PRD-003', 'Switch Réseau Cisco 24 ports', 'Cisco Catalyst 1000 — 24 ports Gigabit, gestion VLAN', 850.000, 1250.000, 19.00, 'RESEAU', 'UNITE', 5, 1),
('PRD-004', 'Ramette Papier A4 80g (boîte 5 ramettes)', 'Papier Premium A4 80g/m² — 500 feuilles/ramette', 18.000, 28.000, 7.00, 'FOURNITURES', 'BOITE', 120, 20),
('PRD-005', 'Chaise de Bureau Ergonomique', 'Chaise ergonomique réglable hauteur et accoudoirs, tissu respirant', 280.000, 420.000, 19.00, 'MOBILIER', 'UNITE', 20, 5),
('PRD-006', 'Bureau Direction 180x90 cm', 'Bureau direction en bois MDF avec caisson, coloris chêne', 650.000, 950.000, 19.00, 'MOBILIER', 'UNITE', 10, 2),
('PRD-007', 'Logiciel Antivirus Kaspersky (1 an)', 'Kaspersky Total Security — 3 postes, 1 an, licence numérique', 90.000, 145.000, 19.00, 'LOGICIELS', 'LICENCE', 50, 10),
('PRD-008', 'Cartouche Toner HP 26A', 'HP CF226A — Toner Noir compatible LaserJet Pro M402/M426', 45.000, 72.000, 19.00, 'CONSOMMABLES', 'UNITE', 60, 15),
('PRD-009', 'Huile de Moteur Total 5W30 (5L)', 'Total Quartz 9000 5W30 — 5 litres, moteur essence/diesel', 38.000, 58.000, 19.00, 'AUTOMOBILE', 'BIDON', 40, 10),
('PRD-010', 'Ciment CEM II 42.5 (sac 50kg)', 'Ciment Portland CEM II 42.5 R — SOTACIB', 8.000, 12.500, 19.00, 'CONSTRUCTION', 'SAC', 500, 100),
('PRD-011', 'Câble Électrique 2.5mm² (100m)', 'Câble rigide U1000R2V 2.5mm², bobine 100m, rouge', 85.000, 130.000, 19.00, 'ELECTRICITE', 'BOBINE', 30, 5),
('PRD-012', 'Disjoncteur Schneider 16A', 'Schneider Electric iC60N — Disjoncteur courbe C 16A/1P', 12.000, 18.500, 19.00, 'ELECTRICITE', 'UNITE', 200, 50),
('PRD-013', 'Peinture Intérieure Blanche 10L', 'Peinture acrylique vinylique blanche, lessivable, mat', 35.000, 52.000, 19.00, 'BATIMENT', 'BIDON', 80, 20),
('PRD-014', 'Tablet Samsung Galaxy Tab A8', 'Samsung Galaxy Tab A8 — 10.5 pouces, 64 Go, WiFi+4G', 420.000, 650.000, 19.00, 'INFORMATIQUE', 'UNITE', 12, 3),
('PRD-015', 'Stylos Bille BIC (lot 50)', 'BIC Cristal — Stylos bleus, pointe 0.4mm, lot de 50', 8.500, 13.000, 7.00, 'FOURNITURES', 'LOT', 100, 20),
('PRD-016', 'Climatiseur Split 12000 BTU', 'Climatiseur réversible Inverter 12000 BTU — Midea', 950.000, 1450.000, 19.00, 'CLIMATISATION', 'UNITE', 15, 3),
('PRD-017', 'Onduleur APC 1500VA', 'APC Back-UPS 1500VA — Protection réseau, 8 prises', 380.000, 580.000, 19.00, 'INFORMATIQUE', 'UNITE', 8, 2),
('PRD-018', 'Serveur NAS Synology DS923+', 'Synology DS923+ — NAS 4 baies, 4 Go RAM, processeur AMD', 2200.000, 3200.000, 19.00, 'INFORMATIQUE', 'UNITE', 3, 1),
('PRD-019', 'Détecteur de Fumée (lot 10)', 'Détecteur ionique, certification NF, pile 9V incluse', 65.000, 98.000, 19.00, 'SECURITE', 'LOT', 40, 10),
('PRD-020', 'Caméra IP Hikvision 4MP', 'Hikvision DS-2CD2143G2-I — 4MP, IR 40m, résolution 2K', 180.000, 270.000, 19.00, 'SECURITE', 'UNITE', 25, 5);

-- ─────────────────────────────────────────────────────────────
-- ENTREPÔT PRINCIPAL
-- ─────────────────────────────────────────────────────────────
INSERT IGNORE INTO entrepots (id, nom, adresse, responsable) VALUES
(1, 'Entrepôt Principal Tunis', 'Zone Industrielle La Charguia, Tunis 2035', 'Mohamed Jebali');

-- PÉRIODES DE TAUX : Gérées dans la base MASTER (benjeddou_erp)
-- Voir master-schema.sql — Les taux BCT sont insérés dans la base centrale
-- et partagés avec tous les tenants via MasterTenantContext.


-- ─────────────────────────────────────────────────────────────
-- DEVIS EXEMPLES (5 devis)
-- ─────────────────────────────────────────────────────────────
INSERT IGNORE INTO devis (numero_devis, date_devis, date_validite, statut, montant_ht, montant_tva, montant_total, client_id) VALUES
('DEV-2026-0001', '2026-01-10', '2026-02-10', 'ACCEPTE',   5700.000, 1083.000,  6783.000, 1),
('DEV-2026-0002', '2026-02-15', '2026-03-15', 'EN_ATTENTE', 2800.000,  532.000,  3332.000, 3),
('DEV-2026-0003', '2026-03-05', '2026-04-05', 'ACCEPTE',   8500.000, 1615.000, 10115.000, 5),
('DEV-2026-0004', '2026-04-20', '2026-05-20', 'REFUSE',    1200.000,  228.000,  1428.000, 8),
('DEV-2026-0005', '2026-06-01', '2026-07-01', 'BROUILLON', 3600.000,  684.000,  4284.000, 2);

-- ─────────────────────────────────────────────────────────────
-- COMMANDES VENTES (5 commandes)
-- ─────────────────────────────────────────────────────────────
INSERT IGNORE INTO commandes (numero_commande, statut, montant_ht, montant_tva, montant_total, client_id, devis_id) VALUES
('CMD-2026-0001', 'LIVREE',    5700.000, 1083.000,  6783.000, 1, 1),
('CMD-2026-0002', 'EN_COURS',  8500.000, 1615.000, 10115.000, 5, 3),
('CMD-2026-0003', 'EN_ATTENTE', 960.000,  182.400,  1142.400, 2, NULL),
('CMD-2026-0004', 'ANNULEE',   1800.000,  342.000,  2142.000, 7, NULL),
('CMD-2026-0005', 'LIVREE',    4200.000,  798.000,  4998.000, 4, NULL);

-- ─────────────────────────────────────────────────────────────
-- FACTURES (5 factures)
-- ─────────────────────────────────────────────────────────────
INSERT IGNORE INTO factures (numero_facture, date_echeance, statut, montant_ht, montant_tva, montant_total, client_id, commande_id) VALUES
('FAC-2026-0001', '2026-02-28', 'PAYEE',      5700.000, 1083.000,  6783.000, 1, 1),
('FAC-2026-0002', '2026-05-31', 'EN_ATTENTE', 8500.000, 1615.000, 10115.000, 5, 2),
('FAC-2026-0003', '2026-06-15', 'EN_ATTENTE',  960.000,  182.400,  1142.400, 2, 3),
('FAC-2026-0004', '2026-04-30', 'PAYEE',      4200.000,  798.000,  4998.000, 4, 5),
('FAC-2026-0005', '2026-07-31', 'RETARD',     3200.000,  608.000,  3808.000, 6, NULL);

-- ─────────────────────────────────────────────────────────────
-- COMMANDES ACHAT (3 commandes fournisseurs)
-- ─────────────────────────────────────────────────────────────
INSERT IGNORE INTO commandes_achat (numero_commande, statut, montant_ht, montant_tva, montant_total, fournisseur_id, date_livraison_prev) VALUES
('CA-2026-0001', 'RECUE',      15000.000, 2850.000, 17850.000, 1, '2026-03-15'),
('CA-2026-0002', 'EN_COURS',    8500.000, 1615.000, 10115.000, 4, '2026-08-20'),
('CA-2026-0003', 'EN_ATTENTE',  3200.000,  608.000,  3808.000, 6, '2026-09-10');

-- ─────────────────────────────────────────────────────────────
-- ÉCRITURES COMPTABLES (10 écritures)
-- ─────────────────────────────────────────────────────────────
INSERT IGNORE INTO ecritures_comptables (numero_ecriture, date_ecriture, type_ecriture, libelle, montant_debit, montant_credit, compte_debit, compte_credit, reference_doc) VALUES
('EC-2026-001', '2026-01-15', 'VENTE',    'Facture FAC-2026-0001 — Société Industrielle Sahem',    6783.000, 6783.000, '4111', '7000', 'FAC-2026-0001'),
('EC-2026-002', '2026-01-31', 'ACHAT',    'Achat matériel informatique SOTACIB',                  17850.000,17850.000, '6010', '4011', 'CA-2026-0001'),
('EC-2026-003', '2026-02-28', 'PAIEMENT', 'Règlement client Sahem — chèque 123456',               6783.000, 6783.000, '5121', '4111', 'FAC-2026-0001'),
('EC-2026-004', '2026-03-15', 'SALAIRE',  'Salaires Mars 2026 — personnel',                       12500.000,12500.000, '6411', '4211', 'SAL-2026-03'),
('EC-2026-005', '2026-03-31', 'TVA',      'Déclaration TVA Mars 2026',                             4350.000, 4350.000, '4456', '5121', 'TVA-2026-03'),
('EC-2026-006', '2026-04-15', 'VENTE',    'Facture FAC-2026-0004 — Cabinet Juridique Tlili',       4998.000, 4998.000, '4111', '7000', 'FAC-2026-0004'),
('EC-2026-007', '2026-04-30', 'PAIEMENT', 'Règlement client Tlili — virement bancaire',            4998.000, 4998.000, '5121', '4111', 'FAC-2026-0004'),
('EC-2026-008', '2026-05-31', 'CHARGE',   'Loyer locaux — Mai 2026',                               3500.000, 3500.000, '6132', '5121', 'LOY-2026-05'),
('EC-2026-009', '2026-06-15', 'VENTE',    'Facture FAC-2026-0002 — Hôtel El Mouradi',             10115.000,10115.000, '4111', '7000', 'FAC-2026-0002'),
('EC-2026-010', '2026-06-30', 'STOCK',    'Entrée en stock — réception CA-2026-0001',                   0.000,    0.000, '3110', '6010', 'CA-2026-0001');
