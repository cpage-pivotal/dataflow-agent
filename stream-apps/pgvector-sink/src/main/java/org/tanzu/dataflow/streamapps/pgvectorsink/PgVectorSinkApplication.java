package org.tanzu.dataflow.streamapps.pgvectorsink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PgVectorSinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(PgVectorSinkApplication.class, args);
    }
}
