#!/usr/bin/env bash
# Usage: PROJECT_ID=your-gcp-project ./deploy.sh
set -euo pipefail

: "${PROJECT_ID:?Set PROJECT_ID to your Google Cloud project id}"
REGION="${REGION:-us-central1}"
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
  --set-env-vars "GOOGLE_CLOUD_PROJECT=${PROJECT_ID},VERTEX_LOCATION=${REGION}" \
  --allow-unauthenticated --project "$PROJECT_ID"
