# SCDF Deployment Reference

This reference covers the deployer properties used when deploying streams via the `deploy_stream` MCP tool, including Cloud Foundry-specific properties, app configuration, and resource allocation.

## deploy_stream Properties

The `deploy_stream` tool accepts a JSON object of key-value deployer properties. These properties control how SCDF deploys each app in the stream on Cloud Foundry.

## Property Namespaces

### deployer.{app}.* — CF Deployer Properties

Control how the app is deployed on Cloud Foundry.

| Property | Description | Example |
|----------|-------------|---------|
| `deployer.{app}.cloudfoundry.services` | Comma-separated CF service instances to bind | `my-pipeline-s3-creds` |
| `deployer.{app}.memory` | Memory in MB | `2048` |
| `deployer.{app}.disk` | Disk in MB | `2048` |
| `deployer.{app}.count` | Number of instances | `2` |
| `deployer.{app}.cloudfoundry.buildpack` | CF buildpack | `java_buildpack_offline` |
| `deployer.{app}.cloudfoundry.health-check` | Health check type | `http`, `port`, `process` |
| `deployer.{app}.cloudfoundry.health-check-http-endpoint` | HTTP health check path | `/actuator/health` |
| `deployer.{app}.cloudfoundry.domain` | CF domain | `apps.example.com` |
| `deployer.{app}.cloudfoundry.route-path` | Route path | `/my-app` |
| `deployer.{app}.cloudfoundry.no-route` | Disable route | `true` |

Use `deployer.*` as a wildcard to apply to all apps in the stream:

```json
{
  "deployer.*.memory": "1024",
  "deployer.*.cloudfoundry.no-route": "true"
}
```

### app.{app}.* — App Configuration Properties

Set Spring Boot configuration properties for a specific app. These become environment variables or application properties in the deployed app.

| Property Pattern | Description | Example |
|---|---|---|
| `app.{app}.{spring.property}` | Any Spring Boot property | `app.s3.s3.remote-dir=my-bucket` |
| `app.{app}.server.port` | Server port | `app.http.server.port=8080` |

### spring.cloud.dataflow.skipper.* — Skipper Properties

Control Skipper behavior (rarely needed):

| Property | Description |
|----------|-------------|
| `spring.cloud.dataflow.skipper.platformName` | Target platform | 

## CredHub Service Binding Pattern

The most important deployer property for RAG pipelines. Binds a CredHub service instance to a specific app:

```json
{
  "deployer.s3.cloudfoundry.services": "my-pipeline-s3-creds",
  "deployer.pgvector-sink.cloudfoundry.services": "my-pipeline-pgvector-sink-creds"
}
```

Multiple services can be bound to a single app (comma-separated):

```json
{
  "deployer.pgvector-sink.cloudfoundry.services": "my-pipeline-pgvector-sink-creds,another-service"
}
```

## Common Deployment Patterns

### S3 Source Configuration

```json
{
  "deployer.s3.cloudfoundry.services": "{pipeline}-s3-creds",
  "app.s3.file.consumer.mode": "contents",
  "app.s3.s3.remote-dir": "{bucket-name}",
  "app.s3.s3.region": "{region}"
}
```

- `file.consumer.mode=contents` — Emits file contents as the message payload (vs. `ref` which emits a file reference)
- `s3.remote-dir` — S3 bucket name or path to monitor
- `s3.region` — Can also be set via CredHub (`spring.cloud.aws.region.static`)

### HTTP Source Configuration

```json
{
  "app.http.server.port": "8080",
  "app.http.http.path-pattern": "/ingest"
}
```

The HTTP source needs a route on CF. Do NOT set `no-route=true` for HTTP sources.

### JDBC Source Configuration

```json
{
  "deployer.jdbc.cloudfoundry.services": "{pipeline}-jdbc-creds",
  "app.jdbc.jdbc.supplier.query": "SELECT * FROM {table} WHERE processed = false",
  "app.jdbc.trigger.fixed-delay": "10000"
}
```

- `trigger.fixed-delay` — Polling interval in milliseconds

### Text Extractor Configuration

```json
{
  "deployer.text-extractor.memory": "2048",
  "app.text-extractor.extractor.formats": "pdf,docx,txt"
}
```

Tika requires extra memory for parser libraries. 2048MB recommended.

### Text Chunker Configuration

```json
{
  "app.text-chunker.chunker.size": "1000",
  "app.text-chunker.chunker.overlap": "200"
}
```

### PgVector Sink Configuration

```json
{
  "deployer.pgvector-sink.cloudfoundry.services": "{pipeline}-pgvector-sink-creds",
  "deployer.pgvector-sink.memory": "1024",
  "app.pgvector-sink.pgvector.table": "vector_store",
  "app.pgvector-sink.pgvector.dimensions": "1536",
  "app.pgvector-sink.pgvector.index-type": "HNSW",
  "app.pgvector-sink.pgvector.distance-type": "COSINE_DISTANCE"
}
```

### Embedding Processor Configuration

```json
{
  "deployer.embedding.cloudfoundry.services": "{pipeline}-embedding-creds",
  "app.embedding.embedding.model": "text-embedding-3-small",
  "app.embedding.embedding.dimensions": "1536"
}
```

### Log Sink (Debugging)

```json
{
  "app.log.log.expression": "payload",
  "app.log.log.level": "INFO"
}
```

No credentials or service bindings needed. Useful for debugging pipelines.

## Full Deployment Example

S3-to-PgVector RAG pipeline:

```json
{
  "deployer.s3.cloudfoundry.services": "legal-docs-rag-s3-creds",
  "deployer.pgvector-sink.cloudfoundry.services": "legal-docs-rag-pgvector-sink-creds",

  "app.s3.file.consumer.mode": "contents",
  "app.s3.s3.remote-dir": "legal-docs",

  "app.text-chunker.chunker.size": "1000",
  "app.text-chunker.chunker.overlap": "200",

  "app.pgvector-sink.pgvector.dimensions": "1536",

  "deployer.text-extractor.memory": "2048",
  "deployer.*.memory": "1024"
}
```

**Note:** App-specific `deployer.{app}.memory` overrides the wildcard `deployer.*.memory`. In this example, `text-extractor` gets 2048MB while all other apps get 1024MB.

## Stream Definition DSL

The stream definition uses pipe-separated app names. App properties can be inlined with `--` but this is discouraged for readability — use `deploy_stream` properties instead.

**Inline syntax (avoid for complex pipelines):**
```
s3 --s3.remote-dir=my-bucket | text-extractor | text-chunker --chunker.size=500 | pgvector-sink
```

**Clean syntax (preferred):**
```
s3 | text-extractor | text-chunker | pgvector-sink
```

Then set all properties in the `deploy_stream` call.

## App Labels

When an app appears multiple times in a stream, use labels to distinguish them:

```
http > :input-queue
:input-queue > text-chunker | pgvector-sink
```

Or with named destinations:

```
:myQueue > log
```

## Resource Recommendations

| App | Memory | Notes |
|-----|--------|-------|
| S3 source | 1024MB | Standard |
| HTTP source | 1024MB | Standard |
| JDBC source | 1024MB | Standard |
| text-extractor | 2048MB | Tika parser libraries |
| text-chunker | 1024MB | Standard |
| embedding | 1024MB | REST calls to OpenAI |
| pgvector-sink | 1024MB | REST calls + JDBC |
| log sink | 512MB | Minimal |
| transform/filter | 512MB | Minimal |
