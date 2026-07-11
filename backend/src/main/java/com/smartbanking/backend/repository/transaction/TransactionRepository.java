package com.smartbanking.backend.repository.transaction;

import com.smartbanking.backend.entity.transaction.Transaction;

import com.smartbanking.backend.entity.transaction.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;


@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    @Query("""
        SELECT t
        FROM Transaction t
        WHERE (t.fromAccountNumber = :accountNumber OR t.toAccountNumber = :accountNumber)
            AND (CAST(:fromDate AS timestamp) IS NULL OR t.createdAt >= :fromDate)
            AND (CAST(:toDate AS timestamp) IS NULL OR t.createdAt <= :toDate)
            AND (CAST(:transactionType AS string) IS NULL OR t.transactionType = :transactionType)
    """)
    Page<Transaction> findHistory(
            @Param("accountNumber") String accountNumber,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            @Param("transactionType") TransactionType transactionType,
            Pageable pageable
    );

    Optional<Transaction> findByReferenceNumber(String referenceNumber);
}