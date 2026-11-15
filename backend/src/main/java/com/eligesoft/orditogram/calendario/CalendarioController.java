package com.eligesoft.orditogram.calendario;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Calendario dell'ente: festività dell'anno e anteprima di un termine (per l'editor). */
@RestController
@RequestMapping("/api/calendario")
public class CalendarioController {

    private final CalendarioProperties proprieta;

    public CalendarioController(CalendarioProperties proprieta) {
        this.proprieta = proprieta;
    }

    @GetMapping
    public Map<String, Object> anno(@RequestParam(required = false) Integer anno) {
        int a = anno != null ? anno : LocalDate.now(proprieta.zoneId()).getYear();
        if (a < 1900 || a > 2200) throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.BAD_REQUEST, "Anno non valido");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("anno", a);
        m.put("giorniNonLavorativi", proprieta.getGiorniNonLavorativi());
        m.put("festivita", proprieta.calendario().festivita(a).stream()
            .map(f -> Map.of("data", f.data().toString(), "nome", f.nome())).toList());
        return m;
    }

    /** Quando scadrebbe oggi un termine di n giorni: modo = calendario | lavorativi | proroga. */
    @GetMapping("/anteprima")
    public Map<String, Object> anteprima(@RequestParam int giorni, @RequestParam(defaultValue = "calendario") String modo) {
        if (giorni < 0 || giorni > 3650) throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.BAD_REQUEST, "Numero di giorni non valido");
        CalendarioLavorativo cal = proprieta.calendario();
        ZonedDateTime ora = ZonedDateTime.now(proprieta.zoneId());
        ZonedDateTime fine = switch (modo) {
            case CalendariMotore.LAVORATIVI -> cal.aggiungiGiorniLavorativi(ora, giorni);
            case CalendariMotore.PROROGA -> cal.proroga(ora.plusDays(giorni));
            default -> ora.plusDays(giorni);
        };
        return Map.of("scadenza", fine.toLocalDate().toString(), "modo", modo, "giorni", giorni);
    }
}
