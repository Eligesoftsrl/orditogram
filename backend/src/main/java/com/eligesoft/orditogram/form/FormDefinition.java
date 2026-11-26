package com.eligesoft.orditogram.form;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** Definizione di un form (JSON) salvata su Postgres, identificata dal formKey usato nel BPMN. */
@Entity
@Table(name = "form_definition")
public class FormDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "form_key", nullable = false, unique = true, length = 200)
    private String formKey;

    @Column(nullable = false, columnDefinition = "text")
    private String json;

    /** L'ultima versione salvata (le precedenti restano in form_versione per le pratiche avviate con loro). */
    @Column(nullable = false)
    private int versione = 1;

    private OffsetDateTime updatedAt;
    private String updatedBy;

    public Long getId() { return id; }
    public String getFormKey() { return formKey; }
    public void setFormKey(String formKey) { this.formKey = formKey; }
    public String getJson() { return json; }
    public void setJson(String json) { this.json = json; }
    public int getVersione() { return versione; }
    public void setVersione(int versione) { this.versione = versione; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
