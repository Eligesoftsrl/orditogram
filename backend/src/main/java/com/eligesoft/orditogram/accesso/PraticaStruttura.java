package com.eligesoft.orditogram.accesso;

import jakarta.persistence.*;

/** La struttura competente di una pratica: dà l'ambito "propria struttura" ai permessi per processo. */
@Entity
@Table(name = "pratica_struttura")
public class PraticaStruttura {

    @Id
    @Column(length = 64)
    private String processInstanceId;

    @Column(nullable = false)
    private Long organizzazioneId;

    public PraticaStruttura() { }

    public PraticaStruttura(String processInstanceId, Long organizzazioneId) {
        this.processInstanceId = processInstanceId;
        this.organizzazioneId = organizzazioneId;
    }

    public String getProcessInstanceId() { return processInstanceId; }
    public Long getOrganizzazioneId() { return organizzazioneId; }
    public void setOrganizzazioneId(Long organizzazioneId) { this.organizzazioneId = organizzazioneId; }
}
