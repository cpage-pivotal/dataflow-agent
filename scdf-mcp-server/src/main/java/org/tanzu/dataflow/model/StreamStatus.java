package org.tanzu.dataflow.model;

import java.util.List;
import java.util.Map;

/**
 * Deployment status of a stream and its constituent app instances.
 */
public record StreamStatus(
        String name,
        String status,
        String description,
        Map<String, List<AppInstanceStatus>> appStatuses
) {}
