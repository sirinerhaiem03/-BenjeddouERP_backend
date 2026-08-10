package com.benjeddou.erp.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

/**
 * Entreprise — Entité stockée dans la BASE MASTER (benjeddou_erp).
 *
 * Chaque entreprise cliente de la plateforme SaaS dispose :
 * - d'un identifiant unique (slug)
 * - d'une base MySQL dédiée et totalement isolée (schemaName)
 * - d'un administrateur principal (adminId → Utilisateur)
 *
 * Architecture : SaaS Multi-Tenant — Schema-per-Tenant
 * Garantit l'isolation totale des données entre entreprises.
 */
@Entity
@Table(name = "entreprises")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Entreprise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nom commercial de l'entreprise */
    @Column(name = "nom", length = 200, nullable = false)
    private String nom;

    /**
     * Identifiant unique du schéma MySQL dédié à cette entreprise.
     * Format : erp_ent_00001, erp_ent_00002, ...
     * C'est le nom de la base de données MySQL physique de l'entreprise.
     */
    @Column(name = "schema_name", length = 100, unique = true, nullable = false)
    private String schemaName;

    /**
     * URL JDBC complète vers la base de l'entreprise.
     * Ex: jdbc:mysql://localhost:3306/erp_ent_00001?useSSL=false&serverTimezone=UTC
     */
    @Column(name = "db_url", length = 500)
    private String dbUrl;

    /** Utilisateur MySQL (même utilisateur root pour toutes les bases en dev) */
    @Column(name = "db_username", length = 100)
    private String dbUsername;

    /** Mot de passe MySQL — en production : chiffrer avec AES ou utiliser AWS Secrets Manager */
    @Column(name = "db_password", length = 255)
    private String dbPassword;

    /** ID de l'utilisateur administrateur principal de cette entreprise */
    @Column(name = "admin_id")
    private Long adminId;

    /** Email de contact principal de l'entreprise */
    @Column(name = "email_contact", length = 200)
    private String emailContact;

    /** Statut de l'entreprise sur la plateforme */
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", length = 20)
    @Builder.Default
    private StatutEntreprise statut = StatutEntreprise.ACTIVE;

    @CreationTimestamp
    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;

    /** Enum des statuts possibles d'une entreprise */
    public enum StatutEntreprise {
        ACTIVE,      // Entreprise opérationnelle
        SUSPENDUE,   // Accès suspendu (abonnement expiré)
        SUPPRIMEE    // Marquée comme supprimée (soft delete)
    }
}
