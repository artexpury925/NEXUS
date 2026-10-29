# Nexus Security Specification & Red Team Audit

## 1. Data Invariants
- All user data lives strictly under `/users/{userId}` and its subcollections (`messages`, `creations`, `workflows`, `clips`).
- No unauthenticated access is permitted anywhere (`request.auth != null`).
- Cross-user reads, queries, writes, and deletes are strictly forbidden (`request.auth.uid == userId` AND `resource.data.userId == request.auth.uid`).
- `/users/{userId}` contains PII (`email`), so `list` on `/users` is strictly denied (`allow list: if false;`), and `get` is restricted solely to `isOwner(userId)`.
- Every `create` and `update` invokes `isValid[Entity](incoming())`, validates `isValidId(...)` on path IDs, enforces `hasAll` / `hasOnly` key allowlists, enforces string `.size()` bounds, enforces bounded array sizes, and enforces immutable `userId` and `createdAt` on updates (including `StudioCreation` updates for App Cloning & Instant Rebranding, where `category in ['webapp', 'game', 'clone', 'dropship']`).

## 2. Dirty Dozen Payloads
1. Unauthenticated read on `/users/alice_123` -> REJECT
2. Authenticated `list` on `/users` (PII scraping) -> REJECT
3. Cross-user `get` by `alice_123` on `/users/bob_456` -> REJECT
4. Identity spoofing on create: `alice_123` writes `userId: "bob_456"` into `/users/alice_123/messages/msg_1` -> REJECT
5. Ghost/Shadow field injection: `alice_123` sends `isAdmin: true` in `/users/alice_123` create or update -> REJECT (`hasOnly`)
6. Oversized payload DoW: `displayName` > 100 chars or `steps` array > 10 items -> REJECT
7. Invalid enum value: `role: "system"` on `ChatMessage` or `category: "script"` on `StudioCreation` (must be in `['webapp', 'game', 'clone', 'dropship']`) -> REJECT
8. Client query without `userId` filter on subcollection or mismatched `userId` -> REJECT
9. Mutating immutable `createdAt` or `userId` during `update` on `AutomationWorkflow` or `StudioCreation` -> REJECT
10. Future timestamp spoofing (`createdAt > request.time`) -> REJECT
11. ID poisoning (`docId` containing spaces/slashes or > 128 chars) -> REJECT
12. Value poisoning on update: changing `runCount` on `AutomationWorkflow` to a string or negative int -> REJECT
