---
name: rag-pipeline-builder
description: >-
  Build and deploy RAG ingestion pipelines on Spring Cloud Data Flow (SCDF) running on Cloud Foundry.
  Orchestrates the full lifecycle: component selection from upstream and custom app catalogs,
  credential provisioning via CredHub, app registration, stream creation, deployment with
  service bindings, and status monitoring. Activate when the user describes a data pipeline
  involving sources (S3, HTTP, JDBC, FTP), processors (text extraction, chunking, embedding),
  and sinks (PgVector, JDBC, MongoDB), or requests pipeline management operations.
metadata:
  author: tanzu-dataflow
  version: "1.0"
---

# RAG Pipeline Builder

Build and deploy RAG (Retrieval-Augmented Generation) ingestion pipelines on Spring Cloud Data Flow (SCDF) running on Cloud Foundry. You orchestrate the full lifecycle: component selection, credential provisioning, app registration, stream creation, deployment, and status monitoring.

## MCP Servers

You have access to three MCP servers:

| Server | Purpose | Transport |
|--------|---------|-----------|
| **scdf** | Register apps, create/deploy/manage streams | Streamable HTTP |
| **cloud-foundry** | CredHub service instances, CF operations | Streamable HTTP |
| **github** | Commit code, trigger builds, query releases | STDIO |

## Activation

Activate when the user describes a data pipeline that involves:
- Ingesting data from a source (S3, HTTP, JDBC, FTP, files, etc.)
- Processing or transforming data (text extraction, chunking, filtering, etc.)
- Storing results in a vector database or other sink
- Any combination of Spring Cloud Stream source → processor → sink

Also activate for pipeline management requests: status checks, undeployment, redeployment, teardown, or credential rotation.

## Workflow

Follow these steps in order. Communicate progress to the user at each stage.

### Step 1: Parse the Request

Extract from the user's description:
- **Source**: Where does data come from? (S3 bucket, HTTP endpoint, database table, FTP server, etc.)
- **Processors**: What transformations are needed? (text extraction, chunking, filtering, embedding, etc.)
- **Sink**: Where does data go? (PgVector, JDBC, MongoDB, Elasticsearch, S3, etc.)
- **Pipeline name**: Ask the user or suggest one based on the use case (e.g., `legal-docs-rag`)

### Step 2: Identify Components

Read `references/prebuilt-apps.md` to determine which apps are available.

For each pipeline component, classify it:

1. **Upstream app** — Available in the Spring Cloud Stream Applications 2025.0.1 catalog (S3, HTTP, JDBC, FTP, SFTP, MongoDB, MQTT, mail, log, filter, transform, splitter, etc.). These are pre-built and just need registration via `bulk_register_apps`.

2. **Custom RAG app** — One of the four pre-built custom apps in this project:
   - `text-extractor` (processor) — Extracts text from PDF/DOCX/TXT via Apache Tika
   - `text-chunker` (processor) — Splits text into overlapping chunks
   - `embedding` (processor) — Generates vector embeddings via OpenAI
   - `pgvector-sink` (sink) — Writes to PostgreSQL+pgvector via Spring AI VectorStore

3. **Agent-generated custom app** — A component that doesn't exist anywhere. You will generate the code, build it, and register it. This requires spawning the `custom-app-builder` subagent.

Present the component plan to the user and confirm before proceeding.

**Important design note**: The `pgvector-sink` handles embedding generation internally via Spring AI's VectorStore. A typical RAG pipeline is: `source | text-extractor | text-chunker | pgvector-sink`. You do NOT need a separate `embedding` processor in this path. The `embedding` processor is only needed when you want to decouple embedding generation from storage (e.g., branching pipelines, multi-sink, or embedding inspection).

### Step 3: Gather Credentials

Read `references/prebuilt-apps.md` to determine which credentials each component requires. Then ask the user for the needed credentials.

**Rules:**
- Only ask for credentials that are actually needed by the selected components
- Group credential requests by component so the user understands what each is for
- Never echo back sensitive values (passwords, API keys, secret keys) — confirm receipt without repeating them
- If the user has already provided credentials in their initial message, acknowledge them

**Example prompt:**

