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

    @Column(name="hashed_password", nullable=false)
    private String hashedPassword;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=16)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=16)
    private UserStatus status;

    @Version
    private long version;

    @Column(name="created_at", nullable=false, updatable=false)
    @Setter(AccessLevel.NONE)
    private Instant createdAt;

    @Column(name="updated_at", nullable=false)
    @Setter(AccessLevel.NONE)
    private Instant updatedAt;

    @OneToOne(
            mappedBy = "user",
            cascade = { CascadeType.PERSIST, CascadeType.MERGE },
            optional = false
    )
    private CustomerProfile profile;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public User(
            String email,
            String phoneNumber,
            String hashedPassword,
            Role role
    ) {
        this.id = UuidCreator.getTimeOrderedEpoch();
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.phoneNumber = phoneNumber;
        this.hashedPassword = Objects.requireNonNull(hashedPassword, "hashedPassword must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.status = UserStatus.ACTIVE;
    }

    public void assignProfile(CustomerProfile profile) {
        Objects.requireNonNull(profile, "profile must not be null");
        if (this.profile != null) {
            throw new IllegalStateException("User already has a profile");
        }
        profile.assignTo(this);
        this.profile = profile;
    }
}
