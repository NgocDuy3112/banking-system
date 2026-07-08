package com.smartbanking.backend.repository.account;

import com.smartbanking.backend.entity.account.Account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, String> {
        List<Account> findByCustomerProfileId(UUID customerProfileId);
        Optional<Account> findByAccountNumberAndCustomerProfileId(String accountNumber, UUID customerProfileId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT a FROM Account a WHERE a.accountNumber = :accountNumber")
        Optional<Account> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);
}