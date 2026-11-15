package com.eligesoft.orditogram.notifica;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** Notifica per un utente, mostrata nella campanella in alto. */
@Entity
@Table(name = "notifica", indexes = @Index(name = "ix_notifica_utente", columnList = "username, letta"))
public class Notifica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false)
    private String titolo;

    @Column(columnDefinition = "text")
    private String messaggio;

    @Column(length = 64)
    private String processInstanceId;

    private OffsetDateTime creataIl;
    private boolean letta;

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getTitolo() { return titolo; }
    public void setTitolo(String titolo) { this.titolo = titolo; }
    public String getMessaggio() { return messaggio; }
    public void setMessaggio(String messaggio) { this.messaggio = messaggio; }
    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }
    public OffsetDateTime getCreataIl() { return creataIl; }
    public void setCreataIl(OffsetDateTime creataIl) { this.creataIl = creataIl; }
    public boolean isLetta() { return letta; }
    public void setLetta(boolean letta) { this.letta = letta; }
}
