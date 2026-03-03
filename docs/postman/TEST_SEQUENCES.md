# Test Sequences

Below are useful call sequences to exercise internal logic. Use the Postman collection variables (baseUrl, userId, playerId, clubId, tournamentId, matchId).

Prerequisites
- Create a phase before starting a tournament: `POST /tournaments/{id}/phases` with a knockout configuration (include `qualifiers` inside `configuration`).
- To verify match creation/advancement, use `GET /tournaments/{id}/matches`.

Sequence 1: Smoke + CRUD
1) POST /users
2) POST /clubs (use userId)
3) POST /players (external true)
4) POST /tournaments
5) GET /tournaments/{id}
6) PUT /tournaments
7) DELETE /tournaments/{id}

Sequence 2: Tournament start idempotency (Knockout or Swiss)
1) POST /tournaments/{id}/start
2) GET /tournaments/{id}/matches -> count matches
3) POST /tournaments/{id}/start again
4) GET /tournaments/{id}/matches -> match count should be unchanged

Sequence 3: Knockout auto-progress
1) POST /tournaments/{id}/start
2) Pick a round-1 match and its dependent match (see matchDependencies)
3) PUT /matches/{matchId}/score with a clear winner
4) GET /matches/{dependentMatchId} -> winner assigned to player1 or player2

Sequence 4: Swiss round creation
1) POST /tournaments/{id}/start
2) Score all but one round-1 matches
3) GET /tournaments/{id}/matches -> ensure no round-2 matches yet
4) Score the last round-1 match
5) GET /tournaments/{id}/matches -> round-2 matches should now exist

Sequence 5: Match winner validation
1) PUT /matches/{id}/score with a tied score (e.g., 6-6, 6-6)
2) Expect 400 Bad Request: "Score does not produce a winner."

Sequence 6: Add/remove players edge cases
1) POST /tournaments/{id}/players with duplicate playerId
2) POST /tournaments/{id}/players with {} or both playerId + name
3) Expect 400 for invalid payload; duplicates should be ignored
4) GET /tournaments/{id}/players -> verify player list changes

Sequence 7: Bracket view
1) POST /tournaments/{id}/start
2) GET /tournaments/{id}/bracket -> rounds grouped by round number
3) If thirdPlacePlayoff is true, final round should include a third-place match

