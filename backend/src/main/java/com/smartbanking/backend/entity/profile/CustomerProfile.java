package com.smartbanking.backend.entity.profile;

import com.github.f4b6a3.uuid.UuidCreator;
import com.smartbanking.backend.entity.account.Account;
import com.smartbanking.backend.entity.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "customer_profiles")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class CustomerProfile {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "user_id",
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(name = "fk_customer_profiles_users_id")
    )
    private User user;

    @OneToMany(
        mappedBy = "customerProfile",
        cascade = { CascadeType.PERSIST, CascadeType.MERGE },
        fetch = FetchType.LAZY
    )
    private List<Account> accounts = new ArrayList<>();

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "citizen_id", nullable = false, length = 12, unique = true)
    private String citizenId;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "cccd_front_image_key", length = 512)
    private String cccdFrontImageKey;

    @Column(name = "cccd_back_image_key", length = 512)
    private String cccdBackImageKey;

    @Column(name = "selfie_image_key", length = 512)
    private String selfieImageKey;

    @Column(name = "address", length = 512)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 16)
    private KYCStatus kycStatus;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Setter(AccessLevel.NONE)
    private Instant updatedAt;

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

    public CustomerProfile(
        User user,
        String fullName,
        String citizenId,
        LocalDate dateOfBirth
    ) {
        this.id = UuidCreator.getTimeOrderedEpoch();
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.fullName = Objects.requireNonNull(fullName, "fullName must not be null");
        this.citizenId = Objects.requireNonNull(citizenId, "citizenId must not be null");
        this.dateOfBirth = dateOfBirth;
        this.kycStatus = KYCStatus.PENDING;
    }

    public void assignTo(User user) {
        Objects.requireNonNull(user, "user must not be null");
        if (this.user != null && this.user.getId() != null && !this.user.getId().equals(user.getId())) {
            throw new IllegalStateException(
                    "CustomerProfile is already assigned to a User");
        }
        this.user = user;
    }

    public void addAccount(Account account) {
        Objects.requireNonNull(account, "account must not be null");
        account.assignTo(this);
        this.accounts.add(account);
    }

    public void removeAccount(Account account) {
        Objects.requireNonNull(account, "account must not be null");
        if (!this.accounts.remove(account)) {
            throw new IllegalStateException(
                    "Account is not assigned to this profile: "
                            + account.getAccountNumber());
        }
    }

    public void submitEkyc(
            String cccdFrontImageKey,
            String cccdBackImageKey,
            String selfieImageKey,
            String address
    ) {
        this.cccdFrontImageKey = Objects.requireNonNull(cccdFrontImageKey, "cccdFrontKey must not be null");
        this.cccdBackImageKey = Objects.requireNonNull(cccdBackImageKey, "cccdBackKey must not be null");
        this.selfieImageKey = Objects.requireNonNull(selfieImageKey, "selfieKey must not be null");
        this.address = Objects.requireNonNull(address, "address must not be null");
        this.kycStatus = KYCStatus.APPROVED;
    }
}