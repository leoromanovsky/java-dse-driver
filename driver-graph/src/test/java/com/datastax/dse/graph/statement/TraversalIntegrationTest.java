/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.dse.graph.statement;

import com.datastax.driver.core.TypeTokens;
import com.datastax.driver.core.VersionNumber;
import com.datastax.driver.core.utils.DseVersion;
import com.datastax.driver.dse.graph.*;
import com.datastax.driver.dse.graph.Edge;
import com.datastax.driver.dse.graph.Vertex;
import com.datastax.driver.dse.graph.VertexProperty;
import com.datastax.dse.graph.CCMTinkerPopTestsSupport;
import com.datastax.dse.graph.internal.utils.DseVersionCheckUtils;
import com.datastax.dse.graph.api.DseGraph;
import com.datastax.dse.graph.remote.SocialTraversalSource;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.datastax.driver.dse.graph.GraphAssertions.assertThat;
import static com.datastax.driver.dse.graph.GraphExtractors.fieldAs;
import static com.datastax.driver.dse.graph.GraphExtractors.vertexPropertyValueAs;
import static com.datastax.dse.graph.api.DseGraph.statementFromTraversal;

@DseVersion(value = "5.0.3", description = "DSE 5.0.3 required for remote TinkerPop support")
public class TraversalIntegrationTest extends CCMTinkerPopTestsSupport {

    protected TraversalIntegrationTest() {
        super(false);
    }

