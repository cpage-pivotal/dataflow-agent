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
  author: cpage-pivotal
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

### Step 0: Identify the Cloud Foundry Environment

The target platform is **Cloud Foundry**. Before doing anything else, ask the user to identify:
- **CF org**: The Cloud Foundry organization they are working in
- **CF space**: The Cloud Foundry space where the pipeline will be deployed

Once identified, use the **Cloud Foundry MCP server** to target the org and space:

```
cf target -o {org} -s {space}
```

This is critical because:
- Service instances (Postgres, GenAI, CredHub) are scoped to a space
- SCDF deploys stream apps into the targeted space
- The agent needs to discover existing service instances in the space (Step 3)

If the user has already specified an org/space, acknowledge it and target it. If not, ask before proceeding.

### Step 1: Parse the Request

Extract from the user's description:
- **Source**: Where does data come from? (S3 bucket, HTTP endpoint, database table, FTP server, etc.)
- **Processors**: What transformations are needed? (text extraction, chunking, filtering, embedding, etc.)
- **Sink**: Where does data go? (PgVector, JDBC, MongoDB, Elasticsearch, S3, etc.)
- **Pipeline name**: Ask the user or suggest one based on the use case (e.g., `legal-docs-rag`)

### Step 2: Identify Components

Read `references/prebuilt-apps.md` to determine which apps are available.

For each pipeline component, classify it:

1. **Upstream app** — Available in the Spring Cloud Stream Applications 2025.0.1 catalog. These are pre-built and just need registration via `bulk_register_apps`.

2. **Custom RAG app** — One of the four pre-built custom apps in this project:
   - `text-extractor` (processor) — Extracts text from PDF/DOCX/TXT via Apache Tika
   - `text-chunker` (processor) — Splits text into overlapping chunks
   - `embedding` (processor) — Generates vector embeddings via OpenAI
   - `pgvector-sink` (sink) — Writes to PostgreSQL+pgvector via Spring AI VectorStore

3. **Agent-generated custom app** — A component that doesn't exist anywhere. You will generate the code, build it, and register it. This requires spawning the `custom-app-builder` subagent.

Present the component plan to the user and confirm before proceeding.

**CRITICAL — Use exact SCDF registration names.** Upstream apps are registered with **short names only** — no type suffix. The HTTP source is `http`, NOT `http-source`. The S3 source is `s3`, NOT `s3-source`. The JDBC sink is `jdbc`, NOT `jdbc-sink`. The log sink is `log`, NOT `log-sink`. SCDF resolves the type from registration metadata. You MUST use the exact names listed in `references/prebuilt-apps.md` in stream definitions. Getting a name wrong causes `create_stream` to fail.

**Important design note**: The `pgvector-sink` handles embedding generation internally via Spring AI's VectorStore. A typical RAG pipeline is: `source | text-extractor | text-chunker | pgvector-sink`. You do NOT need a separate `embedding` processor in this path. The `embedding` processor is only needed when you want to decouple embedding generation from storage (e.g., branching pipelines, multi-sink, or embedding inspection).

### Step 3: Gather Credentials and Identify Service Instances

Read `references/prebuilt-apps.md` to determine which credentials each component requires.

**Preferred approach — use Cloud Foundry service instances whenever possible.** Before asking the user for raw credentials, check the CF space for existing service instances that can provide them:

#### PgVector Database — Prefer a Postgres Service Instance

The preferred way to connect to a PgVector database is through a **Postgres service instance** already provisioned in the CF space. Use the **Cloud Foundry MCP server** to list available services:

```
cf services
```

Look for a Postgres service instance (e.g., from the `postgres` or `postgresql` service offering). If one exists, ask the user if they want to use it. The Postgres service instance provides JDBC URL, username, and password automatically via `VCAP_SERVICES` when bound to the app — no manual credential entry needed.

If no Postgres service instance exists, ask the user to create one or provide credentials manually via CredHub (see `references/credhub-patterns.md`).

#### Embedding Model — Prefer a GenAI Service Instance

The preferred way to get embedding model credentials is through a **GenAI on Tanzu Platform** service instance. Check the CF marketplace and space:

```
cf marketplace -e genai
cf services
```

If GenAI plans are available, help the user select a plan that includes an embedding-capable model (look for plans with `EMBEDDING` capability or `mxbai-embed-large` in the description). Then create a service instance:

```
cf create-service genai {plan} {pipeline}-genai
```

The GenAI service instance provides:
- `api_base` — The OpenAI-compatible API base URL
- `api_key` — The authentication token
- `config_url` — A discovery endpoint listing available models and their capabilities

When bound to an app, these credentials are available in `VCAP_SERVICES` under the `genai` label. The app can use the `api_base + /openai` as the base URL and `api_key` as the API key with Spring AI's OpenAI-compatible client.

If GenAI is not available in the marketplace, fall back to asking the user for an OpenAI API key (provisioned via CredHub).

#### Other Credentials

For all other components (S3, JDBC, FTP, etc.), ask the user for credentials as before — these are provisioned via CredHub service instances.

