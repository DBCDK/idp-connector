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
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        connector = new IDPConnector(CLIENT, wireMockHost, 0);
    }

    @AfterAll
    static void stopWireMockServer() {
        wireMockServer.stop();
    }

    @Test
    void testAuthenticate() throws IDPConnectorException {
        assertThat(connector.authenticate("test", "test", "test"), is(false));
        assertThat(connector.authenticate("realuser", "realgroup", "realpassword"), is(true));
    }

    @Test
    void notAuthenticated() throws IDPConnectorException {
        IDPConnector.RightSet rightSet = connector.lookupRight("test", "test", "test");

        assertThat(rightSet.rights.isEmpty(), is(true));
        assertThat(rightSet.hasRightName("POSTHUS"), is(false));
        assertThat(rightSet.hasRight("POSTHUS", "READ"), is(false));
    }

    @Test
    void authenticatedAndHasRights() throws IDPConnectorException {
        IDPConnector.RightSet rightSet = connector.lookupRight("realuser", "realgroup", "realpassword");

        assertThat(rightSet.rights.isEmpty(), is(false));
        assertThat(rightSet.hasRightName("POSTHUS"), is(true));
        assertThat(rightSet.hasRight("POSTHUS", "READ"), is(true));
        assertThat(rightSet.hasRight("POSTHUS", "WRITE"), is(false));
        assertThat(rightSet.hasRightName("EMNEORD"), is(true));
        assertThat(rightSet.hasRight("EMNEORD", "READ"), is(true));
        assertThat(rightSet.hasRight("EMNEORD", "WRITE"), is(false));
        assertThat(rightSet.hasRightName("BOB"), is(false));
        assertThat(rightSet.hasRight("BOB", "READ"), is(false));
    }

    @Test
    void authenticatedButNoRights() throws IDPConnectorException {
        IDPConnector.RightSet rightSet = connector.lookupRight("norights", "norights", "norights");

        assertThat(rightSet.rights.isEmpty(), is(true));
        assertThat(rightSet.hasRightName("POSTHUS"), is(false));
        assertThat(rightSet.hasRight("POSTHUS", "READ"), is(false));
    }

    @Test
    void emptyArgumentsAuthorize() {
        assertThrows(NullPointerException.class, () -> connector.lookupRight("user", "group", null), "Value of parameter 'password' cannot be null");
        assertThrows(IllegalArgumentException.class, () -> connector.lookupRight("user", "group", ""), "Value of parameter 'password' cannot be empty");
        assertThrows(NullPointerException.class, () -> connector.lookupRight("user", null, "password"), "Value of parameter 'group' cannot be null");
        assertThrows(IllegalArgumentException.class, () -> connector.lookupRight("user", "", "password"), "Value of parameter 'group' cannot be empty");
        assertThrows(NullPointerException.class, () -> connector.lookupRight(null, "group", "password"), "Value of parameter 'user' cannot be null");
        assertThrows(IllegalArgumentException.class, () -> connector.lookupRight("", "group", "password"), "Value of parameter 'user' cannot be empty");
    }

    @Test
    void emptyArgumentsAuthenticate() {
        assertThrows(NullPointerException.class, () -> connector.authenticate("user", "group", null), "Value of parameter 'password' cannot be null");
        assertThrows(IllegalArgumentException.class, () -> connector.authenticate("user", "group", ""), "Value of parameter 'password' cannot be empty");
        assertThrows(NullPointerException.class, () -> connector.authenticate("user", null, "password"), "Value of parameter 'group' cannot be null");
        assertThrows(IllegalArgumentException.class, () -> connector.authenticate("user", "", "password"), "Value of parameter 'group' cannot be empty");
        assertThrows(NullPointerException.class, () -> connector.authenticate(null, "group", "password"), "Value of parameter 'user' cannot be null");
        assertThrows(IllegalArgumentException.class, () -> connector.authenticate("", "group", "password"), "Value of parameter 'user' cannot be empty");
    }

    @Test
    void testExceptionHandling() {
        final IDPConnectorException exception = assertThrows(
                IDPConnectorException.class,
                () -> connector.lookupRight("error", "error", "error")
        );

        assertThat(exception.getMessage(), is("Exception from IDP with status code 500 and message 'PersistenceException'"));
    }

}
