package com.benjeddou.erp.model;

public enum StatutCompte {
    EN_ATTENTE,   // Compte créé, en attente de validation admin
    VALIDE,       // Documents vérifiés, abonnement non encore payé
    ACTIF,        // Compte pleinement opérationnel
    REFUSE        // Compte refusé par l'admin
}
