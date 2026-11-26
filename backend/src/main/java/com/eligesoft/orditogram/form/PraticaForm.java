package com.eligesoft.orditogram.form;

import jakarta.persistence.*;

/** La versione di un form che una pratica usa: fissata quando la pratica parte (vedi VersioniForm). */
@Entity
@Table(name = "pratica_form")
public class PraticaForm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "process_instance_id", nullable = false, length = 64)
    private String processInstanceId;

    @Column(name = "form_key", nullable = false, length = 200)
    private String formKey;

    @Column(nullable = false)
    private int versione;

    protected PraticaForm() {}

    public PraticaForm(String processInstanceId, String formKey, int versione) {
        this.processInstanceId = processInstanceId;
        this.formKey = formKey;
        this.versione = versione;
    }

    public String getProcessInstanceId() { return processInstanceId; }
    public String getFormKey() { return formKey; }
    public int getVersione() { return versione; }
}
