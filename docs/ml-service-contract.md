# Smart Banking — ML Fraud Detection Service Contract

> **Audience:** backend developers integrating with the ML service.
> **Owner:** ML service team (this repo: `ml/`).
> **Status:** v0 (stub) — service runs, but `/score` returns hardcoded responses. Real model coming in Phase 2.

## 1. What this service does

Accepts transaction details (account numbers, amount, balances, type, timestamp), returns a fraud score (`0.0`–`1.0`) and a status band (`CLEAR` / `SUSPICIOUS` / `BLOCKED`). The backend should call this **synchronously** during transaction processing and use the result to decide whether to allow, gate-with-OTP, or block the transfer.

The v1 model uses **structured features only** (amount, balances, behavioral scores, time patterns). NLP on the transaction `description` field is deferred to Phase 2 when real Vietnamese banking data is available (see `docs/dataset-documents.md` § 4).

The service is **best-effort**: if it's down, slow, or returns a 5xx, **the backend MUST fall back to its own rules-based check** and log the failure. This is by design (see `docs/nonfunctional-requirements.md` § Reliability).

## 2. Endpoints

### `POST /score`

**Request body** (JSON):

```json
{
  "transaction_id": "11111111-1111-1111-1111-111111111111",
  "from_account_number": "1234567890",
  "to_account_number":   "0987654321",
  "amount": "5000000.0000",
  "from_balance_before": "15000000.0000",
  "transaction_type": "INTERNAL",
  "occurred_at": "2026-06-16T10:00:00Z"
}
```

| Field | Type | Notes |
|---|---|---|
| `transaction_id` | UUID | The transaction's primary key in your DB. We echo this back so you can correlate. |
| `from_account_number` | string (9–15 chars) | Account number initiating the transfer. |
| `to_account_number` | string (9–15 chars) | Destination account number. |
| `amount` | **string** | ⚠️ **JSON string, not number.** Format: `^\d{0,15}\.\d{0,4}$` (e.g. `"5000000.0000"`). Matches your `DECIMAL(19,4)` column. |
| `from_balance_before` | **string** | Sender's balance before the transaction. Used for balance-emptying detection. |
| `transaction_type` | enum | `"INTERNAL"` or `"INTERBANK"`. Other values → 422. |
| `occurred_at` | ISO-8601 UTC | When the transfer happened. We use this to compute time-of-day features. |

**Response body** (JSON, HTTP 200):

```json
{
  "transaction_id": "11111111-1111-1111-1111-111111111111",
  "fraud_score": 0.12,
  "fraud_status": "CLEAR",
  "risk_level": "LOW",
  "reason_codes": [],
  "model_version": "stub-v0",
  "inference_ms": 4
}
```

| Field | Type | Notes |
|---|---|---|
| `fraud_score` | float 0.0–1.0 | Higher = more suspicious. |
| `fraud_status` | enum | See threshold table below. |
| `risk_level` | enum | `LOW`, `MEDIUM`, `HIGH` — categorical risk based on thresholds. |
| `reason_codes` | string[] | E.g. `["LARGE_AMOUNT", "NEW_RECIPIENT", "OFF_HOURS"]`. Empty in the stub. |
| `model_version` | string | E.g. `"stub-v0"`, `"lgbm-v0.1.0"`. **Pin to this in your decision logic if you want stable behavior across retrainings.** |
| `inference_ms` | int | Server-side inference latency. Useful for SLO dashboards. |

### Threshold table

| Score range | Status | What the backend should do |
|---|---|---|
| `score < 0.5` | `CLEAR` | Allow normally. |
| `0.5 ≤ score < 0.8` | `SUSPICIOUS` | Require step-up OTP. |
| `score ≥ 0.8` | `BLOCKED` | Reject the transaction. |

Thresholds are env-tunable: `ML_THRESHOLD_SUSPICIOUS`, `ML_THRESHOLD_BLOCKED`.

### `GET /health`

Returns `{"status":"ok"}` with HTTP 200. Use for Kubernetes liveness/readiness probes. **No request body.**

## 3. Error contract

| HTTP | When | What the backend should do |
|---|---|---|
| 200 | Happy path | Use the response. |
| 422 | Validation failed (bad UUID, negative amount, unknown type) | **Treat as SUSPICIOUS or BLOCKED**, depending on the field that failed. This is unlikely in production — your client should have sent clean data. |
| 500 | Unhandled internal error (model crashed, DB unreachable, etc.) | **Fall back to your own rules.** Log with `transaction_id` and `model_version` (if present in error body). |
| 503 | Model not loaded (file missing or corrupted) | **Same as 500 — fall back to your rules.** This is the "ML service is healthy but model is broken" case. |
| Timeout (> 250ms) | Model or DB is too slow | **Same as 500.** Time out at 200ms client-side to leave headroom. |

