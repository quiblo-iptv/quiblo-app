# Security Policy

## Reporting a vulnerability

**Please do not open a public issue for a security problem.**

Report it privately through GitHub's private vulnerability reporting:

1. Go to the [Security tab](https://github.com/vibrato-tv/vibrato/security/advisories) of this repository.
2. Choose **Report a vulnerability**.
3. Describe the issue, the affected version, and how to reproduce it.

You should get an acknowledgement within 7 days and a substantive reply within 30 days.

> A dedicated disclosure email address is not yet published. Until it is, GitHub private
> vulnerability reporting is the only supported channel.

## Please do not include

When reporting, **never** attach or paste:

- a real playlist URL, provider hostname, or panel address
- Xtream usernames, passwords, or session tokens
- any credential belonging to you or anyone else

A synthetic reproduction is always sufficient. If a report genuinely cannot be
demonstrated without a real host, say so and we will arrange a private channel — do not
paste it into the report. See `docs/ACCEPTANCE.md`, AC-LEGAL-04.

## Supported versions

Vibrato is pre-1.0. Only the latest release on the `main` branch receives security fixes.

| Version | Supported |
|---|---|
| `main` (latest release) | ✅ |
| Anything older | ❌ |

## Scope

In scope:

- Leakage of stored Xtream credentials, including into logs, exports, or crash traces
  (AC-XT-04, AC-DATA-03)
- Any outbound network request to a host the user did not configure (AC-NFR-03)
- Parser vulnerabilities reachable from playlist input, including crashes and memory
  exhaustion on malformed M3U data
- Import handling that can corrupt or overwrite user state (AC-DATA-04)
- Insecure transport handling or certificate validation bypass

Out of scope:

- The content, legality, or availability of any playlist a user configures. Vibrato
  supplies no content and exercises no control over user-supplied sources.
- Vulnerabilities in a provider's panel or server software.
- Issues that require a rooted device and physical access to exploit.

## Our commitments

- Vibrato has no backend, no telemetry, no analytics, and no crash-reporting SDK. There
  is no server-side component to attack and no data held by the project.
- Credentials never leave the device (`docs/FREEZE.md` §4.6).
- We will credit reporters in the release notes unless asked not to.
