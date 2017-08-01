/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.driver.dse.graph;

/**
 * A default representation of an edge in DSE Graph.
 */
class DefaultEdge extends DefaultElement implements Edge {

    GraphNode inV;

    String inVLabel;

    GraphNode outV;

    String outVLabel;

    DefaultEdge() {
    }

    @Override
    public GraphNode getInV() {
        return inV;
    }

    @Override
    public String getInVLabel() {
        return inVLabel;
    }

    @Override
    public GraphNode getOutV() {
        return outV;
    }

    @Override
    public String getOutVLabel() {
        return outVLabel;
    }

    @Override
    public String toString() {
        return "Edge{" +
                "id=" + id +
                ", label='" + label + '\'' +
                ", properties=" + properties +
                ", inV=" + inV +
                ", inVLabel='" + inVLabel + '\'' +
                ", outV=" + outV +
                ", outVLabel='" + outVLabel + '\'' +
                '}';
    }
}
