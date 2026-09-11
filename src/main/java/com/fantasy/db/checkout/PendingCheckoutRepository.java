package com.fantasy.db.checkout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface PendingCheckoutRepository extends JpaRepository<PendingCheckout, UUID> {

    /**
     * Replaces the user's checkout only if it is still the one the caller read. One statement, so
     * two requests that both read the same checkout cannot both replace it: the second matches no
     * row. Returns the number of rows changed, 0 or 1.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PendingCheckout p set p.provider = :provider, p.reference = :reference, "
            + "p.checkoutUrl = :checkoutUrl, p.updatedAt = :updatedAt "
            + "where p.userId = :userId and p.reference = :replacesReference")
    int replaceIfCurrent(@Param("userId") UUID userId,
                         @Param("provider") String provider,
                         @Param("reference") String reference,
                         @Param("checkoutUrl") String checkoutUrl,
                         @Param("updatedAt") Instant updatedAt,
                         @Param("replacesReference") String replacesReference);
}
