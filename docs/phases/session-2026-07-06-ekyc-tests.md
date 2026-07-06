# Session Summary — 2026-07-06 (eKYC Validation)

## Trạng thái hiện tại

Phase 1 eKYC submission validation đã hoàn tất phần core:
- 4 rules validation đã chốt, code `EkycService.submit` đã thêm, 8 unit test pass.
- Container backend (smartbanking-backend) đang chạy, MySQL/Redis/MinIO healthy.
- 19 test cũ (AuthController*, AccountController*, BackendApplication) fail từ trước — bug môi trường test, **deferred cho session sau**.

## Đã chốt (closed decisions)

| # | Quyết định | Lý do |
   |---|---|---|
| 1 | Validation scope: **Level B** (content-type + size + magic number) | Bắt được 90% lỗi user thật. Không OCR/liveness — để phase sau (theo `CONTEXT.md` Open questions). |
| 2 | Content-type whitelist: **B1** — chỉ `image/jpeg` + `image/png` | CCCD thật chỉ là 2 format này. WebP/GIF quá hiếm cho giấy tờ. |
| 3 | Size: **S3** — CCCD front/back 10KB–5MB, selfie 10KB–2MB | Tier riêng cho selfie tiết kiệm bandwidth. Min 10KB chống ảnh rỗng. |
| 4 | Address: **địa chỉ thường trú** (lấy từ CCCD mặt trước), not-blank + max 500 chars (**A2**) | Quy ước ngân hàng VN: KYC = thường trú. A2 fail sớm tại service, không để Postgres
 reject → 500. |
| 5 | Magic number check **đọc 8 byte đầu**, dùng `readNBytes(8)` | Java 21, an toàn cho cả JPEG (3 byte) và PNG (8 byte signature). |
| 6 | Helper `startsWith(byte[], byte[])` thay vì gán `& 0xFF` thủ công | Gọn, dễ đọc, không cần mask signed byte. |

## File chốt (refs)

- `CONTEXT.md` — mục `KYCAsset` đã sharpen: "địa chỉ thường trú" thay vì "địa chỉ (text)".
- `data-modeling.md` — **CHƯA update** (TODO cho session sau).

## File đã sửa (verified)

### 1. `backend/src/main/java/com/smartbanking/backend/service/kyc/EkycService.java`
- Thêm 6 constants:
    - `MAX_CCCD_BYTES = 5 * 1024 * 1024` (5 MB)
    - `MAX_SELFIE_BYTES = 2 * 1024 * 1024` (2 MB)
    - `MIN_ASSET_BYTES = 10 * 1024` (10 KB)
    - `MAX_ADDRESS_LENGTH = 500`
    - `JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}`
    - `PNG_MAGIC = {(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47}`
- Refactor `submit(...)`: gọi `validateAddress` + 3 `validateAsset` **trước** khi resolve profile / upload.
- Thêm 5 method private:
    - `validateAddress(String)` — not-blank + max 500 → `InvalidEkycAssetException`
    - `validateAsset(String name, MultipartFile file, long maxBytes)` — empty + content-type + size + magic
    - `isAllowedContentType(String)` — chỉ `image/jpeg` / `image/png`
    - `hasValidMagicNumber(MultipartFile)` — đọc 8 byte đầu
    - `startsWith(byte[] data, byte[] prefix)` — helper

### 2. `backend/src/test/java/com/smartbanking/backend/service/kyc/EkycServiceValidationTest.java` (MỚI)
- `@ExtendWith(MockitoExtension.class)` — pure unit test, không boot Spring.
- 2 helper `createJpegOfSize(int)` + `createPngOfSize(int)` để tạo file test.
- 7 method `@Test`:
    1. `submit_validJpegAndPng_advancesToUserLookup` — happy path, expect `UserNotFoundException` (stub)
    2. `submit_webpContentType_rejectedAsInvalidAsset` — content-type fail
    3. `submit_jpegClaimButPlainText_rejectedAsInvalidAsset` — magic number fail
    4. `submit_cccdFrontTooSmall_rejectedAsInvalidAsset` — 5KB < 10KB
    5. `submit_cccdBackTooLarge_rejectedAsInvalidAsset` — 6MB > 5MB
    6. `submit_selfieTooLarge_rejectedAsInvalidAsset` — 3MB > 2MB (verify S3 tier riêng)
    7. `submit_blankAddress_rejectedAsInvalidAsset` — address = 3 spaces
    8. `submit_addressTooLong_rejectedAsInvalidAsset` — 501 chars > 500
- Kết quả: 7/7 pass.

### 3. `backend/build.gradle`
- Thêm `springboot4-dotenv` (F3) — chưa hoàn tất setup, xem TODO bên dưới.

### 4. `CONTEXT.md`
- Section `### KYCAsset` — sharpen: "địa chỉ thường trú" + "lấy từ dòng 'Nơi thường trú' trên CCCD mặt trước".

## Deferred (bug 19 fail cũ)

**Triệu chứng:** Khi chạy `./gradlew test` full, 19 test fail với `ApplicationContext failure threshold (1) exceeded`. Root cause: `application-test.yaml` dùng placeholder
`${JWT_SECRET}` và `${MINIO_*}` mà không có `.env` ở môi trường test → Spring giữ literal `${...}` → bean init fail.

**Đã thử (B):** Hardcode secret → bạn từ chối, muốn giữ `.env` làm source of truth.

**Đã thử (F3):** Thêm `springboot4-dotenv` vào `build.gradle`. Hoạt động (file `application-test.yaml` đã được rewrite từ hardcode → `${JWT_SECRET}`). Nhưng chưa hoàn tất setup:

**TODO cho session sau:**
1. Tạo `configs/.env.test` — copy từ `.env` (trừ `JWT_SECRET` thay bằng secret mới, >= 32 chars).
2. Sửa `backend/src/test/resources/application-test.yaml`, thêm vào đầu file:
   ```yaml
   springdotenv:
     filename: .env.test
     directory: ../../../../configs
    ```

 3. Chạy ./gradlew --stop && rm -rf .gradle/configuration-cache .gradle/caches/build-cache-1 && ./gradlew test — expect 26/26 pass.