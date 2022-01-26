# IDP Connector
Jar library containing helper functions for calling the IDP service

### Usage
In pom.xml add this dependency:

    <groupId>dk.dbc</groupId>
    <artifactId>idp-connector</artifactId>
    <version>1.0-SNAPSHOT</version>

In your EJB add the following inject:

    @Inject
    private IDPConnector idpConnector;

You must have the following environment variables in your deployment:

    IDP_SERVICE_URL

By default the connector caches responses for 8 hours. To use a different value set:

    IDP_CACHE_AGE

The value is the amount of hours to keep the cache. To disable set the value to 0.

### Example

```Java
final IDPConnector.RightSet rights = idpConnector.lookupRight(username, agencyId, password);
return rights.hasRightName(productName);
```
