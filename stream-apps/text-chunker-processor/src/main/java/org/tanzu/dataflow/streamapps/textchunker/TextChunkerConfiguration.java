package org.tanzu.dataflow.streamapps.textchunker;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * Spring Cloud Stream function that splits text into overlapping chunks.
 * Accepts a text payload and emits multiple chunk messages.
 * Each chunk preserves the original message headers plus chunk metadata.
 */
@Configuration
@EnableConfigurationProperties(TextChunkerProperties.class)
public class TextChunkerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TextChunkerConfiguration.class);

    @Bean
    public Function<Message<String>, List<Message<String>>> chunkText(TextChunkerProperties properties) {
        return message -> {
            String text = message.getPayload();
            List<String> chunks = splitIntoChunks(text, properties);
            log.debug("Split {} characters into {} chunks (size={}, overlap={})",
                    text.length(), chunks.size(), properties.size(), properties.overlap());

            List<Message<String>> messages = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                messages.add(MessageBuilder.withPayload(chunks.get(i))
                        .copyHeaders(message.getHeaders())
                        .setHeader("chunk-index", i)
                        .setHeader("chunk-count", chunks.size())
                        .build());
            }
            return messages;
        };
    }

    /**
     * Splits text into overlapping chunks, preferring to break at the configured
     * separator boundary when possible.
     */
    List<String> splitIntoChunks(String text, TextChunkerProperties properties) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        int size = properties.size();
        int overlap = properties.overlap();
        String separator = properties.separator();
        List<String> chunks = new ArrayList<>();

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + size, text.length());

            if (end < text.length()) {
                int separatorPos = text.lastIndexOf(separator, end);
                if (separatorPos > start) {
                    end = separatorPos + separator.length();
                }
            }

            chunks.add(text.substring(start, end).trim());
            start = end - overlap;

            if (start >= text.length()) break;
            if (end == text.length()) break;
        }

        return chunks.stream()
                .filter(chunk -> !chunk.isEmpty())
                .toList();
    }
}
