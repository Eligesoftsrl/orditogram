package com.eligesoft.orditogram.form;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** Una versione salvata di un form: non cambia più, le pratiche avviate con lei la leggono fino alla fine. */
@Entity
@Table(name = "form_versione")
public class FormVersione {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "form_key", nullable = false, length = 200)
    private String formKey;

    @Column(nullable = false)
    private int versione;

    @Column(nullable = false, columnDefinition = "text")
    private String json;

    private OffsetDateTime salvatoIl;
    private String salvatoDa;

    protected FormVersione() {}

    public FormVersione(String formKey, int versione, String json, OffsetDateTime salvatoIl, String salvatoDa) {
        this.formKey = formKey;
        this.versione = versione;
        this.json = json;
        this.salvatoIl = salvatoIl;
        this.salvatoDa = salvatoDa;
    }

    public Long getId() { return id; }
    public String getFormKey() { return formKey; }
    public int getVersione() { return versione; }
    public String getJson() { return json; }
    public OffsetDateTime getSalvatoIl() { return salvatoIl; }
    public String getSalvatoDa() { return salvatoDa; }
}
