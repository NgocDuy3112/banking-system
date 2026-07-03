package com.smartbanking.backend.repository.account;

import com.smartbanking.backend.entity.account.Account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, String> {

        List<Account> findByCustomerProfileId(UUID customerProfileId);

        Optional<Account> findByAccountNumberAndCustomerProfileId(String accountNumber, UUID customerProfileId);
}