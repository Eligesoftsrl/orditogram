package com.eligesoft.orditogram.documento;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Un documento logico di una pratica (es. "contratto.pdf"), con tutte le sue versioni. */
@Entity
@Table(name = "documento", indexes = @Index(name = "ix_documento_pratica", columnList = "processInstanceId"))
public class Documento {

    public static final String INTERNA = "INTERNA";
    public static final String PARTI = "PARTI";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String processInstanceId;

    /** Nome del documento: quello del primo file caricato. */
    @Column(nullable = false)
    private String nome;

    private String creatoDa;
    private OffsetDateTime creatoIl;

    private int versioneCorrente;

    /** INTERNA (solo il personale) o PARTI (anche gli utenti esterni che vedono la pratica). Testo, non enum. */
    @Column(nullable = false, length = 20)
    private String visibilita = INTERNA;

    @OneToMany(mappedBy = "documento", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numero DESC")
    private List<DocumentoVersione> versioni = new ArrayList<>();

    public Long getId() { return id; }
    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getCreatoDa() { return creatoDa; }
    public void setCreatoDa(String creatoDa) { this.creatoDa = creatoDa; }
    public OffsetDateTime getCreatoIl() { return creatoIl; }
    public void setCreatoIl(OffsetDateTime creatoIl) { this.creatoIl = creatoIl; }
    public int getVersioneCorrente() { return versioneCorrente; }
    public void setVersioneCorrente(int versioneCorrente) { this.versioneCorrente = versioneCorrente; }
    public List<DocumentoVersione> getVersioni() { return versioni; }
    public String getVisibilita() { return visibilita; }
    public void setVisibilita(String visibilita) { this.visibilita = visibilita; }
    public boolean visibileAlleParti() { return PARTI.equals(visibilita); }
}
