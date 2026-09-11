package com.fantasy.db.checkout;

import com.fantasy.db.checkout.dto.ReplacePendingCheckoutRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(PendingCheckoutService.class)
class PendingCheckoutServiceTest {

    @Autowired
    private PendingCheckoutService service;

    @Autowired
    private PendingCheckoutRepository repository;

    private final UUID userId = UUID.randomUUID();

    private static ReplacePendingCheckoutRequest store(String reference, String replacesReference) {
        return new ReplacePendingCheckoutRequest(
                "paddle", reference, "https://slapstat.test/pay?_ptxn=" + reference, replacesReference);
    }

    @Test
    void storesAUsersFirstCheckout() {
        service.replace(userId, store("txn_1", null));

        PendingCheckout found = service.find(userId);
        assertThat(found.getReference()).isEqualTo("txn_1");
        assertThat(found.getCheckoutUrl()).isEqualTo("https://slapstat.test/pay?_ptxn=txn_1");
    }

    @Test
    void findThrowsWhenTheUserHasNone() {
        assertThatThrownBy(() -> service.find(UUID.randomUUID())).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void replacesTheCheckoutTheCallerRead() {
        service.replace(userId, store("txn_1", null));

        PendingCheckout replaced = service.replace(userId, store("txn_2", "txn_1"));

        assertThat(replaced.getReference()).isEqualTo("txn_2");
        assertThat(repository.findAll()).hasSize(1);
    }

    /**
     * Two requests read txn_1 and each opened a new checkout. The first to store wins; the second
     * names a checkout that is no longer stored, is refused, and hands out the winner's instead.
     */
    @Test
    void refusesToReplaceACheckoutThatChangedSinceItWasRead() {
        service.replace(userId, store("txn_1", null));
        service.replace(userId, store("txn_2", "txn_1"));

        assertThatThrownBy(() -> service.replace(userId, store("txn_3", "txn_1")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(service.find(userId).getReference()).isEqualTo("txn_2");
    }

    /** Both tabs found no checkout: the second to store must not overwrite the first. */
    @Test
    void refusesASecondFirstCheckout() {
        service.replace(userId, store("txn_1", null));

        assertThatThrownBy(() -> service.replace(userId, store("txn_2", null)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(service.find(userId).getReference()).isEqualTo("txn_1");
    }

    /**
     * The same race when both checks pass before either insert lands: the primary key refuses the
     * second insert rather than letting a merge overwrite the first, which is what Persistable buys.
     */
    @Test
    void aConcurrentFirstInsertConflictsOnTheKeyInsteadOfOverwriting() {
        Instant now = Instant.now();
        repository.saveAndFlush(new PendingCheckout(userId, "paddle", "txn_1", "https://slapstat.test/pay", now, now));

        assertThatThrownBy(() -> repository.saveAndFlush(
                new PendingCheckout(userId, "paddle", "txn_2", "https://slapstat.test/pay", now, now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
