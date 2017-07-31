/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.dse.graph.remote;

import com.datastax.driver.core.exceptions.InvalidQueryException;
import com.datastax.driver.core.utils.DseVersion;
import com.datastax.driver.dse.graph.GraphFixtures;
import com.datastax.dse.graph.CCMTinkerPopTestsSupport;
import com.datastax.dse.graph.TinkerGraphExtractors;
import com.datastax.dse.graph.api.DseGraph;
import org.apache.tinkerpop.gremlin.process.traversal.Path;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.Tree;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.assertj.core.api.Assertions;
import org.testng.annotations.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.datastax.dse.graph.TinkerGraphAssertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@DseVersion(value = "5.0.3", description = "DSE 5.0.3 required for remote TinkerPop support")
public class TraversalIntegrationTest extends CCMTinkerPopTestsSupport {

    TraversalIntegrationTest() {
        super(true);
    }

    /**
     * Ensures that a previously returned {@link Vertex}'s {@link Vertex#id()} can be used as an input to
     * {@link org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource#V(Object...)}
     * to retrieve the {@link Vertex} and that the returned {@link Vertex} is the same.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_use_vertex_id_as_parameter() {
        // given an existing vertex
        Vertex marko = g.V().hasLabel("person").has("name", "marko").next();
        assertThat(marko).hasProperty("name", "marko");

        // then should be able to retrieve that same vertex by id.
        assertThat(g.V(marko.id()).next()).isEqualTo(marko);
    }

    /**
     * Ensures that a previously returned {@link Edge}'s {@link Edge#id()} can be used as an input to
     * {@link org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource#E(Object...)}
     * to retrieve the {@link Edge} and that the returned {@link Edge} is the same.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_use_edge_is_as_parameter() {
        // given an existing edge
        Edge created = g.E().has("weight", 0.2f).next();

        assertThat(created)
                .hasProperty("weight", 0.2f)
                .hasInVLabel("software")
                .hasOutVLabel("person");

        // should be able to retrieve incoming and outgoing vertices by edge id
        Vertex in = g.E(created.id()).inV().next();
        Vertex out = g.E(created.id()).outV().next();

        // should resolve to lop
        assertThat(in)
                .hasLabel("software")
                .hasProperty("name", "lop")
                .hasProperty("lang", "java");

        // should resolve to marko, josh and peter whom created lop.
        assertThat(out)
                .hasLabel("person")
                .hasProperty("name", "peter");
    }

    /**
     * A sanity check that a returned {@link Vertex}'s id is a {@link Map}.  This test could break in the future
     * if the format of a vertex ID changes from a Map to something else in DSE.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_deserialize_vertex_id_as_map() {
        // given an existing vertex
        Vertex marko = g.V().hasLabel("person").has("name", "marko").next();

        // then id should be a map with expected values.
        // Note: this is pretty dependent on DSE Graphs underlying id structure which may vary in the future.
        @SuppressWarnings("unchecked")
        Map<String, String> id = (Map<String, String>) marko.id();
        assertThat(id)
                .hasSize(3)
                .containsEntry("~label", "person")
                .containsKey("community_id")
                .containsKey("member_id");
    }


    /**
     * Ensures that a traversal that returns a result of mixed types is interpreted as a {@link Map} with
     * {@link Object} values.  Also uses
     * {@link org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal#by(Traversal)} with an anonymous
     * traversal to get inbound 'created' edges and folds them into a list.
     * <p/>
     * Executes a vertex traversal that binds label 'a' and 'b' to vertex properties and label 'c' to vertices that
     * have edges from that vertex.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_handle_result_object_of_mixed_types() {
        // find all software vertices and select name, language, and find all vertices that created such software.
        List<Map<String, Object>> results = g.V().hasLabel("software")
                .as("a", "b", "c")
                .select("a", "b", "c")
                .by("name")
                .by("lang")
                .by(__.in("created").fold()).toList();

        // ensure that lop and ripple and their data are the results return.
        assertThat(results).extracting(m -> m.get("a")).containsOnly("lop", "ripple");

        for (Map<String, Object> result : results) {
            assertThat(result).containsOnlyKeys("a", "b", "c");
            // both software are written in java.
            assertThat(result.get("b")).isEqualTo("java");
            // ensure the created vertices match the creators of the software.
            @SuppressWarnings("unchecked")
            List<Vertex> vertices = (List<Vertex>) result.get("c");
            if (result.get("a").equals("lop")) {
                // lop, 'c' should contain marko, josh, peter.
                assertThat(vertices).extracting(TinkerGraphExtractors.vertexPropertyValue("name")).containsOnly("marko", "josh", "peter");
            } else {
                assertThat(vertices).extracting(TinkerGraphExtractors.vertexPropertyValue("name")).containsOnly("josh");
            }
        }
    }

    /**
     * Ensures that a traversal that returns a sub graph can be retrieved.
     * <p/>
     * The subgraph is all members in a knows relationship, thus is all people who marko knows and the
     * edges that connect them.
     */
    @Test(groups = "short")
    @DseVersion("5.0.4")
    public void should_handle_subgraph() {
        // retrieve a subgraph on the knows relationship, this omits the created edges.
        Graph graph = (Graph) g.E().hasLabel("knows").subgraph("subGraph").cap("subGraph").next();

        // there should only be 2 edges (since there are are only 2 knows relationships) and 3 vertices
        assertThat(graph.edges()).hasSize(2);
        assertThat(graph.vertices()).hasSize(3);
    }

