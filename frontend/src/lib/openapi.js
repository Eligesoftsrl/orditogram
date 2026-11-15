// Lettura del documento OpenAPI generato da Orditogram, per la pagina "Documentazione API":
// operazioni raggruppate, campi degli schemi, esempi ed esempi di codice pronti da copiare.

const METODI = ['get', 'post', 'put', 'patch', 'delete']

/** Segue un $ref locale ("#/components/schemas/X"). */
export function risolvi(doc, schema) {
  let s = schema
  const visti = new Set()
  while (s && s.$ref && !visti.has(s.$ref)) {
    visti.add(s.$ref)
    s = s.$ref.replace(/^#\//, '').split('/').reduce((o, k) => o?.[k], doc)
  }
  if (s?.allOf) {
    // unisce le parti (proprietà e obbligatori), come fanno i generatori di client
    const unito = { type: 'object', properties: {}, required: [] }
    for (const parte of s.allOf) {
      const r = risolvi(doc, parte)
      Object.assign(unito.properties, r?.properties || {})
      unito.required.push(...(r?.required || []))
      if (r?.title && !unito.title) unito.title = r.title
      if (r?.description && !unito.description) unito.description = r.description
    }
    return unito
  }
  return s || {}
}

/** Nome dello schema puntato da un $ref (per i titoli). */
export const nomeRef = (schema) => schema?.$ref?.split('/').pop() || null

/** Tipo leggibile: "testo", "numero", "data", "sì/no", "elenco di …", "oggetto". */
export function tipoLeggibile(doc, schema) {
  const s = risolvi(doc, schema)
  if (s.const !== undefined) return `sempre ${JSON.stringify(s.const)}`
  if (s.enum) return 'scelta'
  const t = Array.isArray(s.type) ? s.type : [s.type]
  if (t.includes('array')) return `elenco di ${tipoLeggibile(doc, s.items || {})}`
  if (s.format === 'date') return 'data (AAAA-MM-GG)'
  if (s.format === 'date-time') return 'data e ora'
  if (s.contentMediaType && s.contentMediaType !== 'application/json') return 'file'
  const nomi = { string: 'testo', number: 'numero', integer: 'numero intero', boolean: 'sì/no (true/false)', object: 'oggetto' }
  const tipi = t.filter(Boolean).map(x => nomi[x] || x)
  return tipi.length ? tipi.join(' o ') : 'qualsiasi'
}

/** Righe per la tabella dei campi: [{ nome, tipo, obbligatorio, descrizione, valori, figli }]. */
export function campi(doc, schema, profondita = 0) {
  const s = risolvi(doc, schema)
  const obbl = new Set(s.required || [])
  return Object.entries(s.properties || {}).map(([nome, p]) => {
    const r = risolvi(doc, p)
    const figli = profondita < 2 && r.properties && Object.keys(r.properties).length
      ? campi(doc, r, profondita + 1)
      : profondita < 2 && r.type === 'array' && risolvi(doc, r.items || {}).properties
        ? campi(doc, r.items, profondita + 1) : []
    return {
      nome,
      tipo: tipoLeggibile(doc, p),
      obbligatorio: obbl.has(nome),
      descrizione: [r.title && r.title !== nome ? r.title : null, r.description].filter(Boolean).join(' — '),
      valori: r.enum || null,
      figli
    }
  })
}

/** Esempio costruito dallo schema, se il documento non ne ha uno. */
export function esempio(doc, schema, profondita = 0) {
  const s = risolvi(doc, schema)
  if (s.example !== undefined) return s.example
  if (s.examples?.length) return s.examples[0]
  if (s.const !== undefined) return s.const
  if (s.enum?.length) return s.enum[0]
  if (s.oneOf?.length) return esempio(doc, s.oneOf[0], profondita)
  const t = Array.isArray(s.type) ? s.type[0] : s.type
  if (t === 'object' || s.properties) {
    if (profondita > 3) return {}
    const out = {}
    for (const [k, v] of Object.entries(s.properties || {})) out[k] = esempio(doc, v, profondita + 1)
    return out
  }
  if (t === 'array') return profondita > 3 ? [] : [esempio(doc, s.items || {}, profondita + 1)]
  if (s.format === 'date') return '2026-10-15'
  if (s.format === 'date-time') return '2026-10-15T09:30:00Z'
  if (t === 'number' || t === 'integer') return 100
  if (t === 'boolean') return true
  return s.title || 'testo'
}

/**
 * Operazioni del documento, raggruppate per sezione nell'ordine dei tag:
 * [{ tag, descrizione, operazioni: [{ id, metodo, percorso, sommario, descrizione, parametri, corpo, risposte }] }]
 */
export function sezioni(doc) {
  const ops = []
  for (const [percorso, voce] of Object.entries(doc?.paths || {})) {
    for (const m of METODI) {
      const o = voce[m]
      if (!o) continue
      const json = o.requestBody?.content?.['application/json']
      const multipart = o.requestBody?.content?.['multipart/form-data']
      ops.push({
        id: o.operationId || `${m}-${percorso}`,
        metodo: m.toUpperCase(),
        percorso,
        tag: o.tags?.[0] || 'Altro',
        sommario: o.summary || percorso,
        descrizione: o.description || '',
        parametri: o.parameters || [],
        corpo: json || multipart ? {
          schema: (json || multipart).schema,
          esempio: json ? (json.example ?? esempio(doc, json.schema)) : null,
          json: !!json,
          multipart: !!multipart,
          schemaMultipart: multipart?.schema || null
        } : null,
        risposte: Object.entries(o.responses || {}).map(([codice, r]) => ({
          codice, descrizione: r.description, schema: r.content?.['application/json']?.schema || null
        }))
      })
    }
  }
  const ordine = (doc?.tags || []).map(t => t.name)
  const gruppi = new Map()
  for (const nome of ordine) gruppi.set(nome, [])
  for (const o of ops) {
    if (!gruppi.has(o.tag)) gruppi.set(o.tag, [])
    gruppi.get(o.tag).push(o)
  }
  return [...gruppi.entries()].filter(([, l]) => l.length).map(([tag, operazioni]) => ({
    tag, descrizione: doc?.tags?.find(t => t.name === tag)?.description || '', operazioni
  }))
}

/** Sostituisce {id}, {chiave}… nel percorso con i valori indicati (lasciando i segnaposto mancanti). */
export function compilaPercorso(percorso, valori = {}) {
  return percorso.replace(/\{(\w+)\}/g, (m, k) => (valori[k] ? encodeURIComponent(valori[k]) : m))
}

const json = (v) => JSON.stringify(v, null, 2)
const indenta = (s, n) => s.split('\n').map((r, i) => (i === 0 ? r : ' '.repeat(n) + r)).join('\n')

/**
 * Esempio di codice per chiamare un'operazione.
 * lingua: 'curl' | 'javascript' | 'python'; base: indirizzo del server (…/api/v1).
 */
export function codice(lingua, { metodo, url, corpo, multipart = false }) {
  const conCorpo = corpo !== undefined && corpo !== null
  if (lingua === 'curl') {
    const righe = [`curl -X ${metodo} '${url}'`, `  -H 'X-API-Key: $ORDITOGRAM_API_KEY'`]
    if (multipart) {
      righe.push(`  -F 'richiesta=${JSON.stringify(corpo)};type=application/json'`)
      righe.push(`  -F 'documenti=@istanza.pdf'`)
    } else if (conCorpo) {
      righe.push(`  -H 'Content-Type: application/json'`)
      righe.push(`  -d '${json(corpo).replace(/'/g, "'\\''")}'`)
    }
    return righe.join(' \\\n')
  }
  if (lingua === 'javascript') {
    if (multipart) {
      return `const fd = new FormData()
fd.append('richiesta', new Blob([JSON.stringify(${indenta(json(corpo), 0)})], { type: 'application/json' }))
fd.append('documenti', fileInput.files[0])

const r = await fetch('${url}', {
  method: '${metodo}',
  headers: { 'X-API-Key': process.env.ORDITOGRAM_API_KEY },
  body: fd
})
const dati = await r.json()
if (!r.ok) throw new Error(dati.errore.messaggio)`
    }
    return `const r = await fetch('${url}', {
  method: '${metodo}',
  headers: {
    'X-API-Key': process.env.ORDITOGRAM_API_KEY${conCorpo ? ",\n    'Content-Type': 'application/json'" : ''}
  }${conCorpo ? `,\n  body: JSON.stringify(${indenta(json(corpo), 2)})` : ''}
})
const dati = await r.json()
if (!r.ok) throw new Error(dati.errore.messaggio)`
  }
  if (lingua === 'python') {
    const py = (v) => json(v).replace(/\btrue\b/g, 'True').replace(/\bfalse\b/g, 'False').replace(/\bnull\b/g, 'None')
    if (multipart) {
      return `import json, os, requests

r = requests.${metodo.toLowerCase()}(
    "${url}",
    headers={"X-API-Key": os.environ["ORDITOGRAM_API_KEY"]},
    files={
        "richiesta": (None, json.dumps(${indenta(py(corpo), 8)}), "application/json"),
        "documenti": open("istanza.pdf", "rb"),
    },
)
r.raise_for_status()
print(r.json())`
    }
    return `import os, requests

r = requests.${metodo.toLowerCase()}(
    "${url}",
    headers={"X-API-Key": os.environ["ORDITOGRAM_API_KEY"]},${conCorpo ? `\n    json=${indenta(py(corpo), 4)},` : ''}
)
r.raise_for_status()
print(r.json())`
  }
  return ''
}

/** Come verificare la firma degli avvisi, nei linguaggi più usati. */
export const VERIFICA_FIRMA = {
  'Node.js': `import crypto from 'node:crypto'

// corpo: il testo ricevuto così com'è (prima di fare JSON.parse)
export function firmaValida(corpo, intestazione, segreto, tolleranza = 300) {
  const parti = Object.fromEntries(intestazione.split(',').map(p => p.trim().split('=')))
  const t = Number(parti.t)
  if (!t || Math.abs(Date.now() / 1000 - t) > tolleranza) return false
  const atteso = crypto.createHmac('sha256', segreto).update(\`\${t}.\${corpo}\`).digest('hex')
  return parti.v1?.length === atteso.length &&
    crypto.timingSafeEqual(Buffer.from(atteso), Buffer.from(parti.v1))
}`,
  Python: `import hashlib, hmac, time

def firma_valida(corpo: bytes, intestazione: str, segreto: str, tolleranza: int = 300) -> bool:
    parti = dict(p.strip().split("=", 1) for p in intestazione.split(","))
    t = int(parti.get("t", "0"))
    if abs(time.time() - t) > tolleranza:
        return False
    atteso = hmac.new(segreto.encode(), f"{t}.".encode() + corpo, hashlib.sha256).hexdigest()
    return hmac.compare_digest(atteso, parti.get("v1", ""))`,
  Java: `boolean firmaValida(String corpo, String intestazione, String segreto) throws Exception {
    Map<String, String> p = new HashMap<>();
    for (String s : intestazione.split(",")) { String[] kv = s.trim().split("=", 2); p.put(kv[0], kv[1]); }
    long t = Long.parseLong(p.get("t"));
    if (Math.abs(System.currentTimeMillis() / 1000 - t) > 300) return false;
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(segreto.getBytes(UTF_8), "HmacSHA256"));
    String atteso = HexFormat.of().formatHex(mac.doFinal((t + "." + corpo).getBytes(UTF_8)));
    return MessageDigest.isEqual(atteso.getBytes(UTF_8), p.get("v1").getBytes(UTF_8));
}`,
  PHP: `function firma_valida(string $corpo, string $intestazione, string $segreto, int $tolleranza = 300): bool {
    parse_str(str_replace(',', '&', $intestazione), $p);
    $t = (int)($p['t'] ?? 0);
    if (abs(time() - $t) > $tolleranza) return false;
    $atteso = hash_hmac('sha256', $t . '.' . $corpo, $segreto);
    return hash_equals($atteso, $p['v1'] ?? '');
}`
}