**Rule of thumb for the backend**: any non-200 response from the ML service means "use my own rules, log the failure, continue."

## 4. NFR promises (Phase 2 — currently NOT met by the stub)

| Metric | Target | Stub status |
|---|---|---|
| p99 inference latency | < 250ms | ~1ms (hardcoded) |
| Throughput | 100 RPS | n/a |
| Availability | Best-effort | 100% while the server is up |
| Data freshness | Trained on < 90-day-old data | n/a |
| Fallback | Always available via the backend's own rules | ✅ (your responsibility) |

The stub returns in ~1ms. The real model will do DB lookups (history features) and one ONNX inference, expected < 50ms p99.

## 5. Versioning

- The `model_version` field in the response lets the backend know which model produced the score.
- When we ship a new model, we'll bump the version string (e.g. `lgbm-v0.1.0` → `lgbm-v0.2.0`).
- **Breaking changes** to the contract (renamed fields, new required fields, new enum values) will be communicated via this document and a Slack post. We'll keep the old version live for 2 minor releases before removing it.

## 6. Local development

**Run the service:**

```bash
cd ml
uv sync
cp .env.example .env             # then edit ML_DB_URL
uv run uvicorn app.main:app      # listens on :8000
```

**OpenAPI live URLs:**

- Swagger UI: <http://localhost:8000/docs>
- ReDoc: <http://localhost:8000/redoc>
- Raw JSON: <http://localhost:8000/openapi.json> — import this into Postman or your Java client generator.

**Quick sanity check:**

```bash
curl -X POST http://localhost:8000/score \
  -H 'Content-Type: application/json' \
  -d '{
    "transaction_id":"11111111-1111-1111-1111-111111111111",
    "from_account_number":"1234567890",
    "to_account_number":"0987654321",
    "amount":"5000000.0000",
    "from_balance_before":"15000000.0000",
    "transaction_type":"INTERNAL",
    "occurred_at":"2026-06-16T10:00:00Z"
  }'
```

## 7. Sample Java client (Spring Boot 3.2+, Java 21)

```java
// In your config:
@Configuration
class FraudClientConfig {
    @Bean
    RestClient fraudClient(@Value("${fraud.base-url}") String baseUrl) {
        return RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }
}

// The DTOs (use BigDecimal for amount, not double):
public record ScoreRequest(
    UUID transactionId,
    String fromAccountNumber,
    String toAccountNumber,
    BigDecimal amount,                  // @JsonSerialize with ToStringSerializer
    BigDecimal fromBalanceBefore,       // @JsonSerialize with ToStringSerializer
    TransactionType transactionType,
    OffsetDateTime occurredAt
) {}

public record ScoreResponse(
    UUID transactionId,
    double fraudScore,
    FraudStatus fraudStatus,
    RiskLevel riskLevel,
    List<String> reasonCodes,
    String modelVersion,
    int inferenceMs
) {}

// The call site:
@Service
class FraudService {
    private final RestClient client;

    public ScoreResponse score(ScoreRequest req) {
        return client.post()
            .uri("/score")
            .body(req)
            .retrieve()
            .body(ScoreResponse.class);
    }
}
```

**Two gotchas for the Java side:**
1. Use `BigDecimal` for `amount`, not `double`/`float`. Annotate with `@JsonSerialize(using = ToStringSerializer.class)` so it serializes as `"5000000.0000"` not `5000000.0`.
2. The `RestClient` call must set a timeout of **200ms** (so a 250ms server-side SLA leaves headroom). Use `JdkClientHttpRequestFactory` with a `Duration.ofMillis(200)` connect+read timeout.

## 8. Open questions for the backend

- Do you want us to also expose `GET /score/{transaction_id}` to re-fetch a past score? (Useful for the audit log, but adds a DB dependency.)
- Should `reason_codes` be human-readable strings (current plan) or structured (e.g. `{"code":"LARGE_AMOUNT","weight":0.4}`)? We have a slight preference for the structured form.
- Do you want a `/batch/score` endpoint for backfilling the audit log with historical transactions? ~5 RPS per request, up to 100 txns per call.
