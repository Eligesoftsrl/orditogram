#!/usr/bin/env bash
# Ripristino di un backup fatto con strumenti/backup.sh. ATTENZIONE: sostituisce i dati attuali.
#
#   strumenti/ripristino.sh backup/orditogram-20261004-221500
#   MODO=locale DB_HOST=localhost DOCUMENTI=backend/data/documenti strumenti/ripristino.sh <cartella>
set -euo pipefail

CARTELLA=${1:?Indica la cartella del backup}
MODO=${MODO:-docker}
DB_NOME=${DB_NOME:-orditogram}
DB_UTENTE=${DB_UTENTE:-orditogram}

( cd "$CARTELLA" && sha256sum -c SHA256SUMS )
read -r -p "Sostituire database e documenti attuali con il backup $CARTELLA? Scrivi SI: " ok
[ "$ok" = "SI" ] || { echo "Annullato."; exit 1; }

if [ "$MODO" = "docker" ]; then
  docker compose stop backend
  docker compose exec -T postgres pg_restore -U "$DB_UTENTE" -d "$DB_NOME" --clean --if-exists --no-owner < "$CARTELLA/database.dump"
  docker compose run --rm --no-deps --entrypoint sh backend -c "rm -rf /app/data/documenti && tar -C /app/data -xzf -" < "$CARTELLA/documenti.tar.gz"
  docker compose start backend
else
  PGPASSWORD=${DB_PASSWORD:-orditogram} pg_restore -h "${DB_HOST:-localhost}" -U "$DB_UTENTE" -d "$DB_NOME" --clean --if-exists --no-owner "$CARTELLA/database.dump"
  DOC=${DOCUMENTI:-backend/data/documenti}
  rm -rf "$DOC" && mkdir -p "$(dirname "$DOC")" && tar -C "$(dirname "$DOC")" -xzf "$CARTELLA/documenti.tar.gz"
fi
echo "Ripristino completato."
