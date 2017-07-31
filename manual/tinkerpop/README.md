## Apache TinkerPop™ client integration

As an alternative to its [native graph API](../graph/), the DataStax Enterprise Java driver provides an integration 
layer to interact with DSE through the [Apache TinkerPop™][tinkerpop] library.  This integration has also been referred
to as the '[Fluent API][fluent-api]'.

This component is published in Maven central as a separate artifact:

```xml
<dependency>
  <groupId>com.datastax.dse</groupId>
  <artifactId>dse-java-driver-graph</artifactId>
  <version>1.3.0</version>
</dependency>
```

### DataStax drivers execution compatibility

This new package provides the necessary tools to get the most out the popular _Apache TinkerPop_
Traversal API, while still getting the benefits of the DataStax drivers execution model.
A Traversal is considered as a query, that can then be wrapped inside a [GraphStatement][GraphStatement].

Here's how to create a _DSE Java driver_'s _GraphStatement_ out of a _Apache TinkerPop_
[Traversal][Traversal]:

```java
// traversal() returns a simple GraphTraversalSource that is not meant to be iterated itself
GraphTraversalSource g = DseGraph.traversal();

GraphStatement graphStatement = DseGraph.statementFromTraversal(g.V().has("name", "marko"));
GraphResultSet grs = dseSession.executeGraph(graphStatement);

// the API does not change, whether what's executed is a String, or a Statement created from a Traversal, and so on.
for (GraphNode graphNode : grs) {
    Vertex v = graphNode.asVertex();
}
```

Statements created from _Traversal_ instances will behave, with regards to the cluster,
the same way the _DSE Java driver_ behaves with DSE. All the advanced features of the _DSE Java driver_ come into play, 
automatic retries, load balancing, DataCenter awareness, smart query logging, and so on.

Information about the returned result types with the _DSE Java driver_ can be found on 
[this page](http://docs.datastax.com/en/developer/java-driver-dse/1.2/manual/graph/#handling-results).

### TinkerPop direct compatibility
This package also provides full compatibility with _Apache TinkerPop_'s query execution model
 and result types.

Here's an example of how to get a _Apache TinkerPop_ [GraphTraversalSource][GraphTraversalSource] that is remotely
connected to a _DseGraph_ server, communicating internally via the _DSE Java driver_:

```java
DseCluster dseCluster = DseCluster.builder()
    .addContactPoint("1.2.3.4")
    .withGraphOptions(new GraphOptions()
    .setGraphName("mygraph"))
    .build();
DseSession dseSession = dseCluster.connect();

GraphTraversalSource g = DseGraph.traversal(dseSession);

// Now you can use the Traversal source and use it **as if** it was working against a local graph, and with the usual TinkerPop API. All the communication with the DSE Graph server is done transparently.
List<Vertex> vertices = g.V().hasLabel("person").toList();
```

Traversal sources with different configurations can easily be created. By default the options
specific to _DSE Graph_ are taken from the [DseCluster][DseCluster] configuration, however
the API exposes a way to override each individual setting, per traversal source:

```java
GraphTraversalSource gOLTP = DseGraph.traversal(dseSession, new GraphOptions().setGraphName("mygraph"));
GraphTraversalSource gOLAP = DseGraph.traversal(dseSession, new GraphOptions().setGraphName("myothergraph").setGraphSource(ANALYTICS_SOURCE_NAME));

Vertex v = gOLTP.V().has("name", "marko").next();
long count = gOLAP.V().count().next();
```

Please note that there is no interactivity with DSE Graph until a [Terminal Step][TerminalStep] (such as `next`,
`toList`, etc.) is performed.


#### A word on Results

Objects returned after the Remote traversal execution are objects that are detached from
the original DSE Graph on the server. A _detached_ element is an element that lives on
its own, even though the _detached_ elements contains the complete data, modifications
made to a _detached_ element **do not affect** the data stored in the _DSE Graph_.


### Search and Geo

For ease of use, _DSE Search_ and _Geo_ predicates are directly integrated and provided
in the programmatic API:

```java
GraphTraversalSource g = DseGraph.traversal(dseSession, new GraphOptions().setGraphName("thegraph"));
Vertex v = g.V().has("textProp", Search.tokenPrefix("1")).next();
```

Or:

```java
GraphTraversalSource g = DseGraph.traversal(dseSession, new GraphOptions().setGraphName("thegraph"));

Vertex v1 = g.addV(T.label, "geopoint", "point", Geo.point(50, 50)).next();
Vertex v2 = g.addV(T.label, "geopoint", "point", Geo.point(120, 120)).next();

List<Vertex> v3 = g.V().has("point", Geo.inside(49, 49, 4)).toList();
        
assert v3.size() == 1;
assert v1 == v3.get(0);
```

Please check out the Javadoc of the [Geo][Geo] and [Search][Search] classes for more information.
 
### Gremlin Domain Specific Languages (DSL)

The Gremlin language can be extended by users to match the user's specific use cases and make
the development of graph traversals easier.

Users may require a `GraphTraversal` class that exposes their domain-specific grammar and methods
and need this new grammar via the Fluent API.

As of DSE Java driver 1.4.0, the [DseGraph][DseGraph] class exposes new additional utilities 
to create a traversal source equipped with custom user-defined traversal methods easily.

After generating a custom `GraphTraversalSource` as explained in the [TinkerPop documentation][DSL-tp-docs], 
users may use it directly to create a `GraphStatement` out of a traversal, or iterate a 
connected traversal.

Here's an example using `GraphStatement`s and explicit execution through the `DseSession`:

```java
// see TinkerPop documentation link for the generation of SocialTraversalSource
SocialTraversalSource gSocial = DseGraph.traversal(SocialTraversalSource.class);

GraphStatement gs = DseGraph.statementFromTraversal(gSocial.persons("marko").knows("vadas"));

GraphResultSet rs = dseSession.executeGraph(gs);
```

Using the direct iteration system from a **connected** TinkerPop custom traversal source:

```java
SocialTraversalSource gSocial = DseGraph.traversal(dseSession, SocialTraversalSource.class);
List<Vertex> vertices = gSocial.persons("marko").knows("vadas").toList();
```

### Programmatic Schema API

Available soon!...

[tinkerpop]: http://tinkerpop.apache.org/
[fluent-api]: https://datastax-oss.atlassian.net/browse/JAVA-1250
[DseCluster]: http://docs.datastax.com/en/drivers/java-dse/1.2/com/datastax/driver/dse/DseCluster.html
[Geo]: http://docs.datastax.com/en/drivers/java-dse/1.2/com/datastax/dse/graph/api/predicates/Geo.html
[GraphStatement]: http://docs.datastax.com/en/drivers/java-dse/1.2/com/datastax/driver/dse/graph/GraphStatement.html
[GraphTraversalSource]: https://tinkerpop.apache.org/javadocs/3.2.4/full/org/apache/tinkerpop/gremlin/process/traversal/dsl/graph/GraphTraversalSource.html
[Search]: http://docs.datastax.com/en/drivers/java-dse/1.2/com/datastax/dse/graph/api/predicates/Search.html
[TerminalStep]: http://tinkerpop.apache.org/docs/current/reference/#terminal-steps
[Traversal]: https://tinkerpop.apache.org/javadocs/3.2.4/full/org/apache/tinkerpop/gremlin/process/traversal/Traversal.html
[DseGraph]: http://docs.datastax.com/en/drivers/java-dse/1.4/com/datastax/dse/graph/api/DseGraph.html
[DSL-tp-docs]: http://tinkerpop.apache.org/docs/current/reference/#dsl
