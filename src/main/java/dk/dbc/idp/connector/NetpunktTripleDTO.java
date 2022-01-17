package dk.dbc.idp.connector;

import java.util.Objects;

public class NetpunktTripleDTO {
    private String userIdAut;
    private String passwordAut;
    private String agencyId;

    public String getUserIdAut() {
        return userIdAut;
    }

    public void setUserIdAut(String userIdAut) {
        this.userIdAut = userIdAut;
    }

    public String getPasswordAut() {
        return passwordAut;
    }

    public void setPasswordAut(String passwordAut) {
        this.passwordAut = passwordAut;
    }

    public String getAgencyId() {
        return agencyId;
    }

    public void setAgencyId(String agencyId) {
        this.agencyId = agencyId;
    }

    @Override
    public String toString() {
        return "NetpunktTripleDTO{" +
                "userIdAut='" + userIdAut + '\'' +
                ", passwordAut='" + passwordAut + '\'' +
                ", agencyId='" + agencyId + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NetpunktTripleDTO that = (NetpunktTripleDTO) o;
        return Objects.equals(userIdAut, that.userIdAut) && Objects.equals(passwordAut, that.passwordAut) && Objects.equals(agencyId, that.agencyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userIdAut, passwordAut, agencyId);
    }
}
