package com.eligesoft.orditogram.storico;

import com.eligesoft.orditogram.accesso.PermessiProcessoService;
import com.eligesoft.orditogram.accesso.Permesso;
import com.eligesoft.orditogram.accesso.PermessiService;
import com.eligesoft.orditogram.user.AppUser;
import com.eligesoft.orditogram.user.AppUserRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Quali pratiche vede un utente, come condizione SQL sullo storico di Activiti (ACT_HI_PROCINST "RES", unito alla
 * definizione del processo "D"). Una sola regola per l'elenco e per il dettaglio, valutata nel database:
 *  1. permesso "pratiche.vedi-tutte": tutte;
 *  2. regole "vede" del processo, entro il loro ambito (tutte, propria struttura, struttura e sottostrutture);
 *  3. chi vi ha lavorato (storico) o ha un'attività in carico o disponibile per sé o per i suoi gruppi
 *     (compresi i gruppi automatici "org:<id>" delle organizzazioni);
 *  4. le parti della pratica: la persona o chi appartiene all'organizzazione della parte.
 * Gli utenti esterni non hanno permessi né regole "vede": per loro valgono solo 3 e 4.
 */
@Service
public class VisibilitaPratiche {

    static final String DA = " FROM ACT_HI_PROCINST RES JOIN ACT_RE_PROCDEF D ON D.ID_ = RES.PROC_DEF_ID_ ";

    private final NamedParameterJdbcTemplate jdbc;
    private final PermessiService permessi;
    private final PermessiProcessoService permessiProcesso;
    private final AppUserRepository utenti;

    public VisibilitaPratiche(NamedParameterJdbcTemplate jdbc, PermessiService permessi,
                              PermessiProcessoService permessiProcesso, AppUserRepository utenti) {
        this.jdbc = jdbc;
        this.permessi = permessi;
        this.permessiProcesso = permessiProcesso;
        this.utenti = utenti;
    }

    /** Condizione SQL (con i suoi parametri, aggiunti a "p") vera per le pratiche visibili all'utente. */
    @Transactional(readOnly = true)
    public String condizione(String username, MapSqlParameterSource p) {
        if (username == null) return "1 = 0";
        if (permessi.ha(username, Permesso.PRATICHE_TUTTE)) return "1 = 1";
        AppUser u = utenti.findByUsername(username).filter(AppUser::isEnabled).orElse(null);
        if (u == null) return "1 = 0";
        Set<String> gruppi = u.gruppiEffettivi();

        p.addValue("vis_utente", username);
        List<String> o = new ArrayList<>();
        o.add("RES.PROC_INST_ID_ IN (SELECT e.process_instance_id FROM pratica_evento e WHERE e.utente = :vis_utente)");
        o.add("RES.PROC_INST_ID_ IN (SELECT t.PROC_INST_ID_ FROM ACT_RU_TASK t WHERE t.ASSIGNEE_ = :vis_utente)");
        String candidati = "i.USER_ID_ = :vis_utente";
        if (!gruppi.isEmpty()) {
            p.addValue("vis_gruppi", gruppi);
            candidati += " OR i.GROUP_ID_ IN (:vis_gruppi)";
        }
        o.add("RES.PROC_INST_ID_ IN (SELECT t.PROC_INST_ID_ FROM ACT_RU_TASK t JOIN ACT_RU_IDENTITYLINK i ON i.TASK_ID_ = t.ID_"
            + " WHERE t.ASSIGNEE_ IS NULL AND i.TYPE_ = 'candidate' AND (" + candidati + "))");
        String parti = "pp.utente = :vis_utente";
        if (!u.getOrganizzazioni().isEmpty()) {
            p.addValue("vis_org", u.getOrganizzazioni());
            parti += " OR pp.organizzazione_id IN (:vis_org)";
        }
        o.add("RES.PROC_INST_ID_ IN (SELECT pp.process_instance_id FROM pratica_parte pp WHERE " + parti + ")");

        PermessiProcessoService.Visibilita v = permessiProcesso.visibilita(username);
        if (!v.processiTutte().isEmpty()) {
            p.addValue("vis_tutte", v.processiTutte());
            o.add("D.KEY_ IN (:vis_tutte)");
        }
        if (!v.processiStruttura().isEmpty()) {
            p.addValue("vis_str_p", v.processiStruttura());
            p.addValue("vis_str_o", v.struttureMie());
            o.add("(D.KEY_ IN (:vis_str_p) AND RES.PROC_INST_ID_ IN (SELECT ps.process_instance_id FROM pratica_struttura ps"
                + " WHERE ps.organizzazione_id IN (:vis_str_o)))");
        }
        if (!v.processiStrutturaESotto().isEmpty()) {
            p.addValue("vis_sotto_p", v.processiStrutturaESotto());
            p.addValue("vis_sotto_o", v.struttureMieESotto());
            o.add("(D.KEY_ IN (:vis_sotto_p) AND RES.PROC_INST_ID_ IN (SELECT ps.process_instance_id FROM pratica_struttura ps"
                + " WHERE ps.organizzazione_id IN (:vis_sotto_o)))");
        }
        return "(" + String.join(" OR ", o) + ")";
    }

