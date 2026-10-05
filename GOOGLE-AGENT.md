# Google coding agent for Dua

The optional developer assistant is in [google-agent/README.md](google-agent/README.md).
It uses a Google ADK Coordinator, Android/Web specialists, independent Reviewer
and Validation specialist on Vertex AI to read
Dua-ai, store immutable source proposals, show focused diffs and create confirmed
draft PRs. It checks the exact commit's latest CI run without APK builds.
It is separate from the Android/web app and does not move Vercel or Supabase.

Use the phone-friendly launcher in [google-agent/README.md](google-agent/README.md)
to check Google authentication and allow only your exact private preview origin.
Model/tool/token scheduling budgets include all child agents. Independent Android/Web
tasks use disjoint assigned files; only the Coordinator can request confirmed writes.
Validation status comes from exact-commit CI evidence. A compact phone snapshot
shows stored patch/review/check status. Live five-role validation is still pending.

Start with the phone-friendly private Cloud Shell preview instructions. Managed
Agent Platform deployment and its confirmation-capable client are later activation
steps, requiring project IAM, billing and private GitHub access. No credentials or
cloud resources are created by adding these files. Live deployment remains unverified.
