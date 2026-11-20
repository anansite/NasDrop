#!/bin/sh
cd "$(dirname "$0")/.." || exit 1
TOKEN=$(printf "protocol=https\nhost=github.com\n\n" | git credential fill 2>/dev/null | sed -n 's/^password=//p')
RUN=$(curl -s -m 20 -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/anansite/NasDrop/actions/runs?per_page=1")
echo "$RUN" | python -c "
import json,sys
d=json.load(sys.stdin)
r=d['workflow_runs'][0]
print('RUN', r['id'], r['status'], r['conclusion'])
open('.ci/runid','w').write(str(r['id']))
"
RID=$(cat .ci/runid)
curl -s -m 20 -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/anansite/NasDrop/actions/runs/$RID/jobs" | python -c "
import json,sys
d=json.load(sys.stdin)
for j in d.get('jobs',[]):
    print('JOB', j['name'], j['status'], j.get('conclusion'))
    for s in j.get('steps',[]):
        print('   ', s['number'], s['name'], '->', s['status'], s.get('conclusion') or '')
"
