package org.tanzu.dataflow.model;

import java.util.Map;

/**
 * Runtime status of an individual app instance within a deployed stream.
 */
public record AppInstanceStatus(
        String instanceId,
        String state,
        Map<String, String> attributes
) {}
