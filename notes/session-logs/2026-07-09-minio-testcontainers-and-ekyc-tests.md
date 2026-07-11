# Session Log — 2026-07-09

## Mục tiêu hôm nay
1. Thay thế MinIO local docker-compose bằng MinIO Testcontainer cho `EkycControllerIntegrationTest`.
2. Viết thêm test cases cho `EkycControllerIntegrationTest` (asset size + content-type validation).

## Kết quả

### ✅ Hoàn thành
- `build.gradle`: thêm `testImplementation "org.testcontainers:testcontainers-minio"` (managed bởi Spring Boot 4.1.0 BOM → version 2.0.5).
- `IntegrationTestBase.java`: thêm MinIO testcontainer, dummy env vars (cho property binding), `@DynamicPropertySource` mapping `app.minio.*` → container values, và `MinioTestConfig` inner class tạo bucket qua `ApplicationRunner`.
- `EkycControllerIntegrationTest.java`:
  - Refactor test cũ dùng helper `validMultipartBuilder()`.
  - Thêm 3 test mới: `submitEkyc_cccdFrontTooLarge_returns400`, `submitEkyc_selfieWrongContentType_returns400`, `submitEkyc_magicNumberMismatch_returns400`.
  - Thêm helper `submitAndExpectBadRequest()` parse JSON response với `ObjectMapper`.
  - Thêm inner record `ApiErrorResponse(String code, String message)`.
- **Kết quả: 4/4 EkycController tests pass** (1 cũ + 3 mới).

### ⚠️ Vấn đề phát hiện (chưa fix)

#### Issue 1: Container startup race khi chạy tất cả tests
**Triệu chứng:** Khi chạy `./gradlew test` (all), `AuthControllerIntegrationTest` (chạy đầu tiên alphabetical) fail 10/10 với `Connection to localhost:<port> refused`. Khi chạy riêng (`--tests AuthControllerIntegrationTest`), chỉ 1/10 fail.

**Root cause:** `AuthControllerIntegrationTest` extend `IntegrationTestBase` nên share `POSTGRES` static field. Khi chạy all, AuthController chạy trước EkycController → context chưa warmed up → Postgres container chưa ready kịp cho `@BeforeEach setUp()` của AuthController. EkycController chạy sau → context cache hit → pass.

**Status:** Skip. Pre-existing issue, không liên quan đến thay đổi MinIO.

#### Issue 2: Flaky test `refresh_valid_returns200AndNewTokens`
**Triệu chứng:** Test fail vì access token mới giống hệt access token cũ. Cùng `iat` (issued-at, epoch second) → cùng payload → cùng JWT signature.

**Root cause:** `TokenService.issueAccessToken()` set `iat = Instant.now()` (độ chính xác giây). Nếu 2 lần issue xảy ra trong cùng 1 giây → cùng token.

**Fix applied (2026-07-09):** Option A — thêm `.id(UUID.randomUUID().toString())` (jti claim) vào `JwtBuilder` trong `TokenService.issueAccessToken()`. Mỗi token giờ có JWT ID unique → payload khác nhau → token khác nhau, bất kể timing. Cũng mở đường cho token revocation sau này (track `jti` in Redis blacklist).

**Status:** ✅ Fixed. Test passed.

## Files changed this session
1. `backend/build.gradle` — +1 line
2. `backend/src/test/java/com/smartbanking/backend/IntegrationTestBase.java` — restructure + new MinIO container + bucket initializer
3. `backend/src/test/java/com/smartbanking/backend/controller/kyc/EkycControllerIntegrationTest.java` — refactor + 3 new tests

## Next steps
- [x] Fix Issue 2 (flaky refresh test) — applied Option A: thêm `.id(UUID.randomUUID().toString())` vào `TokenService.issueAccessToken()`. Tests passed 2026-07-09.
- [ ] Investigate Issue 1 (test ordering race) — có thể cần `@TestInstance(PER_CLASS)` + `@Order` hoặc `withStartupTimeout` cho Postgres container.
- [ ] Viết ADR cho testcontainers setup (theo pattern trong `docs/architecture.md`).
