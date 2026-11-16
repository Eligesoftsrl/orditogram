package com.eligesoft.orditogram.user;

import com.eligesoft.orditogram.accesso.Ruolo;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

/** Utente applicativo salvato su Postgres. */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    /** Hash BCrypt, mai la password in chiaro. */
    @Column(nullable = false)
    private String password;

    private String firstName;
    private String lastName;
    private String email;

    private boolean enabled = true;

    /** Per chi entra con un provider OIDC: emittente e soggetto del token ("issuer|sub"), stabili nel tempo. */
    @Column(unique = true, length = 500)
    private String oidcSoggetto;

    /** Ruoli assegnati direttamente all'utente; altri arrivano dai gruppi (vedi accesso/PermessiService). */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "app_user_ruolo", joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "ruolo_id"))
    private Set<Ruolo> ruoli = new HashSet<>();

    /** Gruppi di lavoro: corrispondono ai candidateGroups dei BPMN (es. "operatori"). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_group", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "group_name", length = 100)
    private Set<String> groups = new HashSet<>();

    public String getFullName() {
        String full = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
        return full.isEmpty() ? username : full;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Set<Ruolo> getRuoli() { return ruoli; }
    public void setRuoli(Set<Ruolo> ruoli) { this.ruoli = ruoli; }
    public String getOidcSoggetto() { return oidcSoggetto; }
    public void setOidcSoggetto(String oidcSoggetto) { this.oidcSoggetto = oidcSoggetto; }
    public Set<String> getGroups() { return groups; }
    public void setGroups(Set<String> groups) { this.groups = groups; }
}
