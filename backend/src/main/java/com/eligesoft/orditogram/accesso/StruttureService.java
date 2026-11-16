package com.eligesoft.orditogram.accesso;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** L'albero delle strutture: discendenti, percorso, controllo dei cicli. */
@Service
public class StruttureService {

    private final OrganizzazioneRepository organizzazioni;

    public StruttureService(OrganizzazioneRepository organizzazioni) {
        this.organizzazioni = organizzazioni;
    }

    /** Le strutture indicate più tutte quelle sotto, a qualunque livello. */
    @Transactional(readOnly = true)
    public Set<Long> conDiscendenti(Collection<Long> radici) {
        Set<Long> risultato = new HashSet<>(radici);
        if (radici.isEmpty()) return risultato;
        Map<Long, List<Long>> figli = new HashMap<>();
        for (Organizzazione o : organizzazioni.findAll())
            if (o.getPadreId() != null) figli.computeIfAbsent(o.getPadreId(), k -> new ArrayList<>()).add(o.getId());
        Deque<Long> daVisitare = new ArrayDeque<>(radici);
        while (!daVisitare.isEmpty()) {
            for (Long f : figli.getOrDefault(daVisitare.pop(), List.of()))
                if (risultato.add(f)) daVisitare.push(f);
        }
        return risultato;
    }

    /** Mettere "padre" sopra "struttura" creerebbe un giro (la struttura finirebbe sotto sé stessa). */
    @Transactional(readOnly = true)
    public boolean creaCiclo(Long struttura, Long padre) {
        if (struttura == null || padre == null) return false;
        return conDiscendenti(List.of(struttura)).contains(padre);
    }
}
