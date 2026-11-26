package com.eligesoft.soluzioni.isi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties(Impostazioni.class)
class Configurazione {
    private static final Logger LOG = LoggerFactory.getLogger(Configurazione.class);

    @Bean
    Regole regole(Impostazioni i) {
        Path calendario = vuoto(i.calendarioFile()) ? null : Path.of(i.calendarioFile());
        Regole r = Regole.carica(Path.of(i.configurazione()), i.annualita(), calendario);
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
