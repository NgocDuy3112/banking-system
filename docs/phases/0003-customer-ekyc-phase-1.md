# Customer eKYC Submission — Phase 1

We are adding the eKYC submission step for `CUSTOMER`: upload CCCD images, selfie, address; auto-approve; and unlock the gate that lets a Customer open an Account in a later phase. We are deferring Teller-driven manual KYC review, image-content validation (face match, OCR, liveness), and account opening (which stays a separate endpoint per `CONTEXT.md`'s "KYC is a gate, not the action"). The two decisions below settle the non-obvious parts of this slice.

## Decision 1: MinIO in docker-compose, AWS SDK v2 in code

Object storage is `MinIO` running in `docker-compose.yaml` alongside Postgres and Redis. The backend talks to it through the AWS SDK v2 `software.amazon.awssdk:s3` client pointed at the MinIO endpoint (`http://minio:9000`). `S3Client` is built with `endpointOverride` and `forcePathStyle(true)` so the same client class works against MinIO locally and AWS S3 in production without code changes.

**Why.** The four eKYC assets (CCCD front/back, selfie, address) are object blobs, not relational data. Storing them as `BYTEA` in Postgres works but bloats the database, breaks transactional backups, and forces us to handle streaming ourselves. A real S3 SDK call decouples storage growth from DB size and gives us presigned URLs, multipart upload, and signed download out of the box. Choosing MinIO over AWS S3 in dev is the same reasoning the architecture doc uses for keeping everything in compose: no cloud account, no secret keys leaving the machine, and the production swap is a configuration change, not a code change. `forcePathStyle(true)` is required by MinIO (it does not support virtual-hosted style) and harmless against AWS.

**Trade-off accepted.** A second long-running service in compose, and one more `env_file` entry to wire up. We mitigate by giving MinIO a healthcheck the same way Postgres and Redis already have one, and by keeping bucket creation in a startup step rather than asking the operator to click through the console.

## Decision 2: One-shot PUT, all four assets in one request

`PUT /api/v1/customers/me/ekyc` accepts four multipart parts in a single call: `cccdFront`, `cccdBack`, `selfie`, plus a form field `address` (text). The server uploads all four to MinIO, persists the four returned object keys (one per asset) on the `CustomerProfile`, and flips `kyc_status` from `PENDING` to `APPROVED` in the same transaction. After a successful PUT the profile is ready to open an account in a later phase.

**Why.** eKYC submission is conceptually one event, not four independent uploads. The client experience matches: a single submit button, a single success/failure signal, a single retry if anything fails. Splitting into "upload front, upload back, upload selfie, submit address, submit" creates partial states (front uploaded, back failed) that need reconciliation code, and gives the client nothing — there is no "save draft" use case in MVP. One PUT is also idempotent at the resource level: the second PUT replaces the four assets and re-asserts APPROVED, which is correct behaviour (Customer fixed a bad photo and resubmits). Keeping the four object keys as four columns on `customer_profiles` matches the schema sketched in `data-modeling.md` and avoids a child table for what is exactly one row's worth of data.

**Trade-off accepted.** If any one of the four MinIO uploads fails after the previous ones succeeded, we leak objects in the bucket. The transaction only protects Postgres. We accept this for MVP and plan a small "garbage-collect unreferenced MinIO objects" job in a later phase. The alternative (compensating deletes in a `try/catch` around the upload loop) is real code to write, test, and race against partial MinIO failures — and we have no use case for it yet.

## Endpoint summary

| Method | Path | Auth | Body | Result |
|---|---|---|---|---|
| `PUT` | `/api/v1/customers/me/ekyc` | `ROLE_CUSTOMER` | `multipart/form-data` (4 parts) | `204 No Content` |

Caller identity comes from `@AuthenticationPrincipal UUID userId` (the same pattern `AccountController` uses). The handler resolves `User → CustomerProfile`; no `customerId` is accepted from the client, so a Customer cannot submit on behalf of another Customer.
