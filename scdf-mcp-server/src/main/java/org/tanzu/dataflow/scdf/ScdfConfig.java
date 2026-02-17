package org.tanzu.dataflow.scdf;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.client.RestClient;

/**
 * Configures a {@link RestClient} that authenticates to the SCDF REST API
 * using OAuth2 client credentials from the bound p-dataflow service instance.
 * <p>
 * On Cloud Foundry, the dataflow service binding provides credentials via
 * {@code VCAP_SERVICES}, which Spring Boot flattens into properties like
 * {@code vcap.services.dataflow.credentials.client-id}.
 */
@Configuration
public class ScdfConfig {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(
            @Value("${vcap.services.dataflow.credentials.client-id}") String clientId,
            @Value("${vcap.services.dataflow.credentials.client-secret}") String clientSecret,
            @Value("${vcap.services.dataflow.credentials.access-token-url}") String tokenUri) {

        ClientRegistration registration = ClientRegistration.withRegistrationId("scdf")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri(tokenUri)
                .build();

        return new InMemoryClientRegistrationRepository(registration);
    }

    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository) {

        var clientService = new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                clientRegistrationRepository, clientService);
        manager.setAuthorizedClientProvider(
                OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        return manager;
    }

    @Bean
    RestClient scdfRestClient(
            @Value("${vcap.services.dataflow.credentials.dataflow-url}") String dataflowUrl,
            OAuth2AuthorizedClientManager authorizedClientManager) {

        var oauth2Interceptor = new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);
        oauth2Interceptor.setClientRegistrationIdResolver(request -> "scdf");

        return RestClient.builder()
                .baseUrl(dataflowUrl)
                .requestInterceptor(oauth2Interceptor)
                .build();
    }
}
