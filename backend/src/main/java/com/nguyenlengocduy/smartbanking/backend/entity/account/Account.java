package com.nguyenlengocduy.smartbanking.backend.entity.account;

import jakarta.persistence.*;
//Tài khoản ngân hàng chứa dữ liệu về tiền bạc người dùng, nên bắt buộc phải được lưu trữ vĩnh viễn và an toàn vào database
// (không thể chỉ lưu trên RAM).Tài khoản ngân hàng chứa dữ liệu về tiền bạc người dùng,
// nên bắt buộc phải được lưu trữ vĩnh viễn và an toàn vào database (không thể chỉ lưu trên RAM).
import lombok.*;
//giúp code sạch sẽ không bị rối mắt
import java.util.List;
//một ngân hàng không bao giờ đứng một mình mà sẽ luôn có mối quan hệ với các thực thể khác
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

import com.nguyenlengocduy.smartbanking.backend.entity.profile.CustomerProfile;
import com.nguyenlengocduy.smartbanking.backend.exception.account.*;
 //phyeng_update
//Các khai báo bắt buột của import jakarta.persistence
@Entity //một thực thể database
@Id //khai báo trường nào là khóa chính
@Column(unique = true) //đảm bảo mỗi số tài khoản là duy nhất
//nếu không có các thư viện này thì data chỉ là một data "chết" lưu trong ram tạm thời chứ không down xuống được database
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Service
@RequiredArgsConstructor
public class Account {
 //dành cho hệ thống quản lí ngầm (tự đếm 1, 2, 3, ...)
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true, nullable = false)
  private String accountNumber;



 }