    /**
     * Ensures that a previously returned {@link Vertex}'s {@link Vertex#getId()} can be used as an input to
     * {@link org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource#V(Object...)}
     * to retrieve the {@link Vertex} and that the returned {@link Vertex} is the same.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_use_vertex_id_as_parameter() {
        GraphResultSet resultSet = session().executeGraph(
                statementFromTraversal(g.V().hasLabel("person").has("name", "marko"))
        );

        List<GraphNode> results = resultSet.all();

        assertThat(results.size()).isEqualTo(1);
        Vertex marko = results.get(0).asVertex();
        assertThat(marko).hasProperty("name", "marko");

        resultSet = session().executeGraph(statementFromTraversal(g.V(marko.getId())));

        results = resultSet.all();
        assertThat(results.size()).isEqualTo(1);
        Vertex marko2 = results.get(0).asVertex();
        // Ensure that the returned vertex is the same as the first.
        assertThat(marko2).isEqualTo(marko);
    }

    /**
     * Ensures that a previously returned {@link Edge}'s {@link Edge#getId()} can be used as an input to
     * {@link org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource#E(Object...)}
     * to retrieve the {@link Edge} and that the returned {@link Edge} is the same.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_use_edge_id_as_parameter() {
        GraphResultSet resultSet = session().executeGraph(
                statementFromTraversal(g.E().has("weight", 0.2f))
        );

        List<GraphNode> results = resultSet.all();
        assertThat(results.size()).isEqualTo(1);

        Edge created = results.get(0).asEdge();
        assertThat(created).hasProperty("weight", 0.2f).hasInVLabel("software").hasOutVLabel("person");

        resultSet = session().executeGraph(statementFromTraversal(g.E(created.getId()).inV()));
        results = resultSet.all();
        assertThat(results.size()).isEqualTo(1);
        Vertex lop = results.get(0).asVertex();

        assertThat(lop).hasLabel("software").hasProperty("name", "lop").hasProperty("lang", "java");
    }

    /**
     * A sanity check that a returned {@link Vertex}'s id is a {@link Map}.  This test could break in the future
     * if the format of a vertex ID changes from a Map to something else in DSE.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_deserialize_vertex_id_as_map() {
        GraphResultSet resultSet = session().executeGraph(
                statementFromTraversal(g.V().hasLabel("person").has("name", "marko"))
        );

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
     * Ensures that a traversal that returns a result of mixed types is interpreted as a {@link Map} with
     * {@link Object} values.  Also uses
     * {@link org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal#by(org.apache.tinkerpop.gremlin.process.traversal.Traversal)}
     * with an anonymous traversal to get inbound 'created' edges and folds them into a list.
     * <p/>
     * Executes a vertex traversal that binds label 'a' and 'b' to vertex properties and label 'c' to vertices that
     * have edges from that vertex.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_handle_result_object_of_mixed_types() {
        // find all software vertices and select name, language, and find all vertices that created such software.
        GraphResultSet rs = session().executeGraph(
                statementFromTraversal(g.V()
                        .hasLabel("software").as("a", "b", "c")
                        .select("a", "b", "c")
                        .by("name")
                        .by("lang")
                        .by(__.in("created").fold()))
        );


        List<GraphNode> results = rs.all();
        assertThat(results.size()).isEqualTo(2);

        // Ensure that we got 'lop' and 'ripple' for property a.
        assertThat(results).extracting(fieldAs("a", String.class)).containsOnly("lop", "ripple");

        for (GraphNode result : results) {
            // The row should represent a map with a, b, and c keys.
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
     * Ensures a traversal that yields no results is properly retrieved and is empty.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_return_zero_results() {
        GraphResultSet rs = session().executeGraph(
                statementFromTraversal(g.V().hasLabel("notALabel"))
        );
        assertThat(rs.all().size()).isZero();
    }

    /**
     * Ensures a traversal that yields no results is properly retrieved and is empty,
     * using GraphSON2 and the TinkerPop transform results function.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_return_zero_results_graphson_2() {
        session().getCluster().getConfiguration().getGraphOptions().setGraphSubProtocol(GraphProtocol.GRAPHSON_2_0);
        SimpleGraphStatement simpleGraphStatement = new SimpleGraphStatement("g.V().hasLabel('notALabel')");

        GraphResultSet rs = session().executeGraph(simpleGraphStatement);
        assertThat(rs.one()).isNull();
    }


    /**
     * Ensures that a traversal that yields a vertex with a property that has its own properties that is appropriately
     * parsed and made accessible via {@link VertexProperty#getProperty(String)}.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_parse_meta_properties() {
        executeGraph(GraphFixtures.metaProps);

        GraphResultSet result = session().executeGraph(
                statementFromTraversal(g.addV("meta_v")
                        .property("meta_prop", "hello", "sub_prop", "hi", "sub_prop2", "hi2"))
        );

        Vertex v = result.one().asVertex();
        assertThat(v).hasProperty("meta_prop");

        VertexProperty metaProp = v.getProperty("meta_prop");
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
        // given a schema that defines multiple cardinality properties.
        executeGraph(GraphFixtures.multiProps);

        // when adding a vertex with a multiple cardinality property
        GraphResultSet result = session().executeGraph(statementFromTraversal(g.addV("multi_v")
                .property("multi_prop", "Hello")
                .property("multi_prop", "Sweet")
                .property("multi_prop", "World")));

        Vertex v = result.one().asVertex();
        assertThat(v).hasProperty("multi_prop");

        List<VertexProperty> props = Lists.newArrayList(v.getProperties("multi_prop"));

        assertThat(props).hasSize(3).extracting(vertexPropertyValueAs(String.class)).containsOnly("Hello", "Sweet", "World");
    }

    /**
     * Validates that a traversal using lambda operations with anonymous traversals are applied appropriately and
     * return the expected results.
     * <p/>
     * Traversal that filters 'person'-labeled vertices by name 'marko' and flatMaps outgoing vertices on the 'knows'
     * relationship by their outgoing 'created' vertices and then maps by their 'name' property and folds them into
     * one list.
     * <p/>
     * <b>Note:</b>  This does not validate lambdas with functions as those can't be interpreted and sent remotely.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_handle_lambdas() {
        // Find all people marko knows and the software they created.
        GraphResultSet result = session().executeGraph(
                statementFromTraversal(g.V()
                        .hasLabel("person")
                        .filter(__.has("name", "marko"))
                        .out("knows")
                        .flatMap(__.out("created"))
                        .map(__.values("name")).fold()
                )
        );

        // Marko only knows josh and vadas, of which josh created lop and ripple.
        List<String> software = result.one().as(TypeTokens.listOf(String.class));
        assertThat(software).containsOnly("lop", "ripple");
    }

    /**
     * Validates that when traversing a path and labeling some of the elements during the traversal that the
     * output elements are properly labeled.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_resolve_path_with_some_labels() {
        GraphResultSet rs = session().executeGraph(
                statementFromTraversal(g.V()
                        .hasLabel("person")
                        .has("name", "marko").as("a")
                        .outE("knows")
                        .inV().as("c", "d")
                        .outE("created").as("e", "f", "g")
                        .inV()
                        .path()
                )
        );

        List<GraphNode> results = rs.all();
        assertThat(results.size()).isEqualTo(2);
        for (GraphNode result : results) {
            Path path = result.asPath();
            PathAssert.validatePathObjects(path);
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
        GraphResultSet rs = session().executeGraph(
                statementFromTraversal(g.V()
                        .hasLabel("person")
                        .has("name", "marko").as("a")
                        .outE("knows").as("b")
                        .inV().as("c", "d")
                        .outE("created")
                        .as("e", "f", "g")
                        .inV()
                        .as("h")
                        .path()
                )
        );
        List<GraphNode> results = rs.all();
        assertThat(results.size()).isEqualTo(2);
        for (GraphNode result : results) {
            Path path = result.asPath();
            PathAssert.validatePathObjects(path);
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
        GraphResultSet rs = session().executeGraph(
                statementFromTraversal(g.V()
                        .hasLabel("person")
                        .has("name", "marko")
                        .outE("knows")
                        .inV()
                        .outE("created")
                        .inV()
                        .path()
                )
        );
        List<GraphNode> results = rs.all();
        assertThat(results.size()).isEqualTo(2);
        for (GraphNode result : results) {
            Path path = result.asPath();
            PathAssert.validatePathObjects(path);
            assertThat(path.getLabels()).hasSize(5);
            for (int i = 0; i < 5; i++)
                assertThat(path)
                        .hasNoLabel(i);
        }
    }

    /**
     * Validates that a traversal returning a Tree structure is returned appropriately with the expected
     * contents.
     * <p/>
     * Retrieves trees of people marko knows and the software they created.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_parse_tree() {
        // Get a tree structure showing the paths from mark to people he knows to software they've created.
        GraphResultSet rs = session().executeGraph(
                statementFromTraversal(g.V()
                        .hasLabel("person")
                        .out("knows")
                        .out("created")
                        .tree()
                        .by("name"))
        );

        List<GraphNode> results = rs.all();
        assertThat(results.size()).isEqualTo(1);

        // [{key=marko, value=[{key=josh, value=[{key=ripple, value=[]}, {key=lop, value=[]}]}]}]
        GraphNode result = results.get(0);

        // Since there is no 'Tree' type in DSE yet, we consume the tree as a GraphNode.
        // There should be 1 tree, with marko being the root.
        assertThat(result.size()).isEqualTo(1);

        // marko should have a tree under it for josh.
        GraphNode markoTree = result.get(0);
        assertThat(markoTree).hasChild("key").hasChild("value");
        assertThat(markoTree.get("key").asString()).isEqualTo("marko");
        assertThat(markoTree.get("value").size()).isEqualTo(1);

        // josh should have 2 leafs for software created.
        GraphNode joshTree = markoTree.get("value").get(0);
        assertThat(joshTree).hasChild("key").hasChild("value");
        assertThat(joshTree.get("key").asString()).isEqualTo("josh");
        GraphNode leafs = joshTree.get("value");
        assertThat(leafs.size()).isEqualTo(2);

        // Each software should be a leaf.
        for (int i = 0; i < 2; i++) {
            GraphNode leaf = leafs.get(i);
            assertThat(leaf).hasChild("key").hasChild("value");
            String key = leaf.get("key").asString();
            assertThat(key).isIn("lop", "ripple");
            assertThat(leaf.get("value").size()).isEqualTo(0);
        }
    }

    /**
     * Ensures that a traversal that returns a sub graph can be retrieved.
     * <p/>
     * The subgraph is all members in a knows relationship, thus is all people who marko knows and the
     * edges that connect them.
     */
    @Test(groups = "short")
    public void should_handle_subgraph() {
        GraphResultSet rs = session().executeGraph(
                statementFromTraversal(g.E().hasLabel("knows").subgraph("subGraph").cap("subGraph"))
        );

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
     * A simple smoke test to ensure that a user can supply a custom {@link GraphTraversalSource} for use with DSLs.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_allow_use_of_dsl() throws Exception {
        SocialTraversalSource gSocial = DseGraph.traversal(SocialTraversalSource.class);

        GraphStatement gs = DseGraph.statementFromTraversal(gSocial.persons("marko").knows("vadas"));

        GraphResultSet rs = session().executeGraph(gs);
        List<GraphNode> results = rs.all();

        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0).asVertex())
                .hasProperty("name", "marko")
                .hasProperty("age", 29)
                .hasLabel("person");
    }

    /**
     * Ensures that traversals with barriers (which return results bulked) contain the
     * correct amount of end results.
     * <p/>
     * This will fail if ran against DSE < 5.0.9 or DSE < 5.1.2.
     */
    @Test(groups = "short")
    @DseVersion("5.0.9")
    public void should_return_correct_results_when_bulked() {
        DseVersionCheckUtils.checkDse51Version(ccm().getDSEVersion(), VersionNumber.parse("5.1.2"));

        GraphResultSet rs = session().executeGraph(
                statementFromTraversal(g.E().label().barrier())
        );

        List<String> results = rs.all().stream().map(GraphNode::asString).sorted().collect(Collectors.toList());

        List<String> expected = Arrays.asList("knows", "created", "created", "knows", "created", "created");
        Collections.sort(expected);

        assertThat(results).isEqualTo(expected);
    }
}