    /** L'utente vede questa pratica secondo le regole sopra (la pratica madre è controllata da PraticaAccessService). */
    @Transactional(readOnly = true)
    public boolean vede(String username, String processInstanceId) {
        MapSqlParameterSource p = new MapSqlParameterSource("pi", processInstanceId);
        String sql = "SELECT COUNT(*)" + DA + "WHERE RES.PROC_INST_ID_ = :pi AND " + condizione(username, p);
        Long n = jdbc.queryForObject(sql, p, Long.class);
        return n != null && n > 0;
    }

    /** Filtri dell'elenco. q già senza caratteri jolly; tipologie: chiavi dei processi il cui nome contiene q. */
    public record Ricerca(String stato, String processo, String q, Collection<String> tipologie,
                          Collection<String> esclusi) { }

    /** Una pagina di id di pratiche visibili, le più recenti prima, e il totale. */
    public record Pagina(List<String> id, long totale) { }

    @Transactional(readOnly = true)
    public Pagina cerca(String username, Ricerca r, int pagina, int dimensione) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder dove = new StringBuilder(" WHERE ").append(condizione(username, p));
        if ("in-corso".equals(r.stato())) dove.append(" AND RES.END_TIME_ IS NULL");
        if ("concluse".equals(r.stato())) dove.append(" AND RES.END_TIME_ IS NOT NULL");
        if (r.processo() != null && !r.processo().isBlank()) {
            p.addValue("processo", r.processo().trim());
            dove.append(" AND D.KEY_ = :processo");
        }
        if (r.q() != null && !r.q().isEmpty()) {
            p.addValue("q", "%" + r.q() + "%");
            dove.append(" AND (LOWER(RES.NAME_) LIKE :q");
            if (!r.tipologie().isEmpty()) {
                p.addValue("tipologie", r.tipologie());
                dove.append(" OR D.KEY_ IN (:tipologie)");
            }
            dove.append(")");
        }
        if (!r.esclusi().isEmpty()) {
            p.addValue("esclusi", r.esclusi());
            dove.append(" AND D.KEY_ NOT IN (:esclusi)");
        }
        Long totale = jdbc.queryForObject("SELECT COUNT(*)" + DA + dove, p, Long.class);
        p.addValue("limite", dimensione);
        p.addValue("salto", (long) pagina * dimensione);
        List<String> id = jdbc.queryForList("SELECT RES.PROC_INST_ID_" + DA + dove
            + " ORDER BY RES.START_TIME_ DESC, RES.ID_ DESC LIMIT :limite OFFSET :salto", p, String.class);
        return new Pagina(id, totale == null ? 0 : totale);
    }
}
