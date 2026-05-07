package com.mock112.domain;

/**
 * 초기 기준 마커 (seed marker).
 * import 시 S5 ReferenceMarkerSeed.createForIncident() 입력이 된다.
 */
public class MockSeedMarker {

    private String type;
    private String source;
    private String memo;
    private double lon;
    private double lat;

    public MockSeedMarker() {}

    public MockSeedMarker(String type, String source, String memo, double lon, double lat) {
        this.type = type;
        this.source = source;
        this.memo = memo;
        this.lon = lon;
        this.lat = lat;
    }

    // --- getters & setters ---

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }

    public double getLon() { return lon; }
    public void setLon(double lon) { this.lon = lon; }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
}
