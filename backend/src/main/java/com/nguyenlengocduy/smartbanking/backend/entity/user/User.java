package com.nguyenlengocduy.smartbanking.backend.entity.user;

import com.nguyenlengocduy.smartbanking.backend.entity.profile.CustomerProfile;
import jakarta.persistence.*;
import lombok.*;
import com.github.f4b6a3.uuid.UuidCreator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name="users")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of="id")
public class User {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable=false, unique=true, length=320)
    private String email;

    @Column(name="phone_number", unique=true, length=20)
    private String phoneNumber;

    @Column(unique = true, nullable = false)
    private String citizenId;

    @Column(name="hashed_password", nullable=false)
    private String hashedPassword;

}
