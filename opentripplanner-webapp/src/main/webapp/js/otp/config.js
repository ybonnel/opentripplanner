/**
 * make sure that otp.config.locale is set to your default language
 *
 * NOTE:
 *   - for apps that support a single language simply set your locale here (or in your .html file prior to including config.js)
 *   - for apps that support multiple languages, you will need a scheme that determines the user's desired language,  
 *     which assigns the proper otp.locale.<Language> to otp.config.locale prior to including config.js (this file)
 */
// step 1: make sure we have some type of otp.config, and otp.config.local defined
if(typeof(otp) == "undefined" || otp == null) otp = {};
if(typeof(otp.config) == "undefined" || otp.config == null) otp.config = {};
if(typeof(otp.config.locale) == "undefined" || otp.config.locale == null) otp.config.locale = otp.locale.English;


// step 2: create an object of default otp.config default values (see step3 where we apply this to any existing config)
otp.config_defaults = {
    routerId      : "",
    locale        : otp.config.locale,
    metricsSystem : otp.config.locale.config.metricsSystem,  // Metrics system (e.g., 'english' == feet, miles, other value or null is metric system)

    planner : {
        url            : null,

        // options to turn stuff on / off on the planner
        showWheelchairForm    : true,   // turn on/off the wheelchair check box (on by default)
        showStopIds           : true,   // show stop ids as part of the itinerary
        showPrintButton       : false,  // turn on/off itinerary print button
        showLinksButton       : true,   // turn on/off itinerary links button
        useOptionDependencies : true,   // trip form changes based on mode and optimize flags (e.g., bike mode has no wheelchair or walk distance forms etc...) 
        useRouteLongName      : false,  // format route name with both short-name and long-name...see / override Itinerary.makeRouteName() for different formatting options

        // will add a tree node to the bottom of the itinerary with this message
        itineraryMessages : {
            icon            : null,
            transit         : null,
            bus             : null,
            train           : null,
            bicycle         : null,
            bicycle_transit : null,
            walk            : null 
        },

        linkTemplates  : [
            {name:otp.config.locale.tripPlanner.link.text,  url:'index.html?' + otp.planner.ParamTemplate}, // TODO - this will cause an error if otp.planner is not defined
            {name:otp.config.locale.tripPlanner.link.trip_separator, separator:true},
            {name:otp.config.locale.tripPlanner.link.google_transit, url: otp.config.locale.tripPlanner.link.google_domain + '/maps?<tpl if="arriveBy == \'Arrive\'">ttype=arr&</tpl>date={date}&time={time}&daddr={toLat},{toLon}&saddr={fromLat},{fromLon}&ie=UTF8&dirflg=r'},
            {name:otp.config.locale.tripPlanner.link.bike_separator, separator:true},
            {name:otp.config.locale.tripPlanner.link.google_bikes,   url:otp.config.locale.tripPlanner.link.google_domain + '/maps?daddr={toLat},{toLon}&saddr={fromLat},{fromLon}&ie=UTF8&dirflg=b'},
            {name:otp.config.locale.tripPlanner.link.walk_separator, separator:true},
            {name:otp.config.locale.tripPlanner.link.google_walk,    url:otp.config.locale.tripPlanner.link.google_domain + '/maps?daddr={toLat},{toLon}&saddr={fromLat},{fromLon}&ie=UTF8&dirflg=w'}
        ],

        geocoder  :
        {
            enabled : false,
            url     : "/geocoder/geocode",  
            addressParamName : "address"
        },
        fromToOverride : new Ext.Template('<div class="mapHelp">' + otp.config.locale.config.rightClickMsg + '</div>')

        /* debug */
        /* * /
        geocoder  :
        {
            enabled : true,
            url     : "/js/otp/planner/test/geo-multi.xml",
            addressParamName : "address"
        }
        /* */
    },

    map : {
        // The default extent to zoom the map to when the web app loads.
        // This can either be an OpenLayers.Bounds object or the string "automatic"
        // If set to "automatic", the client will ask the server for the default extent.
        defaultExtent: "automatic",
     
        // These options are passed directly to the OpenLayers.Map constructor.
        options : {
            projection        : new OpenLayers.Projection("EPSG:900913"),
            displayProjection : new OpenLayers.Projection("EPSG:4326"),
            numZoomLevels: 20,
            controls: []
        },

        // Instead of specifying just the base layer options, you can instead
        // specify the full base layer object.
        // The example below creates a new base layer that uses the default OSM
        // tiles.
        baseLayer: new OpenLayers.Layer.OSM({
            url: [
                  "http://a.tah.openstreetmap.org/Tiles/tile/${z}/${x}/${y}.png",
                  "http://b.tah.openstreetmap.org/Tiles/tile/${z}/${x}/${y}.png",
                  "http://c.tah.openstreetmap.org/Tiles/tile/${z}/${x}/${y}.png"
            ],
            numZoomLevels: 20
        }),

        // here's the MapQuest baseMap option for basemap tiles
        // note, the attribution is wrong (leaves out MapQuest info), so it's commented
        MQ_baseLayer: new OpenLayers.Layer.OSM("MapQuest", "http://otile1.mqcdn.com/tiles/1.0.0/osm/${z}/${x}/${y}.png"),


        // NOTE: this object is ignored if a baseLayer (which is an instance of OpenLayers.Layer)
        // config object used in the creation of a new base layer for the map. 
        baseLayerOptions: {
            projection : new OpenLayers.Projection("EPSG:4326"),
            url        : 'http://maps.opengeo.org/geowebcache/service/wms',
            layers     : ['openstreetmap'],
            format     : 'image/png',
            transitionEffect: 'resize'
        }
    },

    // when enabled, adds another item to the accordion for attribution
    attributionPanel : {
        enabled         : false,
        panelTitle      : otp.config.locale.config.attribution.title,
        attributionHtml : '<p class="disclaimer">' + otp.config.locale.config.attribution.content + '</p>'
    },

    // presents a dialog on initial startup of the app, with a message for your customers
    splashScreen : {
        enabled: false,
        timeout: 20,   // seconds to stay open - if <= ZERO, then dialog does not timeout and requires the customer to close the dialog
        title:   'Important: Please read',
        html:    '<p class="splash-screen">'
                 + 'Please note that the trip routing presented here is for demonstration purposes of the <a href="http://opentripplanner.com" target="#">OpenTripPlanner (OTP)</a> only, '
                 + 'and not intended as a travel resource.  You will begin to see improvements in the planned trips as the project matures.  A public beta is scheduled for spring 2011. '
                 + '</p>'
    },

    systemMap : {
        // If true, a system map will be used in the display
        enabled: false,

        // uris and layer names
        // these match up with geoserver
        layerUrlRoutes: 'http://localhost:5180/geoserver/wms',
        layerUrlStops: 'http://localhost:5180/geoserver/wms',
        layerUrlRoutesHighlighted: 'http://localhost:5180/geoserver/wms',
        layerUrlStopsHighlighted: 'http://localhost:5180/geoserver/wms',
        layerNamesRoute: 'routes',
        layerNamesStop: 'stops',
        layerNamesRouteHighlighted: 'routes_highlighted',
        layerNamesStopHighlighted: 'stops_highlighted',
        
        // this is the uri to the extended api that proxies to geoserver
        controlStopsUrl: '/opentripplanner-api-extended/wms'
    },

    // if specified, uri path to a custom logo otherwise use the default "images/ui/logoSmall.png"
    logo: null,

    // List of agency IDs (as specified in GTFS) for which custom icons should be used.
    // Icons should be placed in the custom directory (e.g., custom/nyct)
    // Ex. ['nyct', 'mnr']
    useCustomIconsForAgencies : [],

    // Context menu with trip planning options (e.g., "Start trip here")
    plannerContextMenu : true,

    // Context menu with general map features (e.g., "Center map here")
    mapContextMenu : true,

    CLASS_NAME : "otp.config"
};
try {
    // step 3: apply our default to the existing (possibly empty) otp config
    otp.inherit(otp.config, otp.config_defaults);
    console.log("otp.config updated with default items from otp.config_static");
} catch(e) {
    console.log("ERROR: was unable to run otp.inherid override in config.js - got this exception: " + e);
}
