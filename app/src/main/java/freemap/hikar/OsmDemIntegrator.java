package freemap.hikar;

import freemap.data.Projection;
//import freemap.datasource.CachedTileDeliverer;
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
import java.util.HashMap;
import android.util.Log;



public class OsmDemIntegrator {

	CachedTileDeliverer osm;
	HGTTileDeliverer hgt;
	Projection tilingProj;
	HashMap<String,Tile> hgtupdated, osmupdated;
	int demType;
	double[] multipliers = { 1, 1000000 };
	
	public static final int HGT_OSGB_LFP = 0, HGT_SRTM = 1;
	
	public OsmDemIntegrator(Projection tilingProj)
	{
	    this (tilingProj, HGT_OSGB_LFP, "http://www.free-map.org.uk/downloads/lfp/",
	            "http://www.free-map.org.uk/ws/", "http://www.free-map.org.uk/fm/ws/");
	}
	
	public OsmDemIntegrator(Projection tilingProj, int demType,
	                            String lfpUrl, String srtmUrl, String osmUrl)
	{
	    this.demType = demType;
	    
	    int[] ptWidths = { 101, 61 }, ptHeights = { 101, 31 }, tileWidths = { 5000, 50000 },
                tileHeights = { 5000, 25000 }, endianness = { DEMSource.LITTLE_ENDIAN, DEMSource.BIG_ENDIAN };
        double[] resolutions = { 50, 1 / 1200.0 };
        
        Log.d("hikar", "HELLO");
		WebDataSource demDataSource= demType==HGT_OSGB_LFP ?
		        new WebDataSource(lfpUrl, 
				new LFPFileFormatter()):
				  new WebDataSource(srtmUrl,
				new SRTMMicrodegFileFormatter("srtm2.php", tileWidths[demType], tileHeights[demType]));
		
	
		String[] tileUnits = { "metres", "microdeg" };
		FreemapFileFormatter formatter=new FreemapFileFormatter(tilingProj.getID(), "geojson", tileWidths[demType],
		                                                        tileHeights[demType]);
        formatter.setScript("bsvr2.php");
        formatter.selectWays("highway");
        formatter.addKeyval("inUnits", tileUnits[demType]); // used to tell server that bbox is in microdeg
        
        WebDataSource osmDataSource=new WebDataSource(osmUrl,formatter);
        
        
        Log.d("hikar", "url=" + formatter.format(new Point(400000,100000)));
        Proj4ProjectionFactory factory=new Proj4ProjectionFactory();
		this.tilingProj = tilingProj;
		File cacheDir = new File(android.os.Environment.getExternalStorageDirectory().getAbsolutePath()+"/hikar/cache/" +
		        tilingProj.getID().toLowerCase().replace("epsg:","")+"/");
		if(!cacheDir.exists())
		    cacheDir.mkdirs();
		
		Log.d("hikar","OsmDemIntegrator: tilewidth=" + tileWidths[demType] + " tileheight=" +
		        tileHeights[demType] + " resolution=" + resolutions[demType] +
		            " endianness=" + endianness[demType] + " ptwidth=" + ptWidths[demType] +
		            " ptheight=" + ptHeights[demType] + " multiplier=" + multipliers[demType]);
		
		hgt=new HGTTileDeliverer("dem",demDataSource, new HGTDataInterpreter(
		                ptWidths[demType],ptHeights[demType],resolutions[demType],
		                                    endianness[demType]),
						tileWidths[demType], tileHeights[demType], tilingProj,ptWidths[demType],
						ptHeights[demType],resolutions[demType],cacheDir.getAbsolutePath(),
						multipliers[demType]);
			
		// NW 100215 the OSM data will be sent back from server in epsg:4326 latlon - see FreemapFileFormatter
		// is this correct? yes-it's reprojected by the TileDeliverer
		osm = new CachedTileDeliverer("osm",osmDataSource, 
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
	
	public boolean needNewData(Point lonLat)
	{
	   
	    return osm.needNewData(lonLat) || hgt.needNewData(lonLat);
	}
	
	// ASSUMPTION: the tiling systems for hgt and osm data coincide - which they do here (see constructor)
	public boolean update(Point lonLat) throws Exception
	{
	    
	    hgtupdated = hgt.doUpdateSurroundingTiles(lonLat);
	   
		
	       
	   
	   osmupdated = osm.doUpdateSurroundingTiles(lonLat);
	   
	   
	    for(HashMap.Entry<String,Tile> e: osmupdated.entrySet())
		{
			if(hgtupdated.get(e.getKey()) !=null && osmupdated.get(e.getKey()) != null)
			    //&& !e.getValue().isCache)
			{
			   FreemapDataset d = (FreemapDataset)e.getValue().data;
			   DEM dem = (DEM)(hgtupdated.get(e.getKey()).data);
	          
			   //System.out.println("DEM for " + e.getKey() + "=" + dem);
			   d.applyDEM(dem); 
			 
			   // NOTE should this be commented out??? we presumably want to cache the projected, dem-applied data???
			   // yes - this gives xml when we want json
			  // osm.cacheByKey(d, e.getKey());

			}
			else
			{
			   android.util.Log.d("hikar","osm: is cache, has dem already");
			   return false;// NW 290514 test wasn't here already just to try and trap this condition for testing
			}
	    }
			
	  
		
		return true;
	}
	
	public double getHeight(Point lonLat)
	{
		DEM dem = (DEM) hgt.getData();
		Point projectedPoint = tilingProj.project(lonLat);
		
		if(dem!=null)
		{
			double h=dem.getHeight(projectedPoint.x,projectedPoint.y,tilingProj);
			return h;
		}
		return -1;
	}
	
	public void forceDownload(Point bottomLeft, Point topRight) throws Exception
	{
		osm.forceDownload(bottomLeft, topRight);
	}
	
	public DEM getCurrentDEM()
	{
		return (DEM)hgt.getData();
	}
	
	
	public FreemapDataset getCurrentOSMData()
	{
		return (FreemapDataset)osm.getData();    
	}
	
	
	public HGTTileDeliverer getDEM()
    {
        return hgt;
    }
	
	public HashMap<String, Tile> getCurrentOSMTiles()
	{
	    return osmupdated;
	}
	
	public HashMap<String, Tile> getCurrentDEMTiles()
	{
	    return hgtupdated;
	}
}
