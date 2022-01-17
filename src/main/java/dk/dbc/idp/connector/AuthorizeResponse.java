package dk.dbc.idp.connector;

import java.util.List;

public class AuthorizeResponse {

    private boolean authenticated;
    private List<IDPRights> rights;

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public List<IDPRights> getRights() {
        return rights;
    }

    public void setRights(List<IDPRights> rights) {
        this.rights = rights;
    }
}
