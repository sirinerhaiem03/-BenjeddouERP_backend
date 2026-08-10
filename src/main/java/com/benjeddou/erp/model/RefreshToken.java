package com.benjeddou.erp.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Entité de Refresh Token — J3 Sécurité
 * Stocke les refresh tokens longue durée (7 jours) en base de données.
 * L'access token (JWT) est court : 15 minutes.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** UUID unique du refresh token */
    @Column(name = "token", length = 512, unique = true, nullable = false)
    private String token;

    /** Utilisateur propriétaire du token */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    /** Date d'expiration (7 jours après création) */
    @Column(name = "date_expiration", nullable = false)
    private LocalDateTime dateExpiration;

    /** True si ce token a été révoqué (logout explicite ou double connexion) */
    @Column(name = "revoque")
    @Builder.Default
    private Boolean revoque = false;

    /** Date de création pour traçabilité */
    @Column(name = "date_creation")
    @Builder.Default
    private LocalDateTime dateCreation = LocalDateTime.now();
}
