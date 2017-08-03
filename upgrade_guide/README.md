## Upgrade guide

### 1.4.0

[GraphResultSet#getAvailableWithoutFetching()](http://docs.datastax.com/en/drivers/java-dse/1.4/com/datastax/driver/dse/graph/GraphResultSet.html#getAvailableWithoutFetching--)
is now considered deprecated as it may return incorrect values in cases where the server returns bulking
information (such as in the [barrier()](http://tinkerpop.apache.org/docs/current/reference/#barrier-step) step).
For a reliable count, use [GraphResultSet#all()](http://docs.datastax.com/en/drivers/java-dse/1.4/com/datastax/driver/dse/graph/GraphResultSet.html#all--).


### 1.3.0

The driver will now offer the possibility to use the GraphSON2 sub protocol for the 
Graph driver with string Gremlin queries ([JAVA-1329](https://datastax-oss.atlassian.net/browse/JAVA-1329)).
 
The sub protocol used by default stays GraphSON1. However GraphSON1 may cause problems of type
conversion happening during the serialization of the query to the DSE Graph server, or the deserialization
of the responses back from a string Gremlin query. GraphSON2 offers better support for
the complex data types handled by DSE Graph (for an exhaustive list of the data types supported
by the driver and DSE Graph see [this documentation](../manual/graph#data-types-compatibility-matrix).

Activating GraphSON2 can be done via [GraphOptions#setGraphSubprotocol(GraphProtocol)](http://docs.datastax.com/en/drivers/java-dse/1.3/com/datastax/driver/dse/graph/GraphOptions.html#setGraphSubProtocol-com.datastax.driver.dse.graph.GraphProtocol-)
however it may bring significant behavioral change at runtime. Some `GraphNode` methods allow
to extract nested fields from a result in an agnostic manner such as the `GraphNode#isValue()`,
`GraphNode#isObject()`, `GraphNode#isArray()`, ... methods. The behaviour of these methods 
may change as a side effect of using GraphSON2, since it is a more strictly typed sub protocol:

- The `isObject()` method will return `false` whenever the property to retrieve is not a `Map`.
 With GraphSON1 this method would return `true` for any Graph object such as mainly `Vertex`, `Edge`, `VertexProperty`,`Property` and `Path`.
 
- The `isValue()` method will return `true` for any Graph data type contained in the `GraphNode`
 that is not a `Map` or a `List`, including Graph types such as mainly `Vertex`, `Edge`, `VertexProperty`,`Property` and `Path`.
 With GraphSON1 this method would return `false` for the Graph types cited previously.
 
Using GraphSON2 shouldn't have an impact at runtime with regards to the `GraphNode#asXXXX()` methods if
the user is already following the [data type compatibilities guide](../manual/graph#data-types-compatibility-matrix) 
in association with their DSE Graph schema.
 
It is generally recommended to switch to GraphSON2 as it brings more consistent support for complex data types
in the Graph driver and will be activated by default in the next major version (i.e. DSE Java driver 2.0).


### 1.2.0

The coordinates of the driver artifacts have changed:
  
* core DSE driver: `com.datastax.dse:dse-java-driver-core`
* Apache Tinkerpop integration: `com.datastax.dse:dse-java-driver-graph`

DSE-specific versions of the mapper and extras are now published:

* `com.datastax.dse:dse-java-driver-mapping`
* `com.datastax.dse:dse-java-driver-extras`

The `SSLOptions` interface is now deprecated in favor of
`RemoteEndpointAwareSSLOptions`. 
Similarly, the two existing implementations of that interface, 
`JdkSSLOptions` and `NettySSLOptions`, 
are now deprecated in favor of `RemoteEndpointAwareJdkSSLOptions` 
and `RemoteEndpointAwareNettySSLOptions` respectively (see 
[JAVA-1364](https://datastax-oss.atlassian.net/browse/JAVA-1364)).

In 1.1, the driver would log a warning the first time it would skip 
a retry for a non-idempotent request; this warning has now been 
removed as users should now have adjusted their applications accordingly.

The `caseSensitive` field on `@Column` and `@Field` annotation now only
applies to the `name` field on the annotation and not the name of the
variable / method itself.  If you were previously depending on the
name of the field, you should add a `name` field to the annotation,
i.e.:  `@Column(name="userName", caseSensitive=true)`.


### cassandra-driver-dse-* to dse-driver-1.0.0

For previous versions of DSE, the driver extensions were published as a module of the core driver, under the coordinates
`com.datastax.cassandra:cassandra-driver-dse`. Starting with DSE 5, they become a standalone project:
`com.datastax.cassandra:dse-driver`. By separating the two projects, our goal is to allow separate lifecycles (for
example, we can release a patch version only for `dse-driver` if no core changes are needed).

In addition, we are switching to [semantic versioning] for the new project: each release number will now clearly express
the nature of the changes it contains (patches, new features or breaking changes). Since version numbers are strictly
codified by semver, following DSE server versioning is not possible; to make it clear that the two versioning schemes
are independent, we start the new driver project at 1.0.0.

[semantic versioning]: http://semver.org/

From an API perspective, `dse-driver` brings the following changes:

#### Dedicated cluster and session wrappers

The DSE driver now uses dedicated extensions of the core driver types: `DseCluster` and `DseSession`. Their main
advantage is to allow direct execution of [graph](../manual/graph/) statements.

See the root section of the [manual](../manual/) for more details.

#### Retries of idempotent statements

Historically, the driver retried failed queries indiscriminately. In recent versions of the core driver, the
[Statement#isIdempotent][idempotence] flag was introduced, to mark statements that are unsafe to retry when there is a
chance that they might have been applied already by a replica. To keep backward compatibility with previous versions,
the driver still retried these statements by default, and you had to configure a special retry policy to avoid retrying
them.

Starting with dse-driver-1.0.0, it is now the default behavior to **not retry** non-idempotent statements on write
timeouts or request errors. To help with the transition, a warning will be logged when the driver initializes, and the
first time a retry is aborted because of the `isIdempotent` flag (this warning will be removed in a future version).

Note that the driver does not position the `isIdempotent` flag automatically. Because it does not parse query strings,
it cannot determine if a particular query is idempotent or not. Therefore it takes a cautious approach and marks all
statements as non-idempotent by default. It is up to you to set the flag in your code if you know that your queries are
safe to retry.

Note that this behavior will also become the default in version 3.1.0 of the core driver.

[idempotence]: http://datastax.github.io/java-driver/manual/idempotence/
