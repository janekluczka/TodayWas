---
change_id: supabase-auth-foundation
title: Supabase auth foundation
status: implemented
created: 2026-08-03
updated: 2026-08-09
archived_at: null
---

## Notes

- Supabase project already exists and is empty (`https://ibftrzalfdmqztmiuvnj.supabase.co`) —
  confirmed via Supabase MCP, no manual project-creation step needed.
- The Google Cloud **Web** OAuth Client ID prerequisite is now configured (both in
  `local.properties`'s `GOOGLE_WEB_CLIENT_ID` and Supabase's dashboard) — Google sign-in was
  verified end-to-end on 2026-08-09 (real Credential Manager flow, real Supabase OIDC token
  exchange). No longer a blocker.
- The Supabase project's "Confirm email" setting was turned off during this session's manual
  verification (2026-08-09) so sign-up establishes a session immediately — a deliberate product
  choice, not just a testing convenience. This surfaced a real bug (Account screen's `SUCCESS`
  step going stale after sign-out), fixed in commit 9f26493.
