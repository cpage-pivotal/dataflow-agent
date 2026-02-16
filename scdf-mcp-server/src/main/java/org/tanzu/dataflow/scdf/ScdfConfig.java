package org.tanzu.dataflow.scdf;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.dataflow.rest.client.DataFlowOperations;
import org.springframework.cloud.dataflow.rest.client.DataFlowTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ScdfConfig {

    @Bean
    DataFlowOperations dataFlowOperations(@Value("${scdf.server.url}") String scdfServerUrl) {
        return new DataFlowTemplate(URI.create(scdfServerUrl), null);
    }
}
