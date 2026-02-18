package org.tanzu.dataflow.streamapps.textextractor;

import java.io.ByteArrayInputStream;
import java.util.function.Function;

import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * Spring Cloud Stream function that extracts text from binary documents.
 * Accepts a byte[] payload (PDF, DOCX, or plain text) and emits the extracted text.
 * Uses Apache Tika for content detection and text extraction.
 */
@Configuration
@EnableConfigurationProperties(TextExtractorProperties.class)
public class TextExtractorConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TextExtractorConfiguration.class);

    @Bean
    public Tika tika() {
        return new Tika();
    }

    @Bean
    public Function<Message<byte[]>, Message<String>> extractText(Tika tika, TextExtractorProperties properties) {
        return message -> {
            byte[] payload = message.getPayload();
            try {
                String mimeType = tika.detect(payload);
                log.debug("Detected MIME type: {}", mimeType);

                if (!isSupportedFormat(mimeType, properties)) {
                    log.warn("Unsupported format: {}. Supported: {}", mimeType, properties.formats());
                    return null;
                }

                String text = tika.parseToString(new ByteArrayInputStream(payload));
                log.debug("Extracted {} characters of text", text.length());

                return MessageBuilder.withPayload(text)
                        .copyHeaders(message.getHeaders())
                        .setHeader("content-type", "text/plain")
                        .setHeader("original-mime-type", mimeType)
                        .build();
            } catch (Exception e) {
                log.error("Text extraction failed: {}", e.getMessage(), e);
                throw new RuntimeException("Text extraction failed: " + e.getMessage(), e);
            }
        };
    }

    private boolean isSupportedFormat(String mimeType, TextExtractorProperties properties) {
        var formats = properties.formats();
        if (formats.contains("pdf") && mimeType.equals("application/pdf")) return true;
        if (formats.contains("docx") && mimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) return true;
        if (formats.contains("txt") && mimeType.startsWith("text/")) return true;
        if (formats.contains("doc") && mimeType.equals("application/msword")) return true;
        return false;
    }
}
