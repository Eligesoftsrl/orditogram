package com.eligesoft.orditogram.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite di richieste al minuto per sistema (finestra fissa, in memoria): protegge Orditogram da un'integrazione
 * che entra in un ciclo. Con più istanze di Orditogram il limite vale per ciascuna.
 */
public class LimiteRichieste {

    private record Finestra(long minuto, int conteggio) {}

    private final int perMinuto;
    private final Map<String, Finestra> finestre = new ConcurrentHashMap<>();

    public LimiteRichieste(int perMinuto) {
        this.perMinuto = perMinuto;
    }

    /** Secondi da attendere se il limite è superato, altrimenti 0 (e la richiesta viene contata). */
    public long consuma(String chi, long oraMillis) {
        if (perMinuto <= 0) return 0;
        long minuto = oraMillis / 60_000;
        Finestra f = finestre.compute(chi, (k, prec) ->
            prec == null || prec.minuto() != minuto ? new Finestra(minuto, 1) : new Finestra(minuto, prec.conteggio() + 1));
        if (finestre.size() > 10_000) finestre.entrySet().removeIf(e -> e.getValue().minuto() < minuto);
        if (f.conteggio() <= perMinuto) return 0;
        return Math.max(1, 60 - (oraMillis / 1000) % 60);
    }

    public int perMinuto() { return perMinuto; }
}
