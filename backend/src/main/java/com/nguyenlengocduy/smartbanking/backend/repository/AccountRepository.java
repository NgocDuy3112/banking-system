package com.nguyenlengocduy.smartbanking.backend.repository;

import com.nguyenlengocduy.smartbanking.backend.entity.account.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, String> {

    List<Account> findByCustomerProfileId(UUID customerProfileId);

    Optional<Account> findByAccountNumberAndCustomerProfileId(
            String accountNumber, UUID customerProfileId);

    /**
     * Acquires a row-level write lock on the account. Must be called inside a
     * transactional method; concurrent callers will block until the holding
     * transaction commits or rolls back. Use this for any flow that mutates
     * balance (transfer, lock/unlock with side effects).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberForUpdate(
            @Param("accountNumber") String accountNumber);
}
