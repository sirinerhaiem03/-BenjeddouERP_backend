package com.benjeddou.erp.payload.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.util.Set;

@Getter
@Setter
public class InscriptionRequest {
    @NotBlank
    @Size(min = 3, max = 20)
    private String nomUtilisateur;

    @NotBlank
    @Size(max = 50)
    @Email
    private String email;
    
    private Set<String> roles;
    
    @NotBlank
    @Size(min = 6, max = 40)
    private String motDePasse;

    private String prenom;
    private String nom;
    private String telephone;
    private String societe;

    /** true = période d'essai gratuite, false = abonnement direct */
    private Boolean modeTrial = true;
}