> To set up your pipeline, I'll need the following credentials:
>
> **For the S3 source:**
> - AWS Access Key ID
> - AWS Secret Access Key
> - AWS Region
>
> **For the PgVector sink:**
> - PostgreSQL JDBC URL (e.g., `jdbc:postgresql://host:5432/dbname`)
> - Database username
> - Database password
> - OpenAI API key (for embedding generation)

### Step 4: Provision Credentials

Spawn a subagent to create CredHub service instances. Use natural language like:

> Use a subagent with only the cloud-foundry extension to provision credentials. Give it a 10-minute timeout.
>
> You are a credential provisioner. Create CredHub service instances on Cloud Foundry for a RAG pipeline called "{pipeline_name}".
>
> Create the following CredHub service instances:
>
> {for each component that needs credentials}
> - `cf create-service credhub default {pipeline_name}-{app_name}-creds -c '{json_credentials}'`
> {end for}
>
> After creating all service instances, verify each one exists by running `cf services`.
> Return the list of created service instance names.

Read `references/credhub-patterns.md` for the exact credential key names and JSON structure for each component type.

**Subagent notes:**
- Restrict to only the `cloud-foundry` extension — it doesn't need SCDF or GitHub access
- The default 5-minute timeout is usually sufficient for credential provisioning
- If the subagent times out or fails, you receive no output — retry by spawning a new subagent

### Step 5: Build Custom Apps (if needed)

If the pipeline requires an agent-generated custom app (Step 2, category 3), spawn a subagent to generate, build, and publish it. This is a long-running task — request an extended timeout and higher turn limit:

> Use a subagent with the github and developer extensions to build a custom stream app. Give it a 20-minute timeout and 100 turns.
>
> You are a custom stream app builder. Generate, build, and publish a Spring Cloud Stream application.
>
> Read the file `references/custom-app-scaffold.md` in the skill directory for code patterns and conventions.
>
> App requirements:
> - Name: {app_name}
> - Type: {source|processor|sink}
> - Description: {what the app should do}
> - Dependencies: {any specific libraries needed}
>
> Steps:
> 1. Generate the complete Maven project (pom.xml, application class, configuration, application.properties, tests)
> 2. Commit the code to `stream-apps-custom/{app_name}/` in the tanzu-dataflow repo using the GitHub MCP server
> 3. Trigger the `build-custom-app.yml` workflow via workflow_dispatch with input app_name={app_name}
> 4. Poll the workflow run until it completes (this takes several minutes — poll every 30 seconds)
> 5. If the build fails, read the error logs, fix the code, recommit, and retrigger
> 6. Once the build succeeds, get the JAR artifact URL from the GitHub Release
> 7. Return the artifact URL

**Subagent notes:**
- Request **20-minute timeout** — GitHub Actions builds take 3-5 minutes, and the subagent may need to iterate on failures
- Request **100 turns** — the default 25 may not be enough for generate → commit → trigger → poll → fix → retry cycles
- Restrict to `github` and `developer` extensions — it doesn't need CF or SCDF access
- Subagents cannot spawn their own subagents, so all work happens within this single subagent
- If the subagent times out, you receive no output — check GitHub Actions manually and retry

### Step 6: Register Apps

Use the **SCDF MCP server** to register all required apps.

1. **Register upstream apps** (idempotent, safe to call multiple times):
   ```
   bulk_register_apps()
   ```

2. **Register custom RAG apps** — Use the latest GitHub Release URLs. Check `references/prebuilt-apps.md` for the artifact URL pattern:
   ```
   register_app(name="text-extractor", type="processor", uri="https://github.com/.../text-extractor-processor-1.0.0.jar")
   register_app(name="text-chunker", type="processor", uri="https://github.com/.../text-chunker-processor-1.0.0.jar")
   register_app(name="pgvector-sink", type="sink", uri="https://github.com/.../pgvector-sink-1.0.0.jar")
   ```

   To find the latest release URLs, use the GitHub MCP server: `list_releases(owner="...", repo="tanzu-dataflow")` and look for the most recent `stream-apps-v*` release.

3. **Register agent-generated custom apps** (if any):
   ```
   register_app(name="{app_name}", type="{type}", uri="{artifact_url_from_subagent}")
   ```

