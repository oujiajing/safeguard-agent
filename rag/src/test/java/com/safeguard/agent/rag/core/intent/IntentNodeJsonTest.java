package com.safeguard.agent.rag.core.intent;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class IntentNodeJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test
    void roundTripsCollectionNamesWithoutSerializingComputedProperty() throws Exception {
        IntentNode node = IntentNode.builder()
                .id("insurance")
                .collectionNames(List.of("insurance", " claims ", "insurance"))
                .build();

        String json = objectMapper.writeValueAsString(node);
        IntentNode restored = objectMapper.readValue(json, IntentNode.class);

        assertFalse(json.contains("effectiveCollectionNames"));
        assertEquals(List.of("insurance", "claims"), restored.getEffectiveCollectionNames());
    }

    @Test
    void ignoresComputedPropertyFromExistingCacheEntry() throws Exception {
        String json = """
                {
                  "id": "insurance",
                  "collectionNames": ["insurance"],
                  "effectiveCollectionNames": ["stale"]
                }
                """;

        IntentNode restored = objectMapper.readValue(json, IntentNode.class);

        assertEquals(List.of("insurance"), restored.getEffectiveCollectionNames());
    }
}
