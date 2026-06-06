package com.vibe.rewardsservice.repository;

import com.vibe.rewardsservice.model.entity.CashoutRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface CashoutRepository extends JpaRepository<CashoutRequest, UUID> {}
