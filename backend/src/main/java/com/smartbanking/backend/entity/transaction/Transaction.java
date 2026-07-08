package com.smartbanking.backend.entity.transaction;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;


@Entity
@Table(name = "transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class Transaction {
    @Id
    @Column(nullable=false, updatable=false)
    private UUID id;

    @Setter(AccessLevel.NONE)
    @Column(name = "reference_number", nullable = false, unique = true, length = 32)
    private String referenceNumber;

    @Setter(AccessLevel.NONE)
    @Column(name = "from_account_number", nullable = false, length = 15, updatable = false)
    private String fromAccountNumber;

    @Setter(AccessLevel.NONE)
    @Column(name = "to_account_number", nullable = false, length = 15, updatable = false)
    private String toAccountNumber;

    @Setter(AccessLevel.NONE)
    @Column(name = "amount", nullable = false, precision = 24, scale = 4, updatable = false)
    private BigDecimal amount;

    @Setter(AccessLevel.NONE)
    @Column(name = "fee", nullable = false, precision = 24, scale = 4, updatable = false)
    private BigDecimal fee;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 16, updatable = false)
    private TransactionType transactionType;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_status", nullable = false, length = 16)
    private TransactionStatus transactionStatus;

    @Column(name = "fraud_score")
    private Float fraudScore;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "fraud_status", length = 16, updatable = false)
    private FraudStatus fraudStatus;

    @Setter(AccessLevel.NONE)
    @Column(name = "from_balance_before", nullable = false, precision = 24, scale = 4, updatable = false)
    private BigDecimal fromBalanceBefore;

    @Setter(AccessLevel.NONE)
    @Column(name = "from_balance_after", nullable = false, precision = 24, scale = 4, updatable = false)
    private BigDecimal fromBalanceAfter;

    @Setter(AccessLevel.NONE)
    @Column(name = "to_balance_before", nullable = false, precision = 24, scale = 4, updatable = false)
    private BigDecimal toBalanceBefore;

    @Setter(AccessLevel.NONE)
    @Column(name = "to_balance_after", nullable = false, precision = 24, scale = 4, updatable = false)
    private BigDecimal toBalanceAfter;

    @Column
    private String description;

    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private long version;

    @PrePersist
    private void onCreate() {
        this.createdAt = Instant.now();
    }

    public Transaction(
            UUID id,
            String referenceNumber,
            String fromAccountNumber,
            String toAccountNumber,
            BigDecimal amount,
            BigDecimal fee,
            TransactionType transactionType,
            TransactionStatus transactionStatus,
            Float fraudScore,
            FraudStatus fraudStatus,
            BigDecimal fromBalanceBefore,
            BigDecimal fromBalanceAfter,
            BigDecimal toBalanceBefore,
            BigDecimal toBalanceAfter,
            String description
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.referenceNumber = Objects.requireNonNull(referenceNumber, "referenceNumber must not be null");
        this.fromAccountNumber = Objects.requireNonNull(fromAccountNumber, "fromAccountNumber must not be null");
        this.toAccountNumber = Objects.requireNonNull(toAccountNumber, "toAccountNumber must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.fee = Objects.requireNonNull(fee, "fee must not be null");
        this.transactionType = Objects.requireNonNull(transactionType, "transactionType must not be null");
        this.transactionStatus = Objects.requireNonNull(transactionStatus, "status must not be null");
        this.fraudScore = fraudScore;
        this.fraudStatus = fraudStatus;
        this.fromBalanceBefore = Objects.requireNonNull(fromBalanceBefore, "fromBalanceBefore must not be null");
        this.fromBalanceAfter = Objects.requireNonNull(fromBalanceAfter, "fromBalanceAfter must not be null");
        this.toBalanceBefore = Objects.requireNonNull(toBalanceBefore, "toBalanceBefore must not be null");
        this.toBalanceAfter = Objects.requireNonNull(toBalanceAfter, "toBalanceAfter must not be null");
        this.description = description;
    }

    public void markSuccess() {
        this.transactionStatus = TransactionStatus.SUCCESS;
    }

    public void markFailed() {
        this.transactionStatus = TransactionStatus.FAILED;
    }

    public void markBlocked() {
        this.transactionStatus = TransactionStatus.BLOCKED;
    }
}