    /**
     * Ensures a traversal that yields no results is properly retrieved and is empty.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_return_zero_results() {
        assertThat(g.V().hasLabel("notALabel").toList()).isEmpty();
    }

    /**
     * Ensures that a traversal that yields a vertex with a property that has its own properties that is appropriately
     * parsed and made accessible via {@link VertexProperty#property}.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_parse_meta_properties() {
        // given a schema that defines meta properties.
        executeGraph(GraphFixtures.metaProps);

        // when adding a vertex with that meta property
        Vertex v = g.addV("meta_v").property("meta_prop", "hello", "sub_prop", "hi", "sub_prop2", "hi2").next();

        // then the created vertex should have the meta prop present with its sub properties.
        assertThat(v).hasProperty("meta_prop");
        VertexProperty<String> metaProp = v.property("meta_prop");
        assertThat(metaProp)
                .hasValue("hello")
                .hasProperty("sub_prop", "hi")
                .hasProperty("sub_prop2", "hi2");
    }

    /**
     * Ensures that a traversal that yields a vertex with a property name that is present multiple times that the
     * properties are parsed and made accessible via {@link Vertex#properties(String...)}.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_parse_multiple_cardinality_properties() {
        // given a schema that defines multiple cardinality properties.
        executeGraph(GraphFixtures.multiProps);

        // when adding a vertex with a multiple cardinality property
        Vertex v = g.addV("multi_v")
                .property("multi_prop", "Hello")
                .property("multi_prop", "Sweet")
                .property("multi_prop", "World").next();

        // then the created vertex should have the multi-cardinality property present with its values.
        assertThat(v).hasProperty("multi_prop");
        Iterator<VertexProperty<String>> multiProp = v.properties("multi_prop");
        assertThat(multiProp).extractingResultOf("value").containsExactly("Hello", "Sweet", "World");
    }

    /**
     * Validates that a traversal returning a {@link Tree} structure is returned appropriately with the expected
     * contents.
     * <p/>
     * Retrieves trees of people marko knows and the software they created.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_parse_tree() {
        // Get a tree structure showing the paths from mark to people he knows to software they've created.
        @SuppressWarnings("unchecked")
        Tree<String> tree = g.V().hasLabel("person").out("knows").out("created").tree().by("name").next();

        // Marko knows josh who created lop and ripple.
        assertThat(tree)
                .tree("marko")
                .tree("josh")
                .tree("lop")
                .isLeaf();

        assertThat(tree)
                .tree("marko")
                .tree("josh")
                .tree("ripple")
                .isLeaf();
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
        List<Object> software = g.V().hasLabel("person")
                .filter(__.has("name", "marko"))
                .out("knows")
                .flatMap(__.out("created"))
                .map(__.values("name")).fold().next();

        // Marko only knows josh and vadas, of which josh created lop and ripple.
        assertThat(software).containsOnly("lop", "ripple");
    }

    /**
     * Validates that {@link org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal#tryNext()}
     * functions appropriate by returning an {@link Optional} of which the presence of the underlying data depends
     * on whether or not remaining data is present.
     * <p>
     * This is more of a test of Tinkerpop than the protocol between the client and DSE graph.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_handle_tryNext() {
        GraphTraversal<Vertex, Vertex> traversal = g.V().hasLabel("person").has("name", "marko");

        // value present
        Optional<Vertex> v0 = traversal.tryNext();
        assertThat(v0.isPresent()).isTrue();
        //noinspection OptionalGetWithoutIsPresent
        assertThat(v0.get()).hasProperty("name", "marko");

        // value absent as there was only 1 matching vertex.
        Optional<Vertex> v1 = traversal.tryNext();
        assertThat(v1.isPresent()).isFalse();
    }

    /**
     * Validates that {@link GraphTraversal#toStream()} appropriately creates a stream from the underlying iterator
     * on the traversal, and then an attempt to call toStream again yields no results.
     * <p>
     * This is more of a test of Tinkerpop than the protocol between the client and DSE graph.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_handle_streaming() {
        GraphTraversal<Vertex, Vertex> traversal = g.V().hasLabel("person");
        // retrieve all person vertices to stream, and filter on client side all persons under age 30 and map to their name.
        List<String> under30 = traversal.toStream()
                .filter(v -> v.<Integer>property("age").value() < 30)
                .map(v -> v.<String>property("name").value())
                .collect(Collectors.toList());

        assertThat(under30).containsOnly("marko", "vadas");

        // attempt to get a stream again, which should be empty.
        assertThat(traversal.toStream().collect(Collectors.toList())).isEmpty();
    }

    /**
     * Validates that when traversing a path and labeling some of the elements during the traversal that the
     * output elements are properly labeled.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_resolve_path_with_some_labels() {
        // given a traversal where some objects have labels.
        List<Path> paths = g.V().hasLabel("person")
                .has("name", "marko").as("a")
                .outE("knows")
                .inV().as("c", "d")
                .outE("created").as("e", "f", "g")
                .inV()
                .path().toList();

        // then the paths returned should be labeled for the
        // appropriate objects, and not labeled otherwise.
        for (Path path : paths) {
            validatePathObjects(path);
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
        // given a traversal where all objects have labels.
        List<Path> paths = g.V().hasLabel("person")
                .has("name", "marko").as("a")
                .outE("knows").as("b")
                .inV().as("c", "d")
                .outE("created").as("e", "f", "g")
                .inV().as("h")
                .path().toList();

        // then the paths returned should be labeled for all
        // objects.
        for (Path path : paths) {
            validatePathObjects(path);
            Assertions.assertThat(path.labels()).hasSize(5);
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
        // given a traversal where no objects have labels.
        List<Path> paths = g.V().hasLabel("person")
                .has("name", "marko")
                .outE("knows")
                .inV()
                .outE("created")
                .inV()
                .path().toList();

        // then the paths returned should be labeled for
        // all objects.
        for (Path path : paths) {
            validatePathObjects(path);
            for (int i = 0; i < 5; i++)
                assertThat(path).hasNoLabel(i);
        }
    }

    @Test(groups = "short")
    public void should_handle_asynchronous_execution() {
        StringBuilder names = new StringBuilder();

        CompletableFuture<List<Vertex>> future = g.V().hasLabel("person").promise(Traversal::toList);
        try {
            // dumb processing to make sure the completable future works correctly and correct results are returned
            future.thenAccept(vertices -> vertices.forEach(vertex -> names.append((String)vertex.value("name")))).get();
        } catch (InterruptedException | ExecutionException e) {
            fail("Shouldn't have thrown an exception waiting for the result to complete");
        }

        assertThat(names.toString()).contains("peter", "marko", "vadas", "josh");
    }

    /**
     * Validates that if a traversal is made that encounters an error on the server side that the exception
     * is set on the future.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    @DseVersion("5.1.0")
    public void should_fail_future_returned_from_promise_on_query_error() throws Exception {
        CompletableFuture<?> future = g.V("invalidid").promise(Traversal::next);

        try {
            future.get();
        } catch (ExecutionException e) {
            assertThat(e.getCause()).isInstanceOf(InvalidQueryException.class);
        }
    }

    /**
     * A simple smoke test to ensure that a user can supply a custom {@link GraphTraversalSource} for use with DSLs.
     *
     * @test_category dse:graph
     */
    @Test(groups = "short")
    public void should_allow_use_of_dsl() throws Exception {
        SocialTraversalSource gSocial = DseGraph.traversal(session(), SocialTraversalSource.class);
        List<Vertex> vertices = gSocial.persons("marko").knows("vadas").toList();
        assertThat(vertices.size()).isEqualTo(1);
        assertThat(vertices.get(0))
                .hasProperty("name", "marko")
                .hasProperty("age", 29)
                .hasLabel("person");
    }

