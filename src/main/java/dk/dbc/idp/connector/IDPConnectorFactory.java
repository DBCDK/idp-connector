package dk.dbc.idp.connector;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * IDP service connector factory
 * <p>
 * Synopsis:
 * </p>
 * <pre>
 *    // New instance
 *    IDPConnector connector = IDPConnectorFactory.create("http://idp-service");
 *
 *    // Singleton instance in CDI enabled environment
 *    {@literal @}Inject
 *    IDPConnectorFactory factory;
 *    ...
 *    IDPConnector connector = factory.getInstance();
 *
 *    // or simply
 *    {@literal @}Inject
 *    IDPConnector connector;
 * </pre>
 * <p>
 * CDI case depends on the idp service baseurl being defined as
 * the value of either a system property or environment variable
 * named IDP_SERVICE_URL.
 * </p>
 */
@ApplicationScoped
public class IDPConnectorFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(IDPConnectorFactory.class);

    public static IDPConnector create(String idpBaseUrl) {
        return create(idpBaseUrl, 1000, Duration.ofMillis(500), Duration.ofMillis(500));
    }

    public static IDPConnector create(String idpBaseUrl, int cacheAge) {
        return create(idpBaseUrl, cacheAge, Duration.ofMillis(500), Duration.ofMillis(500));
    }

    public static IDPConnector create(String idpBaseUrl, int connectionTimeout, int readTimeout) {
        final Client client = ClientBuilder.newBuilder()
                .connectTimeout(connectionTimeout, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeout, TimeUnit.MILLISECONDS).build()
                .register(new JacksonConfig())
                .register(new JacksonFeature());
        LOGGER.info("Creating IDPConnector for: {}, with connection timeout: {}, and read timeout: {}", idpBaseUrl, connectionTimeout, readTimeout);
        return new IDPConnector(client, idpBaseUrl);
    }

    public static IDPConnector create(String idpBaseUrl, int cacheAge, Duration connectionTimeout, Duration readTimeout) {
        final Client client = ClientBuilder.newBuilder()
                .connectTimeout(connectionTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(readTimeout.toMillis(), TimeUnit.MILLISECONDS).build()
                .register(new JacksonConfig())
                .register(new JacksonFeature());
        LOGGER.info("Creating IDPConnector for: {}, with connection timeout: {}, and read timeout: {}", idpBaseUrl, connectionTimeout, readTimeout);
        return new IDPConnector(client, idpBaseUrl, cacheAge);
    }

    @Inject
    @ConfigProperty(name = "IDP_SERVICE_URL")
    private String idpBaseUrl;

    @Inject
    @ConfigProperty(name = "IDP_CACHE_AGE", defaultValue = "8")
    private int cacheAge;

    @Inject
    @ConfigProperty(name = "IDP_CONNECT_TIMEOUT_DURATION", defaultValue = "PT0.5S")
    private Duration connectionTimeout;

    @Inject
    @ConfigProperty(name = "IDP_READ_TIMEOUT_DURATION", defaultValue = "PT0.5S")
    private Duration readTimeout;

    IDPConnector idpConnector;

    @PostConstruct
    public void initializeConnector() {
        idpConnector = IDPConnectorFactory.create(idpBaseUrl, cacheAge, connectionTimeout, readTimeout);
    }

    @Produces
    public IDPConnector getInstance() {
        return idpConnector;
    }

    @PreDestroy
    public void tearDownConnector() {
        idpConnector.close();
    }
}
