package com.skulify.pocketmoney.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<PmIdempotencyKey, String> {
}
