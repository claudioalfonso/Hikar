package freemap.hikar;

import freemap.data.Projection;
import freemap.datasource.WebDataSource;
import freemap.jdem.DEMSource;
import freemap.jdem.HGTDataInterpreter;
import freemap.jdem.HGTTileDeliverer;
import freemap.datasource.FreemapFileFormatter;
import freemap.data.Point;
import freemap.jdem.DEM;
import freemap.proj.LFPFileFormatter;
import freemap.proj.Proj4ProjectionFactory;
import freemap.datasource.FreemapDataset;
import freemap.datasource.Tile;
import freemap.datasource.CachedTileDeliverer;
import freemap.andromaps.GeoJSONDataInterpreter;
import freemap.jdem.SRTMMicrodegFileFormatter;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import android.util.Log;

/* OsmDemIntegrator

	integrates OSM and DEM data sources by interpolating the OSM data on the DEM to give
	each point an elevation.

	Note how we handle different projections.

	There are two projection schemes - the TILING projection scheme and the DISPLAY projection
	scheme.

	The TILING projection scheme is used to fetch tiles of data (OSM or DEM). OSM and DEM data
	must use the same TILING projection. Currently supported schemes are OSGB (metres) and
	microdegrees. Note that the microdegrees are converted to degrees when sent to the web service
	to retrieve OSM data; this will allow Hikar to potentially talk to a wider range of GeoJSON
	web services in addition to Freemap's own.

	The DISPLAY projection scheme relates to how data is DISPLAYED.

	Obviously unprojected (epsg:4326) is no good as a DISPLAY projection as 1 degree of latitude
	does not equal 1 degree of longitude and neither approximate to 1 metre. The strategy is to
	fetch TILES in degrees/microdegrees (for best web service support) and then reproject to a
	more suitable projection, e.g. Google Spherical Mercator (3857, aka 3785 or 900913)

	In the UK it's easy as we can just have everything in OSGB. However to make Hikar
	internationally-compatible the best approach for now is probably to use SRTM data (uses
	degrees), get OSM data also in degrees, and project into 3857 or an alternative projection
	for a specific region of the world.
 */


public class OsmDemIntegrator {

    CachedTileDeliverer osm;
    HGTTileDeliverer hgt;
    Projection tilingProj;
    HashMap<String, Tile> hgtupdated, osmupdated;
    int demType;
    double[] multipliers = {1, 1000000};
    ArrayList<Point> failedDemOrigins, failedOsmOrigins;

    public static final int HGT_OSGB_LFP = 0, HGT_SRTM = 1;


    public OsmDemIntegrator(Projection tilingProj, int demType,
                            String lfpUrl, String srtmUrl, String osmUrl) {
        this.demType = demType;
        failedDemOrigins = new ArrayList<Point>();
        failedOsmOrigins = new ArrayList<Point>();

        int[] ptWidths = {101, 61}, ptHeights = {101, 31}, tileWidths = {5000, 50000},
                tileHeights = {5000, 25000}, endianness = {DEMSource.LITTLE_ENDIAN, DEMSource.BIG_ENDIAN};
        double[] resolutions = {50, 1 / 1200.0};


        SRTMMicrodegFileFormatter srtmFormatter = new SRTMMicrodegFileFormatter(tileWidths[demType], tileHeights[demType]);
        WebDataSource demDataSource = demType == HGT_OSGB_LFP ?
                new WebDataSource(lfpUrl,
                        new LFPFileFormatter()) :
                new WebDataSource(srtmUrl, srtmFormatter);


        String[] tileUnits = {"metres", "degrees"};
        FreemapFileFormatter formatter = new FreemapFileFormatter(tilingProj.getID(), "geojson", tileWidths[demType],
                tileHeights[demType]);

        formatter.selectWays("highway");
        formatter.addKeyval("inUnits", tileUnits[demType]);
        if (tileUnits[demType].equals("degrees")) {
            formatter.setMicrodegToDeg(true); // 20180221 web service will receive degrees even though we are using microdegrees - more likely to be compatible with different servers
        }

        WebDataSource osmDataSource = new WebDataSource(osmUrl, formatter);


        Proj4ProjectionFactory factory = new Proj4ProjectionFactory();
        this.tilingProj = tilingProj;
        File cacheDir = new File(android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/hikar/cache/" +
                tilingProj.getID().toLowerCase().replace("epsg:", "") + "/");
        if (!cacheDir.exists())
            cacheDir.mkdirs();

        Log.d("hikar", "OsmDemIntegrator: tilewidth=" + tileWidths[demType] + " tileheight=" +
                tileHeights[demType] + " resolution=" + resolutions[demType] +
                " endianness=" + endianness[demType] + " ptwidth=" + ptWidths[demType] +
                " ptheight=" + ptHeights[demType] + " multiplier=" + multipliers[demType]);

        hgt = new HGTTileDeliverer("dem", demDataSource, new HGTDataInterpreter(
                ptWidths[demType], ptHeights[demType], resolutions[demType],
                endianness[demType]),
                tileWidths[demType], tileHeights[demType], tilingProj, ptWidths[demType],
                ptHeights[demType], resolutions[demType], cacheDir.getAbsolutePath(),
                multipliers[demType]);

        // NW 100215 the OSM data will be sent back from server in epsg:4326 latlon - see FreemapFileFormatter
        // is this correct? yes-it's reprojected by the TileDeliverer
        osm = new CachedTileDeliverer("osm", osmDataSource,
                //new XMLDataInterpreter(new FreemapDataHandler(factory)),
                new GeoJSONDataInterpreter(),
                tileWidths[demType], tileHeights[demType],
                tilingProj,
                cacheDir.getAbsolutePath(), multipliers[demType]);

        // NOTE how the caching works
        // OSM data is cached PRE-REPROJECTION and PRE-applying the dem
        hgt.setCache(true);
        osm.setCache(true);
        osm.setReprojectCachedData(true);
    }

