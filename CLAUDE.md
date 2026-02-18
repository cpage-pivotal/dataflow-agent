# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an **AI-powered pipeline authoring system** for Spring Cloud Data Flow (SCDF). It enables an AI agent (Goose skill) to design, build, register, and deploy Spring Cloud Stream applications as RAG ingestion pipelines on Cloud Foundry. See `PLAN.md` for the full architectural specification.

**Multi-module Maven project:**
- `scdf-mcp-server/` — Custom MCP server exposing SCDF management tools via Streamable HTTP (Phase 1: complete)
- `stream-apps/` — Custom RAG-specific Spring Cloud Stream apps not in the upstream catalog (Phase 2: in progress)
  - `text-extractor-processor/` — Extracts text from PDF/DOCX/TXT via Apache Tika
  - `text-chunker-processor/` — Splits text into overlapping chunks
  - `embedding-processor/` — Calls OpenAI embeddings API via Spring AI
  - `pgvector-sink/` — Writes embeddings to PostgreSQL+pgvector via Spring AI VectorStore

## Build Commands

```bash
# Full build
./mvnw clean package

# SCDF MCP server only
./mvnw -pl scdf-mcp-server clean package

# Stream apps (requires submodule activation)
./mvnw -pl stream-apps -am clean package

# Single stream app
./mvnw -pl stream-apps/embedding-processor -am clean package

# Run all tests
./mvnw test

# Run tests for a specific module
./mvnw -pl scdf-mcp-server test

# Skip tests
./mvnw clean package -DskipTests=true
```

## Deploying

**SCDF MCP server** deploys to Cloud Foundry. It binds to the `p-dataflow` service instance (named `dataflow`) to receive SCDF OAuth2 credentials automatically via `VCAP_SERVICES`.

```bash
./mvnw -pl scdf-mcp-server clean package -DskipTests=true
cf push -f scdf-mcp-server/manifest.yml
```

**Stream apps** are not `cf push`-ed manually. The GitHub Actions workflows (`.github/workflows/`) build them and publish JARs to GitHub Releases. The agent then registers those JARs with SCDF via the `register_app` or `bulk_register_apps` MCP tools.

## Architecture

### Layer Architecture

```
Goose Skill (rag-pipeline-builder/SKILL.md)
    ↓ MCP
scdf-mcp-server  ←→  SCDF REST API (OAuth2 client credentials)
    ↓ registers
stream-apps JARs  ←→  RabbitMQ  ←→  deployed on CF via SCDF
```

### SCDF MCP Server

Uses direct `RestClient` calls to the SCDF REST API — **not** `spring-cloud-dataflow-rest-client` (binary incompatible with Spring 6). OAuth2 credentials come from the `p-dataflow` CF service binding; the `ScdfConfig` class reads them from `vcap.services.dataflow.credentials.*` and configures a `client_credentials` `OAuth2AuthorizedClientManager`.

The 9 MCP tools are defined in `ScdfTools.java` and delegate to `ScdfService.java`. The service uses HAL+JSON parsing for SCDF responses.

Package structure follows Package-by-Feature:
- `org.tanzu.dataflow.scdf` — tools, service, config, security
- `org.tanzu.dataflow.model` — domain records (`StreamAppInfo`, `StreamStatus`, `AppInstanceStatus`)

### Stream Apps

Each app is a Spring Cloud Stream function app using RabbitMQ binder. Function beans (`Function<In,Out>`, `Consumer<In>`) are registered via `spring.cloud.function.definition` in `application.properties`.

Sensitive credentials (OpenAI API keys, DB passwords) are **never** in environment variables or stream definitions. They are stored in CredHub as CF service instances and bound to apps via SCDF deployer properties: `deployer.{app}.cloudfoundry.services={credhub-instance-name}`.

### Upstream vs. Custom Apps

The system uses ~50 upstream apps from the Spring Cloud Stream 2025.0.1 catalog (registered via `bulk_register_apps`) plus the 4 custom RAG apps in `stream-apps/`. Upstream apps are registered from their Maven Central URIs; custom apps are registered from GitHub Releases JAR URLs.

## Key Versions

| Component | Version |
|-----------|---------|
| Java | 21 |
| Spring Boot | 3.5.10 |
| Spring Cloud | 2025.0.1 |
| Spring AI | 1.1.2 |
| Apache Tika | 3.2.3 |

## Implementation Status

- **Phase 1** (SCDF MCP Server): Complete
- **Phase 2** (Stream Apps + GitHub Actions CI): In progress
- **Phase 3** (Goose Skill `skill/rag-pipeline-builder/SKILL.md`): Planned
- **Phase 4** (Integration testing): Planned
