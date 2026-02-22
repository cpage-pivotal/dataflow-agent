# Pre-built App Catalog

This reference lists all apps available for pipeline construction: upstream Spring Cloud Stream Applications (registered via `bulk_register_apps`) and custom RAG-specific apps (registered individually from GitHub Releases).

## Upstream Apps (Spring Cloud Stream Applications 2025.0.1)

Registered with SCDF via `bulk_register_apps()`. All use the RabbitMQ binder. JARs are hosted on Maven Central and downloaded by SCDF only at deployment time.

### Sources

| App Name | Description | Key Configuration Properties |
|----------|-------------|------------------------------|
| `s3` | Polls an AWS S3 bucket for new files | `s3.remote-dir`, `s3.region`, `file.consumer.mode` (`contents`\|`ref`) |
| `http` | Receives HTTP POST payloads | `server.port`, `http.path-pattern` |
| `jdbc` | Polls a database table | `jdbc.supplier.query`, `trigger.fixed-delay`, `spring.datasource.*` |
| `file` | Monitors a local directory for new files | `file.directory`, `file.consumer.mode` |
| `ftp` | Monitors an FTP server for new files | `ftp.host`, `ftp.username`, `ftp.password`, `ftp.remote-dir` |
| `sftp` | Monitors an SFTP server for new files | `sftp.host`, `sftp.username`, `sftp.password`, `sftp.remote-dir` |
| `mongodb` | Polls a MongoDB collection | `spring.data.mongodb.uri`, `mongodb.collection` |
| `mqtt` | Subscribes to an MQTT topic | `mqtt.url`, `mqtt.topics`, `mqtt.username`, `mqtt.password` |
| `mail` | Polls an email inbox (IMAP) | `mail.imap.host`, `mail.imap.username`, `mail.imap.password` |
| `tcp-client` | Connects to a TCP server and receives data | `tcp.host`, `tcp.port` |
| `rabbit` | Consumes from a RabbitMQ queue | `rabbit.queues`, `spring.rabbitmq.*` |

### Processors

| App Name | Description | Key Configuration Properties |
|----------|-------------|------------------------------|
| `transform` | Applies SpEL expressions to transform payloads | `spel.function.expression` |
| `filter` | Filters messages based on SpEL expressions | `filter.function.expression` |
| `splitter` | Splits a single message into multiple | `splitter.expression`, `splitter.delimiters` |
| `groovy` | Applies Groovy scripts to messages | `groovy.script`, `groovy.script-location` |
| `http-request` | Makes HTTP requests and emits responses | `http.request.url-expression`, `http.request.http-method` |
| `script` | Executes scripts (JS, Ruby, Python, Groovy) | `script.language`, `script.script` |

### Sinks

| App Name | Description | Key Configuration Properties |
|----------|-------------|------------------------------|
| `jdbc` | Inserts payloads into a relational database | `jdbc.consumer.table-name`, `jdbc.consumer.columns` |
| `log` | Logs message payloads (debugging) | `log.expression`, `log.level` |
| `mongodb` | Writes to a MongoDB collection | `spring.data.mongodb.uri`, `mongodb.collection` |
| `s3` | Writes to an AWS S3 bucket | `s3.bucket`, `s3.key-expression` |
| `elasticsearch` | Writes to Elasticsearch | `elasticsearch.index`, `spring.elasticsearch.*` |
| `file` | Writes to local files | `file.directory`, `file.name-expression` |
| `rabbit` | Publishes to a RabbitMQ exchange | `rabbit.exchange`, `rabbit.routing-key-expression` |
| `tcp` | Sends data to a TCP server | `tcp.host`, `tcp.port` |

### Upstream App Credential Requirements

Upstream apps use standard Spring Boot externalized configuration. When CredHub service instance keys match the app's expected property names, no bridging code is needed — Spring Boot flattens `VCAP_SERVICES` credentials into properties automatically.

| Upstream App | CredHub Keys | Maps to Spring Boot Property |
|---|---|---|
| `s3` (source/sink) | `spring.cloud.aws.credentials.access-key`, `spring.cloud.aws.credentials.secret-key`, `spring.cloud.aws.region.static` | Spring Cloud AWS auto-config |
| `jdbc` (source/sink) | `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password` | Spring Boot DataSource |
| `mongodb` (source/sink) | `spring.data.mongodb.uri` | Spring Boot MongoDB |
| `mqtt` (source/sink) | `mqtt.url`, `mqtt.username`, `mqtt.password` | Spring Integration MQTT |
| `mail` (source) | `mail.imap.host`, `mail.imap.username`, `mail.imap.password` | Spring Integration Mail |
| `ftp` | `ftp.host`, `ftp.username`, `ftp.password` | Spring Integration FTP |
| `sftp` | `sftp.host`, `sftp.username`, `sftp.password` | Spring Integration SFTP |
| `elasticsearch` (sink) | `spring.elasticsearch.uris`, `spring.elasticsearch.username`, `spring.elasticsearch.password` | Spring Boot Elasticsearch |
| `rabbit` (source/sink) | `spring.rabbitmq.host`, `spring.rabbitmq.username`, `spring.rabbitmq.password` | Spring Boot RabbitMQ |

**No credentials needed**: `http` (source), `log` (sink), `file` (source/sink), `transform`, `filter`, `splitter`, `groovy`, `script`, `http-request`

