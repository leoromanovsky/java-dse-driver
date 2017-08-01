/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.driver.dse.graph;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

class DefaultPath implements Path {

    private static final Function<Set<String>, Set<String>> TO_UNMODIFIABLE_SET = new Function<Set<String>, Set<String>>() {
        @Override
        public Set<String> apply(Set<String> input) {
            return Collections.unmodifiableSet(input);
        }
    };

    List<Set<String>> labels;

    List<GraphNode> objects;

    DefaultPath() {
    }

    @Override
    public List<Set<String>> getLabels() {
        return labels == null || labels.isEmpty()
                ? Collections.<Set<String>>emptyList()
                : Collections.unmodifiableList(Lists.transform(labels, TO_UNMODIFIABLE_SET));
    }

    @Override
    public List<GraphNode> getObjects() {
        return objects == null || objects.isEmpty()
                ? Collections.<GraphNode>emptyList()
                : Collections.unmodifiableList(objects);
    }

    @Override
    public int size() {
        return objects == null ? 0 : objects.size();
    }

    @Override
    public GraphNode getObject(int index) {
        if (index < 0 || index >= size())
            return null;
        return getObjects().get(index);
    }

    @Override
    public GraphNode getObject(String label) {
        List<GraphNode> objects = getObjects(label);
        return objects.isEmpty() ? null : objects.get(0);
    }

    @Override
    public List<GraphNode> getObjects(String label) {
        List<GraphNode> objects = Lists.newArrayListWithExpectedSize(1);
        List<Set<String>> labels = getLabels();
        for (int i = 0; i < labels.size(); i++) {
            if (labels.get(i).contains(label)) {
                objects.add(getObject(i));
            }
        }
        return Collections.unmodifiableList(objects);
    }

    @Override
    public boolean hasLabel(String label) {
        for (Set<String> labelGroup : getLabels()) {
            if (labelGroup.contains(label))
                return true;
        }
        return false;
    }

    @Override
    public Iterator<GraphNode> iterator() {
        return getObjects().iterator();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Path)) return false;
        Path that = (Path) o;
        return Objects.equal(getLabels(), that.getLabels()) &&
                Objects.equal(getObjects(), that.getObjects());
    }

    @Override
    public int hashCode() {
        // getLabels() deliberately left out for faster hashcodes
        return Objects.hashCode(getObjects());
    }

    @Override
    public String toString() {
        return "Path{" +
                "labels=" + getLabels() +
                ", objects=" + getObjects() +
                '}';
    }
}
