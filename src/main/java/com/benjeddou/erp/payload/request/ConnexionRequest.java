package com.benjeddou.erp.payload.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConnexionRequest {
    @NotBlank
    private String nomUtilisateur;

    @NotBlank
    private String motDePasse;
}
