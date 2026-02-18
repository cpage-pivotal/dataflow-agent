package org.tanzu.dataflow.streamapps.embedding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Bridges CredHub-injected credentials to Spring AI configuration properties.
 * When deployed on Cloud Foundry with a CredHub service binding, the EMBEDDING_API_KEY
 * is available in VCAP_SERVICES. This config maps it to the Spring AI OpenAI API key property.
 */
@Configuration
public class CredHubEmbeddingConfig {

    @Value("${EMBEDDING_API_KEY:#{null}}")
    private String embeddingApiKey;

    @PostConstruct
    void configureApiKey() {
        if (embeddingApiKey != null && !embeddingApiKey.isBlank()) {
            System.setProperty("spring.ai.openai.api-key", embeddingApiKey);
        }
    }
}