    public boolean needNewData(Point lonLat) {

        return osm.needNewData(lonLat) || hgt.needNewData(lonLat);
    }

    public boolean checkForFailedData(Point lonLat) {
        failedDemOrigins = hgt.getFailedSurroundingTiles(lonLat);
        failedOsmOrigins = osm.getFailedSurroundingTiles(lonLat);
        return !failedDemOrigins.isEmpty() || !failedOsmOrigins.isEmpty();
    }


    public boolean fetchFailedTiles() throws Exception {

        HashMap<String, Tile> demTiles = new HashMap<String, Tile>(), osmTiles = new HashMap<String, Tile>();
        if (!failedDemOrigins.isEmpty()) {
            demTiles = hgt.doUpdateFailedTiles(failedDemOrigins);
        }
        if (!failedOsmOrigins.isEmpty()) {
            osmTiles = osm.doUpdateFailedTiles(failedOsmOrigins);
        }


        if ((demTiles.isEmpty() && osmTiles.isEmpty()) || hgtupdated == null || osmupdated == null) {
            return false;
        }

        for (HashMap.Entry<String, Tile> demTile : demTiles.entrySet()) {
            hgtupdated.put(demTile.getKey(), demTile.getValue());
        }
        for (HashMap.Entry<String, Tile> osmTile : osmTiles.entrySet()) {
            osmupdated.put(osmTile.getKey(), osmTile.getValue());
        }

        for (HashMap.Entry<String, Tile> osmTile : osmupdated.entrySet()) {
            String key = osmTile.getKey();
            FreemapDataset dataset = (FreemapDataset) osmTile.getValue().data;
            Tile demTile = hgtupdated.get(key);
            if (demTile != null && dataset != null) {
                DEM dem = (DEM) demTile.data;
                if (!dataset.isDEMApplied() && dem != null && !dem.isEmptyData()) {
                    dataset.applyDEM(dem);
                }
            }
        }
        return true;
    }


    // ASSUMPTION: the tiling systems for hgt and osm data coincide - which they do here (see constructor)
    public boolean update(Point lonLat) throws Exception {

        try {
            hgtupdated = hgt.doUpdateSurroundingTiles(lonLat);
        } catch (Exception e) {
            hgtupdated = hgt.getPartiallyUpdatedData();
            throw e;
        }

        try {
            osmupdated = osm.doUpdateSurroundingTiles(lonLat);
        } catch (Exception e) {
            osmupdated = osm.getPartiallyUpdatedData();
            throw e;
        }


        for (HashMap.Entry<String, Tile> e : osmupdated.entrySet()) {
            Log.d("hikar", "retrieving for: " + e.getKey());
            if (hgtupdated.get(e.getKey()) != null && osmupdated.get(e.getKey()) != null)
            //&& !e.getValue().isCache)
            {
                FreemapDataset d = (FreemapDataset) e.getValue().data;
                DEM dem = (DEM) (hgtupdated.get(e.getKey()).data);

                Log.d("hikar", "DEM for " + e.getKey() + "=" + dem);
                if (d == null) {
                    Log.d("hikar", "UNEXPECTED!!! FreemapDataset is null!!!");
                }
                d.applyDEM(dem);

                // NOTE should this be commented out??? we presumably want to cache the projected, dem-applied data???
                // yes - this gives xml when we want json
                // osm.cacheByKey(d, e.getKey());

            } else {
                android.util.Log.d("hikar", "osm: is cache, has dem already");
                return false;// NW 290514 test wasn't here already just to try and trap this condition for testing
            }
        }

        return true;
    }

    public double getHeight(Point lonLat) {
        DEM dem = (DEM) hgt.getData();
        Point projectedPoint = tilingProj.project(lonLat);

        if (dem != null) {
            double h = dem.getHeight(projectedPoint.x, projectedPoint.y, tilingProj);
            return h;
        }
        return -1;
    }


    public HashMap<String, Tile> getCurrentOSMTiles() {
        return osmupdated;
    }

    public HashMap<String, Tile> getCurrentDEMTiles() {
        return hgtupdated;
    }
}
