# Customer Auth — Phase 1

We are implementing authentication for the `CUSTOMER` role only, deferring staff roles (TELLER / AUDITOR / ADMIN), OTP, and rate limiting to later phases. The design below settles the two decisions that were non-obvious enough to record.

## Decision 1: Single-session login (kill prior session on new login)

A successful login invalidates any previously-issued refresh token for the same user. The user is therefore "active" in exactly one session at a time.

**Why.** OWASP / OAuth2 BCP recommends multi-session with refresh-token rotation and reuse detection, but that requires a token family tree, a grace window, and a "revoke the whole tree on reuse" branch. Banking apps in our market overwhelmingly ship single-session, and our MVP has neither the bandwidth nor the threat model to justify the extra surface. Killing the prior session on new login is one Redis op, gives us immediate server-side revocation, and matches how retail banking users actually behave (one device at a time).

**Trade-off accepted.** A user opening the app on phone and web simultaneously will be logged out of one as soon as the other logs in. If that proves painful we will revisit with the multi-session rotation scheme; the cost of migrating later is moderate but not negligible.

## Decision 2: Refresh tokens live in Redis, not Postgres

Refresh tokens are 32 bytes of secure random, base64url-encoded. The plaintext token is returned to the client once. On the server we store only `SHA-256(plaintext)` at Redis key `refresh:{userId}` with a TTL of 7 days. Single-session means one key per user; no family / chain tracking needed.

**Why.** Token revocation is the dominant operation (logout, kill-on-new-login, expiry) and is `O(1)` in Redis. In Postgres the same flow needs a row update or a delete-by-user, plus the cost of maintaining an indexed table. The codebase already chose Redis for OTP (per `docs/architecture.md`); using Redis for refresh tokens keeps the auth state colocated and avoids a second persistence path. Hashing before storage means a Redis dump leak does not yield usable tokens — the attacker still has to brute-force the pre-image of a 32-byte random value, which is infeasible.

**Trade-off accepted.** Refresh-token survival now depends on Redis durability. We mitigate this with Redis AOF persistence in docker-compose and accept that a catastrophic Redis loss forces all users to re-login (worst case: inconvenience, not data corruption — no money is at risk because no transactions are tied to refresh tokens).