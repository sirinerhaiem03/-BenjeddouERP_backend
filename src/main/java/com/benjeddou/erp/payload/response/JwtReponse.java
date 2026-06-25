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

    // ── Nouveaux champs ──────────────────────────────────────────
    /** Statut du compte (ACTIF, EN_ATTENTE, VALIDE, REFUSE) */
    private String statutCompte;

    /** Si true, le compte est en mode trial */
    private Boolean modeTrial;

    /** Nombre d'utilisations restantes en mode trial */
    private Integer utilisationsRestantes;

    public JwtReponse(String accessToken, Long id, String nomUtilisateur, String email,
                       String prenom, String nom, String languePreferee, List<String> roles,
                       String statutCompte, Boolean modeTrial, Integer utilisationsRestantes) {
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
    }
}
