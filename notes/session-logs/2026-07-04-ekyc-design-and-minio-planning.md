# Session Log — 2026-07-04

## Mục tiêu hôm nay
Chốt design và bắt đầu implement feature **Account Creation + eKYC** (đã chốt scope = chỉ submit eKYC, không bao gồm mở Account).

## Đã chốt (closed decisions)

| # | Câu hỏi | Chốt |
|---|---|---|
| 1 | Scope feature | Chỉ submit eKYC; mở Account là endpoint riêng ở phase sau |
| 3 | File storage | **MinIO (S3-compatible, self-hosted)** trong docker-compose, code dùng AWS SDK v2 |
| 4 | Flow sau submit | Submit → auto APPROVED, KHÔNG tự tạo Account |
| 5 | HTTP method | **PUT** (one-shot, replace toàn bộ 4 asset) |
| 6 | Authorization | CUSTOMER tự submit, lấy `userId` từ JWT qua `@AuthenticationPrincipal` |
| 7 | Authz details | Endpoint `PUT /api/v1/customers/me/ekyc`, caller chỉ submit của chính mình, không nhận `customerId` từ client |

## Còn open (cần user trả lời khi quay lại)

Áp dụng cho patch `docker-compose.yaml` — 3 câu hỏi:

1. **Public read trên bucket `kyc-assets`?**
   - Option A: BẬT (`mc anonymous set download local/kyc-assets`) — MVP đơn giản, ảnh eKYC ai có URL cũng xem được
   - Option B: TẮT — cần thêm presigned URL getter ở phase sau, an toàn hơn

2. **Tên env var cho MinIO?**
   - Option A: `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` (MinIO native, từ 2023+)
   - Option B: `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` (AWS SDK native, thân thiện với production swap)

3. **Healthcheck cho `minio` service?**
   - Tui fetch page healthcheck chính thức của MinIO bị 404, không dám đoán
   - Option A: `curl -f http://localhost:9000/minio/health/live` (chuẩn MinIO endpoint, dùng trong cộng đồng)
   - Option B: Bỏ healthcheck, `restart: on-failure` cho backend để tự retry khi MinIO chưa sẵn sàng
   - Option C: User có pattern riêng đã dùng

## File đã tạo/sửa

- **MỚI** `docs/phases/0003-customer-ekyc-phase-1.md` — 2 ADR:
  - Decision 1: MinIO trong compose, AWS SDK v2 trong code (with `endpointOverride` + `forcePathStyle(true)`)
  - Decision 2: One-shot PUT, 4 asset trong 1 request, transaction chỉ bảo vệ Postgres (accept MinIO orphan risk cho MVP)
- **SỬA** `CONTEXT.md`:
  - Thêm concept `KYCAsset` (lưu MinIO local, S3 production, slot: CCCD front/back, selfie, address)
  - Thêm concept `eKYC Submission` (idempotent PUT, auto-approve, không có staff approval trong MVP)
  - Thêm open question: bucket name convention, lifecycle policy, presigned URL TTL

## File CHƯA sửa (task pending)

### docker-compose.yaml — patch đã chuẩn bị sẵn (CHƯA apply, chờ trả lời 3 open Q)

```yaml
  minio:
    image: quay.io/minio/minio:latest
    container_name: smartbanking-minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - ./data/minio:/data
    env_file: ./configs/.env
    healthcheck:
      test: ["CMD", "mc", "ready", "local"]  # ← CẦN CONFIRM
      interval: 5s
      timeout: 3s
      retries: 10

  minio-bootstrap:
    image: minio/mc:latest
    container_name: smartbanking-minio-bootstrap
    depends_on:
      minio:
        condition: service_healthy
    entrypoint: >
      /bin/sh -c "
      mc alias set local http://minio:9000 ${MINIO_ROOT_USER} ${MINIO_ROOT_PASSWORD} &&
      mc mb --ignore-existing local/kyc-assets &&
      mc anonymous set download local/kyc-assets &&
      echo 'minio bootstrap done'
      "
    env_file: ./configs/.env
    restart: "no"
```

Và thêm vào block `backend`:
- `depends_on.minio.condition: service_healthy`
- Env: `MINIO_ENDPOINT=http://minio:9000`, `MINIO_BUCKET=kyc-assets`, và 2 access var (tên tùy Q2)

### Tài nguyên docs đã fetch (vẫn còn trong context-mode index, có thể `ctx_search` lại)

