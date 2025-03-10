/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.graph_builder.impl.shapefile;

import static org.opentripplanner.common.IterableLibrary.filter;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import junit.framework.TestCase;

import org.junit.Test;
import org.onebusaway.gtfs.impl.calendar.CalendarServiceImpl;
import org.onebusaway.gtfs.model.calendar.CalendarServiceData;
import org.opentripplanner.common.geometry.GeometryUtils;
import org.opentripplanner.graph_builder.impl.GtfsGraphBuilderImpl;
import org.opentripplanner.graph_builder.model.GtfsBundle;
import org.opentripplanner.graph_builder.model.GtfsBundles;
import org.opentripplanner.graph_builder.services.DisjointSet;
import org.opentripplanner.routing.algorithm.AStar;
import org.opentripplanner.routing.algorithm.Dijkstra;
import org.opentripplanner.routing.algorithm.strategies.TrivialRemainingWeightHeuristic;
import org.opentripplanner.routing.contraction.ContractionHierarchy;
import org.opentripplanner.routing.contraction.ContractionHierarchySet;
import org.opentripplanner.routing.contraction.Shortcut;
import org.opentripplanner.routing.core.DirectEdge;
import org.opentripplanner.routing.core.Edge;
import org.opentripplanner.routing.core.Graph;
import org.opentripplanner.routing.core.GraphVertex;
import org.opentripplanner.routing.core.OptimizeType;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.core.TraverseOptions;
import org.opentripplanner.routing.core.Vertex;
import org.opentripplanner.routing.edgetype.EndpointVertex;
import org.opentripplanner.routing.edgetype.FreeEdge;
import org.opentripplanner.routing.edgetype.SimpleEdge;
import org.opentripplanner.routing.edgetype.StreetTraversalPermission;
import org.opentripplanner.routing.edgetype.StreetVertex;
import org.opentripplanner.routing.edgetype.TurnEdge;
import org.opentripplanner.routing.edgetype.loader.NetworkLinker;
import org.opentripplanner.routing.impl.GraphSerializationLibrary;
import org.opentripplanner.routing.spt.BasicShortestPathTree;
import org.opentripplanner.routing.spt.GraphPath;
import org.opentripplanner.routing.spt.ShortestPathTree;
import org.opentripplanner.util.TestUtils;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.index.strtree.STRtree;

class ForbiddenEdge extends FreeEdge {
    private static final long serialVersionUID = 1L;

    public ForbiddenEdge(Vertex from, Vertex to) {
        super(from, to);
    }

    @Override
    public State traverse(State s0) {
        return null;
    }
    
}

public class TestContractionHeirarchies extends TestCase {

