package com.eligesoft.orditogram.storico;

import com.eligesoft.orditogram.calendario.CalendarioProperties;
import com.eligesoft.orditogram.user.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

/** Indicatori per i grafici della scrivania, sulle sole pratiche visibili all'utente. */
@RestController
public class IndicatoriController {

    private final IndicatoriPratiche indicatori;
    private final CurrentUserService currentUser;
    private final CalendarioProperties calendario;

    public IndicatoriController(IndicatoriPratiche indicatori, CurrentUserService currentUser, CalendarioProperties calendario) {
        this.indicatori = indicatori;
        this.currentUser = currentUser;
        this.calendario = calendario;
    }

    @GetMapping("/api/pratiche/indicatori")
    public Map<String, Object> indicatori() {
        // i numeri dei processi dell'ente non riguardano gli utenti esterni (il portale li lascia passare come /api/pratiche/*)
        if (currentUser.esterno()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Operazione non consentita");
        return indicatori.calcola(currentUser.username(), Instant.now(), calendario.zoneId());
    }
}
