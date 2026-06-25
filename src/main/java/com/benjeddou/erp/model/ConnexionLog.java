package com.benjeddou.erp.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "connexions_log")
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

    @CreationTimestamp
    @Column(name = "date_connexion", updatable = false)
    private LocalDateTime dateConnexion;

    @Column(name = "adresse_ip", length = 50)
    private String adresseIp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "succes")
    @Builder.Default
    private Boolean succes = true;
}
