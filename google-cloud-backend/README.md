# Dua AI engine (Google Cloud Run, optional)

Small Flask service: `POST /chat {"message": "..."}` -> `{"reply": "..."}` using Vertex AI Gemini.
The Android app does **not** call it yet; its live backend is `web/`.

Env vars: `GOOGLE_CLOUD_PROJECT` (required), `VERTEX_LOCATION` (default `us-central1`), `VERTEX_MODEL` (default `gemini-1.5-flash`).

Deploy: `cd google-cloud-backend && PROJECT_ID=<your-project> ./deploy.sh`

Note: `--allow-unauthenticated` makes the endpoint public, so anyone with the URL can spend your Vertex AI quota.
