# Custom Stream App Scaffold

This reference provides the patterns, conventions, and code examples for generating custom Spring Cloud Stream applications. Use this when the pipeline requires a component that doesn't exist in the upstream catalog or the pre-built custom RAG apps.

## Project Structure

Every custom app follows this layout:

```
stream-apps-custom/{app-name}/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/org/tanzu/dataflow/streamapps/{packagename}/
    │   │   ├── {AppName}Application.java
    │   │   ├── {AppName}Configuration.java
    │   │   └── {AppName}Properties.java        (optional)
    │   └── resources/
    │       └── application.properties
    └── test/
        └── java/org/tanzu/dataflow/streamapps/{packagename}/
            └── {AppName}ApplicationTests.java
```

## POM Template

The POM inherits Spring Boot and adds Spring Cloud Stream with the RabbitMQ binder. Adjust dependencies based on the app's requirements.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.10</version>
        <relativePath/>
    </parent>

    <groupId>org.tanzu</groupId>
    <artifactId>{app-name}</artifactId>
    <version>1.0.0</version>
    <name>{app-name}</name>
    <description>{description}</description>

    <properties>
        <java.version>21</java.version>
        <spring-cloud.version>2025.0.1</spring-cloud.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-stream</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-stream-binder-rabbit</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Add app-specific dependencies here -->

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-stream-test-binder</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**If the app needs Spring AI**, add the Spring AI BOM and the relevant starter:

```xml
<properties>
    <spring-ai.version>1.1.2</spring-ai.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- Spring Cloud BOM (as above) -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- For OpenAI embedding/chat -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-openai</artifactId>
    </dependency>
    <!-- For PgVector storage -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
    </dependency>
</dependencies>
```

## Function Model

Spring Cloud Stream apps use functional beans. The function type determines the app's role:

| App Type | Function Signature | Description |
|----------|-------------------|-------------|
| Source | `Supplier<T>` or `Supplier<Flux<T>>` | Produces messages |
| Processor | `Function<T, R>` | Transforms messages 1:1 |
| Processor (fan-out) | `Function<T, List<R>>` | Transforms messages 1:many |
| Sink | `Consumer<T>` | Consumes messages |

Messages are typically `Message<PayloadType>` to access headers. Use `MessageBuilder` to construct outbound messages with headers.

## Application Class

```java
package org.tanzu.dataflow.streamapps.{packagename};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class {AppName}Application {
    public static void main(String[] args) {
        SpringApplication.run({AppName}Application.class, args);
    }
}
```

## Configuration Patterns

### Source Example

```java
package org.tanzu.dataflow.streamapps.{packagename};

import java.util.function.Supplier;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

@Configuration
public class {AppName}Configuration {

    @Bean
    public Supplier<Message<String>> produceData() {
        return () -> {
            String data = /* generate or fetch data */;
            return MessageBuilder.withPayload(data)
                    .setHeader("source", "my-source")
                    .build();
        };
    }
}
```

### Processor Example (1:1)

```java
package org.tanzu.dataflow.streamapps.{packagename};

import java.util.function.Function;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

@Configuration
public class {AppName}Configuration {

    @Bean
    public Function<Message<String>, Message<String>> processData() {
        return message -> {
            String input = message.getPayload();
            String output = /* transform the input */;
            return MessageBuilder.withPayload(output)
                    .copyHeaders(message.getHeaders())
                    .build();
        };
    }
}
```

### Processor Example (1:many)

```java
package org.tanzu.dataflow.streamapps.{packagename};

import java.util.List;
import java.util.function.Function;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

@Configuration
public class {AppName}Configuration {

    @Bean
    public Function<Message<String>, List<Message<String>>> splitData() {
        return message -> {
            String input = message.getPayload();
            List<String> parts = /* split the input */;
            return parts.stream()
                    .map(part -> MessageBuilder.withPayload(part)
                            .copyHeaders(message.getHeaders())
                            .build())
                    .toList();
        };
    }
}
```

