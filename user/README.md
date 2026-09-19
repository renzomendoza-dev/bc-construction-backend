# User Module

Maven module (`com.bcconstructionservices:user`) holding the app's local user profiles and the
admin tools for managing them. Every other module depends on it — for `created_by`-style
references (`UserLookupHelper`) and for resolving who is making the current request.

**Keycloak owns identity.** Logins, passwords, email and realm roles all live in Keycloak; this
backend only validates the Bearer JWT on each request. `AppUser` is a minimal local row, keyed by
the Keycloak subject (`keycloak_id`), that exists so other tables can hold a plain `Long` foreign
key to a user without calling Keycloak on every request.

## Domain model

| Entity | Purpose |
|---|---|
| `AppUser` | Local profile per Keycloak user: `keycloakId` (unique), `fullName` (from the JWT), `active` flag. |
| `AdminAuditLog` | Who did which admin action (`ACTIVATE`, `DEACTIVATE`, `ASSIGN_ROLE`, `REVOKE_ROLE`, `RESYNC`) to whom. |

## How a user's local profile is created and kept current

`UserSyncService.syncFromToken` runs at the start of **every** authenticated `/api/**` request
(from `app`'s `UserSyncInterceptor`, after the JWT is validated and before the controller), and
again inside `GET /api/users/me`:

- **First request ever:** the `AppUser` row is created, active, with `fullName` from the token's
  `name` claim (falling back to `preferred_username`). This happens before the request's own code
  runs, so a brand-new user's very first action — even a write — already has a local id to be
  recorded against (`created_by`, `CurrentUserService`). The frontend doesn't need to call
  `/me` first.
- **Later requests:** `fullName` is refreshed if the token reports a different one; an unchanged
  profile is never written.
- **Concurrent first requests** (e.g. a page load firing several API calls at once for a new user)
  are safe: the row is created with `INSERT ... ON CONFLICT (keycloak_id) DO NOTHING`, so exactly
  one row results and no request fails. See `UserRepository.insertIfAbsent`.

The interceptor also stores the resolved id as `AuditorAwareImpl`'s per-request cache, so
`created_by`/`updated_by` auditing needs no extra lookup. Requests not authenticated by a JWT are
skipped.

## API endpoints

### Users — `/api/users`
- `GET /api/users` — list active users (any authenticated caller).
- `GET /api/users/me` — the caller's own local profile (synced first; see above).

### Admin — `/api/admin/users` (every endpoint requires `ADMIN`)
- `GET /api/admin/users` — paginated list of all users, filterable by `active`.
- `GET /api/admin/users/{userId}` — one user's detail, including their Keycloak realm roles.
- `PATCH /api/admin/users/{userId}/activate` / `.../deactivate` — toggle the local `active` flag.
- `POST /api/admin/users/{userId}/roles` / `DELETE /api/admin/users/{userId}/roles/{roleName}` —
  assign/revoke a Keycloak realm role, via Keycloak's admin API (`KeycloakAdminClient`, using the
  `keycloak.admin.client-id`/`client-secret` service account).
- `POST /api/admin/users/{userId}/resync` — refresh `fullName` from Keycloak's stored profile.

Every admin action is recorded in `AdminAuditLog`.

## Testing

Unit tests only in this module (no database). The profile sync's real behavior — first-request
creation and concurrent first requests against Postgres — is covered by `app`'s
`UserSyncIntegrationTest`, since it needs the whole application wired together. Run with:

```bash
../mvnw -pl user -am test
```
