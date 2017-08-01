/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.driver.dse.graph;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Multimap;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

/**
 * Base class for elements (vertices, edges and vertex properties).
 */
abstract class DefaultElement implements Element {

    private static final Function<GraphNode, Property> GRAPH_NODE_TO_PROPERTY = new Function<GraphNode, Property>() {
        @Override
        public Property apply(GraphNode input) {
            return input.asProperty();
        }
    };

    GraphNode id;

    String label;

    Multimap<String, GraphNode> properties;

    DefaultElement() {
    }

    @Override
    public GraphNode getId() {
        return id;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public Set<String> getPropertyNames() {
        if (properties == null || properties.isEmpty())
            return Collections.emptySet();
        return Collections.unmodifiableSet(properties.keySet());
    }

    @Override
    public Property getProperty(String name) {
        if (properties == null || properties.isEmpty() || !properties.containsKey(name))
            return null;
        Iterator<? extends Property> iterator = getProperties(name);
        if (!iterator.hasNext())
            return null;
        return iterator.next();
    }

    @Override
    public Iterator<? extends Property> getProperties(String name) {
        if (properties == null || properties.isEmpty() || !properties.containsKey(name))
            return ImmutableSet.<Property>of().iterator();
        return Iterators.unmodifiableIterator(Iterators.transform(properties.get(name).iterator(), GRAPH_NODE_TO_PROPERTY));
    }

    @Override
    public Iterator<? extends Property> getProperties() {
        if (properties == null || properties.isEmpty())
            return ImmutableSet.<Property>of().iterator();
        return Iterators.unmodifiableIterator(Iterators.transform(properties.values().iterator(), GRAPH_NODE_TO_PROPERTY));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Element)) return false;
        Element that = (Element) o;
        // we compare only ids to be consistent with Tinkerpop
        return Objects.equal(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }
}
