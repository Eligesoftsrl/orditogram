package com.eligesoft.orditogram.auth;

import java.util.*;

/** Lettura dei gruppi da un claim del token, anche annidato ("realm_access.roles"). Nessuna dipendenza. */
public final class GruppiDalToken {

    private GruppiDalToken() {}

    public static Set<String> leggi(Map<String, Object> claims, String percorso, Map<String, String> mappa) {
        Object v = claims;
        for (String parte : percorso.split("\\.")) {
            if (!(v instanceof Map<?, ?> m)) return Set.of();
            v = m.get(parte);
        }
        Collection<?> valori = v instanceof Collection<?> c ? c : v instanceof String s ? List.of(s.split("[,\\s]+")) : List.of();
        Set<String> out = new TreeSet<>();
        for (Object o : valori) {
            if (o == null) continue;
            String g = o.toString().trim();
            // Keycloak con "Full group path": /ente/analisti -> analisti
            if (g.startsWith("/")) g = g.substring(g.lastIndexOf('/') + 1);
            if (mappa != null && !mappa.isEmpty()) {
                String mappato = mappa.get(o.toString().trim());
                if (mappato == null) mappato = mappa.get(g);
                if (mappato != null) out.add(mappato);
                else if (!sembraId(g)) out.add(g);   // i GUID non mappati non diventano gruppi
            } else if (!g.isEmpty()) {
                out.add(g);
            }
        }
        return out;
    }

    private static boolean sembraId(String s) {
        return s.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }
}
