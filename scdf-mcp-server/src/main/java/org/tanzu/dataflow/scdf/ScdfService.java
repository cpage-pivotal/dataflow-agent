package org.tanzu.dataflow.scdf;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.rest.client.DataFlowOperations;
import org.springframework.cloud.dataflow.rest.resource.AppRegistrationResource;
import org.springframework.cloud.dataflow.rest.resource.StreamDefinitionResource;
import org.springframework.cloud.dataflow.rest.resource.StreamStatusResource;
import org.springframework.hateoas.PagedModel;
import org.springframework.stereotype.Service;

import org.tanzu.dataflow.model.AppInstanceStatus;
import org.tanzu.dataflow.model.StreamAppInfo;
import org.tanzu.dataflow.model.StreamStatus;

/**
 * Thin wrapper around {@link DataFlowOperations} that translates between the
 * SCDF REST client types and the domain model records exposed by MCP tools.
 */
@Service
public class ScdfService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DataFlowOperations dataFlow;

    ScdfService(DataFlowOperations dataFlow) {
        this.dataFlow = dataFlow;
    }

    // ── App Registration ──────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    public StreamAppInfo registerApp(String name, String type, String uri) {
        var appType = ApplicationType.valueOf(type);
        var registered = dataFlow.appRegistryOperations()
                .register(name, appType, uri, null, true);
        return toStreamAppInfo(registered);
    }

    public List<StreamAppInfo> listRegisteredApps(String type) {
        PagedModel<AppRegistrationResource> page = (type == null || type.isBlank())
                ? dataFlow.appRegistryOperations().list()
                : dataFlow.appRegistryOperations().list(ApplicationType.valueOf(type));
        return page.getContent().stream()
                .map(this::toStreamAppInfo)
                .toList();
    }

    // ── Stream Lifecycle ──────────────────────────────────────────────

    public StreamStatus createStream(String name, String definition, String description) {
        var resource = dataFlow.streamOperations()
                .createStream(name, definition, description, false);
        return toStreamStatus(resource);
    }

    public StreamStatus deployStream(String name, String propertiesJson) {
        Map<String, String> properties = parseProperties(propertiesJson);
        dataFlow.streamOperations().deploy(name, properties);
        return getStreamStatus(name);
    }

    public String undeployStream(String name) {
        dataFlow.streamOperations().undeploy(name);
        return "Stream '%s' undeployed successfully.".formatted(name);
    }

    public String destroyStream(String name) {
        dataFlow.streamOperations().destroy(name);
        return "Stream '%s' destroyed successfully.".formatted(name);
    }

    // ── Stream Status ─────────────────────────────────────────────────

    public StreamStatus getStreamStatus(String name) {
        var definition = dataFlow.streamOperations().getStreamDefinition(name);
        var runtimePage = dataFlow.runtimeOperations().streamStatus(name);
        Map<String, List<AppInstanceStatus>> appStatuses = new LinkedHashMap<>();

        for (StreamStatusResource statusResource : runtimePage) {
            statusResource.getApplications().getContent().forEach(appStatus -> {
                var instances = appStatus.getInstances().getContent().stream()
                        .map(inst -> new AppInstanceStatus(
                                inst.getInstanceId(),
                                inst.getState(),
                                inst.getAttributes()))
                        .toList();
                appStatuses.put(appStatus.getDeploymentId(), instances);
            });
        }

        return new StreamStatus(
                definition.getName(),
                definition.getStatus(),
                definition.getDescription(),
                appStatuses
        );
    }

    public List<StreamStatus> listStreams() {
        var page = dataFlow.streamOperations().list();
        return page.getContent().stream()
                .map(this::toStreamStatus)
                .toList();
    }

    // ── Mapping Helpers ───────────────────────────────────────────────

    private StreamAppInfo toStreamAppInfo(AppRegistrationResource resource) {
        return new StreamAppInfo(
                resource.getName(),
                resource.getType(),
                resource.getUri(),
                resource.getVersion()
        );
    }

    private StreamStatus toStreamStatus(StreamDefinitionResource resource) {
        return new StreamStatus(
                resource.getName(),
                resource.getStatus(),
                resource.getDescription(),
                Map.of()
        );
    }

    private Map<String, String> parseProperties(String propertiesJson) {
        if (propertiesJson == null || propertiesJson.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(propertiesJson, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to parse deployer properties JSON: " + e.getMessage(), e);
        }
    }
}
