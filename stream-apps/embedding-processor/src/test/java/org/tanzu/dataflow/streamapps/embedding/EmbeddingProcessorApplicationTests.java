package org.tanzu.dataflow.streamapps.embedding;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.ai.openai.api-key=test-key",
        "spring.ai.openai.embedding.options.model=text-embedding-3-small"
})
class EmbeddingProcessorApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the application context starts correctly with test properties.
        // Full embedding tests require a live OpenAI API key.
    }
}
