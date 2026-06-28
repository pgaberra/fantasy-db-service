package com.fantasy.db.projection.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DraftStateDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void tolerates_legacy_draft_jsonb_with_removed_by_field() throws Exception {
        String legacy = "{\"picks\":[{\"playerId\":1,\"by\":\"me\"},{\"playerId\":2,\"by\":\"others\"}]}";

        assertThatCode(() -> objectMapper.readValue(legacy, DraftState.class))
                .doesNotThrowAnyException();

        DraftState draft = objectMapper.readValue(legacy, DraftState.class);
        assertThat(draft.picks()).hasSize(2);
        assertThat(draft.picks().getFirst().teamId()).isNull();
        assertThat(draft.teams()).isNull();
        assertThat(draft.order()).isNull();
    }
}
