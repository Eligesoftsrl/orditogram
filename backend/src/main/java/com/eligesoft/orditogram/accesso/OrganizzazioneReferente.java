package com.eligesoft.orditogram.accesso;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** Un referente di un'organizzazione esterna: gestisce le persone della sua organizzazione (vedi ReferentiService). */
@Entity
@Table(name = "organizzazione_referente")
public class OrganizzazioneReferente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organizzazione_id", nullable = false)
    private Long organizzazioneId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private OffsetDateTime nominatoIl;

    @Column(length = 100)
    private String nominatoDa;

    protected OrganizzazioneReferente() { }

    public OrganizzazioneReferente(Long organizzazioneId, Long userId, String nominatoDa) {
        this.organizzazioneId = organizzazioneId;
        this.userId = userId;
        this.nominatoDa = nominatoDa;
        this.nominatoIl = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getOrganizzazioneId() { return organizzazioneId; }
    public Long getUserId() { return userId; }
    public OffsetDateTime getNominatoIl() { return nominatoIl; }
    public String getNominatoDa() { return nominatoDa; }
}
