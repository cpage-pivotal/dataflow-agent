# Credential Patterns

This reference covers how to provision and bind credentials for stream apps on Cloud Foundry, including platform service instances (preferred) and CredHub service instances (fallback).

## Overview

Sensitive credentials (API keys, database passwords, AWS secrets) must never be passed as stream definition properties or environment variables. Instead, they are provided via **Cloud Foundry service bindings** that inject credentials into `VCAP_SERVICES` at runtime.

**Never pass secrets as:**
- Stream definition properties (`app.s3.aws.secret-key=...`)
- Environment variables in deployment properties
- Inline DSL parameters

**Always use CF service bindings.**

## Preferred: Platform Service Instances

When available, prefer using Cloud Foundry **platform service instances** rather than CredHub for credentials. Platform services are managed by the platform and provide credentials automatically via `VCAP_SERVICES`.

### Postgres Service Instance (for PgVector / JDBC)

The preferred way to provide PostgreSQL database credentials is through a Postgres service instance in the CF space. When bound to an app, the Postgres service instance provides JDBC URL, username, and password automatically via `VCAP_SERVICES`.

**Discover existing instances:**
```
cf services
```

**Bind to a stream app via SCDF deployer properties:**
```
deployer.pgvector-sink.cloudfoundry.services=my-postgres
```

No CredHub provisioning or credential bridging code is needed — Spring Boot reads `spring.datasource.*` properties from the Postgres service binding automatically.

### GenAI Service Instance (for Embedding Models)

The preferred way to get embedding model credentials is through a **GenAI on Tanzu Platform** service instance. GenAI provides an OpenAI-compatible API endpoint.

**Discover available plans:**
```
cf marketplace -e genai
```

Plans with embedding capability will mention models like `mxbai-embed-large` or list `EMBEDDING` in their capabilities. Use the `config_url` endpoint (provided in the service binding credentials) to discover model names and capabilities:

```
curl -H "Authorization: Bearer $API_KEY" "$CONFIG_URL"
```

**Create a GenAI service instance:**
```
cf create-service genai {plan} {pipeline}-genai
```

**Bind to a stream app via SCDF deployer properties:**
```
deployer.pgvector-sink.cloudfoundry.services=my-postgres,my-pipeline-genai
deployer.embedding.cloudfoundry.services=my-pipeline-genai
```

The GenAI service instance provides in `VCAP_SERVICES` under the `genai` label:
- `endpoint.api_base` — OpenAI-compatible API base URL (append `/openai` for the full URL)
- `endpoint.api_key` — Authentication token
- `endpoint.config_url` — Discovery endpoint for available models and capabilities

### Combining Platform Services

An app can bind to multiple platform service instances. Comma-separate the names in the deployer property:

```
deployer.pgvector-sink.cloudfoundry.services=my-postgres,my-pipeline-genai
```

This is the preferred pattern for the `pgvector-sink`, which needs both a Postgres database and an embedding model.

## Fallback: CredHub Service Instances

When platform service instances aren't available (e.g., no Postgres or GenAI in the marketplace), use CredHub to store credentials manually.

## Naming Convention

```
{pipeline-name}-{app-name}-creds
```

Examples:
- `legal-docs-rag-s3-creds`
- `legal-docs-rag-pgvector-sink-creds`
- `legal-docs-rag-embedding-creds`
- `events-monitor-jdbc-creds`

The `{app-name}` should match the SCDF app name used in the stream definition.

## Creating CredHub Service Instances

Use the **Cloud Foundry MCP server** to create service instances:

```
cf create-service credhub default {pipeline}-{app}-creds -c '{json_credentials}'
```

The `-c` flag accepts a JSON object where each key-value pair becomes a credential entry.

## Credential JSON Templates

### S3 Source/Sink (upstream)

```json
{
  "spring.cloud.aws.credentials.access-key": "AKIA...",
  "spring.cloud.aws.credentials.secret-key": "...",
  "spring.cloud.aws.region.static": "us-west-2"
}
```

These keys match Spring Cloud AWS auto-configuration properties. No bridging code needed.

### JDBC Source/Sink (upstream)

```json
{
  "spring.datasource.url": "jdbc:postgresql://host:5432/dbname",
  "spring.datasource.username": "myuser",
  "spring.datasource.password": "mypassword"
}
```

These keys match Spring Boot DataSource auto-configuration. No bridging code needed.

### MongoDB Source/Sink (upstream)

```json
{
  "spring.data.mongodb.uri": "mongodb://user:pass@host:27017/dbname"
}
```

### MQTT Source/Sink (upstream)

```json
{
  "mqtt.url": "tcp://broker:1883",
  "mqtt.username": "myuser",
  "mqtt.password": "mypassword"
}
```

### Mail Source (upstream)

