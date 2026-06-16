# Functional Requirements — Banking MVP

## Core Features

| Feature | Mô tả |
|---|---|
| Tạo tài khoản | Customer đăng ký online, eKYC đơn giản (upload CCCD + selfie, không verify thật) |
| Chuyển tiền nội bộ | Chuyển giữa các tài khoản trong cùng ngân hàng, đảm bảo ACID & concurrency |
| Chuyển liên ngân hàng | Mock NAPAS 247, giả lập response từ ngân hàng ngoài |
| Xem số dư | Customer xem số dư tài khoản real-time |
| Lịch sử giao dịch | Xem danh sách giao dịch, filter theo thời gian, loại giao dịch |
| Khóa/mở khóa thẻ | Customer tự khóa/mở thẻ (độc lập với tài khoản) |
| Khóa/mở khóa tài khoản | Customer/Teller khóa/mở tài khoản (đóng băng hoàn toàn) |
| Fraud Detection | ML real-time chặn giao dịch đáng ngờ trước khi thực hiện |
| Audit Log | Ghi lại toàn bộ hành động trong hệ thống |
| 2FA (OTP) | Xác thực OTP khi chuyển tiền, đăng nhập |

## Actors

### Customer
- Tự đăng ký tài khoản online (eKYC)
- Thực hiện giao dịch: chuyển tiền, xem số dư, lịch sử
- Tự khóa/mở khóa thẻ và tài khoản
- Nhận thông báo khi có giao dịch

### Teller
- Hỗ trợ Customer tạo tài khoản tại quầy
- Approve/Reject KYC thủ công
- Nạp/rút tiền mặt tại quầy
- Khóa/mở khóa tài khoản theo yêu cầu Customer
- Tra cứu thông tin tài khoản Customer

### Auditor
- Xem toàn bộ Audit Log
- Xem lịch sử giao dịch của bất kỳ tài khoản nào
- Review các giao dịch bị flag fraud
- **Chỉ có quyền đọc — không thực hiện bất kỳ thao tác ghi nào**

### Admin
- Tạo/quản lý tài khoản Staff (Teller, Auditor)
- Cấu hình hạn mức chuyển tiền, phí giao dịch
- Xem dashboard tổng quan hệ thống
- Kích hoạt/vô hiệu hoá tài khoản Staff