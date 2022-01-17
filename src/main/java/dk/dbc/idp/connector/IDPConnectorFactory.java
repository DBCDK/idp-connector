package dk.dbc.idp.connector;

import dk.dbc.httpclient.HttpClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.ws.rs.client.Client;

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
        final Client client = HttpClient.newClient(new ClientConfig()
                .register(new JacksonConfig())
                .register(new JacksonFeature()));
        LOGGER.info("Creating IDPConnector for: {}", idpBaseUrl);
        return new IDPConnector(client, idpBaseUrl);
    }

    @Inject
    @ConfigProperty(name = "IDP_SERVICE_URL")
    private String idpBaseUrl;

    IDPConnector idpConnector;

    @PostConstruct
    public void initializeConnector() {
        idpConnector = IDPConnectorFactory.create(idpBaseUrl);
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
