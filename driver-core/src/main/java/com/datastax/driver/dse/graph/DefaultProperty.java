/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.driver.dse.graph;

import com.google.common.base.Objects;

class DefaultProperty implements Property {

    String name;

    GraphNode value;

    Element parent;

    DefaultProperty() {
    }

    public String getName() {
        return name;
    }

    @Override
    public GraphNode getValue() {
        return value;
    }

    @Override
    public Element getParent() {
        return parent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Property)) return false;
        Property that = (Property) o;
        return Objects.equal(getName(), that.getName()) &&
                Objects.equal(getValue(), that.getValue()) &&
                Objects.equal(getParent(), that.getParent());
    }

    @Override
    public int hashCode() {
        // getParent() deliberately left out for faster hashcodes
        return Objects.hashCode(getName(), getValue());
    }

    @Override
    public String toString() {
        return "Property{" +
                "name='" + name + '\'' +
                ", value=" + value +
                ", parent-id=" + (parent != null ? parent.getId() : "null") +
                '}';
    }
}
