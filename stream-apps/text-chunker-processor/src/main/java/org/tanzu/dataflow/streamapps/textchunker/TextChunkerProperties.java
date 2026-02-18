package org.tanzu.dataflow.streamapps.textchunker;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chunker")
public record TextChunkerProperties(
        int size,
        int overlap,
        String separator
) {
    public TextChunkerProperties {
        if (size <= 0) size = 1000;
        if (overlap < 0) overlap = 200;
        if (separator == null || separator.isEmpty()) separator = "\n\n";
    }
}
