/*
 *      Copyright (C) 2012-2016 DataStax Inc.
 *
 *      This software can be used solely with DataStax Enterprise. Please consult the license at
 *      http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.dse.graph.remote;

import com.datastax.driver.core.CCMBridge;
import com.datastax.driver.core.CCMConfig;
import com.datastax.driver.core.CCMWorkload;
import com.datastax.driver.core.utils.DseVersion;
import com.datastax.driver.dse.graph.GraphFixtures;
import com.datastax.dse.graph.CCMTinkerPopTestsSupport;
import com.datastax.dse.graph.api.predicates.Geo;
import com.datastax.dse.graph.api.predicates.Search;
import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DseVersion(major = 5.0, minor = 3, description = "DSE 5.0.3 required for remote TinkerPop support")
@CCMConfig(workloads = @CCMWorkload({"solr", "graph"}), jvmArgs = "-Duser.language=en")
@SuppressWarnings("unchecked")
public class SearchIntegrationTest extends CCMTinkerPopTestsSupport {

    SearchIntegrationTest() {
        super(true);
    }

    @Override
    public void onTestContextInitialized() {
        super.onTestContextInitialized();
        executeGraph(GraphFixtures.addressBook(CCMBridge.getDSEVersion()));
        // arbitrary sleep to deal with index time.
        // TODO: Find a better way of dealing with this uncertainty.
        Uninterruptibles.sleepUninterruptibly(20, TimeUnit.SECONDS);
    }

    /**
     * Validates that a graph traversal can be made by using a Search prefix predicate on a string-search-indexed
     * property.
     * <p/>
     * Finds all 'user' vertices having a 'full_name' property beginning with 'Paul'.
     *
     * @test_category dse:graph
     */
    @Test(groups = "long")
    public void search_by_prefix() {
        // Only one user with full_name starting with Paul.
        GraphTraversal traversal = g.V().has("user", "full_name", Search.prefix("Paul")).values("full_name");
        assertThat(traversal.toList()).containsOnly("Paul Thomas Joe");
    }

    /**
     * Validates that a graph traversal can be made by using a Search regex predicate on a string-search-indexed
     * property.
     * <p/>
     * Finds all 'user' vertices having a 'full_name' property matching regex '.*Paul.*'.
     *
     * @test_category dse:graph
     */
    @Test(groups = "long")
    public void search_by_regex() {
        // Only two people with names containing pattern for Paul.
        GraphTraversal traversal = g.V().has("user", "full_name", Search.regex(".*Paul.*")).values("full_name");
        assertThat(traversal.toList()).containsOnly("Paul Thomas Joe", "James Paul Joe");
    }

    /**
     * Validates that a graph traversal can be made by using a Search fuzzy predicate on a string-search-indexed
     * property.
     * <p/>
     * Finds all 'user' vertices having a 'alias' property matching 'mario' with a fuzzy distance of 1.
     *
     * @test_category dse:graph
     */
    @Test(groups = "long")
    @DseVersion(major = 5.1)
    public void search_by_fuzzy() {
        // Alias matches 'mario' fuzzy
        GraphTraversal traversal = g.V().has("user", "alias", Search.fuzzy("mario", 1)).values("full_name");
        // Should match 'Paul Thomas Joe' since alias is 'mario'
        // Should match 'George Bill Steve' since alias is 'wario' watch matches 'mario' within a distance of 1.
        assertThat(traversal.toList()).containsOnly("Paul Thomas Joe", "George Bill Steve");
    }

    /**
     * Validates that a graph traversal can be made by using an 'inside' distance predicate on a string-search-indexed
     * property.
     * <p/>
     * Finds all 'user' vertices having a 'coordinates' property 2 units within -94,44.
     *
     * @test_category dse:graph
     */
    @Test(groups = "long")
    public void search_by_distance() {
        // Should only be two people within 2 units of (-92, 44) (Rochester, Minneapolis)
        GraphTraversal traversal = g.V().has("user", "coordinates", Geo.inside(Geo.point(-92, 44), 2)).values("full_name");
        assertThat(traversal.toList()).containsOnly("Paul Thomas Joe", "George Bill Steve");
    }

    /**
     * Validates that a graph traversal can be made by using a Search token predicate on a text-search-indexed property.
     * <p/>
     * Finds all 'user' vertices having a 'description' property containing the token 'cold'.
     *
     * @test_category dse:graph
     */
    @Test(groups = "long")
    public void search_by_token() {
        // Description containing token 'cold'
        GraphTraversal traversal = g.V().has("user", "description", Search.token("cold")).values("full_name");
        assertThat(traversal.toList()).containsOnly("Jill Alice", "George Bill Steve");
    }

    /**
     * Validates that a graph traversal can be made by using a Search token prefix predicate on a text-search-indexed
     * property.
     * <p/>
     * Finds all 'user' vertices having a 'description' containing the token prefix 'h'.
     */
    @Test(groups = "long")
    public void search_by_token_prefix() {
        // Description containing a token starting with h
        GraphTraversal traversal = g.V().has("user", "description", Search.tokenPrefix("h")).values("full_name");
        assertThat(traversal.toList()).containsOnly("Paul Thomas Joe", "James Paul Joe");
    }

    /**
     * Validates that a graph traversal can be made by using a Search token regex predicate on a text-search-indexed
     * property.
     * <p/>
     * Finds all 'user' vertices having a 'description' containing the token regex '(nice|hospital)'.
     */
    @Test(groups = "long")
    public void search_by_token_regex() {
        // Description containing nice or hospital
        GraphTraversal traversal = g.V().has("user", "description", Search.tokenRegex("(nice|hospital)")).values("full_name");
        assertThat(traversal.toList()).containsOnly("Paul Thomas Joe", "Jill Alice");
    }


    /**
     * Validates that a graph traversal can be made by using a Search fuzzy predicate on a text-search-indexed
     * property.
     * <p/>
     * Finds all 'user' vertices having a 'description' property matching 'lives' with a fuzzy distance of 1.
     *
     * @test_category dse:graph
     */
    @Test(groups = "long")
    @DseVersion(major = 5.1)
    public void search_by_token_fuzzy() {
        // Description containing 'lives' fuzzy
        GraphTraversal traversal = g.V().has("user", "description", Search.tokenFuzzy("lives", 1)).values("full_name");
        // Should match 'Paul Thomas Joe' since description contains 'Lives'
        // Should match 'James Paul Joe' since description contains 'Likes'
        assertThat(traversal.toList()).containsOnly("Paul Thomas Joe", "James Paul Joe");
    }

    /**
     * Validates that a graph traversal can be made by using a Search phrase predicate on a text-search-indexed
     * property.
     * <p/>
     * Finds all 'user' vertices having a 'description' property matching 'a cold' with a distance of 2.
     *
     * @test_category dse:graph
     */
    @Test(groups = "long")
    @DseVersion(major = 5.1)
    public void search_by_phrase() {
        // Full name contains phrase "Paul Joe"
        GraphTraversal traversal = g.V().has("user", "description", Search.phrase("a cold", 2)).values("full_name");
        // Should match 'George Bill Steve' since 'A cold dude' is at distance of 0 for 'a cold'.
        // Should match 'Jill Alice' since 'Enjoys a very nice cold coca cola' is at distance of 2 for 'a cold'.
        assertThat(traversal.toList()).containsOnly("George Bill Steve", "Jill Alice");
    }

}