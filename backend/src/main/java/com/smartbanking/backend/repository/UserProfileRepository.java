package com.smartbanking.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

import com.smartbanking.backend.entity.profile.CustomerProfile;

@Repository
public interface UserProfileRepository extends JpaRepository<CustomerProfile, UUID> {
    Optional<CustomerProfile> findByCitizenId(String citizenId);
}
