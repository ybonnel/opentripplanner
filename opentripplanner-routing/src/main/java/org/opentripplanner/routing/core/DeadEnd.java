package org.opentripplanner.routing.core;

import java.util.ArrayList;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import com.vividsolutions.jts.geom.Coordinate;

public class DeadEnd extends OneStreetVertex implements Vertex, StreetIntersectionVertex {

    private static final long serialVersionUID = 8659709448092487563L;

    double x, y;

    public DeadEnd(IntersectionVertex v) {
        x = v.getX();
        y = v.getY();
        inStreet = v.inStreet;
        outStreet = v.outStreet;
        inStreet.setToVertex(this);
        outStreet.setFromVertex(this);
    }

    @Override
    public void addIncoming(Edge ee) {
       throw new NotImplementedException();
    }

    @Override
    public void addOutgoing(Edge ee) {
        throw new NotImplementedException();
    }

    @Override
    public double distance(Vertex v) {

        double xd = v.getX() - x;
        double yd = v.getY() - y;
        return Math.sqrt(xd * xd + yd * yd) * GenericVertex.METERS_PER_DEGREE_AT_EQUATOR * GenericVertex.COS_MAX_LAT;
    }

    @Override
    public double distance(Coordinate c) {

        double xd = c.x - x;
        double yd = c.y - y;
        return Math.sqrt(xd * xd + yd * yd) * GenericVertex.METERS_PER_DEGREE_AT_EQUATOR * GenericVertex.COS_MAX_LAT;
    }

    @Override
    public Coordinate getCoordinate() {
        return new Coordinate(x, y);
    }

    @Override
    public int getDegreeIn() {
        return inStreet != null ? 1 : 0;
    }

    @Override
    public int getDegreeOut() {
        return outStreet != null ? 1 : 0;
    }

    @Override
    public Iterable<Edge> getIncoming() {
        ArrayList<Edge> edges = new ArrayList<Edge>();
        edges.add(inStreet);
        return edges;
    }

    @Override
    public String getLabel() {
        return "DeadEnd(" + x + "," + y + ")";
    }

    @Override
    public String getName() {
        return getLabel();
    }

    @Override
    public Iterable<Edge> getOutgoing() {
        ArrayList<Edge> edges = new ArrayList<Edge>();
        edges.add(outStreet);
        return edges;
    }

    @Override
    public String getStopId() {
        return null;
    }

    @Override
    public double getX() {
        return x;
    }

    @Override
    public double getY() {
        return y;
    }
}