- `minio-docker-compose` — container deployment
- `minio-mc-cli` — `mc` command reference
- `minio-bucket-bootstrap-mc` — `mc mb --ignore-existing` semantics
- `minio-anonymous-policy` — `mc anonymous set <policy>` semantics

## Plan tiếp theo (9 task còn lại, theo thứ tự đúng)

1. ✅ Capture design → 0003 (done)
2. ✅ Update CONTEXT.md (done)
3. ⏳ **Add MinIO to docker-compose.yaml** (in_progress, chờ 3 open Q)
4. ⏳ Add AWS SDK S3 dep + minio config to build.gradle / application.yaml
5. ⏳ Create V3 migration: ALTER TABLE customer_profiles ADD COLUMN cho 4 asset URL
6. ⏳ Update CustomerProfile entity + method `submitEkyc(frontUrl, backUrl, selfieUrl, address)`
7. ⏳ Implement `KYCAssetStore` (MinIO upload service, dùng `S3Client` + `forcePathStyle(true)`)
8. ⏳ Implement `EkycService` + `EkycController` (PUT /api/v1/customers/me/ekyc, multipart)
9. ⏳ Add SecurityConfig rule `/api/v1/customers/me/**` → `hasRole("CUSTOMER")`
10. ⏳ Add eKYC exception types + GlobalExceptionHandler entries
11. ⏳ Add eKYC integration test (Testcontainers + MinIO container)

## Quan sát kỹ thuật (cho ngày mai)

- `CustomerProfile` đã có `addAccount(Account)` + `assignTo(User)` + setter validate cho `dateOfBirth` — pattern setter có validation nên khi thêm 4 field mới nên theo pattern tương tự (setter kiểm tra null/empty trước khi gán, throw IllegalArgumentException).
- `Account` dùng `@Check(constraints="balance >= 0")` ở table level — cân nhắc dùng cho cột `kyc_status` nếu cần ràng buộc giá trị (nhưng hiện dùng ENUM trong Java + `VARCHAR(16)` trong SQL, đủ rồi).
- `AuthService.register` tạo `User` + `CustomerProfile` trong 1 `@Transactional`. eKYC submit tương tự — chỉ update `CustomerProfile`, không cần touch `User`.
- `SecurityConfig` filter chain đã có pattern `/api/v1/accounts/**` → `hasRole("CUSTOMER")` — thêm `/api/v1/customers/me/**` theo cùng pattern.
- `application.yaml` dùng `app.jwt.*` và `app.refresh.*` — theo pattern này cho `app.minio.*` (endpoint, access-key, secret-key, bucket).
- `dto/auth/` có các record DTO với Bean Validation annotations (`@NotBlank`, `@Email`, `@Size`, `@Pattern`) — follow pattern này cho DTO multipart (dùng `@RequestPart` + manual validation thay vì `@Valid` cho multipart file).

## Quyết định kiến trúc tui chưa nêu với user (cần quyết sớm)

- **Bucket public vs presigned**: nếu chọn TẮT public read ở Q1, tui phải thêm helper `KYCAssetStore.presignedGetUrl(key, ttl)` dùng `S3Presigner`. Over-scope cho MVP, nên tui nghiêng về BẬT.
- **Object key pattern**: tui định `{customerProfileId}/{assetSlot}/{uuid}.{ext}` — không có quyết định user cần đưa ra, nhưng note ra đây để mai khỏi quên.
- **File type validation**: chỉ allow JPEG/PNG cho CCCD/selfie? Hay cho mọi MIME? MVP đơn giản: chỉ check `contentType.startsWith("image/")`. User không cần quyết, nhưng note ra.
- **Max file size**: Spring default multipart limit = 1MB/ file, 10MB/ request. Ảnh CCCD/selfie 5-10MB là bình thường → cần bump limit trong `application.yaml`. Tui sẽ làm khi đến step đó, không cần user quyết trước.

## Files referenced (để mở lại nhanh)

- `docker-compose.yaml` — line 37-52 là block `backend` cần sửa
- `application.yaml` — thêm `app.minio.*` theo pattern `app.jwt.*`
- `build.gradle` — thêm `software.amazon.awssdk:s3:2.29.x`
- `SecurityConfig.java` line 80-90 — thêm match rule
- `CustomerProfile.java` — thêm 4 field, method `submitEkyc`
- `V1__init_users_accounts.sql` — schema `customer_profiles` đã đọc
- `0003-customer-ekyc-phase-1.md` — ADR đầy đủ
- `CONTEXT.md` — concept mới
