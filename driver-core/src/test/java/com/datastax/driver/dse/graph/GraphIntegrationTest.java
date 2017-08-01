/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.driver.dse.graph;

import com.datastax.driver.core.exceptions.InvalidQueryException;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.datastax.driver.core.exceptions.OperationTimedOutException;
import com.datastax.driver.core.utils.DseVersion;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.datastax.driver.dse.graph.GraphAssertions.assertThat;
import static com.datastax.driver.dse.graph.GraphExtractors.fieldAs;
import static com.datastax.driver.dse.graph.GraphExtractors.vertexPropertyValueAs;
import static com.datastax.driver.dse.graph.PathAssert.validatePathObjects;

@SuppressWarnings("Since15")
@DseVersion("5.0.0")
public abstract class GraphIntegrationTest extends CCMGraphTestsSupport {

    private final GraphProtocol graphProtocol;

    // used for checking special exceptions exclusively for graphson 1.0.
    private final boolean isGraphSON1;

    @Factory(dataProvider = "graphProtocolProvider")
    public GraphIntegrationTest(GraphProtocol graphProtocol) {
        this.graphProtocol = graphProtocol;
        this.isGraphSON1 = graphProtocol == GraphProtocol.GRAPHSON_1_0;
    }

    @Override
    public void onTestContextInitialized() {
        super.onTestContextInitialized();
        cluster().getConfiguration().getGraphOptions().setGraphSubProtocol(graphProtocol);
        executeGraph(GraphFixtures.modern);
    }

    /**
     * Ensures that a {@link Vertex} id can be given as a parameter.  This facilitates the convenience of being able to
     * reference a specific vertex as part of a traversal, i.e.:
     * <p/>
     * <code>g.V(vertexId)</code>
     * <p/>
     * The test first retrieves an existing {@link Vertex} by looking it up based on it's label and properties and then
     * takes the returned {@link Vertex} id and uses it to query the vertex again and ensures the returned vertex
     * payload matches.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_use_vertex_id_as_parameter() {
        GraphResultSet resultSet = session().executeGraph(
                new SimpleGraphStatement("g.V().hasLabel('person').has('name', name)").set("name", "marko"));

        List<GraphNode> results = resultSet.all();
        assertThat(results.size()).isEqualTo(1);
        Vertex marko = results.get(0).asVertex();
        assertThat(marko).hasProperty("name", "marko");

        resultSet = session().executeGraph(new SimpleGraphStatement("g.V(myV)").set("myV", marko.getId()));
        results = resultSet.all();
        assertThat(results.size()).isEqualTo(1);
        Vertex marko2 = results.get(0).asVertex();

        // Ensure that the returned vertex is the same as the first.
        assertThat(marko2).isEqualTo(marko);
    }

    /**
     * A sanity check that ensures that we can deserialize a Vertex Id as a Map using {@link GraphNode#as(Class)}.
     * This test could break in the future if the format of a Vertex ID changes from a Map to something else in DSE.
     *
     * @jira_ticket JAVA-1080
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_deserialize_vertex_id_as_map() {
        GraphResultSet resultSet = session().executeGraph(
                new SimpleGraphStatement("g.V().hasLabel('person').has('name', name)").set("name", "marko"));

        List<GraphNode> results = resultSet.all();
        assertThat(results.size()).isEqualTo(1);
        Vertex marko = results.get(0).asVertex();
        assertThat(marko).hasProperty("name", "marko");

        //@formatter:off
        Map<String, Object> deserializedId = marko.getId().as(new TypeToken<Map<String, Object>>() {
        });
        //@formatter:on
        assertThat(deserializedId.keySet()).containsExactly(Iterators.toArray(marko.getId().fieldNames(), String.class));

        Iterator<String> keys = marko.getId().fieldNames();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = deserializedId.get(key);
            assertThat(value).isEqualTo(marko.getId().get(key).as(value.getClass()));
        }
    }

    /**
     * Ensures that a {@link Edge} id can be given as a parameter.  This facilitates the convenience of being able to
     * reference a specific edge as part of a traversal, i.e.:
     * <p/>
     * <code>g.E(edgeId).inV()</code>
     * <p/>
     * The test first retrieves an existing {@link Edge} by looking it up based on a matching property and then
     * takes the returned {@link Edge} instance's id and uses it to query for the incoming {@link Vertex} and ensures
     * the returned vertex matches what is expected.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_use_edge_id_as_parameter() {
        GraphResultSet resultSet = session().executeGraph(
                new SimpleGraphStatement("g.E().has('weight', weight)").set("weight", 0.2f));

        List<GraphNode> results = resultSet.all();
        assertThat(results.size()).isEqualTo(1);
        Edge created = results.get(0).asEdge();
        assertThat(created).hasProperty("weight", 0.2f).hasInVLabel("software").hasOutVLabel("person");

        resultSet = session().executeGraph(new SimpleGraphStatement("g.E(myE).inV()").set("myE", created.getId()));
        results = resultSet.all();
        assertThat(results.size()).isEqualTo(1);
        Vertex lop = results.get(0).asVertex();

        assertThat(lop).hasLabel("software").hasProperty("name", "lop").hasProperty("lang", "java");
    }

    /**
     * Validates that a list can be provided as a parameter and used as part of the executed groovy code.
     * <p/>
     * Executes a script with a list that evaluates each member of the list and creates a vertex for it, with
     * each vertex sharing the same label.  It then queries for all vertices sharing that label and ensures there is a
     * vertex for each element of the given list.
     */
    @Test(groups = "short")
    public void should_use_list_as_a_parameter() {
        GraphStatement schemaStmt = new SimpleGraphStatement("schema.vertexLabel('character').properties('name').create();");
        session().executeGraph(schemaStmt);

        Collection<String> characters = Lists.newArrayList("Mario", "Luigi", "Toad", "Bowser", "Peach", "Wario", "Waluigi");

        SimpleGraphStatement createCharacters = new SimpleGraphStatement("" +
                "characters.each { character -> \n" +
                "    graph.addVertex(label, 'character', 'name', character);\n" +
                "}").set("characters", characters);

        session().executeGraph(createCharacters);

        GraphResultSet resultSet = session().executeGraph("g.V().hasLabel('character').values('name')");

        assertThat(Iterables.transform(resultSet.all(), new Function<GraphNode, String>() {
            @Override
            public String apply(GraphNode input) {
                return input.asString();
            }
        })).containsOnlyElementsOf(characters);
    }

