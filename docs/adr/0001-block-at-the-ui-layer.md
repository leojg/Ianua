# ADR-0001 — Block short-form video at the UI layer, not the network

**Date:** 2026-09-25 · **Status:** accepted

## Context
YouTube Shorts are served from the same hosts (`youtube.com`, `googlevideo.com`) as regular
videos. The Android YouTube app pins TLS and uses QUIC. DNS filtering, hosts files, local
VPNs and proxies therefore cannot tell a Short from a normal video. They can only block
YouTube entirely.

## Decision
Ianua detects and gates short-form video where it becomes visible:
- **Browser:** URL and page structure: navigation interception, DNR redirects, and
  hide-selectors in a content script.
- **Android:** the on-screen accessibility tree of the target app (ADR-0004).

Network-level blocking may be added later only for sites or apps that are short-form in
their entirety (e.g. TikTok), and never as the mechanism for YouTube.

## Consequences
- Detection depends on YouTube's markup and view ids, which change often. Hence data-driven,
  remotely updatable rule packs (ADR-0003).
- On Android, Shorts cannot be removed from the YouTube feed. Ianua can only gate the player.
