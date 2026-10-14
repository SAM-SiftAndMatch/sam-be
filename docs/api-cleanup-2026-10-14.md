# API cleanup (14/10/2026)

Scope: compare `sam-be` REST controllers with actual calls in `sam-fe/src` (including calls split across lines and the Axios refresh interceptor). The project owner confirmed `sam-fe` is the only consumer of user-facing REST APIs. This is a source-level audit, not production traffic telemetry.

## Removed: superseded endpoints

| Removed `GET` endpoint | Replacement used by `sam-fe` |
| --- | --- |
| `/api/v1/jobs` | `/api/v1/jobs/page` |
| `/api/v1/jobs/client/me` | `/api/v1/jobs/client/me/page` |
| `/api/v1/jobs/freelancers/{freelancerId}/projects` | `/api/v1/jobs/freelancers/{freelancerId}/projects/page` |
| `/api/v1/proposals/job/{jobId}` | `/api/v1/proposals/job/{jobId}/page` |
| `/api/v1/proposals/me` | `/api/v1/proposals/me/page` |
| `/api/v1/chat/rooms` | `/api/v1/chat/rooms/page` |
| `/api/v1/chat/rooms/{roomId}/messages` | `/api/v1/chat/rooms/{roomId}/messages/page` (cursor) |
| `/api/v1/chat/rooms/{roomId}/contract/revisions` | `/api/v1/chat/rooms/{roomId}/contract/revisions/page` |
| `/api/v1/projects/{jobId}/timeline` | `/api/v1/projects/{jobId}/timeline/summary` plus task reports on demand |

## Removed: never integrated into `sam-fe`

These endpoints had no application caller, even though some had unused FE API wrappers. They were not active FE replacements.

| Removed endpoint | Reason |
| --- | --- |
| `GET /api/v1/auth/me` | FE already uses the auth response and refresh flow; unused wrapper. |
| `GET /api/v1/chat/rooms/{roomId}/messages/recent` | No FE caller; chat uses cursor history. |
| `GET /api/v1/projects/{jobId}/timeline/tasks/{taskId}/reports/page` | No FE caller; task reports currently use the non-paged endpoint. |
| `GET /api/v1/projects/{jobId}/deliveries/page` | No FE caller; deliveries currently use the non-paged endpoint. |
| `GET /api/v1/wallets/me/transactions` and `/page` | Neither ledger endpoint is displayed by FE yet. |
| `GET /api/v1/payments/contract/{contractId}` and `/page` | Neither payment-history endpoint is displayed by FE yet. |

Only HTTP routes and unused FE wrappers were removed. Existing service/repository methods are outside this HTTP API cleanup and are no longer exposed through the removed routes.

## Deliberately retained

- `GET /api/v1/chat/by-contract/{contractId}` is called by `ClientPaymentPage` (the call is split across lines in source).
- `POST /api/v1/payments/vnpay-ipn` and `POST /api/v1/subscriptions/vnpay-ipn` are payment-provider callbacks, not FE calls.
- `POST /api/v1/payments/{paymentId}/release` remains an admin recovery operation; the unused FE wrapper was removed.
- `GET /api/v1/projects/{jobId}/timeline/tasks/{taskId}/reports` and `GET /api/v1/projects/{jobId}/deliveries` remain because `ProjectWorkflowPanel` calls them.
- `GET /api/v1/subscriptions/me` remains alongside `/me/page` because the FE calls both.

Separately, the active job-recommendations endpoint now fetches freelancer users, profiles and skills in batches to avoid per-recommendation queries.