### Sink Example

```java
package org.tanzu.dataflow.streamapps.{packagename};

import java.util.function.Consumer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

@Configuration
public class {AppName}Configuration {

    @Bean
    public Consumer<Message<String>> consumeData() {
        return message -> {
            String payload = message.getPayload();
            /* write to external system */
        };
    }
}
```

## Configuration Properties (Optional)

Use `@ConfigurationProperties` records for externalized configuration:

```java
package org.tanzu.dataflow.streamapps.{packagename};

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "{appprefix}")
public record {AppName}Properties(
        String someSetting,
        int someNumber
) {
    public {AppName}Properties {
        if (someSetting == null || someSetting.isBlank()) someSetting = "default-value";
        if (someNumber <= 0) someNumber = 100;
    }
}
```

Enable it in the configuration class:

```java
@Configuration
@EnableConfigurationProperties({AppName}Properties.class)
public class {AppName}Configuration {
    // inject via constructor or method parameter
}
```

## application.properties

```properties
spring.application.name={app-name}
spring.cloud.stream.function.definition={functionBeanName}

# App-specific defaults
{appprefix}.some-setting=default-value
```

The `spring.cloud.stream.function.definition` value must match the `@Bean` method name exactly.

## CredHub Credential Bridging

If the app needs sensitive credentials (API keys, database passwords), create a CredHub bridging config that maps CredHub keys to Spring Boot properties via `@PostConstruct`:

```java
package org.tanzu.dataflow.streamapps.{packagename};

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
public class CredHub{AppName}Config {

    @Value("${MY_CREDENTIAL_KEY:#{null}}")
    private String myCredential;

    @PostConstruct
    void configureProperties() {
        if (myCredential != null && !myCredential.isBlank()) {
            System.setProperty("spring.some.property", myCredential);
        }
    }
}
```

**Convention:** CredHub keys use `UPPER_SNAKE_CASE` (e.g., `EMBEDDING_API_KEY`, `PGVECTOR_URL`). The `@Value` annotation reads them from `VCAP_SERVICES` (which Spring Boot flattens into properties on Cloud Foundry). The `@PostConstruct` bridges them to the Spring Boot properties the app actually consumes.

If the CredHub keys directly match the Spring Boot property names (e.g., `spring.datasource.url` as a CredHub key), no bridging code is needed — Spring Boot reads them automatically from `VCAP_SERVICES`.

## Test Template

```java
package org.tanzu.dataflow.streamapps.{packagename};

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class {AppName}ApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

For apps that don't require external services (like databases or APIs), add functional tests:

```java
package org.tanzu.dataflow.streamapps.{packagename};

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestChannelBinderConfiguration.class)
class {AppName}ApplicationTests {

    @Autowired
    private InputDestination input;

    @Autowired
    private OutputDestination output;

    @Test
    void processesMessage() {
        input.send(MessageBuilder.withPayload("test input".getBytes()).build());
        var result = output.receive(5000);
        assertThat(result).isNotNull();
        assertThat(new String(result.getPayload())).contains("expected output");
    }
}
```

## Key Versions

| Component | Version |
|-----------|---------|
| Java | 21 |
| Spring Boot | 3.5.10 |
| Spring Cloud | 2025.0.1 |
| Spring AI | 1.1.2 |
| Apache Tika | 3.2.3 |

## GitHub Actions Build

Custom apps are committed to `stream-apps-custom/{app-name}/` and built via the `build-custom-app.yml` workflow:

1. Trigger: `workflow_dispatch` with `app_name` input
2. Build: `mvn -f stream-apps-custom/{app-name}/pom.xml clean package`
3. Release: JAR published as a GitHub Release asset

The artifact URL follows the pattern:
```
https://github.com/{owner}/tanzu-dataflow/releases/download/custom-{app-name}-v{version}/{app-name}-{version}.jar
```
