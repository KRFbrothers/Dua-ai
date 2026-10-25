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

# DUA_DISABLE_AGENT, DUA_DISABLE_IMAGINE, DUA_DISABLE_VOICE, DUA_MAINTENANCE, DUA_MAINTENANCE_MESSAGE (and future DUA_DISABLE_*).
is_kill_switch() { [[ "$1" =~ ^DUA_(DISABLE_[A-Z0-9_]+|MAINTENANCE(_MESSAGE)?)$ ]]; }

# Optional, explicit and narrow: DUA_REMOVE_SECRETS=NAME1,NAME2 detaches only those secret bindings
# (for a setting removed from apps/api/.env.example). Nothing else is ever removed.
remove_secrets=()
if [[ -n "${DUA_REMOVE_SECRETS:-}" ]]; then
  IFS=, read -r -a requested_removals <<< "$DUA_REMOVE_SECRETS"
  for name in "${requested_removals[@]}"; do
    if [[ ! "$name" =~ ^[A-Z][A-Z0-9_]*$ ]] || is_kill_switch "$name"; then
      echo "DUA_REMOVE_SECRETS: invalid or protected name '$name'." >&2
      exit 1
    fi
    remove_secrets+=("$name")
  done
fi

secret_refs=()
for_settings=()
declare -A attached_settings=()
while IFS= read -r line; do
  if [[ "$line" =~ ^#?[[:space:]]*([A-Z][A-Z0-9_]*)= ]]; then
    name="${BASH_REMATCH[1]}"
    case "$name" in PORT|DUA_REQUIRE_LOGIN|DUA_CHAT_DEADLINE_SECONDS) continue ;; esac
    # Remote kill switches are owned by `gcloud run services update --update-env-vars` (docs/KILL_SWITCHES.md).
    # The deployer never passes them, so they survive every deploy.
    if is_kill_switch "$name"; then continue; fi
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
      attached_settings["$name"]=1
    fi
  fi
done < "$web_dir/.env.example"

feature_flags=(DUA_FILES ENABLE_GITHUB_CONNECTOR DUA_BUILD_PR ENABLE_GENERATED_VOICE)
for flag in "${feature_flags[@]}"; do
  if [[ -v "secret_names[$flag]" && ! -v "attached_settings[$flag]" ]]; then
    echo "Feature flag $flag exists in Secret Manager but was not attached from apps/api/.env.example. Restore its NAME=value entry in apps/api/.env.example, then retry." >&2
    exit 1
  fi
done

# Build the Dua AI website (apps/web/) into apps/api/site so dua-api serves it at /. The folder is
# always recreated, so the image never carries a stale site; without apps/web/ the API deploys alone.
site_src="${DUA_WEBSITE_DIR:-$web_dir/../web}"
rm -rf "$web_dir/site"
mkdir -p "$web_dir/site"
if [[ -f "$site_src/package.json" ]]; then
  command -v npm >/dev/null || { echo "Install Node.js (npm) or run this script in Cloud Shell to build the website." >&2; exit 1; }
  echo "Building the Dua AI website..."
  (cd "$site_src" && npm ci --no-audit --no-fund && npm run build -- --outDir "$web_dir/site" --emptyOutDir)
  if [[ ! -f "$web_dir/site/index.html" ]]; then
    echo "Website build did not produce apps/api/site/index.html." >&2
    exit 1
  fi
else
  echo "apps/web/ not found; deploying the API without the website."
fi

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

# Owner rule: update only. --update-env-vars / --update-secrets merge into the running service, so every
# other variable and secret (including kill switches set with `gcloud run services update`) is kept.
# Never --set-env-vars, --set-secrets, --clear-env-vars or --clear-secrets here.
# Optional write/file features stay off unless the matching Secret Manager flag enables them.
settings=("NODE_ENV=production" "DUA_REQUIRE_LOGIN=1" "DUA_CHAT_DEADLINE_SECONDS=${deadline}")
remove_env=()
for flag in "${feature_flags[@]}"; do
  if [[ -v "attached_settings[$flag]" ]]; then
    # The flag now comes from Secret Manager: drop only its old literal value (narrow, explicit).
    remove_env+=("$flag")
  else
    settings+=("${flag}=0")
  fi
done
join_csv() { local IFS=,; printf '%s' "$*"; }
deploy_args=(--update-env-vars="$(join_csv "${settings[@]}")" --update-secrets="$(join_csv "${secret_refs[@]}")")
(( ${#remove_env[@]} )) && deploy_args+=(--remove-env-vars="$(join_csv "${remove_env[@]}")")
(( ${#remove_secrets[@]} )) && deploy_args+=(--remove-secrets="$(join_csv "${remove_secrets[@]}")")
echo "Deploying dua-api in us-central1; application owner authentication remains required..."
gcloud run deploy "$service" --project "$PROJECT_ID" --region "$region" --image "$image" \
  --service-account "$runtime_account" --port=8080 --timeout=900s --min-instances=0 \
  --allow-unauthenticated "${deploy_args[@]}" --quiet
echo "dua-api URL:"
gcloud run services describe "$service" --project "$PROJECT_ID" --region "$region" --format='value(status.url)'
