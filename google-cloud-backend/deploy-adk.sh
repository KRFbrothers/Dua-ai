#!/usr/bin/env bash
# Plan is read-only. Deploy stages at 0% traffic; promotion/rollback are explicit.
set -euo pipefail
adk_project="${PROJECT_ID:-global-healer-m6shk}"
adk_region="${REGION:-us-central1}"
adk_service="${SERVICE:-dua-ai-engine}"
adk_action="${1:-plan}"
adk_snapshot="${ADK_SNAPSHOT:-/tmp/dua-adk-rollout.json}"
adk_root="$(cd "$(dirname "$0")/.." && pwd)"
case "$adk_action" in
  plan)
    gcloud run services describe "$adk_service" --project "$adk_project" --region "$adk_region" --format=json |
      python3 -c 'import json,sys; d=json.load(sys.stdin); print(json.dumps({"service":d["metadata"]["name"],"readyRevision":d["status"].get("latestReadyRevisionName"),"traffic":d["status"].get("traffic",[])}))'
    ;;
  deploy)
    test -n "${ADK_IMAGE:-}" || { echo 'Set ADK_IMAGE to the intended Artifact Registry image URL.' >&2; exit 2; }
    test -z "$(git -C "$adk_root" status --porcelain)" || { echo 'Use a clean checkout.' >&2; exit 2; }
    test "$(git -C "$adk_root" branch --show-current)" = main || { echo 'Deploy only merged main.' >&2; exit 2; }
    test "$(git -C "$adk_root" rev-parse HEAD)" = "$(git -C "$adk_root" rev-parse origin/main)" || { echo 'Fetch and fast-forward main first.' >&2; exit 2; }
    gcloud run services describe "$adk_service" --project "$adk_project" --region "$adk_region" --format=json |
      python3 -c 'import json,sys; d=json.load(sys.stdin); print(json.dumps({"service":d["metadata"]["name"],"project":sys.argv[1],"region":sys.argv[2],"traffic":d["status"]["traffic"]}))' "$adk_project" "$adk_region" > "$adk_snapshot"
    # Existing secret/env bindings and IAM stay on the service. No --set-* reset.
    gcloud builds submit "$adk_root/google-cloud-backend" --project "$adk_project" \
      --config "$adk_root/google-cloud-backend/cloudbuild-adk.yaml" \
      --substitutions "_IMAGE=$ADK_IMAGE,_SOURCE_SHA=$(git -C "$adk_root" rev-parse HEAD)"
    gcloud run deploy "$adk_service" --project "$adk_project" --region "$adk_region" --image "$ADK_IMAGE" \
      --timeout 900 --no-traffic --tag adk-candidate --update-env-vars "AGENT_ENGINE=adk,ADK_PRODUCTION=1,DUA_BUILD_SHA=$(git -C "$adk_root" rev-parse HEAD)"
    echo "Candidate staged at 0% traffic. Rollback snapshot: $adk_snapshot"
    echo 'Verify candidate owner-only capabilities, migrations and the intended live flow before promote.'
    ;;
  promote)
    test -n "${ADK_VERIFIED_REVISION:-}" || { echo 'Set ADK_VERIFIED_REVISION after checking the candidate.' >&2; exit 2; }
    [[ "$ADK_VERIFIED_REVISION" =~ ^[a-z][a-z0-9-]{0,62}$ ]] || exit 2
    gcloud run services update-traffic "$adk_service" --project "$adk_project" --region "$adk_region" \
      --to-revisions "$ADK_VERIFIED_REVISION=100"
    ;;
  rollback)
    adk_traffic="$(python3 - "$adk_snapshot" "$adk_service" "$adk_project" "$adk_region" <<'PY'
import json,re,sys
with open(sys.argv[1]) as f:d=json.load(f)
if (d.get('service'),d.get('project'),d.get('region'))!=tuple(sys.argv[2:]):raise SystemExit('Snapshot target mismatch')
traffic=[(r.get('revisionName'),r.get('percent',0)) for r in d.get('traffic',[]) if r.get('percent',0)>0]
if not traffic or sum(n for _,n in traffic)!=100 or any(not isinstance(r,str) or not re.fullmatch('[a-z][a-z0-9-]{0,62}',r) or not isinstance(n,int) or not 0<n<=100 for r,n in traffic):raise SystemExit('Invalid rollback traffic')
print(','.join(r+'='+str(n) for r,n in traffic))
PY
)"
    gcloud run services update-traffic "$adk_service" --project "$adk_project" --region "$adk_region" --to-revisions "$adk_traffic"
    ;;
  *) echo 'Usage: deploy-adk.sh plan|deploy|promote|rollback' >&2; exit 2 ;;
esac
