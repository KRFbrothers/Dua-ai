# Operations runbook

How Dua AI runs in production and how to redeploy, configure, back up and check it. Run commands in **Cloud Shell** (or Termux with the Google Cloud CLI).

| Item | Value |
| --- | --- |
| Google Cloud project | `global-healer-m6shk` |
| Region | `us-central1` |
| Chat/Agent engine | Cloud Run service `dua-ai-engine` (`google-cloud-backend/`, Python/Flask) |
| Base API + website | Cloud Run service `dua-api` (`web/` + `website/`, Node) — website at `https://dua-api-228305912525.us-central1.run.app/` |
| Daily backup | Cloud Run job `dua-backup` + Cloud Scheduler `dua-backup-daily` |
| Backup bucket | `gs://dua-backups-m6shk` |
| Auth / data | Supabase (owner-only via `DUA_ALLOWED_EMAIL`) |
| Android APK | GitHub Actions [personal APK workflow](../.github/workflows/personal-apk.yml) ([SIGNING.md](../SIGNING.md)) |

Vercel is fully retired; nothing is deployed there.

## Redeploy

Always deploy from a clean, up-to-date `main`.

### dua-ai-engine

```bash
cd ~/Dua-ai && git fetch origin && git checkout main && git reset --hard origin/main && cd google-cloud-backend && gcloud run deploy dua-ai-engine --source . --region us-central1 --project global-healer-m6shk --allow-unauthenticated --timeout 900 --max-instances 2
```

A source deploy keeps the service's existing environment variables and secrets. `--max-instances 2` caps how many copies can run (and bill) at once.

### dua-api

```bash
cd ~/Dua-ai && git fetch origin && git checkout main && git reset --hard origin/main && PROJECT_ID=global-healer-m6shk bash web/deploy-cloudrun.sh
```

[`deploy-cloudrun.sh`](../web/deploy-cloudrun.sh) builds `website/` into `web/site`, builds the container with Cloud Build and deploys it with `--max-instances=2` (override: `DUA_API_MAX_INSTANCES=1..10`). It attaches dua-api's settings from Secret Manager (names listed in `web/.env.example`) with `--update-env-vars` / `--update-secrets`, so settings that are not in the template are kept, not wiped. A secret binding you no longer want must be removed once by hand (`gcloud run services update dua-api ... --remove-secrets NAME`).

dua-api **fails closed**: if `DUA_ALLOWED_EMAIL`, `SUPABASE_URL` or `SUPABASE_ANON_KEY` is missing, chat, search, connectors, Build PR, cloud sync, sign-in, files, Imagine and voice answer `503` ("This Dua server is locked because owner login setup is incomplete …"), and the startup log shows `[dua-access] WARNING: protected routes will answer 503`. `/api/setup`, `/api/config`, the session check, logout and the website stay up. `DUA_ALLOW_OPEN_ACCESS=1` opens it for local development only and is ignored on Cloud Run.

## Changing settings safely

- On an existing service, change settings **only** with `--update-env-vars` (or `--remove-env-vars`). **Never use `--set-env-vars`**: it replaces every variable and can silently drop secrets/flags.

  ```bash
  gcloud run services update dua-ai-engine --region us-central1 --project global-healer-m6shk --update-env-vars AGENT_VERIFY=1
  ```

- Secrets go into **Secret Manager**, typed with `read -s` so they never appear on screen, in shell history or on the command line. Never paste a secret into a command, chat, screenshot, issue or file:

  ```bash
  read -s -p "Value: " V && printf '%s' "$V" | gcloud secrets versions add SECRET_NAME --data-file=- --project global-healer-m6shk; unset V
  ```

  (Use `gcloud secrets create SECRET_NAME --data-file=- ...` the first time.) Then attach it to the service with `--update-secrets=NAME=SECRET_NAME:latest`.

## Daily backup job (`dua-backup`)

[`backup.py`](../google-cloud-backend/backup.py) runs as Cloud Run job `dua-backup`, built from `google-cloud-backend/`:

- Buildpack images need the launcher: the job runs with command `launcher` and args `python,backup.py` (`gcloud run jobs update dua-backup --command launcher --args "python,backup.py" ...`).
- Schedule: Cloud Scheduler `dua-backup-daily`, cron `0 3 * * *`, time zone `Asia/Kolkata` (03:00 IST).
- Bucket `gs://dua-backups-m6shk` (job setting `BACKUP_BUCKET`; the code default `dua-backups` is not the production bucket):
  - `db/YYYY-MM-DD/<table>.json.gz` — Supabase table exports; a bucket lifecycle rule deletes them after **30 days**.
  - `files/<path>` — incremental mirror of the Supabase Storage bucket `dua-files`; **kept** (deleted app files stay in the backup).
- Needs `SUPABASE_SERVICE_ROLE_KEY` in Secret Manager (job only; never the chat engine).
- Run it now / check:

  ```bash
  gcloud run jobs execute dua-backup --region us-central1 --project global-healer-m6shk
  gcloud run jobs executions list --job dua-backup --region us-central1 --project global-healer-m6shk
  gcloud storage ls gs://dua-backups-m6shk/db/
  ```

Each run logs `[dua-backup] OK {...}` or `[dua-backup] FAILED: ...`. Phone-side encrypted Drive backups are separate: [STORAGE_BACKUP.md](STORAGE_BACKUP.md).

## Monitoring

- An uptime check watches the dua-ai-engine URL, with an email alert policy named **Dua engine down** (Cloud Monitoring → Uptime checks / Alerting).
- Owner-only status: `GET /api/agent/notes/status` on the engine returns `{"enabled","ready","reason"}`.

## Reading logs

```bash
# Engine (look for [dua-agent], [dua-agent-notes], [dua-auth])
gcloud logging read 'resource.type="cloud_run_revision" AND resource.labels.service_name="dua-ai-engine"' --project global-healer-m6shk --limit 50 --freshness 1h
# dua-api (GitHub logs: jsonPayload.source="dua-github", see GITHUB_LOGGING.md)
gcloud logging read 'resource.type="cloud_run_revision" AND resource.labels.service_name="dua-api"' --project global-healer-m6shk --limit 50 --freshness 1h
# Backup job
gcloud logging read 'resource.type="cloud_run_job" AND resource.labels.job_name="dua-backup"' --project global-healer-m6shk --limit 20 --freshness 2d
```

Or open Cloud Run → service → **Logs** in the console. See [GITHUB_LOGGING.md](GITHUB_LOGGING.md) for dua-api's structured GitHub logs.

## Cost and the free credit

The project runs on a Google Cloud free credit that **expires on 3 November 2026**. Switch the Agent from Gemini 4 Argon back to the cheaper default model **before 1 November 2026** (only `AGENT_MODEL` changes):

```bash
gcloud run services update dua-ai-engine --region us-central1 --project global-healer-m6shk --update-env-vars AGENT_MODEL=gemini-3.8-flash
```

Daily caps on Agent runs, chat requests and tokens, and the lower Agent step limits, are described in [Cost guards](../google-cloud-backend/README.md#cost-guards). Check Billing → Reports for spend. Optional paid flags (`AGENT_VERIFY`, `AGENT_TEST_CODE`, `AGENT_ENGINE=adk`) add model calls; see [AGENT.md](AGENT.md).
