package com.fantasy.db.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PlayerBasisTest {

    /** The BFF writes these codes into the stored settings, so each has to read back as itself. */
    @Test
    void everyBasisReadsBackFromItsOwnCode() {
        for (PlayerBasis basis : PlayerBasis.values()) {
            assertThat(PlayerBasis.fromCode(basis.getCode())).isEqualTo(basis);
        }
        assertThat(PlayerBasis.MODEL.getCode()).isEqualTo("model");
    }

    @Test
    void refusesACodeItDoesNotKnow() {
        assertThatThrownBy(() -> PlayerBasis.fromCode("ai"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
