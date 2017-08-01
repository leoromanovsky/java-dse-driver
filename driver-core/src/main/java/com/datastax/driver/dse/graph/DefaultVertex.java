/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.driver.dse.graph;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;

import java.util.Iterator;

/**
 * A default representation of a vertex in DSE graph.
 */
class DefaultVertex extends DefaultElement implements Vertex {

    private static final Function<GraphNode, VertexProperty> GRAPH_NODE_TO_VERTEX_PROPERTY = new Function<GraphNode, VertexProperty>() {
        @Override
        public VertexProperty apply(GraphNode input) {
            return input.asVertexProperty();
        }
    };

    DefaultVertex() {
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof Vertex && super.equals(o);
    }

    @Override
    public VertexProperty getProperty(String name) {
        if (properties == null || properties.isEmpty() || !properties.containsKey(name))
            return null;
        Iterator<VertexProperty> iterator = getProperties(name);
        if (!iterator.hasNext())
            return null;
        return iterator.next();
    }

    @Override
    public Iterator<VertexProperty> getProperties(String name) {
        if (properties == null || properties.isEmpty() || !properties.containsKey(name))
            return ImmutableSet.<VertexProperty>of().iterator();
        return Iterators.unmodifiableIterator(Iterators.transform(properties.get(name).iterator(), GRAPH_NODE_TO_VERTEX_PROPERTY));
    }

    @Override
    public Iterator<VertexProperty> getProperties() {
        if (properties == null || properties.isEmpty())
            return ImmutableSet.<VertexProperty>of().iterator();
        return Iterators.unmodifiableIterator(Iterators.transform(properties.values().iterator(), GRAPH_NODE_TO_VERTEX_PROPERTY));
    }

    @Override
    public String toString() {
        return "Vertex{" +
                "id=" + id +
                ", label='" + label + '\'' +
                ", properties=" + properties +
                '}';
    }
}
