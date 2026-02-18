package org.tanzu.dataflow.streamapps.pgvectorsink;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/testdb",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.ai.vectorstore.pgvector.initialize-schema=false",
        "spring.ai.openai.api-key=test-key"
})
class PgVectorSinkApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the application context starts correctly with test properties.
        // Full integration tests require a running PostgreSQL instance with pgvector.
    }
}