    /**
     * Ensures that the given Path matches one of the exact traversals we'd expect for a person whom Marko
     * knows that has created software and what software that is.
     * <p/>
     * These paths should be:
     * <ul>
     * <li>marko -> knows -> josh -> created -> lop</li>
     * <li>marko -> knows -> josh -> created -> ripple</li>
     * </ul>
     */
    private void validatePathObjects(Path path) {
        // marko should be the origin point.
        assertThat(path)
                .vertexAt(0)
                .hasLabel("person")
                .hasProperty("name", "marko")
                .hasProperty("age", 29);

        // there should be a 'knows' outgoing relationship between marko and josh.
        assertThat(path)
                .edgeAt(1)
                .hasLabel("knows")
                .hasProperty("weight", 1.0f)
                .hasOutVLabel("person")
                .hasInVLabel("person");

        // josh ...
        assertThat(path)
                .vertexAt(2)
                .hasLabel("person")
                .hasProperty("name", "josh")
                .hasProperty("age", 32);

        if (path.<Vertex>get(4).property("name").value().equals("lop")) {
            // there should b ea 'created' relationship between josh and lop.
            assertThat(path)
                    .edgeAt(3)
                    .hasLabel("created")
                    .hasProperty("weight", 0.4f)
                    .hasOutVLabel("person")
                    .hasInVLabel("software");

            assertThat(path)
                    .vertexAt(4)
                    .hasLabel("software")
                    .hasProperty("name", "lop")
                    .hasProperty("lang", "java");
        } else {
            // there should b ea 'created' relationship between josh and ripple.
            assertThat(path)
                    .edgeAt(3)
                    .hasLabel("created")
                    .hasProperty("weight", 1.0f)
                    .hasOutVLabel("person")
                    .hasInVLabel("software");

            assertThat(path)
                    .vertexAt(4)
                    .hasLabel("software")
                    .hasProperty("name", "ripple")
                    .hasProperty("lang", "java");
        }
    }
}
