package freemap.hikar;

/**
 * Created by nick on 27/03/18.
 */

// A 'region' is the subdivision below country.
// This should match what is on geofabrik.
// For example, England and Baden-Wuerttemberg are 'regions' by this definition.
//
// A 'county' is the subdivision below this.

public class RegionInfo {
    public String continent, country, region, county;

    public RegionInfo (String continent, String country, String region, String county) {
        this.continent = continent;
        this.country = country;
        this.region = region;
        this.county = county;
    }

    public String getGeofabrik() {
        return "http://download.geofabrik.de/"+continent+"/"+country+
                (region==null ? "": "/"+region+(county==null ? "":
                        "/"+county))+".osm.pbf";
    }

    public String getDirectoryStructure() {
        return continent+"/"+country+
                (region==null ? "": "/"+region+(county==null ? "":
                        "/"+county))+"/";
    }

    public String getLocalOSMFile() {
        return continent+"_"+country+ (region==null ? "": "_"+region+(county==null ? "":"_"+county))+".osm.pbf";
    }
}
