/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.dse.graph.statement;

import com.datastax.driver.core.utils.DseVersion;
import com.datastax.driver.dse.graph.GraphNode;
import com.datastax.driver.dse.graph.GraphResultSet;
import com.datastax.driver.dse.graph.Vertex;
import com.datastax.driver.dse.graph.VertexAssert;
import com.datastax.dse.graph.DataTypeIntegrationTest;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;

import java.util.List;

import static com.datastax.driver.dse.graph.GraphAssertions.assertThat;
import static com.datastax.dse.graph.api.DseGraph.statementFromTraversal;

/**
 * {@link DataTypeIntegrationTest} implementation using an empty traversal source using the TinkerPop API
 * for traversals and {@link com.datastax.dse.graph.api.DseGraph#statementFromTraversal(GraphTraversal)}
 * for creating {@link com.datastax.driver.dse.graph.GraphStatement} which are executed using
 * {@link com.datastax.driver.dse.DseSession#executeGraph} and the {@link GraphResultSet} API for
 * consuming results.
 */
@DseVersion(value = "5.0.3", description = "DSE 5.0.3 required for remote TinkerPop support")
public class StatementDataTypeIntegrationTest extends DataTypeIntegrationTest {

    StatementDataTypeIntegrationTest() {
        super(false);
    }

    @Override
    protected void create_and_retrieve_vertex_property(Object input, Object output) {
        int id = schemaCounter.incrementAndGet();
        String vertexLabel = "vertex" + id;
        String propertyName = "prop" + id;

        // Create the vertex.  Since we are in development mode, DSE should create the property schema with
        // the appropriate types.
        GraphResultSet result = session().executeGraph(
                statementFromTraversal(g.addV(vertexLabel).property(propertyName, input))
        );
        validateVertexResult(result, vertexLabel, propertyName, output);

        // For completeness, retrieve the vertex and ensure the property value as maintained.
        result = session().executeGraph(
                statementFromTraversal(g.V().hasLabel(vertexLabel).has(propertyName, input))
        );
        validateVertexResult(result, vertexLabel, propertyName, output);
    }

    @SuppressWarnings("unchecked")
    private void validateVertexResult(GraphResultSet resultSet, String vertexLabel, String propertyName, Object expectedResult) {
        // Ensure the created vertex is returned and the property value matches what was provided.
        List<GraphNode> graphNodes = resultSet.all();
        assertThat(graphNodes.size()).isEqualTo(1);
        Vertex v = graphNodes.get(0).asVertex();
        VertexAssert a = assertThat(v).hasLabel(vertexLabel);

        // Validate using the appropriate asXXX method depending on the type of the class.
        if (expectedResult instanceof String) {
            a.hasProperty(propertyName, (String) expectedResult);
        } else if (expectedResult instanceof Integer) {
            a.hasProperty(propertyName, (Integer) expectedResult);
        } else if (expectedResult instanceof Boolean) {
            a.hasProperty(propertyName, (Boolean) expectedResult);
        } else if (expectedResult instanceof Long) {
            a.hasProperty(propertyName, (Long) expectedResult);
        } else if (expectedResult instanceof Double) {
            a.hasProperty(propertyName, (Double) expectedResult);
        }

        // Validate using the as(Clazz) method depending on the expectedClass.
        a.hasProperty(propertyName, expectedResult, (Class<Object>) expectedResult.getClass());
    }
}
