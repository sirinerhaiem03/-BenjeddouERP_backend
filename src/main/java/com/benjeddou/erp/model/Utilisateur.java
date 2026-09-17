package com.benjeddou.erp.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "utilisateurs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nom_utilisateur", length = 50, unique = true, nullable = false)
    private String nomUtilisateur;

    @Column(length = 100, unique = true, nullable = false)
    private String email;

    @Column(name = "mot_de_passe", length = 255, nullable = false)
    private String motDePasse;

    @Column(length = 50)
    private String prenom;

    @Column(length = 50)
    private String nom;

    @Builder.Default
    private Boolean actif = true;

    @Column(name = "langue_preferee", length = 5)
    @Builder.Default
    private String languePreferee = "fr";

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    @Builder.Default
    private Role role = Role.USER;

    @Column(name = "token_recuperation", length = 255)
    private String tokenRecuperation;

    @Column(name = "expiration_token_recuperation")
    private LocalDateTime expirationTokenRecuperation;

    @CreationTimestamp
    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;

    @UpdateTimestamp
    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    // ── Nouveaux champs ──────────────────────────────────────────

    /** Statut du cycle de vie du compte */
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_compte", length = 20)
    @Builder.Default
    private StatutCompte statutCompte = StatutCompte.ACTIF;

    /** Mode essai : limité à nbUtilisationsMax connexions */
    @Column(name = "mode_trial")
    @Builder.Default
    private Boolean modeTrial = false;

    /** Nombre de connexions effectuées en mode trial */
    @Column(name = "nb_utilisations")
    @Builder.Default
    private Integer nbUtilisations = 0;

    /** Nombre max d'utilisations en mode trial (défaut 30) */
    @Column(name = "nb_utilisations_max")
    @Builder.Default
    private Integer nbUtilisationsMax = 30;

    /** JWT actif pour session unique : toute nouvelle connexion invalide l'ancienne */
    @Column(name = "token_session", length = 512)
    private String tokenSession;

    /** Si true, l'utilisateur doit changer son mot de passe à la prochaine connexion */
    @Column(name = "doit_changer_mot_de_passe")
    @Builder.Default
    private Boolean doitChangerMotDePasse = false;

    // ── Champs spécifiques Client ─────────────────────────────────

    /** Numéro de téléphone (utilisé pour OTP) */
    @Column(name = "telephone", length = 20)
    private String telephone;

    /** Nom de la société (pour les clients entreprise) */
    @Column(name = "societe", length = 200)
    private String societe;

    /** Adresse complète */
    @Column(name = "adresse", length = 500)
    private String adresse;

    /** Si true, le client a soumis ses documents KYC */
    @Column(name = "kyc_soumis")
    @Builder.Default
    private Boolean kycSoumis = false;

    // ── J3 : Période d'essai basée sur la date ────────────────────

    /** Date d'expiration de la période d'essai (30 jours après création) */
    @Column(name = "trial_expires_at")
    private LocalDateTime trialExpiresAt;

    // ── Multi-Tenant : Isolation par entreprise ───────────────────────────────

    /**
     * Identifiant de l'entreprise (tenant) à laquelle cet utilisateur appartient.
     * Correspond à l'ID dans la table `entreprises` de la base master.
     * Null pour les SuperAdmin (pas de base tenant dédiée).
     *
     * Permet de retrouver le schéma MySQL de l'utilisateur via EntrepriseRepository.
     */
    @Column(name = "entreprise_id")
    private Long entrepriseId;

    /**
     * Nom du schéma MySQL de l'entreprise (cache local pour éviter une requête supplémentaire).
     * Ex: "erp_ent_00001"
     * Null pour les SuperAdmin.
     */
    @Column(name = "entreprise_schema", length = 100)
    private String entrepriseSchema;
}
