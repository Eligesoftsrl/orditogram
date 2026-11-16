package com.eligesoft.orditogram.anagrafica;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Le righe dell'indice di una scheda: una per ogni campo chiave, ricercabile o relazione compilato. */
final class IndiciScheda {

    private IndiciScheda() {}

    static List<IndiceAnagrafica> calcola(DefinizioneAnagrafica def, Long tipoId, Long schedaId, Map<String, Object> dati) {
        List<IndiceAnagrafica> out = new ArrayList<>();
        for (DefinizioneAnagrafica.CampoIndice c : def.indice()) {
            String v = DefinizioneAnagrafica.normalizza(dati.get(c.nome()));
            if (v != null) out.add(new IndiceAnagrafica(schedaId, tipoId, c.nome(), v, c.chiave()));
        }
        return out;
    }
}
