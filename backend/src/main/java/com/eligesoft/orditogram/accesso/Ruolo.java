package com.eligesoft.orditogram.accesso;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Ruolo applicativo: un nome per un insieme di permessi. Si assegna a un utente oppure a un gruppo
 * (anche un gruppo che arriva dal provider OIDC): chi è nel gruppo riceve il ruolo.
 */
@Entity
@Table(name = "ruolo")
public class Ruolo {

    /** Il ruolo che ha sempre tutti i permessi, anche quelli aggiunti in futuro. Non si modifica né si elimina. */
    public static final String AMMINISTRATORE = "amministratore";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identificativo stabile (es. "analista"): non cambia dopo la creazione. */
    @Column(nullable = false, unique = true, length = 60)
    private String codice;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(columnDefinition = "TEXT")
    private String descrizione;

    /** Creato da Orditogram: si può modificare ma non eliminare. */
    @Column(nullable = false)
    private boolean predefinito;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ruolo_permesso", joinColumns = @JoinColumn(name = "ruolo_id"))
    @Column(name = "permesso", length = 60)
    private Set<String> permessi = new HashSet<>();

    /** Gruppi i cui membri ricevono il ruolo. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ruolo_gruppo", joinColumns = @JoinColumn(name = "ruolo_id"))
    @Column(name = "gruppo", length = 100)
    private Set<String> gruppi = new HashSet<>();

    public boolean isAmministratore() { return AMMINISTRATORE.equals(codice); }

    public Long getId() { return id; }
    public String getCodice() { return codice; }
    public void setCodice(String codice) { this.codice = codice; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getDescrizione() { return descrizione; }
    public void setDescrizione(String descrizione) { this.descrizione = descrizione; }
    public boolean isPredefinito() { return predefinito; }
    public void setPredefinito(boolean predefinito) { this.predefinito = predefinito; }
    public Set<String> getPermessi() { return permessi; }
    public void setPermessi(Set<String> permessi) { this.permessi = permessi; }
    public Set<String> getGruppi() { return gruppi; }
    public void setGruppi(Set<String> gruppi) { this.gruppi = gruppi; }
}
