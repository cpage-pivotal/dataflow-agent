package org.tanzu.dataflow.streamapps.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "embedding")
public record EmbeddingProperties(
        String model,
        int dimensions
) {
    public EmbeddingProperties {
        if (model == null || model.isBlank()) model = "text-embedding-3-small";
        if (dimensions <= 0) dimensions = 1536;
    }
}
