package com.benjeddou.erp.payload.response;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class JwtReponse {
    private String token;
    private String type = "Bearer";
    private Long id;
    private String nomUtilisateur;
    private String email;
    private String prenom;
    private String nom;
    private String languePreferee;
    private List<String> roles;

    // ── Champs sécurité originaux ──────────────────────────────
    /** Statut du compte (ACTIF, EN_ATTENTE, VALIDE, REFUSE) */
    private String statutCompte;

    /** Si true, le compte est en mode trial */
    private Boolean modeTrial;

    /** Nombre d'utilisations restantes en mode trial */
    private Integer utilisationsRestantes;

    /** Si true, l'utilisateur doit changer son mot de passe à la prochaine connexion */
    private Boolean doitChangerMotDePasse;

    // ── J3 : Refresh Token + Trial par date ───────────────────
    /** Refresh token longue durée (7 jours) — stocké en localStorage */
    private String refreshToken;

    /** Durée de validité de l'access token en secondes (900 = 15 min) */
    private Integer expiresIn;

    /** Date d'expiration de la période d'essai (ISO-8601) */
    private String trialExpiresAt;

    /** Nombre de jours restants dans la période d'essai */
    private Long joursTrialRestants;

    public JwtReponse(String accessToken, Long id, String nomUtilisateur, String email,
                       String prenom, String nom, String languePreferee, List<String> roles,
                       String statutCompte, Boolean modeTrial, Integer utilisationsRestantes,
                       Boolean doitChangerMotDePasse) {
        this.token = accessToken;
        this.id = id;
        this.nomUtilisateur = nomUtilisateur;
        this.email = email;
        this.prenom = prenom;
        this.nom = nom;
        this.languePreferee = languePreferee;
        this.roles = roles;
        this.statutCompte = statutCompte;
        this.modeTrial = modeTrial;
        this.utilisationsRestantes = utilisationsRestantes;
        this.doitChangerMotDePasse = doitChangerMotDePasse;
        this.expiresIn = 900;
    }
}
