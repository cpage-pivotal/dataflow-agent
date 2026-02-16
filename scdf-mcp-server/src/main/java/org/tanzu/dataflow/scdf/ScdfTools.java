package org.tanzu.dataflow.scdf;

import java.util.List;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import org.tanzu.dataflow.model.StreamAppInfo;
import org.tanzu.dataflow.model.StreamStatus;

/**
 * MCP tools that expose SCDF operations to AI agents.
 * Each method is a discrete, composable tool invocable over Streamable HTTP.
 */
@Component
public class ScdfTools {

    private final ScdfService scdfService;

    ScdfTools(ScdfService scdfService) {
        this.scdfService = scdfService;
    }

    @McpTool(name = "register_app", description = """
            Register a stream app with SCDF. Use type 'source', 'processor', or 'sink'. \
            The uri can be an HTTP URL to a JAR (e.g. a GitHub Release asset) or Maven coordinates.""")
    public StreamAppInfo registerApp(
            @McpToolParam(description = "App name for SCDF registration") String name,
            @McpToolParam(description = "App type: source, processor, or sink") String type,
            @McpToolParam(description = "Artifact URI (HTTP URL or Maven coords)") String uri) {
        return scdfService.registerApp(name, type, uri);
    }

    @McpTool(name = "list_registered_apps", description = """
            List apps currently registered with SCDF, optionally filtered by type. \
            Returns name, type, uri, and version for each registered app.""")
    public List<StreamAppInfo> listRegisteredApps(
            @McpToolParam(description = "Optional filter: source, processor, or sink. Leave blank for all.", required = false) String type) {
        return scdfService.listRegisteredApps(type);
    }

    @McpTool(name = "create_stream", description = """
            Create a stream definition using SCDF DSL syntax. \
            Example definition: 's3-source | text-extractor | pgvector-sink'. \
            This creates the definition only; use deploy_stream to deploy it.""")
    public StreamStatus createStream(
            @McpToolParam(description = "Stream name (must be unique)") String name,
            @McpToolParam(description = "Stream definition in SCDF DSL syntax, e.g. 'app1 | app2 | app3'") String definition,
            @McpToolParam(description = "Optional human-readable description of the stream", required = false) String description) {
        return scdfService.createStream(name, definition, description);
    }

    @McpTool(name = "deploy_stream", description = """
            Deploy a stream with deployer properties. Properties should be a JSON object mapping \
            property keys to values. Include CF service bindings \
            (deployer.<app>.cloudfoundry.services), app properties (app.<app>.<key>), \
            and resource limits (deployer.*.memory).""")
    public StreamStatus deployStream(
            @McpToolParam(description = "Stream name to deploy") String name,
            @McpToolParam(description = "Deployer properties as a JSON object, e.g. {\"deployer.*.memory\":\"1024\"}") String properties) {
        return scdfService.deployStream(name, properties);
    }

    @McpTool(name = "undeploy_stream", description = """
            Undeploy a running stream. This stops all app instances but preserves the stream \
            definition so it can be redeployed later.""")
    public String undeployStream(
            @McpToolParam(description = "Name of the stream to undeploy") String name) {
        return scdfService.undeployStream(name);
    }

    @McpTool(name = "destroy_stream", description = """
            Destroy a stream definition. The stream must be undeployed first. \
            This permanently removes the stream definition from SCDF.""")
    public String destroyStream(
            @McpToolParam(description = "Name of the stream to destroy") String name) {
        return scdfService.destroyStream(name);
    }

    @McpTool(name = "get_stream_status", description = """
            Get the deployment status of a stream and its constituent apps. \
            Returns the overall stream status and per-app instance details \
            including state and runtime attributes.""")
    public StreamStatus getStreamStatus(
            @McpToolParam(description = "Name of the stream to check") String name) {
        return scdfService.getStreamStatus(name);
    }

    @McpTool(name = "list_streams", description = """
            List all stream definitions and their current statuses. \
            Returns name, status, and description for each stream.""")
    public List<StreamStatus> listStreams() {
        return scdfService.listStreams();
    }
}
