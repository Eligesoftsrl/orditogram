package com.eligesoft.orditogram.storico;

import java.util.List;
import java.util.Map;

/**
 * Pubblicato (evento Spring, nella stessa transazione) ogni volta che lo storico di una pratica si arricchisce:
 * lo ascoltano i webhook per avvisare i sistemi esterni.
 */
public record EventoStorico(String processInstanceId, PraticaEvento.Tipo tipo, String titolo, String utente,
                            String utenteNome, List<Map<String, Object>> dettagli) {}