**Rules:**
- Only ask for credentials that are actually needed by the selected components
- Group credential requests by component so the user understands what each is for
- Never echo back sensitive values (passwords, API keys, secret keys) — confirm receipt without repeating them
- If the user has already provided credentials in their initial message, acknowledge them
- Always check for existing CF service instances before asking for raw credentials

**Example prompt (when Postgres and GenAI service instances exist):**

> I found the following service instances in your space:
>
> - `my-postgres` (Postgres) — I'll bind this to the PgVector sink for database access
> - `my-genai` (GenAI) — I'll bind this to the PgVector sink for embedding generation
>
> **For the S3 source, I'll need:**
> - AWS Access Key ID
> - AWS Secret Access Key
> - AWS Region

**Example prompt (when no platform services exist):**

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

This step provisions the credentials identified in Step 3. There are two paths depending on the credential source:

#### Path A: Platform Service Instances (Postgres, GenAI)

If using a **Postgres service instance** for the PgVector database, no CredHub provisioning is needed for database credentials — the Postgres service instance is bound directly to the app via SCDF deployer properties.

If using a **GenAI service instance** for embeddings, no CredHub provisioning is needed for the embedding API key — the GenAI service instance is bound directly to the app via SCDF deployer properties.

If the GenAI service instance doesn't exist yet, create it now:

```
cf create-service genai {plan} {pipeline}-genai
```

Wait for the service instance to be ready:

```
cf service {pipeline}-genai
```

#### Path B: CredHub Service Instances (everything else)

For credentials that aren't provided by platform service instances (e.g., AWS keys, external database passwords, API keys for providers not on GenAI), spawn a subagent to create CredHub service instances:

> Use a subagent with only the cloud-foundry extension to provision credentials. Give it a 10-minute timeout.
>
> You are a credential provisioner. Create CredHub service instances on Cloud Foundry for a RAG pipeline called "{pipeline_name}".
>
> Create the following CredHub service instances:
>
> {for each component that needs CredHub credentials}
> - `cf create-service credhub default {pipeline_name}-{app_name}-creds -c '{json_credentials}'`
> {end for}
>
> After creating all service instances, verify each one exists by running `cf services`.
> Return the list of created service instance names.

Read `references/credhub-patterns.md` for the exact credential key names and JSON structure for each component type.

#### Summary

After this step, you should have a list of all service instances to bind at deployment time. This may include a mix of:
- Postgres service instance (for PgVector database)
- GenAI service instance (for embedding model)
- CredHub service instances (for AWS keys, other external credentials)

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
> 2. Commit the code to `stream-apps-custom/{app_name}/` in the `cpage-pivotal/dataflow-agent` repo using the GitHub MCP server
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

2. **Register custom RAG apps** — These are pre-built JARs on GitHub Releases. Register whichever ones the pipeline needs:
   ```
   register_app(name="text-extractor", type="processor", uri="https://github.com/cpage-pivotal/dataflow-agent/releases/download/stream-apps-v2/text-extractor-processor-1.0.0.jar")
   register_app(name="text-chunker", type="processor", uri="https://github.com/cpage-pivotal/dataflow-agent/releases/download/stream-apps-v2/text-chunker-processor-1.0.0.jar")
   register_app(name="embedding", type="processor", uri="https://github.com/cpage-pivotal/dataflow-agent/releases/download/stream-apps-v2/embedding-processor-1.0.0.jar")
   register_app(name="pgvector-sink", type="sink", uri="https://github.com/cpage-pivotal/dataflow-agent/releases/download/stream-apps-v2/pgvector-sink-1.0.0.jar")
   ```

   To check for newer releases, use the GitHub MCP server: `list_releases(owner="cpage-pivotal", repo="dataflow-agent")` and look for the most recent `stream-apps-v*` tag.

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

**App names in the DSL** must match the exact registered names. Do NOT add type suffixes like `-source`, `-sink`, or `-processor` to upstream app names. Examples of correct vs. incorrect usage:

| Correct | WRONG | Why |
|---------|-------|-----|
| `http` | `http-source` | Upstream sources have no `-source` suffix |
| `s3` | `s3-source` | Same — short name only |
| `jdbc` | `jdbc-sink` | Same — SCDF infers type from position in the pipeline |
| `log` | `log-sink` | Same |
| `text-extractor` | `text-extractor-processor` | Custom RAG apps use hyphenated names, not the Maven artifact name |
| `pgvector-sink` | `pgvector` | Custom RAG apps include `-sink` in their registered name (this is the registered name, not a type suffix) |

