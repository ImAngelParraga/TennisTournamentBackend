# User Rackets And Stringings Plan

Last Updated: 2026-04-21

## Confirmed Scope
- Users can manage multiple rackets.
- Only the owning authenticated user can create, update, or delete rackets and stringing history.
- Each stringing entry stores separate tensions for mains and crosses.
- String type is stored as free text separately for mains and crosses.
- Racket visibility is user-controlled and can be toggled between `PUBLIC` and `PRIVATE`.
- Soft delete is required for both rackets and stringing history.

## Persistence
- Add Flyway migration `V8__user_rackets_and_stringings.sql`.
- Add `rackets` table with:
  - `owner_user_id`
  - `display_name`
  - optional `brand`, `model`, `string_pattern`
  - `visibility`
  - `created_at`, `updated_at`, `deleted_at`
- Add `racket_stringings` table with:
  - `racket_id`
  - `stringing_date`
  - `mains_tension_kg`, `crosses_tension_kg`
  - `main_string_type`, `cross_string_type`
  - `performance_notes`
  - `created_at`, `updated_at`, `deleted_at`

## API
- Owner endpoints:
  - `GET /users/me/rackets`
  - `GET /users/me/rackets/{racketId}`
  - `POST /users/me/rackets`
  - `PUT /users/me/rackets/{racketId}`
  - `DELETE /users/me/rackets/{racketId}`
  - `POST /users/me/rackets/{racketId}/stringings`
  - `PUT /users/me/rackets/{racketId}/stringings/{stringingId}`
  - `DELETE /users/me/rackets/{racketId}/stringings/{stringingId}`
- Public endpoints:
  - `GET /users/{id}/rackets`
  - `GET /users/{id}/rackets/{racketId}`

## Behavior
- Owner reads return both public and private non-deleted rackets.
- Public reads return only public non-deleted rackets.
- Stringing history is ordered by `stringing_date DESC`, then `created_at DESC`.
- Deleting a racket soft-deletes the racket and hides its history from normal reads.
- Deleting a stringing soft-deletes only that stringing record.

## Validation
- `displayName` must be non-blank.
- Tensions must be greater than zero.
- `stringingDate` must use ISO `YYYY-MM-DD` format.
- Optional text fields should be trimmed and normalized to `null` when blank.

## Tests
- Owner create/list/update/delete racket.
- Owner create/update/delete stringing history.
- Public visibility filtering.
- Owner-only write protection.
- History ordering.
- Soft delete behavior for rackets and stringings.
- Invalid payload handling.

## Documentation
- Keep `docs/postman/TennisTournamentBackend.postman_collection.json` in sync with the final API.
