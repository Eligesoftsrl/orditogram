package com.eligesoft.orditogram.bozza;

import com.eligesoft.orditogram.form.FormService;
import com.eligesoft.orditogram.process.BpmnResourceService;
import com.eligesoft.orditogram.user.CurrentUserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.StartEvent;
import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.repository.ProcessDefinition;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Bozze dei processi (lavoro in corso nell'editor) e pacchetti di processo:
 * un file .zip con BPMN e form, per spostare un processo tra installazioni (es. dal consulente al cliente,
 * oppure da test a produzione). L'importazione crea una bozza: si controlla e si pubblica dall'editor.
 */
@RestController
@RequestMapping("/api/admin")
public class BozzaController {

    private final com.eligesoft.orditogram.registro.RegistroService registro;

    private static final int MAX_FILE_PACCHETTO = 200;
    private static final long MAX_BYTE_PACCHETTO = 20L * 1024 * 1024;

    private final ProcessoBozzaRepository bozze;
    private final RepositoryService repositoryService;
    private final BpmnResourceService bpmn;
    private final FormService forms;
    private final CurrentUserService currentUser;
    private final ObjectMapper mapper;

    public BozzaController(ProcessoBozzaRepository bozze, RepositoryService repositoryService, BpmnResourceService bpmn,
                           FormService forms, CurrentUserService currentUser, ObjectMapper mapper,
            com.eligesoft.orditogram.registro.RegistroService registro) {
        this.registro = registro;
        this.bozze = bozze;
        this.repositoryService = repositoryService;
        this.bpmn = bpmn;
        this.forms = forms;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    // ================================================================= bozze

    @GetMapping("/bozze")
    public List<Map<String, Object>> elenco() {
        return bozze.findAll().stream()
            .sorted(Comparator.comparing(ProcessoBozza::getAggiornatoIl, Comparator.nullsLast(Comparator.reverseOrder())))
            .map(b -> {
                Map<String, Object> m = riepilogo(b);
                m.put("inProduzione", versioneInProduzione(b.getChiave()));
                return m;
            }).toList();
    }

    @GetMapping("/bozze/{chiave}")
    public Map<String, Object> leggi(@PathVariable String chiave) {
        ProcessoBozza b = bozze.findByChiave(chiave)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nessuna bozza per " + chiave));
        Map<String, Object> m = riepilogo(b);
        m.put("xml", b.getXml());
        m.put("inProduzione", versioneInProduzione(chiave));
        return m;
    }

    /** Salva (crea o aggiorna) la bozza. Body: { "xml": "...", "nome": "..." }. */
    @PutMapping("/bozze/{chiave}")
    @Transactional
    public Map<String, Object> salva(@PathVariable String chiave, @RequestBody Map<String, String> body) {
        if (!chiave.matches("[A-Za-z_][\\w.-]{0,199}")) throw bad("Identificativo del processo non valido");
        String xml = body.get("xml");
        if (xml == null || xml.isBlank()) throw bad("Bozza vuota");
        ProcessoBozza b = bozze.findByChiave(chiave).orElseGet(ProcessoBozza::new);
        b.setChiave(chiave);
        b.setNome(body.getOrDefault("nome", chiave));
        b.setXml(xml);
        b.setAggiornatoDa(currentUser.username());
        b.setAggiornatoIl(OffsetDateTime.now());
        return riepilogo(bozze.save(b));
    }

    @DeleteMapping("/bozze/{chiave}")
    @Transactional
    public void scarta(@PathVariable String chiave) {
        bozze.findByChiave(chiave).ifPresent(b -> {
            bozze.delete(b);
            registro.registra("BOZZA_SCARTATA", chiave, null);
        });
    }

    // ================================================================= pacchetti

    /**
     * Esporta il processo con i suoi form. fonte: "bozza" | "produzione" | "auto" (bozza se c'è, altrimenti produzione).
     * Struttura dello zip: orditogram-pacchetto.json, processo.bpmn20.xml, form/<formKey>.json
     */
    @GetMapping("/pacchetti/{chiave}")
    public ResponseEntity<byte[]> esporta(@PathVariable String chiave,
                                          @RequestParam(defaultValue = "auto") String fonte) throws IOException {
        String xml = null;
        String origine = null;
        if (!"produzione".equals(fonte)) {
            var b = bozze.findByChiave(chiave);
            if (b.isPresent()) { xml = b.get().getXml(); origine = "bozza"; }
        }
        if (xml == null && !"bozza".equals(fonte)) {
            ProcessDefinition d = repositoryService.createProcessDefinitionQuery().processDefinitionKey(chiave).latestVersion().singleResult();
            if (d != null) { xml = bpmn.xml(d.getId()); origine = "produzione v" + d.getVersion(); }
        }
        if (xml == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Processo non trovato: " + chiave);

        Process p = bpmn.parse(xml.getBytes(StandardCharsets.UTF_8)).getMainProcess();
        Set<String> formKeys = new LinkedHashSet<>();
        p.findFlowElementsOfType(StartEvent.class).forEach(s -> { if (s.getFormKey() != null) formKeys.add(s.getFormKey()); });
        p.findFlowElementsOfType(UserTask.class, true).forEach(t -> { if (t.getFormKey() != null) formKeys.add(t.getFormKey()); });

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        List<String> inclusi = new ArrayList<>();
        try (ZipOutputStream zip = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            scrivi(zip, "processo.bpmn20.xml", xml);
            for (String fk : formKeys) {
                JsonNode f = forms.load(fk);
                if (f == null) continue;
                scrivi(zip, "form/" + fk + ".json", f.toPrettyString());
                inclusi.add(fk);
            }
            ObjectNode manifest = mapper.createObjectNode();
            manifest.put("formato", 1);
            manifest.put("chiave", chiave);
            manifest.put("nome", Objects.toString(p.getName(), chiave));
            manifest.put("origine", origine);
            manifest.put("esportatoDa", currentUser.username());
            manifest.put("esportatoIl", OffsetDateTime.now().toString());
            manifest.putPOJO("form", inclusi);
            scrivi(zip, "orditogram-pacchetto.json", manifest.toPrettyString());
        }
        registro.registra("PACCHETTO_ESPORTATO", chiave, "Fonte: " + origine);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/zip"))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(chiave + ".orditogram.zip", StandardCharsets.UTF_8).build().toString())
            .body(bos.toByteArray());
    }

    /**
     * Importa un pacchetto: salva i form e crea (o sostituisce) la bozza del processo.
     * Se ci sono form con lo stesso nome ma contenuto diverso e sovrascrivi=false, risponde 409 con l'elenco.
     */
    @PostMapping(value = "/pacchetti", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<Map<String, Object>> importa(@RequestParam("file") MultipartFile file,
                                                       @RequestParam(defaultValue = "false") boolean sovrascrivi) throws IOException {
        String xml = null;
        Map<String, String> formFiles = new LinkedHashMap<>();
        int n = 0;
        long totale = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(file.getBytes()), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                if (++n > MAX_FILE_PACCHETTO) throw bad("Il pacchetto contiene troppi file");
                String nome = e.getName().replace('\\', '/');
                if (nome.contains("..")) throw bad("Percorso non valido nel pacchetto: " + nome);
                byte[] dati = zip.readNBytes((int) Math.min(MAX_BYTE_PACCHETTO, Integer.MAX_VALUE - 8));
                totale += dati.length;
                if (totale > MAX_BYTE_PACCHETTO) throw bad("Il pacchetto è troppo grande");
                String testo = new String(dati, StandardCharsets.UTF_8);
                String base = nome.substring(nome.lastIndexOf('/') + 1);
                if (base.endsWith(".bpmn20.xml") || base.endsWith(".bpmn")) {
                    if (xml != null) throw bad("Il pacchetto contiene più di un processo");
                    xml = testo;
                } else if (nome.startsWith("form/") && base.endsWith(".json")) {
                    formFiles.put(base.substring(0, base.length() - 5), testo);
                }
            }
        } catch (java.util.zip.ZipException ex) {
            throw bad("Il file non è un pacchetto .zip valido");
        }
        if (xml == null) throw bad("Il pacchetto non contiene un file BPMN");

        Process p = bpmn.parse(xml.getBytes(StandardCharsets.UTF_8)).getMainProcess();
        if (p == null || p.getId() == null) throw bad("Il BPMN del pacchetto non contiene un processo");

        // validazione e conflitti
        Map<String, JsonNode> validi = new LinkedHashMap<>();
        List<String> conflitti = new ArrayList<>();
        for (var f : formFiles.entrySet()) {
            JsonNode nuovo = forms.validate(f.getKey(), f.getValue());
            validi.put(f.getKey(), nuovo);
            JsonNode esistente = forms.load(f.getKey());
            if (esistente != null && !esistente.equals(nuovo)) conflitti.add(f.getKey());
        }
        if (!conflitti.isEmpty() && !sovrascrivi) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "message", "Questi form esistono già con un contenuto diverso: " + String.join(", ", conflitti),
                "conflitti", conflitti));
        }

        for (var f : validi.entrySet()) forms.save(f.getKey(), f.getValue().toString(), currentUser.username());
        ProcessoBozza b = bozze.findByChiave(p.getId()).orElseGet(ProcessoBozza::new);
        b.setChiave(p.getId());
        b.setNome(Objects.toString(p.getName(), p.getId()));
        b.setXml(xml);
        b.setAggiornatoDa(currentUser.username());
        b.setAggiornatoIl(OffsetDateTime.now());
        bozze.save(b);

        registro.registra("PACCHETTO_IMPORTATO", p.getId(),
            "Form: " + (validi.isEmpty() ? "nessuno" : String.join(", ", validi.keySet()))
                + (conflitti.isEmpty() ? "" : "; sovrascritti: " + String.join(", ", conflitti)));
        return ResponseEntity.ok(Map.of("chiave", p.getId(), "nome", b.getNome(),
            "formSalvati", validi.keySet(), "sovrascritti", conflitti));
    }

    // ================================================================= supporto

    private Map<String, Object> riepilogo(ProcessoBozza b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("chiave", b.getChiave());
        m.put("nome", b.getNome());
        m.put("aggiornatoDa", currentUser.fullNameOf(b.getAggiornatoDa()));
        m.put("aggiornatoIl", b.getAggiornatoIl());
        return m;
    }

    private Integer versioneInProduzione(String chiave) {
        ProcessDefinition d = repositoryService.createProcessDefinitionQuery().processDefinitionKey(chiave).latestVersion().singleResult();
        return d == null ? null : d.getVersion();
    }

    private static void scrivi(ZipOutputStream zip, String nome, String contenuto) throws IOException {
        zip.putNextEntry(new ZipEntry(nome));
        zip.write(contenuto.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
