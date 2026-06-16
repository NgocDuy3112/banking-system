package com.nguyenlengocduy.smartbanking.backend.entity.account;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

import com.nguyenlengocduy.smartbanking.backend.entity.profile.CustomerProfile;
import com.nguyenlengocduy.smartbanking.backend.exception.account.*;

@Entity
@Table(name="accounts")
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
@EqualsAndHashCode(of="accountNumber")
public class Account {
    private static final int MONEY_SCALE = 4;
    private static final MathContext MONEY_MATH =
            new MathContext(24, RoundingMode.HALF_EVEN);

    @Id
    @Column(
            name="account_number",
            length=15,
            nullable=false,
            unique=true
    )
    private String accountNumber;

    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(
            name="customer_profile_id",
            nullable=false,
            foreignKey=@ForeignKey(name="fk_accounts_customer_profile")
    )
    private CustomerProfile customerProfile;

    @Setter(AccessLevel.NONE)
    @Column(
            nullable=false,
            precision=24,
            scale=4,
            check=@CheckConstraint(constraint = "balance >= 0")
    )
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=3)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=16)
    private AccountStatus accountStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=16)
    private AccountType accountType;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Setter(AccessLevel.NONE)
    private Instant updatedAt;

    @Version
    private long version;

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

    public Account(
            String accountNumber,
            BigDecimal balance,
            Currency currency,
            AccountType accountType
    ) {
        this.accountNumber = Objects.requireNonNull(accountNumber, "accountNumber must not be null");
        this.balance = Objects.requireNonNull(balance, "balance must not be null");
        this.currency = Objects.requireNonNull(currency, "currency must not be null");
        this.accountType = Objects.requireNonNull(accountType, "accountType must not be null");
        this.accountStatus = AccountStatus.ACTIVE;
    }

    public void assignTo(CustomerProfile customerProfile) {
        Objects.requireNonNull(customerProfile, "customerProfile must not be null");
        if (this.customerProfile != null
                && this.customerProfile.getId() != null
                && !this.customerProfile.getId().equals(customerProfile.getId())) {
            throw new IllegalStateException(
                    "Account " + this.accountNumber
                            + " is already owned by profile " + this.customerProfile.getId()
                            + " and cannot be reassigned to " + customerProfile.getId());
        }
        this.customerProfile = customerProfile;
    }

    private void requireActive() {
        if (this.accountStatus != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(this.accountNumber, this.accountStatus);
        }
    }

    public void credit(BigDecimal amount) {
        requireActive();
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Credit amount must be positive, was: " + amount
            );
        }
        if (amount.scale() > MONEY_SCALE) {
            throw new IllegalArgumentException(
                    "Credit amount must be less than money scale, was: " + amount.scale()
            );
        }
        this.balance = this.balance.add(amount, MONEY_MATH)
                .setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);
    }

    public void debit(BigDecimal amount) {
        requireActive();
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(
                "Debit amount must be positive, was: " + amount);
        }
        if (amount.scale() > MONEY_SCALE) {
            throw new IllegalArgumentException(
                    "Debit amount has too many decimal places: " + amount.scale());
        }
        BigDecimal newBalance = this.balance.subtract(amount, MONEY_MATH)
                .setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);
        if (newBalance.signum() < 0) {
            throw new InsufficientFundsException(this.accountNumber, this.balance, amount);
        }
        this.balance = newBalance;
    }
}
