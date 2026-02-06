# Issues

## Later (not required for initial client dev)
- [ ] Bracket lifecycle rules: lock phases/players after start; define when changes are allowed
- [ ] Seeding strategy: deterministic ordering + optional seeded brackets
- [ ] Match state validation: prevent scoring without both players; prevent re-scoring unless explicitly allowed
- [ ] Tournament status: add DRAFT/STARTED/COMPLETED to simplify UI + rule enforcement
- [ ] Persistence sanity: idempotent start/progression and safeguards against duplicate matches

## Known missing
- [ ] Auth for club/admin actions
- [ ] CORS configuration for frontend clients
