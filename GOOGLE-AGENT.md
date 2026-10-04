# Google coding agent for Dua

The optional developer assistant is in [google-agent/README.md](google-agent/README.md).
It uses Google ADK/Gemini on Vertex AI to read Dua-ai, propose source changes,
create confirmed draft PRs and dispatch existing GitHub checks without APK builds.
It is separate from the Android/web app and does not move Vercel or Supabase.

Start with the phone-friendly private Cloud Shell preview instructions. Managed
Agent Platform deployment and its confirmation-capable client are later activation
steps, requiring project IAM, billing and private GitHub access. No credentials or
cloud resources are created by adding these files. Live deployment remains unverified.
