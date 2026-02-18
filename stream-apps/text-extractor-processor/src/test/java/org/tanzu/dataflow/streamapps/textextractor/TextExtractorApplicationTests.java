package org.tanzu.dataflow.streamapps.textextractor;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TextExtractorApplicationTests {

    @Autowired
    private Function<Message<byte[]>, Message<String>> extractText;

    @Test
    void extractsPlainText() {
        String input = "Hello, this is a plain text document.";
        Message<byte[]> message = MessageBuilder
                .withPayload(input.getBytes(StandardCharsets.UTF_8))
                .build();

        Message<String> result = extractText.apply(message);

        assertThat(result).isNotNull();
        assertThat(result.getPayload()).contains("Hello, this is a plain text document.");
    }
}
