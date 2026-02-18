package org.tanzu.dataflow.streamapps.embedding;

import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * Spring Cloud Stream function that converts text into vector embeddings.
 * Accepts a text payload and emits the embedding as a float array.
 * Uses Spring AI's {@link EmbeddingModel} abstraction, configured for OpenAI by default.
 * The API key is injected via CredHub service binding (EMBEDDING_API_KEY in VCAP_SERVICES).
 */
@Configuration
@EnableConfigurationProperties(EmbeddingProperties.class)
public class EmbeddingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingConfiguration.class);

    @Bean
    public Function<Message<String>, Message<float[]>> generateEmbedding(
            EmbeddingModel embeddingModel, EmbeddingProperties properties) {
        return message -> {
            String text = message.getPayload();
            log.debug("Generating embedding for {} characters using model={}, dimensions={}",
                    text.length(), properties.model(), properties.dimensions());

            EmbeddingResponse response = embeddingModel.call(
                    new EmbeddingRequest(java.util.List.of(text), null));

            float[] embedding = response.getResult().getOutput();
            log.debug("Generated embedding with {} dimensions", embedding.length);

            return MessageBuilder.withPayload(embedding)
                    .copyHeaders(message.getHeaders())
                    .setHeader("embedding-model", properties.model())
                    .setHeader("embedding-dimensions", embedding.length)
                    .build();
        };
    }
}
