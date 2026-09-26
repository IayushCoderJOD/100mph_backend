# 100mph API

Spring Boot 3.5 + MongoDB backend for the `100mph_rehab` Expo app.

This is **Phase 1 — auth and identity** from the build spec in
[`../100mph_rehab/README.md`](../100mph_rehab/README.md). It follows that
document's API contract, error envelope and security rules. It deviates on the
stack: the spec recommends Node + Postgres, and this is Java + MongoDB.

## Run it

```bash
# 1. Mongo must be up
systemctl start mongod        # or: docker run -d -p 27017:27017 mongo:7

# 2. A secret (>= 32 bytes). The app refuses to start without a valid one.
export JWT_SECRET="$(openssl rand -base64 48)"

# 3. Go
./mvnw spring-boot:run        # or: mvn spring-boot:run
```

Then `curl localhost:8090/v1/health`.

The port is 8090, not Spring's usual 8080, because 8080 is already taken
on these machines by a Keycloak container. Set `PORT` to move it.

On an empty database the seeder writes the programs, plans and demo accounts
from the app's `src/data/mock.ts`, so `mock.demoLogins` works unchanged:

| Email | Password | Role |
|---|---|---|
| `admin@100mph.in` | `admin@123` | admin |
| `memb1@100mph.in` | `memb@123` | member |

Set `SEED_ENABLED=false` before any real data exists. See `.env.example` for
every knob.

## Deploy

Production runs the `prod` profile. Set, at minimum:

| Variable | Why |
|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | Seeding off, CORS locked to the list below, INFO logging |
| `JWT_SECRET` | 32+ bytes (`openssl rand -base64 48`). The prod profile has no fallback |
| `MONGODB_URI` | Atlas, Mumbai region |
| `CORS_ALLOWED_ORIGINS` | The web app's origin(s), e.g. `https://app.100mph.in`. Phone apps need none |
| `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` | Creates the first admin on an empty database (12+ characters). Existing accounts are never touched, so these can stay set |

`DeploymentGuard` refuses to boot against any non-local database that still
has a development default in force — the committed JWT key, demo seeding, or
LAN-wide CORS — and names which one.

## Endpoints

Base `/v1`. JSON only, `Authorization: Bearer <access_token>` unless noted.

### Auth — public

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/auth/password` | `{ email, password, device_id?, timezone? }` | `200` token pair + user |
| `POST` | `/auth/refresh` | `{ refresh_token }` | `200` rotated pair + user |
| `POST` | `/auth/logout` | `{ refresh_token }` | `204` |
| `POST` | `/auth/password/forgot` | `{ email }` | `202` always |
| `POST` | `/auth/password/reset` | `{ token, password }` | `204` |

### Session and identity

| Method | Path | Notes |
|---|---|---|
| `GET` | `/me` | The boot call — user, subscription, entitlement, flags |
| `PATCH` | `/me` | `{ full_name?, phone?, timezone?, avatar_url?, date_of_birth?, height_cm?, weight_kg? }` — blank `phone` clears it |
| `PUT` | `/me/password` | `{ current_password, new_password }` — ends other sessions, returns a fresh pair |
| `PUT` | `/me/program` | `{ program_id }` |

### Back office — admin role only

| Method | Path | Notes |
|---|---|---|
| `POST` | `/admin/users` | Provision a client — backs `app/admin/create-user.tsx` |
| `GET` | `/admin/users` | Roster — backs `app/(tabs)/clients.tsx` |
| `PATCH` | `/admin/users/{id}/status` | `{ status }` — active / invited / suspended; not your own |
| `PUT` | `/admin/users/{id}/password` | `{ password }` — a new temporary password; ends every session. The recovery path until reset emails are wired |

### Public

`GET /v1/health`, `GET /v1/programs`.

## How it works

**The wire format is the client's format.** Jackson is configured for
`SNAKE_CASE` globally, so `full_name`, `access_token` and `active_program_id`
come out matching `src/data/types.ts` without a mapping layer. Java stays
camelCase.

**Access tokens are JWTs; refresh tokens are not.** An access token is a signed
15-minute HMAC-SHA JWT carrying only `sub` and `role` (the digest strength
follows the length of `JWT_SECRET`) — anything else a handler
needs it loads fresh, so a stale claim can never stand in for the current
record. A refresh token is 256 bits of opaque randomness stored as a SHA-256
digest, because it has to be revocable and a self-contained JWT is not.

**Refresh tokens rotate, with reuse detection.** Presenting one mints its
replacement and revokes it. Presenting an already-rotated token means two
parties hold it, and there is no way to tell which one is calling — so the whole
family, traced by `family_id`, is revoked and both are forced to sign in again.
Each login starts a new family, so a second phone is an independent session.

**Nothing leaks who has an account.** A wrong password and an unknown email
return the same `invalid_credentials`, and the password is verified against a
dummy hash even when no user matched so the two take the same time. Forgot-
password answers `202` either way.

**Entitlement is computed, never stored.** `GET /me` derives `can_train` from
account status and subscription on every read, comparing the period end against
the member's own calendar day. The client branches on this and nothing else.

**Suspension bites immediately.** Setting a user suspended revokes every open
refresh token, and the refresh path re-reads status rather than trusting the
token — so access ends at the next refresh, not in 30 days.

### Storage notes

Calendar days (`member_since`, `current_period_end`) are stored as `"2026-08-14"`
strings, not BSON dates. Spring Data would otherwise write midnight in the JVM's
zone, and a server in UTC would read back the previous day. A calendar day is
not an instant — README §2.5. Enums are stored lower-cased so a query written
against the database uses the same spelling as one written against the API.

## Errors

One envelope, always (README §7.1):

```jsonc
{ "error": { "code": "invalid_credentials",
             "message": "…",            // for logs, not for users
             "details": { },
             "request_id": "…" } }
```

`code` is the stable contract; the client maps codes to copy. Every response
carries `X-Request-Id`, echoing the caller's if supplied.

Auth codes: `invalid_credentials`, `account_suspended`, `token_missing`,
`token_invalid`, `token_expired`, `refresh_token_invalid`, `refresh_token_reused`,
`too_many_attempts` (with `Retry-After`), `email_already_exists`,
`password_too_weak`, `reset_token_invalid`.

## Tests

```bash
./mvnw test
```

18 integration tests through the real filter chain against a real Mongo
(`hundredmph_test`), covering rotation, reuse detection, enumeration resistance,
throttle lockout, role gating and the provision → first-sign-in → suspend loop.

## Not built yet

Phase 1 is auth only. Still to come, in the spec's order: content (§5.3),
schedule and session logs (§5.4–5.5), check-ins (§5.6), progression (§5.7),
billing via Razorpay webhooks (§5.9), media (§5.8).

Deliberately deferred within auth:

- **OTP sign-in** (`/auth/otp/*`). The spec assumes a phone-first flow, but the
  app today has one email-and-password login screen. The `phone` field and its
  unique index are already in place for when that screen exists.
- **Sending the reset email.** `/auth/password/forgot` mints and stores the
  token correctly; the delivery step logs it instead of mailing it. Wire the
  mailer where `AuthController` logs, and never put the token in the response.
- **Per-IP rate limiting.** Per-account throttling is in. The spec's per-IP
  limits belong in Redis or at the edge, not in the app's own datastore.
