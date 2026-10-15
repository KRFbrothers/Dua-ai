#!/usr/bin/env bash
# Deploy only the Node API. No .env is loaded, no secret value is read by this script.
set -euo pipefail

: "${PROJECT_ID:?Set PROJECT_ID to your Google Cloud project ID}"
if [[ ! "$PROJECT_ID" =~ ^[a-z][a-z0-9-]{4,28}[a-z0-9]$ ]]; then
  echo "PROJECT_ID must be a Google Cloud project ID, not its display name or number." >&2
  exit 1
fi
deadline="${DUA_CHAT_DEADLINE_SECONDS:-780}"
if [[ ! "$deadline" =~ ^[0-9]{2,3}$ ]] || (( 10#$deadline < 10 || 10#$deadline > 790 )); then
  echo "DUA_CHAT_DEADLINE_SECONDS must be 10–790 (default 780; Cloud Run timeout is 900)." >&2
  exit 1
fi
command -v gcloud >/dev/null || { echo "Install Google Cloud CLI or run this script in Cloud Shell." >&2; exit 1; }
web_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
region=us-central1
service=dua-api
runtime_account="dua-api@${PROJECT_ID}.iam.gserviceaccount.com"
build_account="dua-api-build@${PROJECT_ID}.iam.gserviceaccount.com"
image="${region}-docker.pkg.dev/${PROJECT_ID}/${service}/api:$(date -u +%Y%m%d%H%M%S)"

echo "Enabling Cloud Run, Cloud Build, Artifact Registry, IAM and Secret Manager APIs..."
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com \
  iam.googleapis.com secretmanager.googleapis.com --project "$PROJECT_ID" --quiet

# Only names from the checked-in template can become runtime settings. Commented tuning
# entries are supported too. PORT is supplied by Cloud Run; login/deadline are set below.
declare -A secret_names=()
# Capture the command status: process substitution would hide list failures.
available_secrets="$(gcloud secrets list --project "$PROJECT_ID" --format='value(name)')"
while IFS= read -r name; do
  [[ -n "$name" ]] && secret_names["${name##*/}"]=1
done <<< "$available_secrets"
for required in SUPABASE_URL SUPABASE_ANON_KEY DUA_ALLOWED_EMAIL; do
  if [[ ! -v "secret_names[$required]" ]]; then
    echo "Missing Secret Manager secret: $required. Create it with the existing backend's value, then retry." >&2
    exit 1
  fi
done

secret_refs=()
for_settings=()
while IFS= read -r line; do
  if [[ "$line" =~ ^#?[[:space:]]*([A-Z][A-Z0-9_]*)= ]]; then
    name="${BASH_REMATCH[1]}"
    case "$name" in PORT|DUA_REQUIRE_LOGIN|DUA_CHAT_DEADLINE_SECONDS) continue ;; esac
    if [[ -v "secret_names[$name]" ]]; then
      # Pin a version so instances of one revision cannot silently load different values.
      version="$(gcloud secrets versions list "$name" --project "$PROJECT_ID" --filter='state=ENABLED' --sort-by='~createTime' --limit=1 --format='value(name)')"
      version="${version##*/}"
      if [[ ! "$version" =~ ^[0-9]+$ ]]; then
        echo "Secret $name needs an enabled version before deployment." >&2
        exit 1
      fi
      secret_refs+=("${name}=${name}:${version}")
      for_settings+=("$name")
    fi
  fi
done < "$web_dir/.env.example"

echo "Preparing separate build and runtime service accounts..."
for account in dua-api dua-api-build; do
  if ! gcloud iam service-accounts describe "${account}@${PROJECT_ID}.iam.gserviceaccount.com" --project "$PROJECT_ID" >/dev/null 2>&1; then
    gcloud iam service-accounts create "$account" --project "$PROJECT_ID" --quiet
  fi
done
gcloud projects add-iam-policy-binding "$PROJECT_ID" --member="serviceAccount:${build_account}" \
  --role=roles/cloudbuild.builds.builder --quiet --format=none
gcloud projects add-iam-policy-binding "$PROJECT_ID" --member="serviceAccount:${build_account}" \
  --role=roles/logging.logWriter --quiet --format=none
for name in "${for_settings[@]}"; do
  gcloud secrets add-iam-policy-binding "$name" --project "$PROJECT_ID" \
    --member="serviceAccount:${runtime_account}" --role=roles/secretmanager.secretAccessor --quiet --format=none
done
if ! gcloud artifacts repositories describe "$service" --location "$region" --project "$PROJECT_ID" >/dev/null 2>&1; then
  gcloud artifacts repositories create "$service" --repository-format=docker --location "$region" --project "$PROJECT_ID" --quiet
fi
gcloud artifacts repositories add-iam-policy-binding "$service" --location "$region" --project "$PROJECT_ID" \
  --member="serviceAccount:${build_account}" --role=roles/artifactregistry.writer --quiet --format=none

echo "Building the Node-only container (no runtime secrets supplied to the build)..."
gcloud builds submit "$web_dir" --project "$PROJECT_ID" --region "$region" \
  --config "$web_dir/cloudbuild.yaml" --substitutions="_IMAGE=${image}" \
  --service-account="projects/${PROJECT_ID}/serviceAccounts/${build_account}" \
  --default-buckets-behavior=regional-user-owned-bucket --quiet

# Keep optional write/file features off unless the matching Secret Manager flag enables them.
# --set-* replaces this service's old settings, so removed secret bindings do not linger.
settings=("NODE_ENV=production" "DUA_REQUIRE_LOGIN=1" "DUA_CHAT_DEADLINE_SECONDS=${deadline}")
for flag in DUA_FILES ENABLE_GITHUB_CONNECTOR DUA_BUILD_PR ENABLE_GENERATED_VOICE; do
  [[ -v "secret_names[$flag]" ]] || settings+=("${flag}=0")
done
join_csv() { local IFS=,; printf '%s' "$*"; }
echo "Deploying dua-api in us-central1; application owner authentication remains required..."
gcloud run deploy "$service" --project "$PROJECT_ID" --region "$region" --image "$image" \
  --service-account "$runtime_account" --port=8080 --timeout=900s --min-instances=0 \
  --allow-unauthenticated --set-env-vars="$(join_csv "${settings[@]}")" \
  --set-secrets="$(join_csv "${secret_refs[@]}")" --quiet
echo "dua-api URL:"
gcloud run services describe "$service" --project "$PROJECT_ID" --region "$region" --format='value(status.url)'
