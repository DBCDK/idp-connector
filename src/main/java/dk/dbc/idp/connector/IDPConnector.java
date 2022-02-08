package dk.dbc.idp.connector;

import dk.dbc.httpclient.FailSafeHttpClient;
import dk.dbc.httpclient.HttpPost;
import dk.dbc.invariant.InvariantUtil;
import dk.dbc.util.Stopwatch;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

public class IDPConnector {
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
            .withDelay(Duration.ofSeconds(5))
            .withMaxRetries(3);

    private final FailSafeHttpClient failSafeHttpClient;
    private final String baseUrl;

    public IDPConnector(Client client, String idpBaseUrl) {
        this(FailSafeHttpClient.create(client, RETRY_POLICY), idpBaseUrl);
    }

    public IDPConnector(Client client, String idpBaseUrl, int cacheAge) {
        this(FailSafeHttpClient.create(client, RETRY_POLICY), idpBaseUrl, cacheAge);
    }

    public IDPConnector(FailSafeHttpClient failSafeHttpClient, String idpBaseUrl) {
        this.failSafeHttpClient = InvariantUtil.checkNotNullOrThrow(failSafeHttpClient, "failSafeHttpClient");
        this.baseUrl = InvariantUtil.checkNotNullNotEmptyOrThrow(idpBaseUrl, "idpBaseUrl");
        this.authenticateCache = new PassiveExpiringMap<>(MAX_CACHE_AGE, TimeUnit.HOURS);
        this.authorizeCache = new PassiveExpiringMap<>(MAX_CACHE_AGE, TimeUnit.HOURS);
    }

    public IDPConnector(FailSafeHttpClient failSafeHttpClient, String idpBaseUrl, int cacheAge) {
        this.failSafeHttpClient = InvariantUtil.checkNotNullOrThrow(failSafeHttpClient, "failSafeHttpClient");
        this.baseUrl = InvariantUtil.checkNotNullNotEmptyOrThrow(idpBaseUrl, "idpBaseUrl");
        this.authenticateCache = new PassiveExpiringMap<>(MAX_CACHE_AGE, TimeUnit.HOURS);
        this.authorizeCache = new PassiveExpiringMap<>(MAX_CACHE_AGE, TimeUnit.HOURS);
    }

    public void close() {
        failSafeHttpClient.getClient().close();
    }

    private String createNetpunktCacheKey(String user, String group, String password) {
        return String.format("%s_%s_%s", user, group, password);
    }

    public boolean authenticate(final String user, final String group, final String password) throws IDPConnectorException {
        InvariantUtil.checkNotNullNotEmptyOrThrow(user, "user");
        InvariantUtil.checkNotNullNotEmptyOrThrow(group, "group");
        InvariantUtil.checkNotNullNotEmptyOrThrow(password, "password");

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
        InvariantUtil.checkNotNullNotEmptyOrThrow(user, "user");
        InvariantUtil.checkNotNullNotEmptyOrThrow(group, "group");
        InvariantUtil.checkNotNullNotEmptyOrThrow(password, "password");

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
        final Stopwatch stopwatch = new Stopwatch();
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
            LOGGER.info("POST {} took {} milliseconds",
                    basePath,
                    stopwatch.getElapsedTime(TimeUnit.MILLISECONDS));
        }
    }

    private void assertResponseStatus(Response response, Response.Status... expectedStatus)
            throws IDPConnectorException {
        final Response.Status actualStatus =
                Response.Status.fromStatusCode(response.getStatus());
        if (!Arrays.asList(expectedStatus).contains(actualStatus)) {
            // TODO Handle exception from IDP better?
            throw new IDPConnectorUnexpectedStatusCodeException(
                    String.format("IDP service returned with unexpected status code: %s", actualStatus),
                    response.getStatus());
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