---

## Custom RAG Apps

Built in the `stream-apps/` directory of the tanzu-dataflow repo. Published as JAR artifacts on GitHub Releases.

**Artifact URL pattern:**
```
https://github.com/{owner}/tanzu-dataflow/releases/download/stream-apps-v{build_number}/{app_name}-{version}.jar
```

To find the latest release URLs, use the GitHub MCP server:
```
list_releases(owner="{owner}", repo="tanzu-dataflow")
```

Look for the most recent release tagged `stream-apps-v*`.

### text-extractor (processor)

Extracts text content from binary documents (PDF, DOCX, plain text) using Apache Tika.

| Field | Value |
|-------|-------|
| **SCDF registration name** | `text-extractor` |
| **SCDF type** | `processor` |
| **Function signature** | `Function<Message<byte[]>, Message<String>>` |
| **Function bean name** | `extractText` |
| **Artifact name** | `text-extractor-processor-1.0.0.jar` |

**Configuration properties (non-sensitive, set via `app.text-extractor.*`):**

| Property | Default | Description |
|----------|---------|-------------|
| `extractor.formats` | `pdf,docx,txt` | Comma-separated list of supported formats |

**Credentials:** None required.

**Headers emitted:** `content-type: text/plain`, `original-mime-type: {detected MIME type}`

**Memory recommendation:** 2048MB (Tika loads parser libraries)

---

### text-chunker (processor)

Splits text into overlapping chunks suitable for embedding and vector storage.

| Field | Value |
|-------|-------|
| **SCDF registration name** | `text-chunker` |
| **SCDF type** | `processor` |
| **Function signature** | `Function<Message<String>, List<Message<String>>>` (one-to-many) |
| **Function bean name** | `chunkText` |
| **Artifact name** | `text-chunker-processor-1.0.0.jar` |

**Configuration properties (non-sensitive, set via `app.text-chunker.*`):**

| Property | Default | Description |
|----------|---------|-------------|
| `chunker.size` | `1000` | Chunk size in characters |
| `chunker.overlap` | `200` | Overlap between chunks in characters |
| `chunker.separator` | `\n\n` | Preferred split boundary |

**Credentials:** None required.

**Headers emitted:** `chunk-index: {0-based index}`, `chunk-count: {total chunks}`

---

### embedding (processor)

Calls an embedding API (OpenAI by default) to convert text into vector embeddings. Uses Spring AI's `EmbeddingModel` abstraction.

| Field | Value |
|-------|-------|
| **SCDF registration name** | `embedding` |
| **SCDF type** | `processor` |
| **Function signature** | `Function<Message<String>, Message<float[]>>` |
| **Function bean name** | `generateEmbedding` |
| **Artifact name** | `embedding-processor-1.0.0.jar` |

**Configuration properties (non-sensitive, set via `app.embedding.*`):**

| Property | Default | Description |
|----------|---------|-------------|
| `embedding.model` | `text-embedding-3-small` | Embedding model name |
| `embedding.dimensions` | `1536` | Output vector dimensions |

**Credentials (sensitive, via CredHub):**

| CredHub Key | Description |
|-------------|-------------|
| `EMBEDDING_API_KEY` | API key for the embedding provider (e.g., OpenAI) |

**CredHub service instance name:** `{pipeline}-embedding-creds`

**When to use:** Only needed when embedding generation must be decoupled from storage (branching pipelines, multi-sink scenarios, embedding inspection). For typical RAG pipelines, the `pgvector-sink` handles embedding generation internally.

---

### pgvector-sink (sink)

Writes text documents to PostgreSQL with the PgVector extension. Uses Spring AI's `VectorStore` abstraction, which handles both embedding generation and storage — accepts text input, not pre-computed vectors.

| Field | Value |
|-------|-------|
| **SCDF registration name** | `pgvector-sink` |
| **SCDF type** | `sink` |
| **Function signature** | `Consumer<Message<String>>` |
| **Function bean name** | `writeToVectorStore` |
| **Artifact name** | `pgvector-sink-1.0.0.jar` |

**Configuration properties (non-sensitive, set via `app.pgvector-sink.*`):**

| Property | Default | Description |
|----------|---------|-------------|
| `pgvector.table` | `vector_store` | Table name for vector storage |
| `pgvector.dimensions` | `1536` | Vector dimensions |
| `pgvector.index-type` | `HNSW` | Index type: `HNSW`, `IVFFLAT`, `NONE` |
| `pgvector.distance-type` | `COSINE_DISTANCE` | Distance metric: `COSINE_DISTANCE`, `EUCLIDEAN_DISTANCE` |

**Credentials (sensitive, via CredHub):**

| CredHub Key | Description |
|-------------|-------------|
| `PGVECTOR_URL` | JDBC URL for PostgreSQL (e.g., `jdbc:postgresql://host:5432/dbname`) |
| `PGVECTOR_USERNAME` | Database username |
| `PGVECTOR_PASSWORD` | Database password |
| `EMBEDDING_API_KEY` | API key for the embedding provider (used by VectorStore) |

**CredHub service instance name:** `{pipeline}-pgvector-sink-creds`

**Important:** The pgvector-sink auto-creates the vector store table and indexes on first run (`spring.ai.vectorstore.pgvector.initialize-schema=true`). The PostgreSQL database must have the `pgvector` extension installed.
