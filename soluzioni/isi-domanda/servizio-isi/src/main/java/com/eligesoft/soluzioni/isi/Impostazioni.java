package com.eligesoft.soluzioni.isi;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Impostazioni del servizio (application.yml o variabili d'ambiente: {@code ISI_TOKEN}, {@code ISI_ANNUALITA},
 * {@code ISI_CALENDARIO_LIBERO}, {@code ISI_CALENDARIO_FILE}, {@code ORDITOGRAM_URL}, {@code ORDITOGRAM_CHIAVE}).
 *
 * @param token            segreto che Orditogram manda come {@code Authorization: Bearer}; vuoto = nessun controllo (solo sviluppo)
 * @param annualita        annualità delle regole ({@code <configurazione>/<annualita>.json})
 * @param configurazione   cartella delle regole
 * @param datiProva        cartella dei servizi finti (anagrafica INAIL)
 * @param calendarioLibero solo collaudo: lo sportello accetta invii a qualsiasi ora
 * @param calendarioFile   solo collaudo: un calendario con date diverse da quelle dell'annualità
 * @param orditogramUrl    indirizzo di Orditogram per avvisare le pratiche dell'esito degli elenchi
 * @param orditogramChiave chiave API di Orditogram con l'ambito {@code messaggi:invia}
 */
@ConfigurationProperties(prefix = "isi")
public record Impostazioni(String token, String annualita, String configurazione, String datiProva,
                           boolean calendarioLibero, String calendarioFile, String orditogramUrl,
                           String orditogramChiave) {}
