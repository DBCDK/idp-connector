package dk.dbc.idp.connector;

import java.util.List;

public class AuthorizeResponse {

    private boolean authenticated;
    private String agencyId;
    private String identity;
    private List<IDPRights> rights;

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public String getAgencyId() {
        return agencyId;
    }

    public void setAgencyId(String agencyId) {
        this.agencyId = agencyId;
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public List<IDPRights> getRights() {
        return rights;
    }

    public void setRights(List<IDPRights> rights) {
        this.rights = rights;
    }
}
