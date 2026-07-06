# Session Summary — 2026-07-05 (eKYC Phase 1, cont.)

## Trạng thái hiện tại

Feature **Customer eKYC Submission (Phase 1)** gần xong. Còn 2 task:
- **Active (in_progress):** Validation cho `EkycService.submit`
- **Pending:** Integration test với Testcontainers + MinIO

## Đã chốt (closed decisions)

| # | Câu hỏi | Chốt |
|---|---|---|
| Q1 | Public read trên bucket? | **TẮT**, dùng presigned URL qua `S3Presigner` |
| Q2 | Env var name? | **AWS SDK native** — `S3_ACCESS_KEY` / `S3_SECRET_KEY` |
| Q3 | Healthcheck cho MinIO? | `curl http://localhost:9000/minio/health/live` (sau đó dùng `wget --spider`) |
| Q4 | S3 provider enum? | **2 enum** `AWS` + `S3_COMPATIBLE` (VIETNIX = S3_COMPATIBLE) |
| Q5 | Endpoint required? | `provider != AWS` → endpoint required |

## File chốt (refs)

- **ADR:** `docs/phases/0003-customer-ekyc-phase-1.md` (2 decisions: MinIO + AWS SDK, one-shot PUT)
- **Concept docs:** `CONTEXT.md` đã update với `KYCAsset`, `eKYC Submission`
- **Schema docs:** `docs/data-modeling.md` đã update CustomerProfile (suffix `_key` thay vì `_url`)

## File đã sửa (verified)

### 1. `docker-compose.yaml`
- Thêm 2 service: `minio` (port 9000/9001), `minio-bootstrap` (tạo bucket 1 lần)
- Thêm `minio` vào `backend.depends_on`
- Thêm 4 env var MinIO vào `backend.environment` (đã đổi sang `S3_*` theo provider refactor)
- **Lưu ý:** V1 dùng `wget --spider` thay vì `curl` (vì MinIO image không có curl)

### 2. `backend/build.gradle`
- Thêm `implementation 'software.amazon.awssdk:s3:2.30.0'`

### 3. `backend/src/main/resources/application.yaml`
- Đổi `app.minio.*` → `app.s3.*` (7 field): provider, region, endpoint, access-key, secret-key, bucket, presigned-url-ttl

### 4. `backend/src/main/java/com/smartbanking/backend/config/`
- **Xóa:** `AppMinioProperties.java`
- **Mới:** `S3Provider.java` (enum `AWS`, `S3_COMPATIBLE`)
- **Mới:** `AppS3Properties.java` (record với 7 field, validation `provider != AWS` → endpoint required)
- **Mới:** `S3Config.java` (2 @Bean: `S3Client` + `S3Presigner`, switch logic theo provider)

### 5. `backend/src/main/resources/db/migration/V3__ekyc_asset_columns.sql`
```sql
ALTER TABLE customer_profiles
    ADD COLUMN cccd_front_image_key VARCHAR(512),
    ADD COLUMN cccd_back_image_key  VARCHAR(512),
    ADD COLUMN selfie_image_key      VARCHAR(512),
    ADD COLUMN address              VARCHAR(512);
```

### 6. `backend/src/main/java/com/smartbanking/backend/entity/profile/CustomerProfile.java`
- Thêm 4 field: `cccdFrontImageKey`, `cccdBackImageKey`, `selfieImageKey`, `address`
- Thêm method `submitEkyc(...)` — atomic update 4 field + set `kycStatus = APPROVED`

### 7. `backend/src/main/java/com/smartbanking/backend/service/kyc/`
- **Mới:** `KYCAssetStore.java` — wrap `S3Client.putObject` + `S3Presigner.presignGetObject`. Inner enum `AssetSlot` với 3 value (CCCD_FRONT, CCCD_BACK, SELFIE), folder name dùng **hyphen** (`cccd-front`, `cccd-back`, `selfie`).
- **Mới:** `EkycService.java` — `@Transactional submit(...)`, upload MinIO trước rồi update DB (theo ADR `0003`).

### 8. `backend/src/main/java/com/smartbanking/backend/controller/kyc/EkycController.java`
- Endpoint `PUT /api/v1/customers/me/ekyc` — `consumes = "multipart/form-data"`, 3 `@RequestPart` + 1 `@RequestParam`, return `204 No Content`.

### 9. `backend/src/main/java/com/smartbanking/backend/exception/kyc/`
- **Mới:** `EkycUploadException.java` — `extends RuntimeException` (đã fix từ `Exception`)
- **Mới:** `InvalidEkycAssetException.java` — `extends RuntimeException`

### 10. `backend/src/main/java/com/smartbanking/backend/exception/GlobalExceptionHandler.java`
- Thêm 2 handler:
  - `EkycUploadException` → 500 + `EKYC_UPLOAD`
  - `InvalidEkycAssetException` → 400 + `INVALID_EKYC_ASSET`

### 11. `backend/src/main/java/com/smartbanking/backend/config/SecurityConfig.java`
- Thêm rule: `/api/v1/customers/me/**` → `hasRole("CUSTOMER")`

