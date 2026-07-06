# CONTEXT — Banking MVP (Backend)

Glossary cho domain. **Không** chứa implementation details, schema, hay quyết định kỹ thuật.
Quyết định kỹ thuật thuộc `docs/architecture.md` và `docs/adr/`.

## Scope
Backend Java/Spring Boot của hệ thống ngân hàng MVP. Frontend, ML service, payment rails ngoài scope — sẽ là bounded context khác khi thêm.

---

## Concepts

### User
Authentication subject của hệ thống. Mỗi User có đúng **một** Role. User có thể là cá nhân Customer, hoặc nhân viên ngân hàng (Teller/Auditor/Admin) — không có business/corporate User trong MVP.

### Customer
End user dùng app banking để gửi tiền, chuyển khoản, mở tài khoản. Trong model, Customer được tách thành hai phần:
- **User**: thông tin đăng nhập (email, hashed password, role, status).
- **CustomerProfile**: thông tin KYC (tên, CCCD, ngày sinh, KYC status).

Lý do tách: sau này cần chia sẻ `User` (auth) với staff flow mà không lẫn KYC data.

### CustomerProfile
Hồ sơ KYC gắn với đúng một User có `Role = CUSTOMER`. Mỗi CustomerProfile sở hữu 0..N Account. MVP hiện enforce 0..1 (mỗi customer tối đa một tài khoản cá nhân chính theo quy định hiện hành tại Việt Nam) — phase sau mở rộng thành 1..N khi cho phép tài khoản phụ (savings, credit). Hiện không có cách nào để một CustomerProfile vô chủ (`user = null`).

CustomerProfile có 4 KYC asset (CCCD mặt trước, mặt sau, selfie, address) — xem `KYCAsset`. KYC asset là **gate** để CustomerProfile được phép mở Account thật, không phải hành động mở account. Xem [eKYC Submission](#ekyc-submission).

### KYCAsset
Một file ảnh hoặc text đính kèm CustomerProfile để phục vụ eKYC: CCCD mặt trước, CCCD mặt sau, selfie, và **địa chỉ thường trú** (text — lấy từ dòng "Nơi thường trú" trên CCCD mặt trước). Mỗi CustomerProfile có tối đa một asset cho mỗi slot. Lưu trên object storage (MinIO local, S3 production) — server giữ URL/key trong Postgres, không lưu bytes. Lifecycle: tạo khi submit eKYC, có thể bị thay thế khi resubmit; chưa có retention/cleanup policy trong MVP.

### eKYC Submission
Hành động Customer upload đầy đủ 4 KYC asset lần đầu (hoặc resubmit khi sai). Trong MVP, hành động này tự động chuyển `KYCStatus` từ `PENDING` → `APPROVED` (không có bước staff duyệt — staff flow thuộc phase sau). Endpoint `PUT /api/v1/customers/me/ekyc`, idempotent (resubmit = thay asset cũ). Sau khi submit thành công, Customer có thể gọi endpoint mở Account (phase sau).
_Avoid_: staff-driven manual approval, draft/lưu-dở, content validation (face match, OCR, liveness) — không nằm trong MVP.

### StaffProfile
Hồ sơ nhân viên ngân hàng (Teller, Auditor, Admin). Cấu trúc và quan hệ với `User` **chưa được chốt** — xem [open questions](#open-questions).

### Role
Vai trò của một User trong hệ thống. Giá trị: `ADMIN`, `CUSTOMER`, `AUDITOR`, `TELLER`.
CUSTOMER là role mặc định cho self-registration. Staff roles (ADMIN/AUDITOR/TELLER) được tạo nội bộ, không qua self-register.

### UserStatus
Trạng thái vòng đời của User. Giá trị: `ACTIVE`, `LOCKED`, `DISABLED`.
- `LOCKED` — User bị khóa tạm thời (sai pass nhiều lần, hoặc admin khóa tay).
- `DISABLED` — User bị vô hiệu hóa vĩnh viễn, không thể đăng nhập lại.

### Account
Tài khoản ngân hàng thuộc về đúng một CustomerProfile. Có số dư (balance), loại tiền (Currency), và trạng thái (AccountStatus).

### AccountType
Loại tài khoản. Giá trị: `DEBIT`, `CREDIT`, `SAVINGS`. Tương ứng với product offerings khác nhau; quy tắc nghiệp vụ chi tiết của từng loại thuộc scope sau.

### AccountStatus
Trạng thái vòng đời của Account. Giá trị: `ACTIVE`, `LOCKED`, `CLOSED`.
- `LOCKED` — đóng băng, không cho credit/debit nhưng giữ số dư.
- `CLOSED` — đã đóng, không giao dịch được nữa.

### Currency
Loại tiền tệ của balance. Giá trị: `VND`, `USD`, `EUR`. MVP không giới hạn — schema cho phép thêm sau.

### KYCStatus
Trạng thái eKYC của CustomerProfile. Giá trị: `PENDING`, `APPROVED`, `REJECTED`. Mặc định khi tạo là `PENDING`. Đây là gate để CustomerProfile được phép mở Account thật.

---

## Authentication

### AccessToken
Short-lived (30 phút) bearer credential kèm theo mỗi request đến API. Chứa `sub` (User id), `role`, `email`. Chỉ chứng minh "ai gọi", không chứng minh "được làm gì với resource nào" — authorization luôn query lại DB.
_Avoid_: session token, auth cookie (ngoài scope MVP).

### RefreshToken
Long-lived (7 ngày) credential dùng để đổi lấy AccessToken mới. Là opaque random string (không phải JWT), lưu trên server dưới dạng hash. Client chỉ giữ plaintext; server so khớp hash.
_Avoid_: long-lived JWT (không thể revoke ngay lập tức).

### Session
Đơn vị "user hiện đang đăng nhập ở một nơi". MVP chỉ cho phép **một Session per User tại một thời điểm** (single-session) — login mới sẽ kill session cũ. Xem `docs/adr/0001-customer-auth-phase-1.md` để biết lý do.
_Avoid_: multi-session, family tree, rotation chain (Phase 2 trở đi nếu cần).

### Registration
Hành động tạo mới một User có `Role = CUSTOMER`, đi kèm `CustomerProfile` ở trạng thái `PENDING`. Hai entity tạo trong cùng một transaction — không tồn tại "User CUSTOMER mà không có Profile".
_Avoid_: staff registration (TELLER/AUDITOR/ADMIN tạo nội bộ — Phase sau).

---

## Open questions

Các câu còn vướng trong domain, cần chốt trước khi viết feature:

- **StaffProfile layout**: cấu trúc quan hệ `User ↔ StaffProfile` chưa quyết. Cần chốt khi implement staff onboarding.
- **Card ↔ Account**: docs đề cập Card entity, code chưa có. Cần chốt khi viết Card module.
- **Object storage backend**: MinIO trong dev, S3 production — chưa chốt cụ thể bucket name convention, lifecycle policy, presigned URL TTL.

---

## Related docs

- [docs/architecture.md](docs/architecture.md) — module boundaries, tech decisions
- [docs/data-modeling.md](docs/data-modeling.md) — entity/field mapping (implementation view)
- [docs/functional-requirements.md](docs/functional-requirements.md) — what the system does
- [docs/nonfunctional-requirements.md](docs/nonfunctional-requirements.md) — how the system behaves
