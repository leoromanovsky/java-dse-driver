/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.driver.dse.graph;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractListAssert;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PathAssert extends AbstractAssert<PathAssert, Path> {

    public PathAssert(Path actual) {
        super(actual, PathAssert.class);
    }

    public GraphNodeAssert object(int i) {
        assertThat(actual.getObjects().size()).isGreaterThanOrEqualTo(i);
        return new GraphNodeAssert(actual.getObject(i));
    }

    public PathAssert hasLabel(String label) {
        assertThat(actual.hasLabel(label)).isTrue();
        return myself;
    }

    public PathAssert doesNotHaveLabel(String label) {
        assertThat(actual.hasLabel(label)).isFalse();
        return myself;
    }

    public PathAssert hasLabel(int i, String... labels) {
        assertThat(actual.getLabels().size()).isGreaterThanOrEqualTo(i);
        assertThat(actual.getLabels().get(i)).containsOnly(labels);
        return myself;
    }

    public PathAssert hasNoLabel(int i) {
        return hasLabel(i);
    }

    public GraphNodeAssert object(String label) {
        return new GraphNodeAssert(actual.getObject(label));
    }

    public AbstractListAssert<?, ? extends List<GraphNode>, GraphNode> objects(String label) {
        return assertThat(actual.getObjects(label));
    }

    /**
     * Ensures that the given Path matches one of the exact traversals we'd expect for a person whom Marko
     * knows that has created software and what software that is.
     * <p>
     * These paths should be:
     * <ul>
     * <li>marko -> knows -> josh -> created -> lop</li>
     * <li>marko -> knows -> josh -> created -> ripple</li>
     * </ul>
     * <p>
     * GraphSON2 elements in paths don't contain properties so this method doesn't check
     * elements' properties.
     */
    public static void validatePathObjects(Path path) {

        // marko should be the origin point.
        GraphAssertions.assertThat(path)
                .object(0)
                .asVertex()
                .hasLabel("person")
        ;

        // there should be a 'knows' outgoing relationship between marko and josh.
        GraphAssertions.assertThat(path)
                .object(1)
                .asEdge()
                .hasLabel("knows")
                .hasOutVLabel("person")
                .hasOutV(path.getObjects().get(0))
                .hasInVLabel("person")
                .hasInV(path.getObjects().get(2))
        ;

        // josh...
        GraphAssertions.assertThat(path)
                .object(2)
                .asVertex()
                .hasLabel("person")
        ;

        // there should be a 'created' relationship between josh and lop.
        GraphAssertions.assertThat(path)
                .object(3)
                .asEdge()
                .hasLabel("created")
                .hasOutVLabel("person")
                .hasOutV(path.getObjects().get(2))
                .hasInVLabel("software")
                .hasInV(path.getObjects().get(4))
        ;

        // lop..
        GraphAssertions.assertThat(path)
                .object(4)
                .asVertex()
                .hasLabel("software")
        ;
    }
}
