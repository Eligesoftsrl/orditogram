package com.eligesoft.orditogram.calendario;

import org.activiti.engine.ActivitiException;
import org.activiti.engine.impl.calendar.*;
import org.activiti.engine.runtime.ClockReader;
import org.activiti.spring.SpringProcessEngineConfiguration;
import org.activiti.spring.boot.ProcessEngineConfigurationConfigurer;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aggiunge al motore due calendari, usabili dai timer con activiti:businessCalendarName:
 *
 *  "lavorativi": P30D = 30 giorni lavorativi (salta fine settimana e festività);
 *                le altre durate e le date slittano al primo giorno lavorativo.
 *  "proroga":    giorni di calendario, ma se il termine cade in un giorno non lavorativo
 *                slitta al primo giorno lavorativo successivo (come i termini di legge).
 */
@Component
public class CalendariMotore implements ProcessEngineConfigurationConfigurer {

    public static final String LAVORATIVI = "lavorativi";
    public static final String PROROGA = "proroga";
    private static final Pattern GIORNI = Pattern.compile("^P(\\d+)D$");

    private final CalendarioProperties proprieta;

    public CalendariMotore(CalendarioProperties proprieta) {
        this.proprieta = proprieta;
    }

    @Override
    public void configure(SpringProcessEngineConfiguration conf) {
        // l'orologio del motore esiste solo dopo l'inizializzazione: lo si legge al momento del calcolo
        ClockReader orologio = new ClockReader() {
            public Date getCurrentTime() { return conf.getClock().getCurrentTime(); }
            public Calendar getCurrentCalendar() { return conf.getClock().getCurrentCalendar(); }
            public Calendar getCurrentCalendar(TimeZone tz) { return conf.getClock().getCurrentCalendar(tz); }
            public TimeZone getCurrentTimeZone() { return conf.getClock().getCurrentTimeZone(); }
        };
        MapBusinessCalendarManager m = new MapBusinessCalendarManager();
        m.addBusinessCalendar(DurationBusinessCalendar.NAME, new DurationBusinessCalendar(orologio));
        m.addBusinessCalendar(DueDateBusinessCalendar.NAME, new DueDateBusinessCalendar(orologio));
        m.addBusinessCalendar(CycleBusinessCalendar.NAME, new CycleBusinessCalendar(orologio));
        m.addBusinessCalendar(LAVORATIVI, new Calendario(orologio, true));
        m.addBusinessCalendar(PROROGA, new Calendario(orologio, false));
        conf.setBusinessCalendarManager(m);
    }

    private final class Calendario extends BusinessCalendarImpl {
        private final boolean giorniLavorativi;

        Calendario(ClockReader orologio, boolean giorniLavorativi) {
            super(orologio);
            this.giorniLavorativi = giorniLavorativi;
        }

        @Override
        public Date resolveDuedate(String descrizione, int maxIterations) {
            CalendarioLavorativo cal = proprieta.calendario();
            ZoneId zona = proprieta.zoneId();
            String s = descrizione == null ? "" : descrizione.trim();
            try {
                Matcher g = GIORNI.matcher(s);
                if (giorniLavorativi && g.matches()) {
                    ZonedDateTime ora = ZonedDateTime.ofInstant(clockReader.getCurrentTime().toInstant(), zona);
                    return Date.from(cal.aggiungiGiorniLavorativi(ora, Integer.parseInt(g.group(1))).toInstant());
                }
                Date base = s.startsWith("P")
                    ? new DurationHelper(s, clockReader).getDateAfter()
                    : new DueDateBusinessCalendar(clockReader).resolveDuedate(s);
                return Date.from(cal.proroga(ZonedDateTime.ofInstant(base.toInstant(), zona)).toInstant());
            } catch (ActivitiException e) {
                throw e;
            } catch (Exception e) {
                throw new ActivitiException("Termine non valido: " + descrizione + " (" + e.getMessage() + ")", e);
            }
        }
    }
}
