-- =====================================================================================
--  Orditogram: elimina TUTTE le pratiche (in corso e concluse) e i dati collegati.
--
--  Restano: processi pubblicati e loro versioni, bozze, form, utenti e gruppi.
--  Si eliminano: istanze e storico del motore Activiti, attività, timer, variabili,
--                storico eventi di Orditogram, notifiche, documenti e versioni (solo i record:
--                i file su disco vanno cancellati a parte, vedi in fondo).
--
--  Uso: ferma il backend, poi esegui tutto lo script (in DBeaver: Esegui script, Alt+X)
--  sul database "bpm". È in una transazione: se qualcosa va storto non cambia nulla.
--  Consigliato prima un backup:  pg_dump -U bpm -d bpm -F c -f bpm-prima-della-pulizia.dump
-- =====================================================================================

BEGIN;

-- contenuti binari legati alle pratiche (variabili serializzate, errori dei job): si annotano prima
CREATE TEMP TABLE iter_bytearray_da_eliminare ON COMMIT DROP AS
SELECT bytearray_id_ AS id FROM act_ru_variable WHERE bytearray_id_ IS NOT NULL
UNION SELECT exception_stack_id_ FROM act_ru_job WHERE exception_stack_id_ IS NOT NULL AND process_instance_id_ IS NOT NULL
UNION SELECT exception_stack_id_ FROM act_ru_timer_job WHERE exception_stack_id_ IS NOT NULL AND process_instance_id_ IS NOT NULL
UNION SELECT exception_stack_id_ FROM act_ru_suspended_job WHERE exception_stack_id_ IS NOT NULL AND process_instance_id_ IS NOT NULL
UNION SELECT exception_stack_id_ FROM act_ru_deadletter_job WHERE exception_stack_id_ IS NOT NULL AND process_instance_id_ IS NOT NULL
UNION SELECT bytearray_id_ FROM act_hi_varinst WHERE bytearray_id_ IS NOT NULL
UNION SELECT bytearray_id_ FROM act_hi_detail  WHERE bytearray_id_ IS NOT NULL;

-- ---------------------------------------------------------------- motore: dati in esecuzione
DELETE FROM act_ru_integration;
DELETE FROM act_ru_variable;
DELETE FROM act_ru_identitylink  WHERE proc_inst_id_ IS NOT NULL OR task_id_ IS NOT NULL;   -- restano "chi può avviare"
DELETE FROM act_ru_event_subscr  WHERE proc_inst_id_ IS NOT NULL OR execution_id_ IS NOT NULL;
DELETE FROM act_ru_job           WHERE process_instance_id_ IS NOT NULL OR execution_id_ IS NOT NULL;
DELETE FROM act_ru_timer_job     WHERE process_instance_id_ IS NOT NULL OR execution_id_ IS NOT NULL;
DELETE FROM act_ru_suspended_job WHERE process_instance_id_ IS NOT NULL OR execution_id_ IS NOT NULL;
DELETE FROM act_ru_deadletter_job WHERE process_instance_id_ IS NOT NULL OR execution_id_ IS NOT NULL;
DELETE FROM act_ru_task;
DELETE FROM act_ru_execution;

-- ---------------------------------------------------------------- motore: storico
DELETE FROM act_hi_detail;
DELETE FROM act_hi_varinst;
DELETE FROM act_hi_identitylink;
DELETE FROM act_hi_comment;
DELETE FROM act_hi_attachment;
DELETE FROM act_hi_taskinst;
DELETE FROM act_hi_actinst;
DELETE FROM act_hi_procinst;
DELETE FROM act_evt_log;

DELETE FROM act_ge_bytearray
 WHERE deployment_id_ IS NULL                           -- mai le risorse dei processi pubblicati
   AND id_ IN (SELECT id FROM iter_bytearray_da_eliminare);

-- ---------------------------------------------------------------- Orditogram
DELETE FROM documento_versione;
DELETE FROM documento;
DELETE FROM pratica_evento;
DELETE FROM notifica;

-- riepilogo: tutti zero
SELECT 'pratiche in corso' AS cosa, count(*) AS restano FROM act_ru_execution
UNION ALL SELECT 'pratiche nello storico', count(*) FROM act_hi_procinst
UNION ALL SELECT 'attività', count(*) FROM act_ru_task
UNION ALL SELECT 'documenti', count(*) FROM documento
UNION ALL SELECT 'eventi', count(*) FROM pratica_evento
UNION ALL SELECT 'notifiche', count(*) FROM notifica;

COMMIT;

-- =====================================================================================
--  File dei documenti: dopo lo script cancella il contenuto della cartella indicata in
--  application.yml (app.documenti.dir, di default backend/data/documenti), per esempio:
--    macOS/Linux:  rm -rf backend/data/documenti/*
--    Windows:      rmdir /s /q backend\data\documenti   (verrà ricreata all'avvio)
-- =====================================================================================
