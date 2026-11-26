#!/usr/bin/env bash
# Avvia il servizio ISI per una dimostrazione: tutti i periodi del bando aperti da ieri per 60 giorni
# (compilazione, codice, sportello), così la demo funziona in qualsiasi giorno dell'anno.
# Mai in produzione: lì il servizio parte senza ISI_DEMO e usa il calendario vero dell'annualità.
#
#   ./avvia-demo.sh            usa la credenziale "prova" (la stessa da dare a Orditogram)
#   ./avvia-demo.sh --da-zero  cancella prima i registri della demo
set -euo pipefail
cd "$(dirname "$0")"

if [[ "${1:-}" == "--da-zero" ]]; then
  rm -rf dati/demo*
  echo "Registri della demo cancellati."
fi
if [[ ! -f target/servizio-isi.jar ]] || [[ -n "$(find src pom.xml -newer target/servizio-isi.jar -print -quit)" ]]; then
  ./mvnw -B -q package -DskipTests
fi

export ISI_DEMO=1
export ISI_TOKEN="${ISI_TOKEN:-prova}"
export ISI_DB="${ISI_DB:-jdbc:h2:file:./dati/demo}"
echo "Servizio ISI in modalità demo su http://127.0.0.1:${ISI_PORTA:-8095} (credenziale: $ISI_TOKEN)"
exec java -jar target/servizio-isi.jar
