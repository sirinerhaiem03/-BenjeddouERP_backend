package com.benjeddou.erp.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ConnexionLog — Enregistrement complet de chaque tentative de connexion.
 * Stocke les informations appareil, IP, localisation et statut de session.
 */
@Entity
@Table(name = "connexions_log", indexes = {
    @Index(name = "idx_connexion_user",      columnList = "utilisateur_id"),
    @Index(name = "idx_connexion_statut",    columnList = "statut"),
    @Index(name = "idx_connexion_token",     columnList = "session_token"),
    @Index(name = "idx_connexion_sig_token", columnList = "signalement_token")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConnexionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    // ── Statut de la session ────────────────────────────────────
    public enum StatutSession { ACTIVE, TERMINEE, EXPIREE, REVOQUEE }

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    @Builder.Default
    private StatutSession statut = StatutSession.ACTIVE;

    // ── Token de session ────────────────────────────────────────
    @Column(name = "session_token", length = 600)
    private String sessionToken;

    // ── Token de signalement (lien email "Ce n'est pas moi") ───
    @Column(name = "signalement_token", length = 100, unique = true)
    @Builder.Default
    private String signalementToken = UUID.randomUUID().toString();

    /** true si l'utilisateur a signalé cette connexion comme suspecte */
    @Column(name = "est_signale")
    @Builder.Default
    private Boolean estSignale = false;

    /** Date du signalement */
    @Column(name = "date_signalement")
    private LocalDateTime dateSignalement;

    // ── Réseau ──────────────────────────────────────────────────
    @Column(name = "adresse_ip", length = 50)
    private String adresseIp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    // ── Appareil (collecté côté Angular) ────────────────────────
    @Column(name = "type_appareil", length = 50)       // PC / Mobile / Tablet
    private String typeAppareil;

    @Column(name = "os", length = 100)                 // Windows 11, Android 14…
    private String os;

    @Column(name = "navigateur", length = 100)         // Chrome 125, Firefox 126…
    private String navigateur;

    @Column(name = "resolution", length = 20)          // 1920x1080
    private String resolution;

    @Column(name = "langue", length = 20)              // fr-TN
    private String langue;

    @Column(name = "fuseau_horaire", length = 60)      // Africa/Tunis
    private String fuseauHoraire;

    // ── Empreinte numérique de l'appareil (Device Fingerprint) ────────
    /** Hash SHA-256 généré côté client (canvas + WebGL + localStorage UUID) */
    @Column(name = "device_fingerprint", length = 100)
    private String deviceFingerprint;

    /** true si cet appareil a déjà été utilisé par cet utilisateur */
    @Column(name = "appareil_connu")
    @Builder.Default
    private Boolean appareilConnu = false;

    // ── Type de réseau (collecté côté client via navigator.connection) ────
    @Column(name = "type_reseau", length = 30)     // Wi-Fi / 4G/5G / Ethernet / Inconnu
    private String typeReseau;

    // ── Analyse de risque ─────────────────────────────────────────────────
    /** Score de risque 0–100 calculé automatiquement à chaque connexion */
    @Column(name = "niveau_risque")
    @Builder.Default
    private Integer niveauRisque = 0;

    /** true si la connexion est jugée inhabituelle (nouveau pays, heure suspecte…) */
    @Column(name = "connexion_inhabituelle")
    @Builder.Default
    private Boolean connexionInhabituelle = false;

    // ── Localisation (résolution IP côté backend) ────────────────
    @Column(name = "pays", length = 60)
    private String pays;

    @Column(name = "region", length = 60)
    private String region;

    @Column(name = "ville", length = 60)
    private String ville;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "fournisseur_internet", length = 100)
    private String fournisseurInternet;

    // ── Résultat ────────────────────────────────────────────────
    @Column(name = "succes", nullable = false)
    @Builder.Default
    private Boolean succes = true;

    // ── Horodatage ──────────────────────────────────────────────
    @CreationTimestamp
    @Column(name = "date_connexion", updatable = false)
    private LocalDateTime dateConnexion;

    @Column(name = "date_deconnexion")
    private LocalDateTime dateDeconnexion;

    @Column(name = "motif_revocation", length = 200)
    private String motifRevocation;
}

