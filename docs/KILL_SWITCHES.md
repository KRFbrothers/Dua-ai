# Remote kill switches (no Firebase)

Turn Dua features off (and back on) from Cloud Run, without a new APK. All switches are **off by default**.
Each command creates a new Cloud Run revision in ~30 seconds; the app picks the change up on its next start
or when it comes back to the foreground (at most once a minute; the Try again button on the paused screen checks immediately).

Project `global-healer-m6shk`, region `us-central1`. Services: `dua-api` (web/) and `dua-ai-engine` (google-cloud-backend/).

| Switch | dua-api | dua-ai-engine | What the user sees |
| --- | --- | --- | --- |
| `DUA_DISABLE_AGENT=1` | Reported in `/api/config` (`switches.agent`) | Agent hidden in `GET /` and refused in `/api/chat` and `/api/agent/action` (works for old APKs too) | "Agent mode is paused right now…"; normal chat keeps working |
| `DUA_DISABLE_IMAGINE=1` | `/api/imagine/*` refused; `imagine` reported off | Engine Imagine endpoint refused | "Imagine is paused right now…" |
| `DUA_DISABLE_VOICE=1` | `/api/voice/*` refused; AI voices and live voice reported off | – | "AI voices and live voice are paused…"; device voice still works |
| `DUA_MAINTENANCE=1` (+ optional `DUA_MAINTENANCE_MESSAGE`, max 300 characters) | Chat, search, Imagine, voice, Build PR refused; account, cloud sync, files and config keep working | Chat, Agent and Imagine refused | New app: full-screen "App paused" page with the message. Old app: the message as a chat error |

If the app cannot reach `/api/config` (offline, timeout after 5 s), it keeps working normally (fail open).

## Agent

```sh
# Off (both services, so old APKs are covered too)
gcloud run services update dua-api --project=global-healer-m6shk --region=us-central1 --update-env-vars=DUA_DISABLE_AGENT=1
gcloud run services update dua-ai-engine --project=global-healer-m6shk --region=us-central1 --update-env-vars=DUA_DISABLE_AGENT=1
# Back on
gcloud run services update dua-api --project=global-healer-m6shk --region=us-central1 --update-env-vars=DUA_DISABLE_AGENT=0
gcloud run services update dua-ai-engine --project=global-healer-m6shk --region=us-central1 --update-env-vars=DUA_DISABLE_AGENT=0
```

## Imagine

```sh
gcloud run services update dua-api --project=global-healer-m6shk --region=us-central1 --update-env-vars=DUA_DISABLE_IMAGINE=1
gcloud run services update dua-ai-engine --project=global-healer-m6shk --region=us-central1 --update-env-vars=DUA_DISABLE_IMAGINE=1
# Back on
gcloud run services update dua-api --project=global-healer-m6shk --region=us-central1 --update-env-vars=DUA_DISABLE_IMAGINE=0
gcloud run services update dua-ai-engine --project=global-healer-m6shk --region=us-central1 --update-env-vars=DUA_DISABLE_IMAGINE=0
```

## Voice (AI voices and live voice; dua-api only)

```sh
gcloud run services update dua-api --project=global-healer-m6shk --region=us-central1 --update-env-vars=DUA_DISABLE_VOICE=1
gcloud run services update dua-api --project=global-healer-m6shk --region=us-central1 --update-env-vars=DUA_DISABLE_VOICE=0
```

## Maintenance (whole app)

```sh
# On, default message
gcloud run services update dua-api --project=global-healer-m6shk --region=us-central1 --update-env-vars=DUA_MAINTENANCE=1
gcloud run services update dua-ai-engine --project=global-healer-m6shk --region=us-central1 --update-env-vars=DUA_MAINTENANCE=1
# On, own message (the ^@^ prefix lets the message contain commas)
gcloud run services update dua-api --project=global-healer-m6shk --region=us-central1 "--update-env-vars=^@^DUA_MAINTENANCE=1@DUA_MAINTENANCE_MESSAGE=Dua is upgrading, back by 6 pm."
gcloud run services update dua-ai-engine --project=global-healer-m6shk --region=us-central1 "--update-env-vars=^@^DUA_MAINTENANCE=1@DUA_MAINTENANCE_MESSAGE=Dua is upgrading, back by 6 pm."
# Off
gcloud run services update dua-api --project=global-healer-m6shk --region=us-central1 --update-env-vars=DUA_MAINTENANCE=0
gcloud run services update dua-ai-engine --project=global-healer-m6shk --region=us-central1 --update-env-vars=DUA_MAINTENANCE=0
```

Check: `curl -s https://<dua-api URL>/api/config | jq .switches`.

## Deploys keep the switches

`web/deploy-cloudrun.sh` only uses `--update-env-vars` / `--update-secrets` (never `--set-*` or `--clear-*`), and it never
passes any `DUA_DISABLE_*` or `DUA_MAINTENANCE*` variable, so a switch you set stays set across dua-api deploys
(tested in `web/test/deploy-cloudrun.test.mjs`). The only removals it can make are explicit and narrow:
a feature flag that moved into Secret Manager drops its old literal value, and `DUA_REMOVE_SECRETS=NAME1,NAME2`
(optional, when running the script) detaches exactly those secret bindings; kill-switch names are refused there.
Turn a switch off with the "Back on" / "Off" commands above.
