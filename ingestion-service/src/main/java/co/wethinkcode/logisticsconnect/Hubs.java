package co.wethinkcode.logisticsconnect;

public class Hubs {
    private final String hubId;
    private final String province;
    private final String sortingCenter;
    private final Boolean active;

    public Hubs(String hubId, String province, String sortingCenter, Boolean active) {
        this.hubId = hubId;
        this.province = province;
        this.sortingCenter = sortingCenter;
        this.active = active;
    }

    public String getHubId() {
        return hubId;
    }

    public String getProvince() {
        return province;
    }

    public String getSortingCenter() {
        return sortingCenter;
    }

    public Boolean getActive() {
        return active;
    }
}