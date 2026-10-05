# Google coding agent for Dua

The optional developer assistant is in [google-agent/README.md](google-agent/README.md).
It uses a Google ADK Coordinator and independent Reviewer on Vertex AI to read
Dua-ai, store immutable source proposals, show focused diffs and create confirmed
draft PRs. It checks the exact commit's latest CI run without APK builds.
It is separate from the Android/web app and does not move Vercel or Supabase.

Use the phone-friendly launcher in [google-agent/README.md](google-agent/README.md)
to check Google authentication and allow only your exact private preview origin.
Model/tool/token scheduling budgets include the reviewer. Android/Web specialist
delegation is a later milestone; the current implementation has two agents.

Start with the phone-friendly private Cloud Shell preview instructions. Managed
Agent Platform deployment and its confirmation-capable client are later activation
steps, requiring project IAM, billing and private GitHub access. No credentials or
cloud resources are created by adding these files. Live deployment remains unverified.
