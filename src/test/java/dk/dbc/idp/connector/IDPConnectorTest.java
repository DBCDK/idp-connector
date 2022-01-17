package dk.dbc.idp.connector;

import com.github.tomakehurst.wiremock.WireMockServer;
import dk.dbc.httpclient.HttpClient;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.ws.rs.client.Client;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class IDPConnectorTest {

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
        connector = new IDPConnector(CLIENT, wireMockHost);
    }

    @AfterAll
    static void stopWireMockServer() {
        wireMockServer.stop();
    }

    @Test
    void noRights() throws IDPConnectorException {
        IDPConnector.RightSet rightSet = connector.lookupRight("test", "test", "test");

        assertThat(rightSet.rights.isEmpty(), is(true));
        assertThat(rightSet.hasRightName("POSTHUS"), is(false));
        assertThat(rightSet.hasRight("POSTHUS", "READ"), is(false));
    }

    @Test
    void hasRights() throws IDPConnectorException {
        IDPConnector.RightSet rightSet = connector.lookupRight("realuser", "realgroup", "realpassword");

        assertThat(rightSet.rights.isEmpty(), is(false));
        assertThat(rightSet.hasRightName("POSTHUS"), is(true));
        assertThat(rightSet.hasRight("POSTHUS", "READ"), is(true));
        assertThat(rightSet.hasRightName("EMNEORD"), is(true));
        assertThat(rightSet.hasRight("EMNEORD", "READ"), is(true));
        assertThat(rightSet.hasRightName("BOB"), is(false));
        assertThat(rightSet.hasRight("BOB", "READ"), is(false));
    }

}
