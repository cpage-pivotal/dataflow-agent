package org.tanzu.dataflow.model;

/**
 * Represents a registered stream application in SCDF.
 */
public record StreamAppInfo(
        String name,
        String type,
        String uri,
        String version
) {}
