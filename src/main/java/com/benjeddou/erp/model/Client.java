package com.benjeddou.erp.model;

import com.benjeddou.erp.security.encryption.EncryptionConverter;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Client — Données personnelles chiffrées en base de données.
 *
 * Champs chiffrés (AES-256-GCM) :
 *  - email       : donnée personnelle (RGPD)
 *  - telephone   : donnée personnelle (RGPD)
 *  - adresse     : donnée personnelle (RGPD)
 *
 * Nota : le chiffrement est transparent via EncryptionConverter (JPA AttributeConverter).
 * Les valeurs sont automatiquement chiffrées à l'écriture et déchiffrées à la lecture.
 */
@Entity
@Table(name = "clients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Le nom du client est obligatoire")
    @Size(max = 100, message = "Le nom ne peut pas dépasser 100 caractères")
    @Column(length = 100, nullable = false)
    private String nom;

    /** Email chiffré en BDD — AES-256-GCM */
    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    @Size(max = 200, message = "L'email ne peut pas dépasser 200 caractères")
    @Column(length = 500, unique = true, nullable = false)
    @Convert(converter = EncryptionConverter.class)
    private String email;

    /** Téléphone chiffré en BDD — AES-256-GCM */
    @Pattern(regexp = "^[+\\d\\s\\-().]{0,20}$", message = "Format de téléphone invalide")
    @Column(length = 500)
    @Convert(converter = EncryptionConverter.class)
    private String telephone;

    /** Adresse chiffrée en BDD — AES-256-GCM */
    @Column(length = 1000)
    @Convert(converter = EncryptionConverter.class)
    private String adresse;

    @Column(name = "matricule_fiscale", length = 50)
    private String matriculeFiscale;

    @CreationTimestamp
    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;

    @UpdateTimestamp
    @Column(name = "date_modification")
    private LocalDateTime dateModification;
}
