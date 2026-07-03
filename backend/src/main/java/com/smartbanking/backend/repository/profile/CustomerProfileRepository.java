package com.smartbanking.backend.repository.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

import com.smartbanking.backend.entity.profile.CustomerProfile;

@Repository
public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, UUID> {
    Optional<CustomerProfile> findByCitizenId(String citizenId);
    Optional<CustomerProfile> findByUserId(String userId);
}