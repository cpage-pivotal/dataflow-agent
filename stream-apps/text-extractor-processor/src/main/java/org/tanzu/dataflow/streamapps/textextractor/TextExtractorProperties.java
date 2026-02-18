package org.tanzu.dataflow.streamapps.textextractor;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "extractor")
public record TextExtractorProperties(
        Set<String> formats
) {
    public TextExtractorProperties {
        if (formats == null || formats.isEmpty()) {
            formats = Set.of("pdf", "docx", "txt");
        }
    }
}
