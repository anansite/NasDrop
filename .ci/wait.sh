#!/bin/sh
cd "$(dirname "$0")/.." || exit 1
TOKEN=$(printf "protocol=https\nhost=github.com\n\n" | git credential fill 2>/dev/null | sed -n 's/^password=//p')
i=0
while [ $i -lt 60 ]; do
  curl -s -m 20 -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json" \
    "https://api.github.com/repos/anansite/NasDrop/actions/runs?per_page=1" -o .ci/r.json
  OUT=$(python -c "
import json
d=json.load(open('.ci/r.json'))
r=d['workflow_runs'][0]
print(r['id'], r['status'], r['conclusion'])
")
  echo "$(date +%H:%M:%S) $OUT"
  case "$OUT" in
    *" completed "*) echo "DONE: $OUT"; exit 0 ;;
  esac
  i=$((i+1))
  sleep 30
done
echo "TIMEOUT"
