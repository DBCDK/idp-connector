package dk.dbc.idp.connector;

public class IDPConnectorWireMockRecorder {
    /*
        Steps to reproduce wiremock recording:

        * Start standalone runner
            java -jar wiremock-standalone-{WIRE_MOCK_VERSION}.jar --proxy-all="{RECORD_SERVICE_HOST}" --record-mappings --verbose

        * Run the main method of this class

        * Replace content of src/test/resources/{__files|mappings} with that produced by the standalone runner
     */

    public static void main(String[] args) throws Exception {
        IDPConnectorTest.connector = new IDPConnector(
                IDPConnectorTest.CLIENT, "http://localhost:8080");

        final IDPConnectorTest idpConnectorTest = new IDPConnectorTest();

        idpConnectorTests(idpConnectorTest);
    }

    private static void idpConnectorTests(IDPConnectorTest idpConnectorTest) throws IDPConnectorException {
        idpConnectorTest.notAuthenticated();
        idpConnectorTest.authenticatedAndHasRights();
        idpConnectorTest.authenticatedButNoRights();
    }
}
