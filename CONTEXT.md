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
Hồ sơ KYC gắn với đúng một User có `Role = CUSTOMER`. Mỗi CustomerProfile sở hữu 0..N Account. Hiện không có cách nào để một CustomerProfile vô chủ (`user = null`).

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

---

## Related docs

- [docs/architecture.md](docs/architecture.md) — module boundaries, tech decisions
- [docs/data-modeling.md](docs/data-modeling.md) — entity/field mapping (implementation view)
- [docs/functional-requirements.md](docs/functional-requirements.md) — what the system does
- [docs/nonfunctional-requirements.md](docs/nonfunctional-requirements.md) — how the system behaves
