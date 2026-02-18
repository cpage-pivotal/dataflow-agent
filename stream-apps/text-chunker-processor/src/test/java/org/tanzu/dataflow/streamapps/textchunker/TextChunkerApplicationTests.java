package org.tanzu.dataflow.streamapps.textchunker;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;

@SpringBootTest
class TextChunkerApplicationTests {

    @Autowired
    private Function<Message<String>, List<Message<String>>> chunkText;

    @Test
    void chunksSmallTextIntoSingleChunk() {
        Message<String> message = MessageBuilder
                .withPayload("Short text that fits in one chunk.")
                .build();

        List<Message<String>> result = chunkText.apply(message);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getPayload()).isEqualTo("Short text that fits in one chunk.");
        assertThat(result.getFirst().getHeaders().get("chunk-index")).isEqualTo(0);
        assertThat(result.getFirst().getHeaders().get("chunk-count")).isEqualTo(1);
    }

    @Test
    void chunksLargeTextWithOverlap() {
        String text = "a".repeat(2500);
        Message<String> message = MessageBuilder.withPayload(text).build();

        List<Message<String>> result = chunkText.apply(message);

        assertThat(result.size()).isGreaterThan(1);
        for (Message<String> chunk : result) {
            assertThat(chunk.getPayload().length()).isLessThanOrEqualTo(1000);
        }
    }
}