    @Test
    public void testBasic() {
        /* Somewhere in the Arabian sea.
         * Low latitude avoids potential weirdness from the earth's curvature:
         * the tests assume a perfectly square grid.
         * Positive latitude and longitude for clarity.  
         */
        final double LAT0 = 01.0;
        final double LON0 = 65.0;
        final double STEP = 0.001;
        // number of rows and columns in grid
        final int N = 10;

        Graph graph = new Graph();
        // create a NxN grid of vertices
        Vertex[][] verticesIn = new Vertex[N][];
        Vertex[][] verticesOut = new Vertex[N][];
        for (int y = 0; y < N; ++y) {
            verticesIn[y] = new Vertex[N];
            verticesOut[y] = new Vertex[N];
            for (int x = 0; x < N; ++x) {
                double xc = x * STEP + LON0;
                double yc = y * STEP + LAT0;
                Vertex in = new EndpointVertex("(" + x + ", " + y + ") in", xc, yc);
                graph.addVertex(in);
                verticesIn[y][x] = in;

                Vertex out = new EndpointVertex("(" + x + ", " + y + ") out", xc, yc);
                graph.addVertex(out);
                verticesOut[y][x] = out;
            }
        }


        /*
         * (i, j) iteration variables are used for: 
         * (y, x) and (lat, lon) respectively when making west-east streets
         * (x, y) and (lon, lat) respectively when making south-north streets
         * 
         * verticesOut are connected to all StreetVertex leading away from the given grid point
         * verticesIn are connected to all StreetVertex leading toward the given grid point
         * Note: this means that in a search, the last TurnEdge at the destination will not be traversed 
         */
        for (int i = 0; i < N; ++i) {
            for (int j = 0; j < N - 1; ++j) {
                double lon = j * STEP + LON0;
                double lat = i * STEP + LAT0;
                double d = 111.111;
                LineString geometry = GeometryUtils.makeLineString(lon, lat, lon + STEP, lat);
                StreetVertex we = new StreetVertex("a(" + j + ", " + i + ")", geometry, "", d, false, null);
                StreetVertex ew = new StreetVertex("a(" + j + ", " + i + ")", (LineString) geometry.reverse(), "", d, true, null);
                
                graph.addVertex(we);
                graph.addVertex(ew);

                lon = i * STEP + LON0;
                lat = j * STEP + LAT0;
                d = 111.111;
                geometry = GeometryUtils.makeLineString(lon, lat, lon, lat + STEP);
                StreetVertex sn = new StreetVertex("d(" + i + ", " + j + ")", geometry, "", d, false, null);
                StreetVertex ns = new StreetVertex("d(" + i + ", " + j + ")", (LineString) geometry.reverse(), "", d, true, null);

                graph.addVertex(sn);
                graph.addVertex(ns);
                
                graph.addEdge(new FreeEdge(verticesOut[i][j], we));
                graph.addEdge(new FreeEdge(verticesOut[j][i], sn));
                
                graph.addEdge(new FreeEdge(verticesOut[i][j + 1], ew));
                graph.addEdge(new FreeEdge(verticesOut[j + 1][i], ns));
                
                graph.addEdge(new FreeEdge(ew, verticesIn[i][j]));
                graph.addEdge(new FreeEdge(ns, verticesIn[j][i]));
            
                graph.addEdge(new FreeEdge(we, verticesIn[i][j + 1]));
                graph.addEdge(new FreeEdge(sn, verticesIn[j + 1][i]));
                
                assertEquals(we, graph.addVertex(we));
                assertEquals(ew, graph.addVertex(ew));
                assertEquals(sn, graph.addVertex(sn));
                assertEquals(ns, graph.addVertex(ns));
            }
        }

        for (int y = 0; y < N; ++y) {
            for (int x = 0; x < N; ++x) {
                Vertex vertexIn = verticesIn[y][x];
                for (DirectEdge e1: filter(graph.getIncoming(vertexIn),DirectEdge.class)) {
                    Vertex vertexOut = verticesOut[y][x];
                    StreetVertex fromv = (StreetVertex) e1.getFromVertex();
                    for (DirectEdge e2: filter(graph.getOutgoing(vertexOut),DirectEdge.class)) {
                        StreetVertex tov = (StreetVertex) e2.getToVertex();
                        if (tov.getEdgeId().equals(fromv.getEdgeId())) {
                            continue;
                        }
                        graph.addEdge(new TurnEdge(fromv, tov));
                    }
                    assertTrue(graph.getDegreeOut(fromv) <= 4);
                }
            }
        }
        
        final int graphSize = N * N * 2 + (N * (N - 1) * 4);
        assertEquals(graphSize, graph.getVertices().size());
        
        // test Dijkstra
        TraverseOptions options = new TraverseOptions();
        options.optimizeFor = OptimizeType.QUICK;
        options.walkReluctance = 1;
        options.speed = 1;

        // test hop limit
        Dijkstra dijkstra = new Dijkstra(graph, verticesOut[0][0], options, graph.getVertex("a(0, 0)"), 3);
        BasicShortestPathTree spt = dijkstra.getShortestPathTree(verticesIn[0][2], 4);
        State v03 = spt.getState(verticesIn[0][3]);
        assertNull(v03);

        dijkstra = new Dijkstra(graph, verticesOut[0][0], options, graph.getVertex("a(0, 0)"), 6);
        spt = dijkstra.getShortestPathTree(verticesIn[0][3], 500);
        v03 = spt.getState(verticesIn[0][3]);
        assertNotNull(v03);

        // test distance limit
        dijkstra = new Dijkstra(graph, verticesOut[0][1], options, verticesIn[0][2]);
        spt = dijkstra.getShortestPathTree(verticesIn[0][3], 20);
        v03 = spt.getState(verticesIn[0][3]);
        assertNull(v03);

        dijkstra = new Dijkstra(graph, verticesOut[0][1], options, verticesIn[0][2]);
        spt = dijkstra.getShortestPathTree(verticesIn[0][3], 130);
        v03 = spt.getState(verticesIn[0][3]);
        assertNotNull(v03);
        
        // test getShortcuts
        
        ContractionHierarchy testch = new ContractionHierarchy(graph, new TraverseOptions(TraverseMode.WALK, OptimizeType.QUICK), 0.0);
        Vertex v = graph.getVertex("a(2, 2)");
        List<Shortcut> shortcuts = testch.getShortcuts(v, true).shortcuts;
        
        assertEquals(16, shortcuts.size());
        
        v = graph.getVertex("(0, 0) in");
        shortcuts = testch.getShortcuts(v, true).shortcuts;
        assertEquals(0, shortcuts.size());

        // test hierarchy construction
        ContractionHierarchy hierarchy = new ContractionHierarchy(graph, new TraverseOptions(TraverseMode.WALK, OptimizeType.QUICK), 1.0);

        assertTrue(hierarchy.down.getVertices().size() == graphSize);
        assertTrue(hierarchy.up.getVertices().size() == graphSize);
        assertTrue(hierarchy.graph.getVertices().size() == 0);

        System.out.println("Contracted");


        // test query
        GraphPath path = hierarchy.getShortestPath(verticesOut[0][0], verticesIn[N - 1][N - 1], 1000000000,
                options);
        assertNotNull(path);

        assertEquals((N - 1) * 2 + 2, path.states.size());


        // test that path is coherent
        for (int i = 0; i < path.states.size() - 1; ++i) {
        	State s = path.states.get(i);
            Vertex sv = s.getVertex();
            Edge e = path.edges.get(i);
            assertSame(e.getFromVertex(), sv);
        }


        options = new TraverseOptions();
        options.optimizeFor = OptimizeType.QUICK;
        options.speed = 1;
        // Turn off remainging weight heuristic: Unless latitude is very low, heuristic will sometimes 
        // lead algorithm to attempt to reduce distance incorrectly via FreeEdges 
        options.remainingWeightHeuristic = new TrivialRemainingWeightHeuristic();
        for (int x1 = 0; x1 < N; ++x1) {
            for (int y1 = 0; y1 < N; ++y1) {
                for (int x2 = 0; x2 < N; ++x2) {
                    for (int y2 = 0; y2 < N; ++y2) {
                        if (x1 == x2 && y1 == y2) {
                            continue;
                        }
                        options.setArriveBy(false);
                        path = hierarchy.getShortestPath(verticesOut[y1][x1], verticesIn[y2][x2], 1000000000,
                                options);

                        assertNotNull(path);
                        assertEquals(Math.abs(x1 - x2) + Math.abs(y1 - y2) + 2, path.states.size());
                        
                        options.setArriveBy(true);
                        path = hierarchy.getShortestPath(verticesOut[y1][x1], verticesIn[y2][x2], 1000000000,
                                options);

                        assertNotNull(path);
                        assertEquals(Math.abs(x1 - x2) + Math.abs(y1 - y2) + 2, path.states.size());
                    }
                }
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUDG() {
        final int N = 50;

        Graph graph = new Graph();
        STRtree tree = new STRtree();

        Random random = new Random(1);

        ArrayList<Vertex> vertices = new ArrayList<Vertex>(N);
        for (int i = 0; i < N; ++i) {
            double x = random.nextDouble() * 1000;
            double y = random.nextDouble() * 1000;
            Vertex v = new EndpointVertex("(" + x + ", " + y + ")", x, y);
            graph.addVertex(v);
            Envelope env = new Envelope(v.getCoordinate());
            tree.insert(env, v);
            vertices.add(v);
        }

        int expansion = 1;
        for (GraphVertex gv : graph.getVertices()) {
            Vertex v = gv.vertex;
            final Coordinate c = v.getCoordinate();
            Envelope env = new Envelope(c);
            env.expandBy(50 * expansion);
            List<Vertex> nearby = tree.query(env);
            while (nearby.size() < 7) {
                env.expandBy(50);
                expansion += 1;
                nearby = tree.query(env);
            }
            Collections.sort(nearby, new Comparator<Vertex>() {
                public int compare(Vertex a, Vertex b) {
                    return (int) (a.distance(c) - b.distance(c));
                }
            });
            for (Vertex n : nearby.subList(1, 6)) {
                graph.addEdge(new FreeEdge(v, n));
                graph.addEdge(new FreeEdge(n, v));
            }
            Vertex badTarget = nearby.get(6);
            graph.addEdge(new ForbiddenEdge(badTarget, v));
        }

        // ensure that graph is connected
        DisjointSet<Vertex> components = new DisjointSet<Vertex>();
        Vertex last = null;
        for (Vertex v : vertices) {
            for (DirectEdge e: filter(graph.getOutgoing(v),DirectEdge.class)) {
                components.union(v, e.getToVertex());
            }
            last = v;
        }
        int lastKey = components.find(last);
        for (Vertex v : vertices) {
            int key = components.find(v);
            if (key != lastKey) {
                lastKey = components.union(v, last);
                last = v;
                Coordinate c = v.getCoordinate();
                graph.addEdge(new SimpleEdge(v, last, last.distance(c), 0));
                graph.addEdge(new SimpleEdge(last, v, last.distance(c), 0));
            }
        }

        ContractionHierarchy hierarchy = new ContractionHierarchy(graph, new TraverseOptions(TraverseMode.WALK, OptimizeType.QUICK), 1.0);

        TraverseOptions options = new TraverseOptions();
        options.optimizeFor = OptimizeType.QUICK;
        options.walkReluctance = 1;
        options.speed = 1;
        GraphPath path = hierarchy.getShortestPath(vertices.get(0), vertices.get(1), 0, options);
        assertNotNull(path);
        assertTrue(path.states.size() > 1);
        
        long now = System.currentTimeMillis();
        for (Vertex start : vertices) {
            int j = (int) (Math.random() * vertices.size());
            Vertex end = vertices.get(j);
            if (start == end) {
                continue;
            }
            GraphPath path2 = hierarchy.getShortestPath(start, end, 0, options);
            assertNotNull(path2);
        }
        System.out.println("time per query: " + (System.currentTimeMillis() - now) / 1000.0 / N);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testNYC() throws Exception {
    	// be sure this date matches your subway gtfs validity period
    	// it could be derived from the Graph's validity dates
        long startTime = TestUtils.dateInSeconds(2009, 10, 11, 12, 0, 0);
        GraphPath path;
        Graph graph = new Graph();
        ContractionHierarchy hierarchy;
      
        URL resource = getClass().getResource("nyc_streets/streets.shp");
        File file = null;
        if (resource != null) {
            file = new File(resource.getFile());
        } 
        if (file == null || !file.exists()) {
            System.out
                    .println("No New York City basemap; skipping; see comment in TestShapefileStreetGraphBuilderImpl for details");
            return;
        }

        ShapefileFeatureSourceFactoryImpl factory = new ShapefileFeatureSourceFactoryImpl(file);

        ShapefileStreetSchema schema = new ShapefileStreetSchema();
        schema.setIdAttribute("SegmentID");
        schema.setNameAttribute("Street");

        // only featuretyp=0 are streets
        CaseBasedBooleanConverter selector1 = new CaseBasedBooleanConverter("FeatureTyp", false);
        HashMap<String, Boolean> streets = new HashMap<String, Boolean>();
        streets.put("0", true);
        selector1.setValues(streets);

        // also, streets are sometime duplicated
        NullBooleanConverter selector2 = new NullBooleanConverter("SAFStPlace", true);

        schema.setFeatureSelector(new CompositeBooleanConverter(selector1, selector2));

        CaseBasedTraversalPermissionConverter perms = new CaseBasedTraversalPermissionConverter(
                "TrafDir", StreetTraversalPermission.PEDESTRIAN_AND_BICYCLE);

        perms.addPermission("W", StreetTraversalPermission.ALL,
                StreetTraversalPermission.PEDESTRIAN);
        perms.addPermission("A", StreetTraversalPermission.PEDESTRIAN,
                StreetTraversalPermission.ALL);
        perms.addPermission("T", StreetTraversalPermission.ALL, StreetTraversalPermission.ALL);

        schema.setPermissionConverter(perms);

        ShapefileStreetGraphBuilderImpl loader = new ShapefileStreetGraphBuilderImpl();
        loader.setFeatureSourceFactory(factory);
        loader.setSchema(schema);

        loader.buildGraph(graph);       
        
        // load gtfs 

        resource = getClass().getResource("subway.zip");
        file = null;
        if (resource != null) {
            file = new File(resource.getFile());
        } 
        if (file == null || !file.exists()) {
            System.out
                    .println("No New York City subway GTFS; skipping; see comment in TestShapefileStreetGraphBuilderImpl for details");
            return;
        }

        GtfsGraphBuilderImpl gtfsBuilder = new GtfsGraphBuilderImpl();
        GtfsBundle bundle = new GtfsBundle();
        bundle.setPath(file);

        ArrayList<GtfsBundle> bundleList = new ArrayList<GtfsBundle>();
        bundleList.add(bundle); 
        GtfsBundles bundles = new GtfsBundles();
        bundles.setBundles(bundleList);
        gtfsBuilder.setGtfsBundles(bundles);
        
        gtfsBuilder.buildGraph(graph);

        NetworkLinker nl = new NetworkLinker(graph);
        nl.createLinkage();

        TraverseOptions options = new TraverseOptions();
        options.setModes(new TraverseModeSet(TraverseMode.WALK, TraverseMode.SUBWAY));
        options.optimizeFor = OptimizeType.QUICK;
        
        CalendarServiceData data = graph.getService(CalendarServiceData.class);
        assertNotNull(data);
        CalendarServiceImpl calendarService = new CalendarServiceImpl();
        calendarService.setData(data);
        options.setCalendarService(calendarService);
        options.setServiceDays(startTime);
        options.setTransferTable(graph.getTransferTable());
        
        Vertex start1 = graph.getVertex("0072480");
        Vertex end1 = graph.getVertex("0032341");

        assertNotNull(end1);
        assertNotNull(start1);
        
        ShortestPathTree shortestPathTree = AStar.getShortestPathTree(graph, start1, end1, startTime, options);
        path = shortestPathTree.getPath(end1, true);
        assertNotNull(path);

        boolean subway1 = false;
        for (State state : path.states) {
        	if (state.getBackEdge() == null) 
        		continue;
        	System.out.println(state.getBackEdgeNarrative().getMode());
            if (TraverseMode.SUBWAY.equals(state.getBackEdgeNarrative().getMode())) {
                subway1 = true;
                break;
            }
        }
        assertTrue("Path must take subway", subway1);

        ContractionHierarchySet chs = new ContractionHierarchySet();
        chs.addTraverseOptions(new TraverseOptions(TraverseMode.WALK, OptimizeType.QUICK));
        chs.setContractionFactor(0.50);
        chs.setGraph(graph);
        chs.build();
        
        GraphSerializationLibrary.writeGraph(chs, new File("/tmp/contracted"));

        chs = new GraphSerializationLibrary().readGraph(new File("/tmp/contracted"));
        hierarchy = chs.getHierarchy(options);
        assertNotNull(hierarchy);
        
        // find start and end vertices
        Vertex start = null;
        Vertex end = null;

        start = hierarchy.up.getVertex("0072480");
        end = hierarchy.up.getVertex("0032341");
        if (start == null) start = hierarchy.down.getVertex("0072480");
        if (start == null) start = hierarchy.graph.getVertex("0072480");
        if (end == null) end = hierarchy.down.getVertex("0032341");
        if (end == null) end = hierarchy.graph.getVertex("0032341");
        
        assertNotNull(start);
        assertNotNull(end);
        
        path = hierarchy.getShortestPath(start, end, 0, options);
        assertNotNull(path);

        GraphPath pathWithSubways = hierarchy.getShortestPath(start, end, startTime, options);
        assertNotNull(pathWithSubways);
        boolean subway = false;
        for (State state : pathWithSubways.states) {
        	if (state.getBackEdge() == null) 
        		continue;
            if (TraverseMode.SUBWAY.equals(state.getBackEdgeNarrative().getMode())) {
                subway = true;
                break;
            }
        }
        assertTrue("Path must take subway", subway);

        
        //try reverse routing
        options.setArriveBy(true);
        pathWithSubways = hierarchy.getShortestPath(start, end, startTime, options);
        assertNotNull("Reverse path must be found", pathWithSubways);
        subway = false;
        for (State state : pathWithSubways.states) {
        	if (state.getBackEdge() == null) 
        		continue;
            if (TraverseMode.SUBWAY.equals(state.getBackEdgeNarrative().getMode())) {
                subway = true;
                break;
            }
        }
        assertTrue("Reverse path must take subway", subway);

        options.setArriveBy(false);
        
        // test max time 
        options.worstTime = startTime + 60 * 90; //an hour and a half is too much time

        path = hierarchy.getShortestPath(start, end, startTime,
                options);
        assertNotNull(path);
            
        options.worstTime = startTime + 60; //but one minute is not enough

        path = hierarchy.getShortestPath(start, end, startTime,
                options);
        assertNull(path);        
        
        
        long now = System.currentTimeMillis();
        int i = 0;
        int notNull = 0;
        Collection<GraphVertex> upVertices = hierarchy.up.getVertices();
        ArrayList<GraphVertex> vertices = new ArrayList<GraphVertex>(upVertices);
        vertices.addAll(hierarchy.down.getVertices());
        vertices.addAll(hierarchy.graph.getVertices());
               
        DisjointSet<Vertex> components = new DisjointSet<Vertex>();
        for (GraphVertex gv : vertices) {
            for (DirectEdge e: filter(gv.getOutgoing(), DirectEdge.class)) {
                components.union(gv.vertex, e.getToVertex());
            }
        }
        
        ArrayList<GraphVertex> verticesOut = new ArrayList<GraphVertex>();
        for (GraphVertex gv : vertices) {
            Vertex v = gv.vertex;
            int componentSize = components.size(components.find(v));
            if (componentSize > upVertices.size() / 2) {
                if (gv.getDegreeOut() != 0) {
                    verticesOut.add(gv);
                }
            }
        }
        
        assertTrue(verticesOut.size() > 0);
        
        //nyc has islands
        assertTrue(vertices.size() > verticesOut.size());
        vertices = verticesOut;
        
        Random random = new Random(0);
        
        for (GraphVertex gv1 : vertices) {
            Vertex v1 = gv1.vertex;
            if (++i == 100) {
                //only look at 100 pairs of vertices
                break; 
            }
            if (hierarchy.up.getVertex(v1.getLabel()) == null) {
                --i;
                continue;
            }
            GraphVertex gv2 = null;
            Vertex v2 = null;
            while (v2 == null || gv2.getDegreeIn() == 0) {
                int j = Math.abs(random.nextInt()) % vertices.size();
                gv2 = vertices.get(j);
                v2 = gv2.vertex;
                if (v1.getLabel() == v2.getLabel()) {
                    continue;
                }
                
                if (hierarchy.down.getVertex(v2.getLabel()) == null) {
                    v2 = null;
                    continue;
                }
            }
            options.setArriveBy(i % 2 == 0); //half of all trips will be reverse trips just for fun
            assertNotNull(v1);
            assertNotNull(v2);
            GraphPath path2 = hierarchy.getShortestPath(v1, v2, startTime, options);
            //assertNotNull(path2);
            if (path2 != null) {
                notNull += 1;
            }
        }

        System.out.println("not null: " + notNull + " of " + i);
        System.out.println("time for 100 shortest paths (on a not-particularly-contracted graph): " + (System.currentTimeMillis() - now) / 1000.0);
        
        //occasionally, a few paths will be null because they start on bridges going out of the city
        assertTrue(notNull / (float) i > 0.95); 
    }

}
