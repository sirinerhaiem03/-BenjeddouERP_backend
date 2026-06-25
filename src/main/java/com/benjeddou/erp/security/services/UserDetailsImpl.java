package com.benjeddou.erp.security.services;

import com.benjeddou.erp.model.Utilisateur;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class UserDetailsImpl implements UserDetails {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String nomUtilisateur;
    private String email;

    @JsonIgnore
    private String motDePasse;
    
    private String prenom;
    private String nom;
    private String languePreferee;
    private Boolean actif;

    private Collection<? extends GrantedAuthority> authorities;

    public UserDetailsImpl(Long id, String nomUtilisateur, String email, String motDePasse, 
                           String prenom, String nom, String languePreferee, Boolean actif,
                           Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.nomUtilisateur = nomUtilisateur;
        this.email = email;
        this.motDePasse = motDePasse;
        this.prenom = prenom;
        this.nom = nom;
        this.languePreferee = languePreferee;
        this.actif = actif;
        this.authorities = authorities;
    }

    public static UserDetailsImpl build(Utilisateur utilisateur) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        if (utilisateur.getRole() != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + utilisateur.getRole().name()));
        }

        return new UserDetailsImpl(
                utilisateur.getId(),
                utilisateur.getNomUtilisateur(),
                utilisateur.getEmail(),
                utilisateur.getMotDePasse(),
                utilisateur.getPrenom(),
                utilisateur.getNom(),
                utilisateur.getLanguePreferee(),
                utilisateur.getActif(),
                authorities
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPrenom() {
        return prenom;
    }

    public String getNom() {
        return nom;
    }

    public String getLanguePreferee() {
        return languePreferee;
    }

    @Override
    public String getPassword() {
        return motDePasse;
    }

    @Override
    public String getUsername() {
        return nomUtilisateur;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return actif;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return actif;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        UserDetailsImpl user = (UserDetailsImpl) o;
        return Objects.equals(id, user.id);
    }
}