### Step 7: Create the Stream

Use the **SCDF MCP server** to create the stream definition.

The stream definition uses SCDF DSL syntax — a pipe-separated list of app names:

```
create_stream(
  name="{pipeline_name}",
  definition="{source} | {processor1} | {processor2} | ... | {sink}",
  description="{user-friendly description}"
)
```

**App names in the DSL** must match the registered app names exactly. For upstream apps, the name is the short name (e.g., `s3`, `http`, `jdbc`). For custom RAG apps, use: `text-extractor`, `text-chunker`, `embedding`, `pgvector-sink`.

**App properties in the DSL**: Non-sensitive configuration can be set inline using `--` syntax:

```
s3 --s3.remote-dir=my-bucket | text-extractor | text-chunker --chunker.size=500 | pgvector-sink
```

However, prefer setting app properties via `deploy_stream` properties instead (Step 8) for clarity and editability.

### Step 8: Deploy the Stream

Use the **SCDF MCP server** to deploy the stream with deployer properties.

Read `references/scdf-deployment.md` for the full deployer property reference.

Build the deployment properties JSON object:

```json
{
  "deployer.{app}.cloudfoundry.services": "{pipeline}-{app}-creds",
  "app.{app}.{property}": "{value}",
  "deployer.*.memory": "1024"
}
```

**Key property types:**

| Property Pattern | Purpose | Example |
|---|---|---|
| `deployer.{app}.cloudfoundry.services` | Bind CredHub service instances | `deployer.s3.cloudfoundry.services=my-pipeline-s3-creds` |
| `app.{app}.{property}` | App-specific configuration | `app.s3.s3.remote-dir=my-bucket` |
| `deployer.*.memory` | Memory for all apps | `deployer.*.memory=1024` |
| `deployer.{app}.memory` | Memory for a specific app | `deployer.pgvector-sink.memory=2048` |
| `deployer.{app}.count` | Instance count | `deployer.pgvector-sink.count=2` |

Then deploy:

```
deploy_stream(name="{pipeline_name}", properties="{json_properties}")
```

### Step 9: Verify Deployment

Use the **SCDF MCP server** to check the stream status:

```
get_stream_status(name="{pipeline_name}")
```

The response includes:
- Overall stream status: `deploying`, `deployed`, `failed`, `undeployed`
- Per-app instance status with state and runtime attributes

If the status is `deploying`, wait 30-60 seconds and check again. Stream apps on Cloud Foundry take time to stage and start.

If any app shows `failed`, check the runtime attributes for error details. Common issues:
- **Missing credentials**: CredHub service instance name typo or credentials not provisioned
- **Memory**: Increase `deployer.{app}.memory` (default 1024MB may be insufficient for Tika or Spring AI)
- **RabbitMQ**: Ensure the SCDF server's RabbitMQ service is accessible

Report the final status to the user with a summary of the deployed pipeline.

### Step 10: Report to User

Summarize the deployed pipeline:
- Pipeline name and SCDF dashboard URL
- Components and their roles
- CredHub service instances created
- Any configuration properties set
- How to check status, undeploy, or teardown

## Pipeline Management

### Check Status

```
get_stream_status(name="{pipeline_name}")
```

### Undeploy (Stop)

Stops all app instances but preserves the stream definition:

```
undeploy_stream(name="{pipeline_name}")
```

### Redeploy

After undeploying, redeploy with the same or updated properties:

```
deploy_stream(name="{pipeline_name}", properties="{json_properties}")
```

### Teardown (Full Cleanup)

1. Undeploy the stream:
   ```
   undeploy_stream(name="{pipeline_name}")
   ```

2. Destroy the stream definition:
   ```
   destroy_stream(name="{pipeline_name}")
   ```

3. Delete CredHub service instances via the **Cloud Foundry MCP server**:
   ```
   cf delete-service {pipeline_name}-{app}-creds -f
   ```

### Credential Rotation

1. Update the CredHub service instance via the **Cloud Foundry MCP server**:
   ```
   cf update-service {pipeline_name}-{app}-creds -c '{"key":"new-value"}'
   ```

2. Undeploy and redeploy the stream to pick up the new credentials:
   ```
   undeploy_stream(name="{pipeline_name}")
   deploy_stream(name="{pipeline_name}", properties="{original_properties}")
   ```

