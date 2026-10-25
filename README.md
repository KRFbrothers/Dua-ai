# Dua AI

Dua AI is my personal assistant.

Dua is a native Android Compose app in `app/`, with a Node API in `apps/api/` (deployed on Cloud Run as `dua-api`) and a separate Vertex AI chat engine in `google-cloud-backend/`. `apps/api/` is the backend API; `dua-api` also serves the owner-only website built from `apps/web/` at `/` (see [docs/WEBSITE.md](docs/WEBSITE.md)). The Android build defaults to `https://dua-api-228305912525.us-central1.run.app` for account/data services and `https://dua-ai-engine-228305912525.us-central1.run.app` for chat; these are build defaults, not a verification of live service configuration.

Dua is a personal, single-user app: it is built for its owner only and installed on his own device(s) as a debug or sideloaded APK. There are no other users, no Google Play release and no monetisation. The backend is meant to stay locked to the owner's account (`DUA_REQUIRE_LOGIN=1`, `DUA_ALLOWED_EMAIL`) with provider spending limits
