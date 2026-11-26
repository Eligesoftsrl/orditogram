package com.eligesoft.soluzioni.isi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * Servizio ISI: il calcolo del Bando ISI e i registri del bando dietro un'API HTTP chiamata dai connettori
 * {@code apiConnector} dei processi Orditogram {@code isiDomanda} e {@code isiElenchi}.
 * Soluzione del cliente (livello 2 della regola dei quattro livelli): non fa parte di Orditogram.
 */
@SpringBootApplication
public class ServizioIsiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServizioIsiApplication.class, args);
    }

    /** L'orologio del server: l'ora di arrivo allo sportello si prende da qui (nei test si sostituisce). */
    @Bean
    Clock orologio() {
        return Clock.systemDefaultZone();
    }
}