## Error Handling

### Build Failures (Custom Apps)

Build error recovery happens *inside* the subagent (it reads logs, fixes code, and retries). If the subagent itself fails:

1. **Subagent returns a build failure**: The subagent exhausted its retries. Check the GitHub Actions logs manually using the GitHub MCP server (`list_workflow_runs`), report the error to the user, and ask for guidance.
2. **Subagent times out (no output returned)**: The build took longer than the timeout. Use the GitHub MCP server to check the workflow run status. If the build succeeded, get the artifact URL from the release. If it failed or is still running, spawn a new subagent to continue.
3. **Subagent exhausts turn limit**: Same as timeout — check GitHub Actions status and spawn a new subagent if needed.

### Deployment Failures

If `get_stream_status` shows a failed app:
1. Check the app instance attributes for error messages
2. Common fixes:
   - Increase memory: undeploy, redeploy with `deployer.{app}.memory=2048`
   - Fix credentials: update the CredHub service instance, redeploy
   - Missing app registration: register the app, recreate and deploy the stream
3. After fixing, undeploy and redeploy the stream

### Credential Errors

If an app fails with credential-related errors (connection refused, authentication failed):
1. Verify the CredHub service instance exists: `cf services`
2. Verify the credential key names match what the app expects (see `references/prebuilt-apps.md`)
3. Update the service instance with corrected values
4. Redeploy the stream

## Worked Examples

### Example 1: S3 to PgVector RAG Pipeline

**User**: "Monitor my S3 bucket 'legal-docs' in us-west-2 for new PDF files, extract text, chunk it, and store in PgVector."

**Component plan**:
- Source: `s3` (upstream)
- Processor: `text-extractor` (custom RAG)
- Processor: `text-chunker` (custom RAG)
- Sink: `pgvector-sink` (custom RAG)

**Credentials needed**:
- S3: AWS access key, secret key, region
- PgVector sink: PostgreSQL URL, username, password, OpenAI API key

**Stream definition**:
```
s3 | text-extractor | text-chunker | pgvector-sink
```

**Deployment properties**:
```json
{
  "deployer.s3.cloudfoundry.services": "legal-docs-rag-s3-creds",
  "deployer.pgvector-sink.cloudfoundry.services": "legal-docs-rag-pgvector-sink-creds",
  "app.s3.file.consumer.mode": "contents",
  "app.s3.s3.remote-dir": "legal-docs",
  "app.s3.s3.region": "us-west-2",
  "app.text-chunker.chunker.size": "1000",
  "app.text-chunker.chunker.overlap": "200",
  "deployer.text-extractor.memory": "2048",
  "deployer.*.memory": "1024"
}
```

### Example 2: HTTP to PgVector RAG Pipeline

**User**: "I want to POST documents to an HTTP endpoint and store them as embeddings in PgVector."

**Component plan**:
- Source: `http` (upstream)
- Processor: `text-chunker` (custom RAG) — optional, depends on document size
- Sink: `pgvector-sink` (custom RAG)

**Credentials needed**:
- PgVector sink: PostgreSQL URL, username, password, OpenAI API key

**Stream definition**:
```
http | text-chunker | pgvector-sink
```

**Deployment properties**:
```json
{
  "deployer.pgvector-sink.cloudfoundry.services": "http-rag-pgvector-sink-creds",
  "app.http.server.port": "8080",
  "deployer.*.memory": "1024"
}
```

### Example 3: JDBC Source to Log Sink (Debugging)

**User**: "Poll my PostgreSQL table 'events' every 10 seconds and log the results."

**Component plan**:
- Source: `jdbc` (upstream)
- Sink: `log` (upstream)

**Credentials needed**:
- JDBC: PostgreSQL URL, username, password

**Stream definition**:
```
jdbc | log
```

**Deployment properties**:
```json
{
  "deployer.jdbc.cloudfoundry.services": "events-monitor-jdbc-creds",
  "app.jdbc.jdbc.supplier.query": "SELECT * FROM events WHERE processed = false",
  "app.jdbc.trigger.fixed-delay": "10000",
  "deployer.*.memory": "1024"
}
```
