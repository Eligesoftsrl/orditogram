package com.eligesoft.soluzioni.isi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(Impostazioni.class)
class Configurazione {
    private static final Logger LOG = LoggerFactory.getLogger(Configurazione.class);

    @Bean
    Regole regole(Impostazioni i, Clock orologio) {
        Path calendario = vuoto(i.calendarioFile()) ? null : Path.of(i.calendarioFile());
        Regole r = i.demo()
                ? Regole.conCalendario(Path.of(i.configurazione()), i.annualita(), Regole.calendarioDemo(orologio))
                : Regole.carica(Path.of(i.configurazione()), i.annualita(), calendario);
        if (i.demo()) LOG.warn("ATTENZIONE: demo, tutti i periodi del bando aperti per 60 giorni (mai in produzione)");
        LOG.info("Regole {} caricate da {}", r.versione(), i.configurazione());
        if (i.calendarioLibero()) LOG.warn("ATTENZIONE: calendario libero, lo sportello accetta invii a qualsiasi ora (solo collaudo)");
        if (calendario != null) LOG.warn("ATTENZIONE: calendario del collaudo {} (solo collaudo)", calendario);
        if (vuoto(i.token())) LOG.warn("ATTENZIONE: ISI_TOKEN non impostato, il servizio risponde a chiunque (solo sviluppo)");
        return r;
    }

    static boolean vuoto(String s) {
        return s == null || s.isBlank();
    }
}