    /**
     * Validates that a map of mixed types can be provided as a parameter and used as part of the executed groovy
     * code.
     * <p/>
     * Executes a script that evaluates the map representing traits of Albert Einstein and creates a 'scientist'
     * vertex labeled for it.  For each element of map.citizenship,
     * creates a vertex labeled 'country' for which an outgoing edge labeled 'had_citizenship' is created from the
     * scientist vertex to the country vertex.  It then queries that the scientist vertex was added properly and
     * that all countries of citizenship had a vertex created with an accompanying edge from the scientist as well.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_use_map_as_a_parameter() {
        GraphStatement schemaStmt = new SimpleGraphStatement("" +
                "schema.propertyKey('year_born').Int().create()\n" +
                "schema.propertyKey('field').Text().create()\n" +
                "schema.vertexLabel('scientist').properties('name', 'year_born', 'field').create()\n" +
                "schema.vertexLabel('country').properties('name').create()\n" +
                "schema.edgeLabel('had_citizenship').connection('scientist', 'country').create()");
        session().executeGraph(schemaStmt);

        String name = "Albert Einstein";
        int year = 1879;
        String field = "Physics";
        List<String> citizenship = Lists.newArrayList("Kingdom of Württemberg", "Switzerland", "Austria", "Germany", "United States");
        Map<String, Object> einsteinProps = ImmutableMap.<String, Object>builder()
                .put("name", name)
                .put("year_born", year)
                .put("citizenship", citizenship)
                .put("field", field)
                .build();

        // Create a vertex for Einstein, and then add a vertex for each country of citizenship and an outgoing
        // edge from Einstein to country he had citizenship in.
        SimpleGraphStatement addV = new SimpleGraphStatement("" +
                "Vertex scientist = graph.addVertex(label, 'scientist', 'name', m.name, 'year_born', m.year_born, 'field', m.field)\n" +
                "m.citizenship.each { c -> \n" +
                "    Vertex country = graph.addVertex(label, 'country', 'name', c);\n" +
                "    scientist.addEdge('had_citizenship', country);\n" +
                "}").set("m", einsteinProps);

        session().executeGraph(addV);

        // Ensure einstein was properly added.
        GraphNode result = session().executeGraph("g.V().hasLabel('scientist').has('name', name)",
                ImmutableMap.<String, Object>of("name", name)).one();
        assertThat(result)
                .asVertex()
                .hasLabel("scientist")
                .hasProperty("name", name)
                .hasProperty("year_born", year)
                .hasProperty("field", field);

        // Ensure each country vertex was added and an edge was created from it to einstein.
        GraphResultSet resultSet = session().executeGraph(
                "g.V(vId).outE('had_citizenship').inV().values('name')",
                ImmutableMap.<String, Object>of("vId", result.asVertex().getId()));

        List<GraphNode> results = resultSet.all();
        assertThat(Iterables.transform(results, new Function<GraphNode, String>() {
            @Override
            public String apply(GraphNode input) {
                return input.asString();
            }
        })).containsOnlyElementsOf(citizenship);
    }

    /**
     * Ensures that a traversal that returns an object with labels can be properly represented as graph result.
     * <p/>
     * Executes a vertex traversal that binds label 'a' and 'b' to vertex properties and label 'c' to vertices that
     * have edges from that vertex.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_handle_result_object_of_mixed_types() {
        // find all software vertices and select name, language, and find all vertices that created such software.
        GraphResultSet rs = session().executeGraph("g.V().hasLabel('software').as('a', 'b', 'c')." +
                "select('a','b','c')." +
                "by('name')." +
                "by('lang')." +
                "by(__.in('created').fold())");

        List<GraphNode> results = rs.all();
        assertThat(results.size()).isEqualTo(2);

        // Ensure that we got 'lop' and 'ripple' for property a.
        assertThat(results).extracting(fieldAs("a", String.class)).containsOnly("lop", "ripple");

        for (GraphNode result : results) {
            // The row should represent a map with a, b, and c keys.
            assertThat(result.isObject()).isTrue();
            assertThat(result.fieldNames()).containsOnlyOnce("a", "b", "c");
            // 'e' should not exist, thus it should be null.
            assertThat(result.get("e")).isNull();
            // both software are written in java.
            assertThat(result.get("b").isNull()).isFalse();
            assertThat(result.get("b").asString()).isEqualTo("java");
            GraphNode c = result.get("c");
            assertThat(c.isArray()).isTrue();
            if (result.get("a").asString().equals("lop")) {
                // 'c' should contain marko, josh, peter.
                // Ensure we have three vertices.
                assertThat(c.size()).isEqualTo(3);
                List<Vertex> vertices = Lists.newArrayList(c.get(0).asVertex(), c.get(1).asVertex(), c.get(2).asVertex());
                assertThat(vertices)
                        .extracting(vertexPropertyValueAs("name", String.class))
                        .containsOnly("marko", "josh", "peter");
            } else {
                // ripple, 'c' should contain josh.
                // Ensure we have 1 vertex.
                assertThat(c.size()).isEqualTo(1);
                Vertex vertex = c.get(0).asVertex();
                assertThat(vertex).hasProperty("name", "josh");
            }
        }
    }

    /**
     * Ensures that a traversal that returns a subgraph can be properly deserialized as a graph result.
     * <p/>
     * A subgraph should contain 2 members, vertices and edges, with each being a list of the appropriate types.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_handle_subgraph() {
        GraphResultSet rs = session().executeGraph("g.E().hasLabel('knows').subgraph('subGraph').cap('subGraph')");

        List<GraphNode> results = rs.all();
        assertThat(results.size()).isEqualTo(1);

        GraphNode result = results.get(0);
        assertThat(result)
                .hasChild("edges")
                .hasChild("vertices");

        // There should only be 2 edges as there are only 2 knows edges (marko knows josh and vadas)
        GraphNode edges = result.get("edges");
        assertThat(edges.size()).isEqualTo(2);
        // Ensure edges can be deserialized as a list and that we can get each value as an Edge.
        for (int i = 0; i < edges.size(); i++) {
            GraphNode edge = edges.get(i);
            assertThat(edge).asEdge();
        }

        // There should only be 3 vertices (marko, josh, and vadas).
        GraphNode vertices = result.get("vertices");
        assertThat(vertices.size()).isEqualTo(3);
        // Ensure vertices can be deserialized as a list and that we can get each value as a Vertex.
        for (int i = 0; i < vertices.size(); i++) {
            GraphNode vertex = vertices.get(i);
            assertThat(vertex).asVertex();
        }
    }

    /**
     * Ensures a traversal that yields no results is properly parsed and returned.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_return_zero_results() {
        GraphResultSet rs = session().executeGraph("g.V().hasLabel('notALabel')");
        assertThat(rs.all().size()).isZero();
    }

    /**
     * Ensures that a traversal that yields a vertex with a property that has its own properties that is appropriately
     * parsed and made accessible via {@link VertexProperty#getProperties()}.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_parse_meta_properties() {
        executeGraph(GraphFixtures.metaProps);

        Vertex v0 = session().executeGraph("g.addV(label, 'meta_v', 'meta_prop', 'hello')").one().asVertex();
        ImmutableMap<String, Object> vIdParam = ImmutableMap.<String, Object>of("vId", v0.getId());
        session().executeGraph("g.V(vId).next().property('meta_prop').property('sub_prop', 'hi')", vIdParam);
        session().executeGraph("g.V(vId).next().property('meta_prop').property('sub_prop2', 'hi2')", vIdParam);

        Vertex v1 = session().executeGraph("g.V(vId)", vIdParam).one().asVertex();

        assertThat(v1).hasProperty("meta_prop");

        VertexProperty metaProp = v1.getProperty("meta_prop");
        assertThat(metaProp)
                .hasValue("hello")
                .hasProperty("sub_prop", "hi")
                .hasProperty("sub_prop2", "hi2");
    }

    /**
     * Ensures that a traversal that yields a vertex with a property name that is present multiple times that the
     * properties are parsed and made accessible via {@link Vertex#getProperties(String)}.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_parse_multiple_cardinality_properties() {
        executeGraph(GraphFixtures.multiProps);

        Vertex v = session().executeGraph("g.addV(label, 'multi_v', 'multi_prop', 'Hello', 'multi_prop', 'Sweet', 'multi_prop', 'World')").one().asVertex();

        assertThat(v).hasProperty("multi_prop");

        List<VertexProperty> props = Lists.newArrayList(v.getProperties("multi_prop"));

        assertThat(props).hasSize(3).extracting(vertexPropertyValueAs(String.class)).containsOnly("Hello", "Sweet", "World");
    }

    /**
     * Ensures that {@link GraphStatement#setGraphInternalOption(String, String)} properly updates custom payload
     * to set a config option that will be used by DSE server for this statement's transaction only by setting
     * the <code>cfg.read_only</code> option to true and attempting to add a vertex.  This should cause the
     * statement to fail and DSE to throw an {@link InvalidQueryException}.
     *
     * @test_category dse:graph
     * @jira_ticket JAVA-1208
     */
    @Test(groups = "short", expectedExceptions = {InvalidQueryException.class},
            expectedExceptionsMessageRegExp = "Cannot open new entities in read-only transaction")
    public void should_set_tx_as_read_only_using_internal_option() {
        GraphStatement stmt = new SimpleGraphStatement("graph.addVertex(label, 'software', 'name', 'lop2', 'lang', 'java');")
                .setGraphInternalOption("cfg.read_only", "true");

        session().executeGraph(stmt);
    }

