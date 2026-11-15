#!/usr/bin/env bash
# Backup di Orditogram: database (pg_dump) e documenti, in una cartella con data e ora.
#
#   Con Docker Compose (dalla cartella del progetto):   strumenti/backup.sh
#   Senza Docker:   MODO=locale DB_HOST=localhost DOCUMENTI=backend/data/documenti strumenti/backup.sh
#
# Variabili: DESTINAZIONE (default ./backup), DB_NOME, DB_UTENTE (default orditogram), DB_PASSWORD (solo modo locale)
set -euo pipefail

MODO=${MODO:-docker}
DESTINAZIONE=${DESTINAZIONE:-./backup}
DB_NOME=${DB_NOME:-orditogram}
DB_UTENTE=${DB_UTENTE:-orditogram}
QUANDO=$(date +%Y%m%d-%H%M%S)
CARTELLA="$DESTINAZIONE/orditogram-$QUANDO"
mkdir -p "$CARTELLA"

echo "Backup di Orditogram in $CARTELLA"
if [ "$MODO" = "docker" ]; then
  docker compose exec -T postgres pg_dump -U "$DB_UTENTE" -d "$DB_NOME" -F c > "$CARTELLA/database.dump"
  docker compose exec -T backend tar -C /app/data -czf - documenti > "$CARTELLA/documenti.tar.gz"
else
  PGPASSWORD=${DB_PASSWORD:-orditogram} pg_dump -h "${DB_HOST:-localhost}" -U "$DB_UTENTE" -d "$DB_NOME" -F c -f "$CARTELLA/database.dump"
  tar -C "$(dirname "${DOCUMENTI:-backend/data/documenti}")" -czf "$CARTELLA/documenti.tar.gz" "$(basename "${DOCUMENTI:-backend/data/documenti}")"
fi
( cd "$CARTELLA" && sha256sum database.dump documenti.tar.gz > SHA256SUMS )
echo "Fatto: $(du -sh "$CARTELLA" | cut -f1). Conserva la cartella fuori dal server."
