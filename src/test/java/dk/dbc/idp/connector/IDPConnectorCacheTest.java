package dk.dbc.idp.connector;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import dk.dbc.httpclient.HttpClient;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.ws.rs.client.Client;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

class IDPConnectorCacheTest {

    private static WireMockServer wireMockServer;
    private static String wireMockHost;

    final static Client CLIENT = HttpClient.newClient(new ClientConfig()
            .register(new JacksonFeature()));
    static IDPConnector connector;

    @BeforeAll
    static void startWireMockServer() {
        wireMockServer = new WireMockServer(options().dynamicPort()
                .dynamicHttpsPort());
        wireMockServer.start();
        wireMockHost = "http://localhost:" + wireMockServer.port();
        configureFor("localhost", wireMockServer.port());
    }

    @BeforeAll
    static void setConnector() {
        connector = new IDPConnector(CLIENT, wireMockHost, 1);
    }

    @AfterAll
    static void stopWireMockServer() {
        wireMockServer.stop();
    }

    @Test
    void cacheTestAuthenticateFalse() throws IDPConnectorException {
        wireMockServer.resetRequests();

        connector.authenticate("test", "test", "test");
        connector.authenticate("test", "test", "test");
        connector.authenticate("test", "test", "test");

        verify(1, new RequestPatternBuilder().withUrl("/api/v1/authenticate/"));
    }

    @Test
    void cacheTestAuthenticateFalseTrue() throws IDPConnectorException {
        wireMockServer.resetRequests();

        connector.authenticate("realuser", "realgroup", "realpassword");
        connector.authenticate("realuser", "realgroup", "realpassword");
        connector.authenticate("realuser", "realgroup", "realpassword");

        verify(1, new RequestPatternBuilder().withUrl("/api/v1/authenticate/"));
    }

    @Test
    void cacheHitForUnauthorized() throws IDPConnectorException {
        wireMockServer.resetRequests();

        connector.lookupRight("test", "test", "test");
        connector.lookupRight("test", "test", "test");
        connector.lookupRight("test", "test", "test");

        verify(1, new RequestPatternBuilder().withUrl("/api/v1/authorize/"));
    }

    @Test
    void cacheHitForAuthorized() throws IDPConnectorException {
        wireMockServer.resetRequests();

        connector.lookupRight("realuser", "realgroup", "realpassword");
        connector.lookupRight("realuser", "realgroup", "realpassword");
        connector.lookupRight("realuser", "realgroup", "realpassword");

        verify(1, new RequestPatternBuilder().withUrl("/api/v1/authorize/"));
    }

    @Test
    void cacheHitDifferentPasswords() throws IDPConnectorException {
        wireMockServer.resetRequests();

        connector.lookupRight("test", "test", "nope");
        connector.lookupRight("test", "test", "wrong");
        connector.lookupRight("test", "test", "wrong again");
        connector.lookupRight("test", "test", "maybe this one");
        connector.lookupRight("test", "test", "wrong again");

        verify(4, new RequestPatternBuilder().withUrl("/api/v1/authorize/"));
    }

}
