package com.eligesoft.orditogram.storico;

import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.FlowElement;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.SubProcess;
import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.repository.ProcessDefinition;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Indicatori della scrivania, calcolati solo sulle pratiche che l'utente vede (stessa regola dell'elenco,
 * {@link VisibilitaPratiche#condizione}): pratiche per processo, dove si trovano (passo attuale),
 * avanzamento stimato, aperte e chiuse per settimana, termini in arrivo.
 *
 * L'avanzamento è una stima: attività utente già completate (distinte) sul totale delle attività utente del
 * modello. Una pratica in corso non supera mai il 95%, perché i rami non percorsi non si sanno in anticipo.
 */
@Service
public class IndicatoriPratiche {

    static final int SETTIMANE = 8;
    static final int GIORNI_TERMINI = 14;
    /** Oltre questo numero di pratiche in corso l'avanzamento si calcola sulle più recenti. */
    static final int MAX_PER_AVANZAMENTO = 5000;

    private final NamedParameterJdbcTemplate jdbc;
    private final VisibilitaPratiche visibilita;
    private final RepositoryService repositoryService;

    public IndicatoriPratiche(NamedParameterJdbcTemplate jdbc, VisibilitaPratiche visibilita, RepositoryService repositoryService) {
        this.jdbc = jdbc;
        this.visibilita = visibilita;
        this.repositoryService = repositoryService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> calcola(String username, Instant ora, ZoneId zona) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String dove = " WHERE " + visibilita.condizione(username, p);
        Date da30 = Date.from(ora.minus(30, ChronoUnit.DAYS));
        LocalDate lunedi = LocalDate.ofInstant(ora, zona).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate primaSettimana = lunedi.minusWeeks(SETTIMANE - 1);
        Date daSettimane = Date.from(primaSettimana.atStartOfDay(zona).toInstant());
        p.addValue("da30", da30);
        p.addValue("daSettimane", daSettimane);
        p.addValue("ora", Date.from(ora));
        p.addValue("fraGiorni", Date.from(ora.plus(GIORNI_TERMINI, ChronoUnit.DAYS)));

        // ---- per processo: in corso, concluse negli ultimi 30 giorni, durata media
        Map<String, Map<String, Object>> processi = new LinkedHashMap<>();
        jdbc.query("SELECT D.KEY_ AS chiave, MAX(D.NAME_) AS nome,"
                + " SUM(CASE WHEN RES.END_TIME_ IS NULL THEN 1 ELSE 0 END) AS in_corso,"
                + " SUM(CASE WHEN RES.END_TIME_ >= :da30 THEN 1 ELSE 0 END) AS concluse,"
                + " AVG(CASE WHEN RES.END_TIME_ >= :da30 THEN RES.DURATION_ END) AS durata"
                + VisibilitaPratiche.DA + dove + " GROUP BY D.KEY_", p, rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            String chiave = rs.getString("chiave");
            m.put("chiave", chiave);
            m.put("nome", rs.getString("nome") != null ? rs.getString("nome") : chiave);
            m.put("inCorso", rs.getLong("in_corso"));
            m.put("concluse30", rs.getLong("concluse"));
            Number durata = (Number) rs.getObject("durata");
            m.put("durataMediaGiorni", durata == null ? null : Math.round(durata.doubleValue() / 86_400_000d * 10) / 10d);
            m.put("passi", new ArrayList<Map<String, Object>>());
            processi.put(chiave, m);
        });

        // ---- dove si trovano: pratiche in corso per attività aperta, nell'ordine del modello
        Map<String, List<String>> ordine = new HashMap<>();
        Map<String, Integer> totaleAttivita = new HashMap<>();
        jdbc.query("SELECT D.KEY_ AS chiave, T.TASK_DEF_KEY_ AS passo, MAX(T.NAME_) AS nome, COUNT(DISTINCT RES.PROC_INST_ID_) AS n"
                + VisibilitaPratiche.DA + "JOIN ACT_RU_TASK T ON T.PROC_INST_ID_ = RES.PROC_INST_ID_"
                + dove + " AND RES.END_TIME_ IS NULL GROUP BY D.KEY_, T.TASK_DEF_KEY_", p, rs -> {
            Map<String, Object> proc = processi.get(rs.getString("chiave"));
            if (proc == null) return;
            Map<String, Object> passo = new LinkedHashMap<>();
            passo.put("chiave", rs.getString("passo"));
            passo.put("nome", rs.getString("nome"));
            passo.put("pratiche", rs.getLong("n"));
            @SuppressWarnings("unchecked") List<Map<String, Object>> passi = (List<Map<String, Object>>) proc.get("passi");
            passi.add(passo);
        });
        for (Map<String, Object> proc : processi.values()) {
            List<String> ord = ordine.computeIfAbsent((String) proc.get("chiave"), this::attivitaInOrdine);
            @SuppressWarnings("unchecked") List<Map<String, Object>> passi = (List<Map<String, Object>>) proc.get("passi");
            passi.sort(Comparator.comparingInt(x -> { int i = ord.indexOf((String) x.get("chiave")); return i < 0 ? Integer.MAX_VALUE : i; }));
        }

        // ---- avanzamento stimato delle pratiche in corso
        long[] fasce = new long[4];
        Map<String, long[]> somme = new HashMap<>();   // chiave → [somma percentuali, conteggio]
        p.addValue("maxAvanzamento", MAX_PER_AVANZAMENTO);
        jdbc.query("SELECT D.KEY_ AS chiave, RES.PROC_DEF_ID_ AS definizione,"
                + " (SELECT COUNT(DISTINCT H.TASK_DEF_KEY_) FROM ACT_HI_TASKINST H WHERE H.PROC_INST_ID_ = RES.PROC_INST_ID_ AND H.END_TIME_ IS NOT NULL) AS fatte"
                + VisibilitaPratiche.DA + dove + " AND RES.END_TIME_ IS NULL ORDER BY RES.START_TIME_ DESC LIMIT :maxAvanzamento", p, rs -> {
            int totale = totaleAttivita.computeIfAbsent(rs.getString("definizione"), this::contaAttivita);
            int pct = avanzamento(rs.getInt("fatte"), totale);
            fasce[Math.min(3, pct / 25)]++;
            long[] s = somme.computeIfAbsent(rs.getString("chiave"), k -> new long[2]);
            s[0] += pct; s[1]++;
        });
        somme.forEach((k, s) -> {
            Map<String, Object> proc = processi.get(k);
            if (proc != null && s[1] > 0) proc.put("avanzamentoMedio", Math.round((double) s[0] / s[1]));
        });

        // ---- aperte e chiuse per settimana
        long[] aperte = new long[SETTIMANE], chiuse = new long[SETTIMANE];
        jdbc.query("SELECT RES.START_TIME_ AS inizio, RES.END_TIME_ AS fine" + VisibilitaPratiche.DA + dove
                + " AND (RES.START_TIME_ >= :daSettimane OR RES.END_TIME_ >= :daSettimane)", p, rs -> {
            int a = settimana(rs.getTimestamp("inizio"), primaSettimana, zona);
            if (a >= 0) aperte[a]++;
            int c = settimana(rs.getTimestamp("fine"), primaSettimana, zona);
            if (c >= 0) chiuse[c]++;
        });
        List<Map<String, Object>> settimane = new ArrayList<>();
        for (int i = 0; i < SETTIMANE; i++) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("dal", primaSettimana.plusWeeks(i).toString());
            s.put("aperte", aperte[i]);
            s.put("chiuse", chiuse[i]);
            settimane.add(s);
        }

        // ---- termini e solleciti dei processi (timer) già passati o nei prossimi giorni, per giorno
        long[] termini = new long[GIORNI_TERMINI + 1];   // [0] = già passati e non ancora scattati
        LocalDate oggi = LocalDate.ofInstant(ora, zona);
        jdbc.query("SELECT J.DUEDATE_ AS quando" + VisibilitaPratiche.DA + "JOIN ACT_RU_TIMER_JOB J ON J.PROCESS_INSTANCE_ID_ = RES.PROC_INST_ID_"
                + dove + " AND RES.END_TIME_ IS NULL AND J.DUEDATE_ < :fraGiorni", p, rs -> {
            java.sql.Timestamp q = rs.getTimestamp("quando");
            if (q == null) return;
            long giorni = ChronoUnit.DAYS.between(oggi, LocalDate.ofInstant(q.toInstant(), zona));
            termini[(int) Math.max(0, Math.min(GIORNI_TERMINI, giorni))]++;
        });
        List<Map<String, Object>> giorniTermini = new ArrayList<>();
        for (int i = 0; i <= GIORNI_TERMINI; i++) giorniTermini.add(Map.of("giorno", oggi.plusDays(i).toString(), "termini", termini[i]));

        long inCorso = processi.values().stream().mapToLong(m -> (Long) m.get("inCorso")).sum();
        long concluse30 = processi.values().stream().mapToLong(m -> (Long) m.get("concluse30")).sum();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("inCorso", inCorso);
        out.put("concluse30", concluse30);
        out.put("processi", processi.values().stream()
            .sorted(Comparator.comparingLong((Map<String, Object> m) -> (Long) m.get("inCorso")).reversed()).toList());
        out.put("avanzamento", Map.of("da0a25", fasce[0], "da25a50", fasce[1], "da50a75", fasce[2], "da75a100", fasce[3]));
        out.put("settimane", settimane);
        out.put("termini", giorniTermini);
        return out;
    }

    /** Percentuale stimata: attività completate sul totale del modello, al massimo 95 per una pratica in corso. */
    static int avanzamento(int fatte, int totale) {
        if (totale <= 0) return 0;
        return (int) Math.min(95, Math.round(100d * fatte / totale));
    }

    /** Indice della settimana (0 = la più vecchia) di una data, o -1 se fuori dalle ultime SETTIMANE. */
    static int settimana(java.util.Date quando, LocalDate primaSettimana, ZoneId zona) {
        if (quando == null) return -1;
        long giorni = ChronoUnit.DAYS.between(primaSettimana, LocalDate.ofInstant(quando.toInstant(), zona));
        if (giorni < 0) return -1;
        long i = giorni / 7;
        return i < SETTIMANE ? (int) i : -1;
    }

    private int contaAttivita(String processDefinitionId) {
        BpmnModel modello = repositoryService.getBpmnModel(processDefinitionId);
        if (modello == null) return 0;
        List<String> ids = new ArrayList<>();
        for (Process pr : modello.getProcesses()) raccogli(pr.getFlowElements(), ids);
        return ids.size();
    }

    /** Le attività utente dell'ultima versione del processo, nell'ordine in cui sono scritte nel modello. */
    private List<String> attivitaInOrdine(String chiave) {
        ProcessDefinition d = repositoryService.createProcessDefinitionQuery().processDefinitionKey(chiave).latestVersion().singleResult();
        if (d == null) return List.of();
        BpmnModel modello = repositoryService.getBpmnModel(d.getId());
        List<String> ids = new ArrayList<>();
        if (modello != null) for (Process pr : modello.getProcesses()) raccogli(pr.getFlowElements(), ids);
        return ids;
    }

    private static void raccogli(Collection<FlowElement> elementi, List<String> ids) {
        for (FlowElement e : elementi) {
            if (e instanceof UserTask) ids.add(e.getId());
            else if (e instanceof SubProcess s) raccogli(s.getFlowElements(), ids);
        }
    }
}
