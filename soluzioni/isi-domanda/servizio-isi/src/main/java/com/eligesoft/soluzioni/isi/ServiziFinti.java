package com.eligesoft.soluzioni.isi;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.eligesoft.soluzioni.isi.Testi.testo;

/** Controlli semplici e servizi esterni finti (da sostituire con quelli veri di INAIL in produzione). */
@Component
public class ServiziFinti {
    private final Path cartella;

    public ServiziFinti(Impostazioni impostazioni) {
        this.cartella = Path.of(impostazioni.datiProva());
    }

    /** RF-40 CA-02: il tecnico non può essere titolare, legale rappresentante, amministratore o socio. */
    public static Map<String, String> incaricoTecnico(Map<String, ?> d) {
        String cf = testo(d.get("cfTecnico")).toUpperCase(Locale.ROOT);
        Set<String> vietati = new HashSet<>();
        vietati.add(testo(d.get("cfLegaleRappresentante")).toUpperCase(Locale.ROOT));
        Arrays.stream(testo(d.get("cfSoci")).split("[,;\n]")).map(s -> s.strip().toUpperCase(Locale.ROOT)).forEach(vietati::add);
        vietati.remove("");
        if (cf.length() != 16) return Registro.mappa("incaricoAmmesso", "No", "motivoIncarico", "Codice fiscale del tecnico non valido");
        if (vietati.contains(cf)) {
            return Registro.mappa("incaricoAmmesso", "No", "motivoIncarico", "Il tecnico non può essere titolare, legale "
                    + "rappresentante, amministratore o socio dell'impresa (RF-40)");
        }
        return Registro.mappa("incaricoAmmesso", "Sì", "motivoIncarico", "Incarico ammesso");
    }

    /** RF-71, finto: imprese di prova in {@code dati-prova/imprese.json}. */
    public Map<String, String> anagraficaInail(String codiceFiscale) {
        Map<String, Map<String, String>> imprese;
        try {
            imprese = Regole.JSON.readValue(Files.readString(cartella.resolve("imprese.json")), new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Map<String, String> dati = imprese.get(testo(codiceFiscale).toUpperCase(Locale.ROOT));
        if (dati == null) {
            return Registro.mappa("anagraficaTrovata", "No", "avvisoAnagrafica",
                    "Impresa non trovata nell'anagrafica INAIL: compila i dati a mano");
        }
        Map<String, String> out = Registro.mappa("anagraficaTrovata", "Sì", "avvisoAnagrafica", "Dati letti dall'anagrafica INAIL");
        out.putAll(new LinkedHashMap<>(dati));
        return out;
    }
}
