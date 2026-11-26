package com.eligesoft.orditogram.registro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Ogni notte toglie le letture e gli errori più vecchi del periodo di conservazione (minimizzazione dei dati).
 * Il registro delle azioni di amministrazione non scade. 0 giorni = conserva per sempre.
 */
@Component
public class PuliziaRegistro {

    private static final Logger log = LoggerFactory.getLogger(PuliziaRegistro.class);

    private final RegistroLetturaRepository letture;
    private final RegistroErroreRepository errori;
    private final int giorniLetture;
    private final int giorniErrori;

    public PuliziaRegistro(RegistroLetturaRepository letture, RegistroErroreRepository errori,
                           @Value("${app.registro.letture-giorni:365}") int giorniLetture,
                           @Value("${app.registro.errori-giorni:90}") int giorniErrori) {
        this.letture = letture;
        this.errori = errori;
        this.giorniLetture = giorniLetture;
        this.giorniErrori = giorniErrori;
    }

    @Scheduled(cron = "${app.registro.pulizia:0 30 3 * * *}")
    @Transactional
    public void pulisci() {
        OffsetDateTime adesso = OffsetDateTime.now();
        int l = giorniLetture > 0 ? letture.eliminaPrimaDi(adesso.minusDays(giorniLetture)) : 0;
        int e = giorniErrori > 0 ? errori.eliminaPrimaDi(adesso.minusDays(giorniErrori)) : 0;
        if (l + e > 0) log.info("Registro: tolte {} letture e {} errori scaduti", l, e);
    }
}
