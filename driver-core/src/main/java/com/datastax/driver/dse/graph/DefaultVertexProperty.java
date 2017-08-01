/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.driver.dse.graph;

import com.google.common.base.Objects;

class DefaultVertexProperty extends DefaultElement implements VertexProperty {

    GraphNode value;

    Vertex parent;

    DefaultVertexProperty() {
    }

    public String getName() {
        return getLabel();
    }

    @Override
    public Vertex getParent() {
        return parent;
    }

    @Override
    public GraphNode getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VertexProperty)) return false;
        if (!super.equals(o)) return false;
        VertexProperty that = (VertexProperty) o;
        return Objects.equal(getValue(), that.getValue()) &&
                Objects.equal(getParent(), that.getParent());
    }

    @Override
    public int hashCode() {
        // getParent() deliberately left out for faster hashcodes
        return Objects.hashCode(super.hashCode(), getValue());
    }

    @Override
    public String toString() {
        return "VertexProperty{" +
                "id=" + id +
                ", properties=" + properties +
                ", name=" + getName() +
                ", value=" + value +
                ", parent-id=" + (parent != null ? parent.getId() : "null") +
                '}';
    }
}
