package com.benjeddou.erp.model;

public enum StatutAbonnement {
    EN_ATTENTE,   // soumis, en attente de validation
    VALIDE,       // validé par admin, en attente de paiement confirmé
    ACTIF,        // actif et en cours
    EXPIRE,       // date de fin dépassée
    ANNULE        // annulé
}
