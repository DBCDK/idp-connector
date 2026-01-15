package dk.dbc.idp.connector;

import dk.dbc.commons.useragent.UserAgent;
import dk.dbc.httpclient.FailSafeHttpClient;
import dk.dbc.httpclient.HttpPost;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.Response;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class IDPConnector {
    public enum TimingLogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR
    }
    private static final Logger LOGGER = LoggerFactory.getLogger(IDPConnector.class);
    private static final String PATH_AUTHENTICATE = "/api/v1/authenticate/";
    private static final String PATH_AUTHORIZE = "/api/v1/authorize/";
    private static final int MAX_CACHE_AGE = 8;

    private final PassiveExpiringMap<String, AuthenticateResponse> authenticateCache;
    private final PassiveExpiringMap<String, AuthorizeResponse> authorizeCache;

    /* Currently, retry handling is disabled to retain backwards compatibility
     * with older versions of the FailSafeHttpClient in use in systems using
     * this connector.
     */
    private static final RetryPolicy<Response> RETRY_POLICY = new RetryPolicy<Response>()
            .handle(ProcessingException.class)
            .handleResultIf(response -> response.getStatus() == 500)
            .withDelay(Duration.ofSeconds(1))
            .withMaxRetries(3);

    private final FailSafeHttpClient failSafeHttpClient;
    private final String baseUrl;

    public IDPConnector(Client client, UserAgent userAgent, String baseUrl) {
        this(FailSafeHttpClient.create(client, userAgent, RETRY_POLICY), baseUrl);
    }

    public IDPConnector(Client client, UserAgent userAgent, String baseUrl, int cacheAge) {
        this(FailSafeHttpClient.create(client, userAgent, RETRY_POLICY), baseUrl, cacheAge);
    }

    public IDPConnector(FailSafeHttpClient failSafeHttpClient, String baseUrl) {
        Objects.requireNonNull(failSafeHttpClient, "failSafeHttpClient");
        Objects.requireNonNull(baseUrl, "baseUrl");
        this.failSafeHttpClient = failSafeHttpClient;
        this.baseUrl = baseUrl;
        this.authenticateCache = new PassiveExpiringMap<>(MAX_CACHE_AGE, TimeUnit.HOURS);
        this.authorizeCache = new PassiveExpiringMap<>(MAX_CACHE_AGE, TimeUnit.HOURS);
    }

    public IDPConnector(FailSafeHttpClient failSafeHttpClient, String baseUrl, int cacheAge) {
        Objects.requireNonNull(failSafeHttpClient, "failSafeHttpClient");
        Objects.requireNonNull(baseUrl, "baseUrl");
        this.failSafeHttpClient = failSafeHttpClient;
        this.baseUrl = baseUrl;
        this.authenticateCache = new PassiveExpiringMap<>(cacheAge, TimeUnit.HOURS);
        this.authorizeCache = new PassiveExpiringMap<>(cacheAge, TimeUnit.HOURS);
    }

    public void close() {
        failSafeHttpClient.getClient().close();
    }

    private String createNetpunktCacheKey(String user, String group, String password) {
        return String.format("%s_%s_%s", user, group, password);
    }

    public boolean authenticate(final String user, final String group, final String password) throws IDPConnectorException {
        checkNotNullOrEmpty(user, "user");
        checkNotNullOrEmpty(group, "group");
        checkNotNullOrEmpty(password, "password");

        final String cacheKey = createNetpunktCacheKey(user, group, password);
        AuthenticateResponse authenticateResponse = authenticateCache.get(cacheKey);

        if (authenticateResponse == null) {
            final NetpunktTripleDTO netpunktTripleDTO = new NetpunktTripleDTO();
            netpunktTripleDTO.setAgencyId(group);
            netpunktTripleDTO.setUserIdAut(user);
            netpunktTripleDTO.setPasswordAut(password);

            LOGGER.info("Authenticating {}/{}", group, user);
            authenticateResponse = postRequest(PATH_AUTHENTICATE, netpunktTripleDTO, AuthenticateResponse.class);

            authenticateCache.put(cacheKey, authenticateResponse);
        }

        return authenticateResponse.isAuthenticated();
    }

    public RightSet lookupRight(final String user, final String group, final String password) throws IDPConnectorException {
        checkNotNullOrEmpty(user, "user");
        checkNotNullOrEmpty(group, "group");
        checkNotNullOrEmpty(password, "password");

        final String cacheKey = createNetpunktCacheKey(user, group, password);
        AuthorizeResponse authorizeResponse = this.authorizeCache.get(cacheKey);
        if (authorizeResponse == null) {
            final NetpunktTripleDTO netpunktTripleDTO = new NetpunktTripleDTO();
            netpunktTripleDTO.setAgencyId(group);
            netpunktTripleDTO.setUserIdAut(user);
            netpunktTripleDTO.setPasswordAut(password);

            LOGGER.info("Fetching rights for {}/{}", group, user);
            authorizeResponse = postRequest(PATH_AUTHORIZE, netpunktTripleDTO, AuthorizeResponse.class);

            authorizeCache.put(cacheKey, authorizeResponse);
        }

        final RightSet result = new RightSet();

        if (authorizeResponse.isAuthenticated() && authorizeResponse.getRights() != null) {
            for (IDPRights idpRight : authorizeResponse.getRights()) {
                result.add(idpRight.getProductName(), idpRight.getName());
            }
        }

        return result;
    }

    private <T> T postRequest(String basePath,
                              NetpunktTripleDTO data,
                              Class<T> type) throws IDPConnectorException {
        final StopWatch watch = new Log4JStopWatch();
        try {
            final HttpPost httpPost = new HttpPost(failSafeHttpClient)
                    .withBaseUrl(baseUrl)
                    .withPathElements(basePath)
                    .withData(data, "application/json")
                    .withHeader("Accept", "application/json");
            final Response response = httpPost.execute();
            assertResponseStatus(response, Response.Status.OK);
            return readResponseEntity(response, type);
        } finally {
            watch.stop("POST " + basePath);
        }
    }

    private void assertResponseStatus(Response response, Response.Status... expectedStatus)
            throws IDPConnectorException {
        final Response.Status actualStatus =
                Response.Status.fromStatusCode(response.getStatus());
        if (!Arrays.asList(expectedStatus).contains(actualStatus)) {
            try {
                final MessageDTO messageDTO = response.readEntity(MessageDTO.class);
                throw new IDPConnectorException(String.format("Exception from IDP with status code %s and message '%s'",
                        response.getStatus(), messageDTO.getMessage()));
            } catch (ProcessingException e) {
                throw new IDPConnectorUnexpectedStatusCodeException(
                        String.format("IDP service returned with unexpected status code: %s", actualStatus),
                        response.getStatus());
            }
        }
    }

    private <T> T readResponseEntity(Response response, Class<T> type)
            throws IDPConnectorException {
        final T entity = response.readEntity(type);
        if (entity == null) {
            throw new IDPConnectorException(
                    String.format("IDP service returned with null-valued %s entity", type.getName()));
        }
        return entity;
    }

    private void checkNotNullOrEmpty(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
    }

    public class RightSet {
        HashMap<String, HashSet<String>> rights;

        private RightSet() {
            this.rights = new HashMap<>();
        }

        private void add(String name, String right) {
            this.rights.computeIfAbsent(name, k -> new HashSet<>());

            this.rights.get(name).add(right);
        }

        public boolean hasRight(String name, String right) {
            return this.rights.containsKey(name) && this.rights.get(name).contains(right);
        }

        public boolean hasRightName(String name) {
            return this.rights.containsKey(name);
        }

        public String toString() {
            return "RightSet{rights=" + this.rights + '}';
        }
    }
}