For custom RAG apps, use: `text-extractor`, `text-chunker`, `embedding`, `pgvector-sink`.

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
  "deployer.{app}.cloudfoundry.services": "{service-instance-name}",
  "app.{app}.{property}": "{value}",
  "deployer.*.memory": "1024"
}
```

**Service bindings can include any CF service instance** — not just CredHub. For apps that need multiple services (e.g., pgvector-sink needs both a Postgres instance and a GenAI instance), comma-separate the service names:

```json
{
  "deployer.pgvector-sink.cloudfoundry.services": "my-postgres,my-pipeline-genai"
}
```

**Key property types:**

| Property Pattern | Purpose | Example |
|---|---|---|
| `deployer.{app}.cloudfoundry.services` | Bind CF service instances (Postgres, GenAI, CredHub, etc.) | `deployer.pgvector-sink.cloudfoundry.services=my-postgres,my-genai` |
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

### Example 1: S3 to PgVector RAG Pipeline (with Platform Services)

**User**: "I'm in the `data-team` org, `dev` space. Monitor my S3 bucket 'legal-docs' in us-west-2 for new PDF files, extract text, chunk it, and store in PgVector."

**Step 0 — Target CF environment**:
```
cf target -o data-team -s dev
```

**Component plan**:
- Source: `s3` (upstream)
- Processor: `text-extractor` (custom RAG)
- Processor: `text-chunker` (custom RAG)
- Sink: `pgvector-sink` (custom RAG)

**Step 3 — Discover service instances**:
Agent runs `cf services` and finds:
- `legal-docs-postgres` (Postgres service instance) — use for PgVector database

Agent runs `cf marketplace -e genai` and finds plans with embedding capability. Creates:
```
cf create-service genai mxbai-embed-large legal-docs-rag-genai
```

**Credentials needed via CredHub** (only what platform services don't cover):
- S3: AWS access key, secret key, region

**Service instances to bind**:
- `legal-docs-rag-s3-creds` (CredHub — AWS credentials)
- `legal-docs-postgres` (Postgres — database access)
- `legal-docs-rag-genai` (GenAI — embedding model)

**Stream definition**:
```
s3 | text-extractor | text-chunker | pgvector-sink
```

**Deployment properties**:
```json
{
  "deployer.s3.cloudfoundry.services": "legal-docs-rag-s3-creds",
  "deployer.pgvector-sink.cloudfoundry.services": "legal-docs-postgres,legal-docs-rag-genai",
  "app.s3.file.consumer.mode": "contents",
  "app.s3.s3.remote-dir": "legal-docs",
  "app.s3.s3.region": "us-west-2",
  "app.text-chunker.chunker.size": "1000",
  "app.text-chunker.chunker.overlap": "200",
  "deployer.text-extractor.memory": "2048",
  "deployer.*.memory": "1024"
}
```

### Example 2: HTTP to PgVector RAG Pipeline (with Platform Services)

**User**: "I want to POST documents to an HTTP endpoint and store them as embeddings in PgVector. I'm in org `acme` space `staging`."

**Step 0 — Target CF environment**:
```
cf target -o acme -s staging
```

**Component plan**:
- Source: `http` (upstream)
- Processor: `text-chunker` (custom RAG) — optional, depends on document size
- Sink: `pgvector-sink` (custom RAG)

**Step 3 — Discover service instances**:
Agent finds `staging-postgres` (Postgres) and GenAI in the marketplace. Creates:
```
cf create-service genai mxbai-embed-large http-rag-genai
```

**Credentials needed via CredHub**: None — all credentials come from platform services.

**Stream definition**:
```
http | text-chunker | pgvector-sink
```

**Deployment properties**:
```json
{
  "deployer.pgvector-sink.cloudfoundry.services": "staging-postgres,http-rag-genai",
  "app.http.server.port": "8080",
  "deployer.*.memory": "1024"
}
```

### Example 3: S3 to PgVector (CredHub Fallback — No Platform Services)

**User**: "Monitor S3 for PDFs and store in PgVector. I don't have Postgres or GenAI service instances."

This example shows the fallback when platform service instances aren't available — all credentials go through CredHub.

**Credentials needed via CredHub**:
- S3: AWS access key, secret key, region
- PgVector sink: PostgreSQL URL, username, password, OpenAI API key

**Deployment properties**:
```json
{
  "deployer.s3.cloudfoundry.services": "my-pipeline-s3-creds",
  "deployer.pgvector-sink.cloudfoundry.services": "my-pipeline-pgvector-sink-creds",
  "app.s3.file.consumer.mode": "contents",
  "app.s3.s3.remote-dir": "my-bucket",
  "deployer.text-extractor.memory": "2048",
  "deployer.*.memory": "1024"
}
```

### Example 4: JDBC Source to Log Sink (Debugging)

**User**: "Poll my PostgreSQL table 'events' every 10 seconds and log the results."

**Component plan**:
- Source: `jdbc` (upstream)
- Sink: `log` (upstream)

**Credentials needed**:
- JDBC: PostgreSQL URL, username, password (via CredHub or a Postgres service instance)

**Stream definition**:
```
jdbc | log
```

**Deployment properties** (using a Postgres service instance):
```json
{
  "deployer.jdbc.cloudfoundry.services": "my-postgres",
  "app.jdbc.jdbc.supplier.query": "SELECT * FROM events WHERE processed = false",
  "app.jdbc.trigger.fixed-delay": "10000",
  "deployer.*.memory": "1024"
}
```
