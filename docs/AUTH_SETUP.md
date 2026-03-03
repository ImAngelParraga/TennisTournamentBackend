# Auth Setup (Clerk + Ktor JWT)

## Required Environment Variables

- `CLERK_ISSUER`
  - Example: `https://your-instance.clerk.accounts.dev`
- `CLERK_AUDIENCE`
  - Audience expected in API tokens.
- `ALLOWED_ORIGINS`
  - Comma-separated list of frontend origins.
  - Example:
    `http://localhost:3000,http://127.0.0.1:3000,http://localhost:5173,http://127.0.0.1:5173,https://your-frontend-domain.com`

## Behavior

- Public read endpoints (`GET`) remain public.
- All write endpoints require `Authorization: Bearer <token>`.
- Write authorization is enforced by DB roles:
  - club owner
  - club admins
- `/users` write endpoints are intentionally disabled and return `403`.
- A local user is auto-provisioned the first time an authenticated token subject (`sub`) is seen.

## Local Testing

In tests, the backend uses a local HMAC test verifier (not Clerk network calls):
- issuer: `http://localhost/test-issuer`
- audience: `test-audience`
- secret: `test-secret` (override with `AUTH_TEST_JWT_SECRET` if needed)

## Example Request

```bash
curl -X POST http://localhost:8080/tournaments/1/start \
  -H "Authorization: Bearer <jwt>"
```

## Expected Auth Errors

- `401 Unauthorized`: token missing/invalid.
- `403 Forbidden`: token valid but user lacks required club/tournament/match permission.
