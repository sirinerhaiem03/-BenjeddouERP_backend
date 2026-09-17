package com.benjeddou.erp.payload.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * ConnexionRequest - Payload du POST /api/auth/login.
 *
 * Le champ identifiant accepte indifferemment :
 *   - le nom d'utilisateur (ex: "admin")
 *   - l'adresse e-mail     (ex: "admin@benjeddou.com")
 *   - le numero de telephone (ex: "+21612345678")
 *
 * Le backend resout automatiquement l'utilisateur correspondant.
 *
 * Après 2 tentatives échouées, le CAPTCHA local est requis.
 * Les champs captchaSessionId et captchaCode doivent alors être fournis.
 * En plus du CAPTCHA local, le token Google reCAPTCHA v2 doit aussi être fourni.
 */
@Getter
@Setter
public class ConnexionRequest {

    /** Identifiant universel : username, email ou telephone */
    @NotBlank
    private String identifiant;

    @NotBlank
    private String motDePasse;

    // ── CAPTCHA local (requis après 2 échecs) ──────────────────────────────
    /** Identifiant de session CAPTCHA retourné par GET /api/auth/captcha */
    private String captchaSessionId;

    /** Code alphanumérique saisi par l'utilisateur */
    private String captchaCode;

    // ── Google reCAPTCHA v2 (requis en même temps que le CAPTCHA local) ───
    /** Token reCAPTCHA v2 généré par le widget Google côté frontend */
    private String recaptchaToken;

    // ── Informations appareil (optionnelles, collectees par Angular) ───────
    private String typeAppareil;
    private String os;
    private String navigateur;
    private String resolution;
    private String langue;
    private String fuseauHoraire;
    private String deviceFingerprint;
    private String typeReseau;
}

