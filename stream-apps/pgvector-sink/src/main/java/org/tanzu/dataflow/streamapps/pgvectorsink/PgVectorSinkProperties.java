package org.tanzu.dataflow.streamapps.pgvectorsink;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pgvector")
public record PgVectorSinkProperties(
        String table,
        int dimensions,
        String indexType,
        String distanceType
) {
    public PgVectorSinkProperties {
        if (table == null || table.isBlank()) table = "vector_store";
        if (dimensions <= 0) dimensions = 1536;
        if (indexType == null || indexType.isBlank()) indexType = "HNSW";
        if (distanceType == null || distanceType.isBlank()) distanceType = "COSINE_DISTANCE";
    }
}