### 12. `docs/data-modeling.md`
- Update `CustomerProfile` table: `citizen_id` (thay `cccd_number`), 3 cột `_key` (thay `_url`) + ghi chú MinIO object key pattern, `address` ghi chú nullable.

## Code cần sửa tiếp (task in_progress)

### Task: Validation cho `EkycService.submit`

Mở `service/kyc/EkycService.java`, thêm method `validateAsset(...)` + `validateAddress(...)`, gọi đầu method `submit(...)` **trước** khi resolve profile.

```java
@Transactional
public void submit(
        UUID userId,
        MultipartFile cccdFront,
        MultipartFile cccdBack,
        MultipartFile selfie,
        String address
) {
    validateAsset("cccdFront", cccdFront);
    validateAsset("cccdBack", cccdBack);
    validateAsset("selfie", selfie);
    validateAddress(address);

    CustomerProfile customerProfile = getCustomerProfile(userId);
    // ... rest unchanged
}

private void validateAsset(String name, MultipartFile file) {
    if (file == null || file.isEmpty()) {
        throw new InvalidEkycAssetException(name + " is empty");
    }
    String contentType = file.getContentType();
    if (contentType == null || !contentType.startsWith("image/")) {
        throw new InvalidEkycAssetException(
            name + " must be an image, got: " + contentType
        );
    }
}

private void validateAddress(String address) {
    if (address == null || address.isBlank()) {
        throw new InvalidEkycAssetException("address is blank");
    }
}
```

Thêm import:
```java
import com.smartbanking.backend.exception.kyc.InvalidEkycAssetException;
```

**Verify:** `./gradlew build -x test`

## Task cuối cùng (pending)

### Task: Integration test với Testcontainers + MinIO

File: `backend/src/test/java/com/smartbanking/backend/service/kyc/EkycServiceIT.java` (hoặc `EkycControllerIT.java`).

**Setup cần:**
- Testcontainers Postgres (đã có trong `build.gradle`: `org.testcontainers:testcontainers-postgresql`)
- Testcontainers MinIO (`org.testcontainers:minio` chưa có — cần thêm dep)

**Test case tối thiểu:**
1. Register user qua AuthService (setup JWT)
2. PUT ekyc với multipart files giả (MockMultipartFile)
3. Verify: response 204, profile.kycStatus = APPROVED, 4 cột asset có giá trị, 4 object trong MinIO bucket (dùng `S3Client.listObjects`)

**Open question cần quyết khi làm:**
- Có test thật sự upload lên MinIO container (chậm nhưng realistic) hay mock `KYCAssetStore` (nhanh, đơn giản)?
- Tui nghiêng về: test `EkycService` thật với MinIO container (integration test đúng nghĩa), test `EkycController` với `@MockBean KYCAssetStore` (đơn giản, test HTTP layer).

## Còn lại (post-feature)

| Item | Ghi chú |
|---|---|
| Update `docker-compose.yaml` env var `MINIO_*` → `S3_*` | Bạn tự sửa — tui không đụng `configs/.env` |
| Update `configs/.env` thêm `S3_*` | Bạn tự sửa |
| Multipart file size limit trong `application.yaml` | CCCD/selfie có thể 5-10MB, Spring default 1MB/file. Thêm: `spring.servlet.multipart.max-file-size: 10MB` và `max-request-size: 50MB` |
| Cleanup orphan MinIO objects (nếu submit eKYC fail sau khi upload) | Phase sau, không thuộc MVP. Đã chấp nhận ở ADR `0003`. |
| Presigned GET endpoint cho client xem eKYC asset | Phase sau, KYCAssetStore đã có method `presignedGetUrl(...)` sẵn. |

## Quan sát kỹ thuật (để mai đọc nhanh)

- `CustomerProfileRepository.findByUserId(String userId)` — method signature nhận `String` thay vì `UUID`. Đây có thể là bug pre-existing nhưng không ảnh hưởng eKYC (eKYC dùng `User.getProfile()` qua UserRepository, không gọi `findByUserId`).
- App property refactor (MinIO → S3) chưa ảnh hưởng `docker-compose.yaml`/`configs/.env` — cần update tên env var trước khi chạy app lần đầu.
- `ddl-auto: validate` sẽ fail startup nếu entity ↔ schema mismatch. Sau khi sửa CustomerProfile entity + V3 migration, build pass là OK; chưa test với dev DB.
- `KYCAssetStore` cần bean `S3Client` + `S3Presigner` từ `S3Config` — đã có, build pass là OK.

## Files reference (nhanh)

- `docker-compose.yaml` line 37-70 — MinIO + bootstrap
- `application.yaml` block `app.s3.*` (7 field)
- `build.gradle` line cuối block dependencies
- `SecurityConfig.java` line 80-95 — 2 rule CUSTOMER
- `CustomerProfile.java` line 52-62 (4 field mới) + line 129-140 (submitEkyc method)
- `KYCAssetStore.java` line 25-27 (enum folder name hyphen) — quan trọng, không được đổi lại underscore
- `EkycService.java` line 47-63 (submit method) — chèn validate ở đầu
- `EkycController.java` — đã ổn, không cần sửa
- `V3__ekyc_asset_columns.sql` — đã ổn
