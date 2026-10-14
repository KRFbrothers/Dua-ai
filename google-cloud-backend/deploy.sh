#!/usr/bin/env bash
# Required: PROJECT_ID, SUPABASE_URL, SUPABASE_ANON_KEY (or SUPABASE_PUBLISHABLE_KEY), DUA_ALLOWED_EMAIL.
# Optional: VERTEX_MODEL=gemini-3.8-flash, VERTEX_LOCATION=global, DUA_APP_TOKEN (additional gate only).
set -euo pipefail

: "${PROJECT_ID:?Set PROJECT_ID to your Google Cloud project id}"
: "${SUPABASE_URL:?Set SUPABASE_URL to the same Auth project used by the Vercel backend}"
: "${DUA_ALLOWED_EMAIL:?Set DUA_ALLOWED_EMAIL to the configured owner email}"
AUTH_KEY="${SUPABASE_PUBLISHABLE_KEY:-${SUPABASE_ANON_KEY:-}}"
: "${AUTH_KEY:?Set SUPABASE_PUBLISHABLE_KEY or SUPABASE_ANON_KEY (not a service-role key)}"
# gcloud's comma-separated environment map must not interpret a value as another setting.
for value in "$SUPABASE_URL" "$DUA_ALLOWED_EMAIL" "$AUTH_KEY"; do
  if [[ "$value" == *,* ]]; then echo "Auth settings must not contain commas." >&2; exit 1; fi
done
REGION="${REGION:-us-central1}"
VERTEX_MODEL="${VERTEX_MODEL:-gemini-3.8-flash}"
# Vertex AI location (where Gemini runs) is separate from the Cloud Run region; Gemini 3.x uses "global".
VERTEX_LOCATION="${VERTEX_LOCATION:-global}"
TOKEN_VAR=""
if [ -n "${DUA_APP_TOKEN:-}" ]; then TOKEN_VAR=",DUA_APP_TOKEN=${DUA_APP_TOKEN}"; fi
PROJECT_NUMBER=$(gcloud projects describe "$PROJECT_ID" --format="value(projectNumber)")

echo "1. Enabling required Google Cloud APIs..."
gcloud services enable aiplatform.googleapis.com cloudbuild.googleapis.com run.googleapis.com artifactregistry.googleapis.com --project "$PROJECT_ID"

echo "2. Granting Cloud Build & Vertex AI permissions to the default compute service account..."
for ROLE in roles/storage.admin roles/artifactregistry.admin roles/logging.logWriter roles/cloudbuild.builds.builder roles/aiplatform.user; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:${PROJECT_NUMBER}-compute@developer.gserviceaccount.com" \
    --role="$ROLE" --quiet
done

echo "3. Deploying Dua AI Engine to Cloud Run..."
gcloud run deploy dua-ai-engine --source . --region "$REGION" \
  --update-env-vars "GOOGLE_CLOUD_PROJECT=${PROJECT_ID},VERTEX_LOCATION=${VERTEX_LOCATION},VERTEX_MODEL=${VERTEX_MODEL},SUPABASE_URL=${SUPABASE_URL},SUPABASE_PUBLISHABLE_KEY=${AUTH_KEY},DUA_ALLOWED_EMAIL=${DUA_ALLOWED_EMAIL}${TOKEN_VAR}" \
  --timeout 900 --allow-unauthenticated --project "$PROJECT_ID"
