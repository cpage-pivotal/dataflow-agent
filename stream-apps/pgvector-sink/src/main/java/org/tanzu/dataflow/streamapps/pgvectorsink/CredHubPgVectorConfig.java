package org.tanzu.dataflow.streamapps.pgvectorsink;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Bridges CredHub-injected credentials to Spring datasource and AI properties.
 * When deployed on Cloud Foundry with CredHub service bindings:
 * - PGVECTOR_URL, PGVECTOR_USERNAME, PGVECTOR_PASSWORD provide database access
 * - EMBEDDING_API_KEY provides the embedding model API key
 */
@Configuration
public class CredHubPgVectorConfig {

    @Value("${PGVECTOR_URL:#{null}}")
    private String pgvectorUrl;

    @Value("${PGVECTOR_USERNAME:#{null}}")
    private String pgvectorUsername;

    @Value("${PGVECTOR_PASSWORD:#{null}}")
    private String pgvectorPassword;

    @Value("${EMBEDDING_API_KEY:#{null}}")
    private String embeddingApiKey;

    @PostConstruct
    void configureProperties() {
        if (pgvectorUrl != null && !pgvectorUrl.isBlank()) {
            System.setProperty("spring.datasource.url", pgvectorUrl);
        }
        if (pgvectorUsername != null && !pgvectorUsername.isBlank()) {
            System.setProperty("spring.datasource.username", pgvectorUsername);
        }
        if (pgvectorPassword != null && !pgvectorPassword.isBlank()) {
            System.setProperty("spring.datasource.password", pgvectorPassword);
        }
        if (embeddingApiKey != null && !embeddingApiKey.isBlank()) {
            System.setProperty("spring.ai.openai.api-key", embeddingApiKey);
        }
    }
}
