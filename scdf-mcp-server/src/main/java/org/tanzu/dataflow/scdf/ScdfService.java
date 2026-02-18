package org.tanzu.dataflow.scdf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import org.tanzu.dataflow.model.AppInstanceStatus;
import org.tanzu.dataflow.model.StreamAppInfo;
import org.tanzu.dataflow.model.StreamStatus;

/**
 * Calls the SCDF REST API directly using an OAuth2-authenticated {@link RestClient}.
 * This avoids the binary-incompatible {@code spring-cloud-dataflow-rest-client}
 * (compiled against Spring 5.x) and works cleanly with Spring Boot 3.5.x / Spring 6.x.
 */
@Service
public class ScdfService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient restClient;

    ScdfService(RestClient scdfRestClient) {
        this.restClient = scdfRestClient;
    }

    /**
     * Upstream Spring Cloud Stream Applications 2025.0.1 RabbitMQ descriptor URL.
     * Each line is "{type}.{name}=https://repo.maven.apache.org/.../{name}-rabbit-5.1.1.jar".
     * Metadata and bootVersion lines (e.g. source.s3.metadata=...) are skipped.
     */
    private static final String UPSTREAM_DESCRIPTOR_URL =
            "https://repo.maven.apache.org/maven2/org/springframework/cloud/stream/app/" +
            "stream-applications-descriptor/2025.0.1/" +
            "stream-applications-descriptor-2025.0.1.rabbit-apps-maven-repo-url.properties";

    // ── App Registration ──────────────────────────────────────────────

    public String bulkRegisterApps() {
        String descriptor = RestClient.create().get()
                .uri(UPSTREAM_DESCRIPTOR_URL)
                .retrieve()
                .body(String.class);

        if (descriptor == null || descriptor.isBlank()) {
            return "No apps found in upstream descriptor.";
        }

        int count = 0;
        for (String line : descriptor.lines().toList()) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] parts = line.split("=", 2);
            if (parts.length != 2) continue;
            String[] keyParts = parts[0].split("\\.", 2);
            if (keyParts.length != 2 || keyParts[1].contains(".")) continue;

            String type = keyParts[0];
            String name = keyParts[1];
            String uri = parts[1].trim();
            registerApp(name, type, uri);
            count++;
        }
        return "Registered %d upstream Spring Cloud Stream Applications (2025.0.1) with SCDF.".formatted(count);
    }

    public StreamAppInfo registerApp(String name, String type, String uri) {
        restClient.post()
                .uri("/apps/{type}/{name}", type, name)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("uri=" + encodeValue(uri) + "&force=true")
                .retrieve()
                .toBodilessEntity();

        return new StreamAppInfo(name, type, uri, null);
    }

    public List<StreamAppInfo> listRegisteredApps(String type) {
        String uri = (type == null || type.isBlank()) ? "/apps" : "/apps?type=" + type;
        var json = restClient.get()
                .uri(uri)
                .retrieve()
                .body(JsonNode.class);

        var apps = new ArrayList<StreamAppInfo>();
        if (json != null && json.has("_embedded") && json.get("_embedded").has("appRegistrationResourceList")) {
            for (JsonNode app : json.get("_embedded").get("appRegistrationResourceList")) {
                apps.add(new StreamAppInfo(
                        app.path("name").asText(),
                        app.path("type").asText(),
                        app.path("uri").asText(null),
                        app.path("version").asText(null)));
            }
        }
        return apps;
    }

    // ── Stream Lifecycle ──────────────────────────────────────────────

    public StreamStatus createStream(String name, String definition, String description) {
        var body = "name=" + encodeValue(name) +
                "&definition=" + encodeValue(definition) +
                "&deploy=false";
        if (description != null && !description.isBlank()) {
            body += "&description=" + encodeValue(description);
        }

        restClient.post()
                .uri("/streams/definitions")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(body)
                .retrieve()
                .toBodilessEntity();

        return new StreamStatus(name, "created", description, Map.of());
    }

    public StreamStatus deployStream(String name, String propertiesJson) {
        Map<String, String> properties = parseProperties(propertiesJson);

        restClient.post()
                .uri("/streams/deployments/{name}", name)
                .header("Content-Type", "application/json")
                .body(properties)
                .retrieve()
                .toBodilessEntity();

        return getStreamStatus(name);
    }

    public String undeployStream(String name) {
        restClient.delete()
                .uri("/streams/deployments/{name}", name)
                .retrieve()
                .toBodilessEntity();

        return "Stream '%s' undeployed successfully.".formatted(name);
    }

    public String destroyStream(String name) {
        restClient.delete()
                .uri("/streams/definitions/{name}", name)
                .retrieve()
                .toBodilessEntity();

        return "Stream '%s' destroyed successfully.".formatted(name);
    }

    // ── Stream Status ─────────────────────────────────────────────────

    public StreamStatus getStreamStatus(String name) {
        var json = restClient.get()
                .uri("/streams/definitions/{name}", name)
                .retrieve()
                .body(JsonNode.class);

        String status = json != null ? json.path("status").asText("unknown") : "unknown";
        String desc = json != null ? json.path("description").asText(null) : null;

        Map<String, List<AppInstanceStatus>> appStatuses = new LinkedHashMap<>();
        var runtimeJson = restClient.get()
                .uri("/runtime/streams/{name}", name)
                .retrieve()
                .body(JsonNode.class);

        if (runtimeJson != null && runtimeJson.has("_embedded")
                && runtimeJson.get("_embedded").has("streamStatusResourceList")) {
            for (JsonNode streamStatus : runtimeJson.get("_embedded").get("streamStatusResourceList")) {
                if (streamStatus.has("applications") && streamStatus.get("applications").has("_embedded")
                        && streamStatus.get("applications").get("_embedded").has("appStatusResourceList")) {
                    for (JsonNode appStatus : streamStatus.get("applications").get("_embedded").get("appStatusResourceList")) {
                        String deploymentId = appStatus.path("deploymentId").asText();
                        var instances = new ArrayList<AppInstanceStatus>();
                        if (appStatus.has("instances") && appStatus.get("instances").has("_embedded")
                                && appStatus.get("instances").get("_embedded").has("appInstanceStatusResourceList")) {
                            for (JsonNode inst : appStatus.get("instances").get("_embedded").get("appInstanceStatusResourceList")) {
                                var attrs = new LinkedHashMap<String, String>();
                                inst.path("attributes").fields().forEachRemaining(
                                        e -> attrs.put(e.getKey(), e.getValue().asText()));
                                instances.add(new AppInstanceStatus(
                                        inst.path("instanceId").asText(),
                                        inst.path("state").asText(),
                                        attrs));
                            }
                        }
                        appStatuses.put(deploymentId, instances);
                    }
                }
            }
        }

        return new StreamStatus(name, status, desc, appStatuses);
    }

    public List<StreamStatus> listStreams() {
        var json = restClient.get()
                .uri("/streams/definitions")
                .retrieve()
                .body(JsonNode.class);

        var streams = new ArrayList<StreamStatus>();
        if (json != null && json.has("_embedded")
                && json.get("_embedded").has("streamDefinitionResourceList")) {
            for (JsonNode def : json.get("_embedded").get("streamDefinitionResourceList")) {
                streams.add(new StreamStatus(
                        def.path("name").asText(),
                        def.path("status").asText("unknown"),
                        def.path("description").asText(null),
                        Map.of()));
            }
        }
        return streams;
    }

    // ── Helpers ───────────────────────────────────────────────────────

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

    private static String encodeValue(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