    /**
     * Ensures that {@link GraphStatement}s do not have idempotence set by default, and therefore are not idempotent.
     * Thus when executing a {@link GraphStatement} that times out that it is not retried on the next host and a
     * {@link OperationTimedOutException} is raised to the client.
     *
     * @jira_ticket JAVA-1222
     * @test_category retry_policy
     */
    @Test(groups = "short", expectedExceptions = {OperationTimedOutException.class})
    public void should_not_retry_graph_statement_by_default() {
        GraphStatement stmt = new SimpleGraphStatement("java.util.concurrent.TimeUnit.MILLISECONDS.sleep(1000L);")
                .setReadTimeoutMillis(1);
        // Statement should have no idempotence config by default.
        assertThat(stmt.isIdempotent()).isNull();

        session().executeGraph(stmt);
    }

    /**
     * Ensures that when executing a {@link GraphStatement} that is configured to be non-idempotent that when it times
     * times out it is not retried on the next host and a {@link OperationTimedOutException} is raised to the
     * client.
     *
     * @jira_ticket JAVA-1222
     * @test_category retry_policy
     */
    @Test(groups = "short", expectedExceptions = {OperationTimedOutException.class})
    public void should_not_retry_non_idempotent_graph_statement_by_default() {
        GraphStatement stmt = new SimpleGraphStatement("java.util.concurrent.TimeUnit.MILLISECONDS.sleep(1001L);")
                .setReadTimeoutMillis(1).setIdempotent(false);
        assertThat(stmt.isIdempotent()).isFalse();

        session().executeGraph(stmt);
    }