```json
{
  "mail.imap.host": "imap.example.com",
  "mail.imap.username": "user@example.com",
  "mail.imap.password": "mypassword"
}
```

### FTP Source (upstream)

```json
{
  "ftp.host": "ftp.example.com",
  "ftp.username": "ftpuser",
  "ftp.password": "ftppassword"
}
```

### SFTP Source (upstream)

```json
{
  "sftp.host": "sftp.example.com",
  "sftp.username": "sftpuser",
  "sftp.password": "sftppassword"
}
```

### Elasticsearch Sink (upstream)

```json
{
  "spring.elasticsearch.uris": "https://es-host:9200",
  "spring.elasticsearch.username": "elastic",
  "spring.elasticsearch.password": "mypassword"
}
```

### Embedding Processor (custom RAG — fallback when GenAI is not available)

Only use CredHub for embedding credentials when a GenAI service instance is not available. Prefer binding a GenAI service instance instead.

```json
{
  "EMBEDDING_API_KEY": "sk-..."
}
```

The `CredHubEmbeddingConfig` class bridges `EMBEDDING_API_KEY` to `spring.ai.openai.api-key` via `@PostConstruct`.

### PgVector Sink (custom RAG — fallback when Postgres/GenAI are not available)

Only use CredHub for PgVector credentials when Postgres and/or GenAI service instances are not available. Prefer binding platform service instances instead (see "Preferred: Platform Service Instances" above).

```json
{
  "PGVECTOR_URL": "jdbc:postgresql://host:5432/vectordb",
  "PGVECTOR_USERNAME": "pguser",
  "PGVECTOR_PASSWORD": "pgpassword",
  "EMBEDDING_API_KEY": "sk-..."
}
```

The `CredHubPgVectorConfig` class bridges these keys:
- `PGVECTOR_URL` → `spring.datasource.url`
- `PGVECTOR_USERNAME` → `spring.datasource.username`
- `PGVECTOR_PASSWORD` → `spring.datasource.password`
- `EMBEDDING_API_KEY` → `spring.ai.openai.api-key`

### Agent-Generated Custom Apps

For custom apps generated by the `custom-app-builder` subagent, the credential JSON structure depends on what the app needs. The subagent should:

1. Define `UPPER_SNAKE_CASE` CredHub keys in the app's `CredHub*Config` class
2. Bridge them to Spring Boot properties via `@PostConstruct`
3. Document the required keys so the credential-provisioner subagent can create the service instance

## How Credentials Flow

### 1. CredHub Service Instance

Created via `cf create-service credhub default {name} -c '{json}'`. Cloud Foundry stores the JSON in CredHub's encrypted backend.

### 2. SCDF Deployer Property

At deployment time, the deployer property binds the service to the specific app:

```
deployer.{app}.cloudfoundry.services={pipeline}-{app}-creds
```

When SCDF deploys the stream app on CF, it includes this service binding in the app manifest.

### 3. VCAP_SERVICES at Runtime

Cloud Foundry injects the credentials into the app's environment as `VCAP_SERVICES`:

```json
{
  "credhub": [{
    "label": "credhub",
    "name": "legal-docs-rag-s3-creds",
    "credentials": {
      "spring.cloud.aws.credentials.access-key": "AKIA...",
      "spring.cloud.aws.credentials.secret-key": "...",
      "spring.cloud.aws.region.static": "us-west-2"
    }
  }]
}
```

### 4. Spring Boot Property Flattening

Spring Boot's Cloud Foundry connector flattens `VCAP_SERVICES` credentials into properties. Each key in the `credentials` object becomes a Spring Boot property.

**For upstream apps:** If the CredHub keys match the app's expected property names (e.g., `spring.datasource.url`), the app reads them automatically. No code changes needed.

**For custom apps with bridging:** The `CredHub*Config` classes use `@Value("${KEY:#{null}}")` to read the keys and `System.setProperty(...)` in `@PostConstruct` to bridge them to the properties the app consumes.

## Multiple Service Bindings

An app can be bound to multiple CredHub service instances. Separate names with commas:

```
deployer.pgvector-sink.cloudfoundry.services=my-pipeline-pgvector-creds,my-pipeline-extra-creds
```

Each service instance's credentials are merged into `VCAP_SERVICES`. If two service instances provide the same key, the behavior is undefined — avoid key collisions.

## Credential Lifecycle

### Update Credentials

```
cf update-service {pipeline}-{app}-creds -c '{"key":"new-value"}'
```

After updating, undeploy and redeploy the stream to pick up changes.

### Delete Credentials

```
cf delete-service {pipeline}-{app}-creds -f
```

The `-f` flag skips confirmation. Only delete after the stream is undeployed and destroyed.

### Verify Credentials Exist

```
cf services
```

Look for the service instance name in the output. Status should be `create succeeded`.
