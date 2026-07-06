# Fix Testcontainers không nhận Podman (macOS) — Symlink Docker socket

## Vấn đề

Khi chạy test dùng Testcontainers (ví dụ `@ServiceConnection` với Postgres container) trên máy dùng **Podman** thay vì Docker Desktop, sẽ gặp lỗi:

```
UnixSocketClientProviderStrategy: failed with exception InvalidConfigurationException
(Could not find unix domain socket). Root cause NoSuchFileException (/var/run/docker.sock)
```

**Nguyên nhân:** Testcontainers mặc định hard-code tìm socket tại `/var/run/docker.sock` (vị trí chuẩn của Docker Desktop/Docker Engine). Nó không tự biết Podman tồn tại hay socket của Podman nằm ở đâu, trừ khi được chỉ định qua `DOCKER_HOST` hoặc `docker.host` property — mà việc set các biến này qua Gradle/IntelliJ thường không được JVM test worker kế thừa đúng cách, gây lỗi dai dẳng dù đã config.

## Giải pháp: Symlink socket của Podman vào vị trí mặc định của Docker

### Cơ chế

`/var/run/docker.sock` là một **Unix domain socket** — một điểm giao tiếp (IPC) giữa các process trên cùng máy, tương tự cổng mạng nhưng hoạt động qua filesystem thay vì network stack. Docker/Podman daemon lắng nghe (listen) trên file socket này; bất kỳ client nào (Testcontainers, Docker CLI...) muốn giao tiếp chỉ cần mở kết nối tới đường dẫn đó.

Một **symlink** chỉ là một "tên" trỏ tới một đường dẫn thật khác — không copy hay tạo dữ liệu mới. Khi tạo:

```bash
sudo ln -sf /path/to/podman.sock /var/run/docker.sock
```

Bất kỳ process nào mở `/var/run/docker.sock`, kernel sẽ tự "theo" symlink và kết nối thẳng tới socket thật của Podman:

```
Testcontainers → mở /var/run/docker.sock
                       │
                (symlink trỏ tới)
                       ▼
   /Users/<user>/.../podman-machine-default/podman.sock
                       │
              (Podman daemon đang listen ở đây)
                       ▼
                 Podman engine
```

Testcontainers tìm thấy `/var/run/docker.sock` theo đúng logic mặc định của nó, hoàn toàn không biết (và không cần biết) đầu bên kia là Podman chứ không phải Docker thật — vì Podman implement tương thích Docker REST API nên mọi request đều được hiểu và trả lời bình thường.

### Các bước thực hiện

**1. Đảm bảo Podman machine đang chạy**
```bash
podman machine list
```
Cột `RUNNING` phải là `true`. Nếu chưa:
```bash
podman machine start
```

**2. Lấy đường dẫn socket thật của Podman**
```bash
podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}'
```

**3. Tạo symlink**
```bash
sudo ln -sf $(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}') /var/run/docker.sock
```
(`-f` để ghi đè nếu có symlink cũ hỏng)

**4. Verify**
```bash
ls -la /var/run/docker.sock
```
Phải thấy trỏ đúng tới file socket của Podman, không phải lỗi "No such file or directory".

**5. Dọn config cũ (nếu có)**

Không cần `docker.host` property hay `DOCKER_HOST` env var nữa vì giờ Testcontainers tự tìm thấy theo path mặc định:
```bash
rm ~/.testcontainers.properties   # nếu không dùng cho việc khác
```

**6. Chạy lại test bình thường**
```bash
./gradlew test
```
hoặc chạy trực tiếp trong IntelliJ.

## ⚠️ Lưu ý: Symlink mất sau khi restart máy

`/var/run` trên macOS là thư mục runtime tạm (trỏ tới `/private/var/run`), bị dọn sạch mỗi lần khởi động lại máy. Vì vậy **sau mỗi lần reboot, phải tạo lại symlink**.

### Mẹo: tạo alias cho tiện

Thêm vào `~/.zshrc`:
```bash
alias podman-docker-link='sudo ln -sf $(podman machine inspect --format "{{.ConnectionInfo.PodmanSocket.Path}}") /var/run/docker.sock'
```

Sau khi restart máy, chỉ cần chạy:
```bash
podman-docker-link
```

### Tự động hóa hoàn toàn (tùy chọn)

Có thể tạo LaunchAgent (macOS) để tự chạy lệnh symlink này mỗi khi máy khởi động, không cần nhớ chạy thủ công — hỏi thêm nếu cần setup phần này.
