package com.eligesoft.orditogram.connettore;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Un connettore che l'analista può usare nelle attività automatiche dell'editor.
 * Basta implementare questa interfaccia (insieme a org.activiti.api.process.runtime.connector.Connector)
 * su un bean Spring: compare da solo nel catalogo, con il suo modulo di parametri.
 *
 * Nel BPMN diventa:
 *   <serviceTask implementation="{id}">
 *     <extensionElements><activiti:field name="{parametro}" stringValue="..."/></extensionElements>
 *   </serviceTask>
 */
public interface ConnettoreOrditogram {

    /** Nome del bean Spring, usato come implementation nel BPMN. */
    String id();

    /** Nome mostrato all'analista, es. "Invia notifica". */
    String nome();

    /** Cosa fa, in una frase. */
    String descrizione();

    /** Parametri configurabili nell'editor. */
    List<Parametro> parametri();

    /**
     * Dati della pratica che il connettore scrive (es. la risposta di un'API), dati i valori dei parametri.
     * Servono al controllo dei processi e alla dichiarazione delle variabili per Activiti.
     */
    default Set<String> datiProdotti(Map<String, String> valori) {
        return Set.of();
    }

    /**
     * true se, con questi parametri, un errore del connettore porta la pratica sulla strada dell'evento
     * di errore (BpmnError). Il controllo del processo avvisa se quella strada manca.
     */
    default boolean puoFallire(Map<String, String> valori) {
        return false;
    }

    /**
     * tipo:
     *  "testo" | "testoLungo" | "codice" (testo lungo a spaziatura fissa)
     *  "scelta"           (una delle opzioni)
     *  "destinatario"     (iniziatore | gruppo:nome | username)
     *  "destinatariEmail" (elenco separato da virgole di: iniziatore, gruppo:x, utente:x, campo:x, email:indirizzo)
     *  "mappatura"        (righe "datoPratica = percorso.nella.risposta")
     *  "credenziale"      (nome di una credenziale configurata sul server)
     * Nei testi l'analista può inserire ${nomeDato} per usare i dati della pratica.
     */
    record Parametro(String nome, String etichetta, String tipo, boolean obbligatorio,
                     String aiuto, String predefinito, List<String> opzioni) {

        public static Parametro testo(String nome, String etichetta, boolean obbligatorio, String aiuto) {
            return new Parametro(nome, etichetta, "testo", obbligatorio, aiuto, null, List.of());
        }

        public static Parametro testoLungo(String nome, String etichetta, boolean obbligatorio, String aiuto) {
            return new Parametro(nome, etichetta, "testoLungo", obbligatorio, aiuto, null, List.of());
        }

        public static Parametro di(String tipo, String nome, String etichetta, boolean obbligatorio, String aiuto) {
            return new Parametro(nome, etichetta, tipo, obbligatorio, aiuto, null, List.of());
        }

        public static Parametro scelta(String nome, String etichetta, String predefinito, String aiuto, String... opzioni) {
            return new Parametro(nome, etichetta, "scelta", true, aiuto, predefinito, List.of(opzioni));
        }
    }
}