    /**
     * Ensures that when executing a {@link GraphStatement} that is configured to be idempotent that when it times
     * times out it will be attempted on the next host.  With no remaining hosts to try, a
     * {@link NoHostAvailableException} should be raised to the client.
     *
     * @jira_ticket JAVA-1222
     * @test_category retry_policy
     */
    @Test(groups = "short", expectedExceptions = {NoHostAvailableException.class})
    public void should_retry_idempotent_graph_statement_by_default() {
        GraphStatement stmt = new SimpleGraphStatement("java.util.concurrent.TimeUnit.MILLISECONDS.sleep(1002L);")
                .setReadTimeoutMillis(1).setIdempotent(true);
        assertThat(stmt.isIdempotent()).isTrue();

        session().executeGraph(stmt);
    }

    /**
     * Ensures that all kinds of nodes known to the GraphNode interface are correctly detected.
     *
     * @jira_ticket JAVA-1447
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_detect_all_node_types() {

        GraphNode root = session().executeGraph(
                "return [ root: [" +
                        "object: [foo:42], " +
                        "array: [1,2,3], " +
                        "vertex: g.V().next(), " +
                        "edge: g.E().next(), " +
                        "value: 42, " +
                        "nil: null " +
                        "]]").one().get("root");

        GraphNode object = root.get("object");
        GraphNode array = root.get("array");
        GraphNode vertex = root.get("vertex");
        GraphNode edge = root.get("edge");
        GraphNode value = root.get("value");
        GraphNode nil = root.get("nil");

        // object node
        assertThat(object).isNotNull();
        assertThat(object.isObject()).isTrue();
        assertThat(object.isVertex()).isFalse();
        assertThat(object.isEdge()).isFalse();
        assertThat(object.isArray()).isFalse();
        assertThat(object.isValue()).isFalse();
        assertThat(object.isNull()).isFalse();

        // array node
        assertThat(array).isNotNull();
        assertThat(array.isObject()).isFalse();
        assertThat(array.isVertex()).isFalse();
        assertThat(array.isEdge()).isFalse();
        assertThat(array.isArray()).isTrue();
        assertThat(array.isValue()).isFalse();
        assertThat(array.isNull()).isFalse();

        // vertex node
        assertThat(vertex).isNotNull();
        // vertex is only considered an object in graphson 1 since it is returned without type information
        // so as far as we know it's an object with properties.
        assertThat(vertex.isObject()).isEqualTo(isGraphSON1);
        assertThat(vertex.isVertex()).isTrue();
        assertThat(vertex.isEdge()).isFalse();
        assertThat(vertex.isArray()).isFalse();
        // vertex is not a value in graphson 1 since it's considered an object.
        assertThat(vertex.isValue()).isEqualTo(!isGraphSON1);
        assertThat(vertex.isNull()).isFalse();

        // edge node
        assertThat(edge).isNotNull();
        assertThat(edge.isObject()).isEqualTo(isGraphSON1);
        assertThat(edge.isVertex()).isFalse();
        assertThat(edge.isEdge()).isTrue();
        assertThat(edge.isArray()).isFalse();
        assertThat(edge.isValue()).isEqualTo(!isGraphSON1);
        assertThat(edge.isNull()).isFalse();

        // value node
        assertThat(value).isNotNull();
        assertThat(value.isObject()).isFalse();
        assertThat(value.isVertex()).isFalse();
        assertThat(value.isEdge()).isFalse();
        assertThat(value.isArray()).isFalse();
        assertThat(value.isValue()).isTrue();
        assertThat(value.isNull()).isFalse();

        // null node
        assertThat(nil).isNotNull();
        assertThat(nil.isObject()).isFalse();
        assertThat(nil.isVertex()).isFalse();
        assertThat(nil.isEdge()).isFalse();
        assertThat(nil.isArray()).isFalse();
        assertThat(nil.isValue()).isTrue(); // null node is a value node
        assertThat(nil.isNull()).isTrue();
    }

    /**
     * Validates that when traversing a path and labeling some of the elements during the traversal that the
     * output elements are properly labeled.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_resolve_path_with_some_labels() {
        GraphResultSet rs = session().executeGraph("g.V().hasLabel('person').has('name', 'marko').as('a')" +
                ".outE('knows').inV().as('c', 'd').outE('created').as('e', 'f', 'g').inV().path()");

        List<GraphNode> results = rs.all();
        assertThat(results.size()).isEqualTo(2);
        for (GraphNode result : results) {
            Path path = result.asPath();
            validatePathObjects(path);
            assertThat(path.getLabels()).hasSize(5);
            assertThat(path)
                    .hasLabel(0, "a")
                    .hasNoLabel(1)
                    .hasLabel(2, "c", "d")
                    .hasLabel(3, "e", "f", "g")
                    .hasNoLabel(4);
        }
    }

    /**
     * Validates that when traversing a path and labeling all of the elements during the traversal that the
     * output elements are properly labeled.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_resolve_path_with_labels() {
        GraphResultSet rs = session().executeGraph("g.V().hasLabel('person').has('name', 'marko').as('a')" +
                ".outE('knows').as('b').inV().as('c', 'd').outE('created').as('e', 'f', 'g').inV().as('h').path()");

        List<GraphNode> results = rs.all();
        assertThat(results.size()).isEqualTo(2);
        for (GraphNode result : results) {
            Path path = result.asPath();
            validatePathObjects(path);
            assertThat(path.getLabels()).hasSize(5);
            assertThat(path)
                    .hasLabel(0, "a")
                    .hasLabel(1, "b")
                    .hasLabel(2, "c", "d")
                    .hasLabel(3, "e", "f", "g")
                    .hasLabel(4, "h");
        }
    }

    /**
     * Validates that when traversing a path and labeling none of the elements during the traversal that all the
     * labels are empty in the result.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_resolve_path_without_labels() {
        GraphResultSet rs = session().executeGraph("g.V().hasLabel('person').has('name', 'marko')" +
                ".outE('knows').inV().outE('created').inV().path()");
        List<GraphNode> results = rs.all();
        assertThat(results.size()).isEqualTo(2);
        for (GraphNode result : results) {
            Path path = result.asPath();
            validatePathObjects(path);
            assertThat(path.getLabels()).hasSize(5);
            for (int i = 0; i < 5; i++)
                assertThat(path)
                        .hasNoLabel(i);
        }
    }
}
