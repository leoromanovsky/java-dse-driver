## Manual

### Quick start

Here's a short program that connects to DSE and executes a CQL query:

```java
import com.datastax.driver.dse.DseCluster;
import com.datastax.driver.dse.DseSession;

DseCluster cluster = null;
try {
    cluster = DseCluster.builder()                                               // (1)
            .addContactPoint("127.0.0.1")
            .build();
    DseSession session = cluster.connect();                                      // (2)

    Row row = session.execute("select release_version from system.local").one(); // (3)
    System.out.println(row.getString("release_version"));                        // (4)
} finally {
    if (cluster != null) cluster.close();                                        // (5)
}
```

1. the [DseCluster] object is the main entry point of the driver. It holds the known state of the actual DSE cluster
   (notably the [Metadata](metadata/)). This class is thread-safe, you should create a single instance (per target
   DSE cluster), and share it throughout your application;
2. the [DseSession] is what you use to execute queries. Likewise, it is thread-safe and should be reused;
3. we use `execute` to send a query to DSE. This returns a [ResultSet], which is essentially a collection of [Row]
   objects. On the next line, we extract the first row (which is the only one in this case);
4. we extract the value of the first (and only) column from the row;
5. finally, we close the cluster after we're done with it. This will also close any session that was created from this
   cluster. This step is important because it frees underlying resources (TCP connections, thread pools...). In a real
   application, you would typically do this at shutdown (for example, when undeploying your webapp).

Note: this example uses the synchronous API. Most methods have [asynchronous](async/) equivalents.

*For users familiar with the DataStax driver for Cassandra, `DseCluster` and `DseSession` wrap their pure-CQL
equivalents. You can use `DseSession` as a drop-in replacement for `Session`. In fact the DSE driver also includes these
classes, but you'll typically use a `DseCluster` to interact with DSE.*

### Troubleshooting connection issues

If the example above fails to connect (throwing `NoHostAvailableException`), check that the contact points are accessible
from the client machine, for example:

```
telnet 1.2.3.4 9042
```

Here are some common mistakes:

* using the wrong address. See [Address resolution](address_resolution/) for an explanation of the various addresses that
  can be configured server-side, and which one should be used by the client.
* using the wrong port. The value passed to `Cluster.builder().withPort()` (default 9042 )should match `native_transport_port`
  in `cassandra.yaml`.
* on older server versions, the native transport must be enabled explicitly. See `start_native_transport` in `cassandra.yaml`.


### Setting up the driver

#### [DseCluster]

##### Creating an instance

[DseCluster.Builder] provides a fluent API:

```java
DseCluster cluster = DseCluster.builder()
        .withClusterName("myCluster")
        .addContactPoint("127.0.0.1")
        .build();
```


##### Creation options

The only required option is the list of contact points, i.e. the hosts that the driver will initially contact to
discover the cluster topology. You can provide a single contact point, but it is usually a good idea to provide more, so
that the driver can fallback if the first one is down.

The other aspects that you can configure on the `DseCluster` are:

* [address translation](address_resolution/);
* [authentication](auth/);
* [compression](compression/);
* [load balancing](load_balancing/);
* [metrics](metrics/);
* low-level [Netty configuration][NettyOptions];
* [query options][QueryOptions];
* [reconnections](reconnection/);
* [retries](retries/);
* [socket options](socket_options/);
* [SSL](ssl/);
* [speculative executions](speculative_execution/);
* [query timestamps](query_timestamps/);
* [graph options](graph/).

In addition, you can register various types of listeners to be notified of cluster events; see [Host.StateListener],
[LatencyTracker], and [SchemaChangeListener].

##### Cluster initialization

A freshly-built `DseCluster` instance does not initialize automatically; that will be triggered by one of the following
actions:

* an explicit call to `cluster.init()`;
* a call to `cluster.getMetadata()`;
* creating a session with `cluster.connect()` or one of its variants;
* calling `session.init()` on a session that was created with `cluster.newSession()`.

The initialization sequence is the following:

* initialize internal state (thread pools, utility components, etc.);
* try to connect to each of the contact points in sequence. The order is not deterministic (in fact, the driver shuffles
  the list to avoid hotspots if a large number of clients share the same contact points). If no contact point replies,
  a [NoHostAvailableException] is thrown and the process stops here;
* otherwise, the successful contact point is elected as the [control host](control_connection/). The driver negotiates
  the [native protocol version](native_protocol/) with it, and queries its system tables to discover the addresses of
  the other hosts.

Note that, at this stage, only the control connection has been established. Connections to other hosts will only be
opened when a session gets created.

#### [DseSession]

By default, a session isn't tied to any specific keyspace. You'll need to prefix table names in your queries:

```java
Session session = cluster.connect();
session.execute("select * from myKeyspace.myTable where id = 1");
```

You can also specify a keyspace name at construction time, it will be used as the default when table names are not
qualified:

```java
DseSession session = cluster.connect("myKeyspace");
session.execute("select * from myTable where id = 1");
session.execute("select * from otherKeyspace.otherTable where id = 1");
```

You might be tempted to open a separate session for each keyspace used in your application; however, note that
[connection pools](pooling/) are created at the session level, so each new session will consume additional system
resources:

```java
// Warning: creating two sessions doubles the number of TCP connections opened by the driver
DseSession session1 = cluster.connect("ks1");
DseSession session2 = cluster.connect("ks2");
```

Finally, if you issue a `USE` statement, it will change the default keyspace on that session:

```java
DseSession session = cluster.connect();
// No default keyspace set, need to prefix:
session.execute("select * from myKeyspace.myTable where id = 1");

session.execute("USE myKeyspace");
// Now the keyspace is set, unqualified query works:
session.execute("select * from myTable where id = 1");
```

Be very careful though: if the session is shared by multiple threads, switching the keyspace at runtime could easily
cause unexpected query failures.


### Running queries

You run queries with the session's `execute` method:

```java
ResultSet rs = session.execute("select release_version from system.local");
```

As shown here, the simplest form is to pass a query string directly. You can also pass an instance of
[Statement](statements/).

#### Processing rows

Executing a query produces a [ResultSet], which is an iterable of [Row]. The basic way to process all rows is to use
Java's for-each loop:

```java
for (Row row : rs) {
    // process the row
}
```

Note that this will return **all results** without limit (even though the driver might use multiple queries in the
background). To handle large result sets, you might want to use a `LIMIT` clause in your CQL query, or use one of the
techniques described in the [paging](paging/) documentation.

When you know that there is only one row (or are only interested in the first one), the driver provides a convenience
method:

```java
Row row = rs.one();
```

#### Reading columns

[Row] provides getters to extract column values; they can be either positional or named:

```java
Row row = session.execute("select first_name, last_name from users where id = 1").one();

// The two are equivalent:
String firstName = row.getString(0);
String firstName = row.getString("first_name");
```

##### CQL to Java type mapping

<table border="1" style="text-align:center; width:100%;margin-bottom:1em;">
    <tr> <td><b>CQL3 data type</b></td> <td><b>Getter name</b></td> <td><b>Java type</b></td> </tr>
    <tr> <td>ascii</td> <td>getString</td> <td>java.lang.String</td> </tr>
    <tr> <td>bigint</td> <td>getLong</td> <td>long</td> </tr>
    <tr> <td>blob</td> <td>getBytes</td> <td>java.nio.ByteBuffer</td> </tr>
    <tr> <td>boolean</td> <td>getBool</td> <td>boolean</td> </tr>
    <tr> <td>counter</td> <td>getLong</td> <td>long</td> </tr>
    <tr> <td>date</td> <td>getDate</td> <td><a href="http://docs.datastax.com/en/drivers/java-dse/1.2/com/datastax/driver/core/LocalDate.html">LocalDate</a></td> </tr>
    <tr> <td>decimal</td> <td>getDecimal</td> <td>java.math.BigDecimal</td> </tr>
    <tr> <td>double</td> <td>getDouble</td> <td>double</td> </tr>
    <tr> <td>float</td> <td>getFloat</td> <td>float</td> </tr>
    <tr> <td>inet</td> <td>getInet</td> <td>java.net.InetAddress</td> </tr>
    <tr> <td>int</td> <td>getInt</td> <td>int</td> </tr>
    <tr> <td>list</td> <td>getList</td> <td>java.util.List<T></td> </tr>
    <tr> <td>map</td> <td>getMap</td> <td>java.util.Map<K, V></td> </tr>
    <tr> <td>set</td> <td>getSet</td> <td>java.util.Set<T></td> </tr>
    <tr> <td>smallint</td> <td>getShort</td> <td>short</td> </tr>
    <tr> <td>text</td> <td>getString</td> <td>java.lang.String</td> </tr>
    <tr> <td>time</td> <td>getTime</td> <td>long</td> </tr>
    <tr> <td>timestamp</td> <td>getTimestamp</td> <td>java.util.Date</td> </tr>
    <tr> <td>timeuuid</td> <td>getUUID</td> <td>java.util.UUID</td> </tr>
    <tr> <td>tinyint</td> <td>getByte</td> <td>byte</td> </tr>
    <tr> <td>tuple</td> <td>getTupleValue</td> <td><a href="tuples/">TupleValue</a></td> </tr>
    <tr> <td>user-defined types</td> <td>getUDTValue</td> <td><a href="udts/">UDTValue</a></td> </tr>
    <tr> <td>uuid</td> <td>getUUID</td> <td>java.util.UUID</td> </tr>
    <tr> <td>varchar</td> <td>getString</td> <td>java.lang.String</td> </tr>
    <tr> <td>varint</td> <td>getVarint</td> <td>java.math.BigInteger</td> </tr>
</table>

In addition to these default mappings, you can register your own types with [custom codecs](custom_codecs/).

##### Primitive types

For performance reasons, the driver uses primitive Java types wherever possible (`boolean`, `int`...); the CQL value
`NULL` is encoded as the type's default value (`false`, `0`...), which can be ambiguous. To distinguish `NULL` from
actual values, use `isNull`:

```java
Integer age = row.isNull("age") ? null : row.getInt("age");
```

##### Collection types

To ensure type safety, collection getters are generic. You need to provide type parameters matching your CQL type when
calling the methods:

```java
// Assuming given_names is a list<text>:
List<String> givenNames = row.getList("given_names", String.class);
```

For nested collections, element types are generic and cannot be expressed as Java `Class` instances. We use Guava's
[TypeToken](https://github.com/google/guava/wiki/ReflectionExplained) instead:

```java
// Assuming teams is a set<list<text>>:
TypeToken<List<String>> listOfStrings = new TypeToken<List<String>>() {};
Set<List<String>> teams = row.getSet("teams", listOfStrings);
```

Since type tokens are anonymous inner classes, it's recommended to store them as constants in a utility class instead of
re-creating them each time.

##### Row metadata

`Row` exposes an API to explore the column metadata at runtime:

```java
for (ColumnDefinitions.Definition definition : row.getColumnDefinitions()) {
    System.out.printf("Column %s has type %s%n",
            definition.getName(),
            definition.getType());
}
```


### More information

If you're reading this from the [generated HTML documentation on
datastax.com](http://docs.datastax.com/en/developer/java-driver-dse/1.1/), use the "Contents"
menu on the right hand side to navigate sub-sections. If you're [browsing the source files on
github.com](https://github.com/datastax/java-dse-driver/tree/1.x/manual),
simply navigate to each sub-directory.

[DseCluster]:               http://docs.datastax.com/en/drivers/java-dse/1.2/com/datastax/driver/dse/DseCluster.html
[DseCluster.Builder]:       http://docs.datastax.com/en/drivers/java-dse/1.2/com/datastax/driver/dse/DseCluster.Builder.html
[DseSession]:               http://docs.datastax.com/en/drivers/java-dse/1.2/com/datastax/driver/dse/DseSession.html
[ResultSet]:                http://docs.datastax.com/en/drivers/java-dse/1.2/com/datastax/driver/core/ResultSet.html
[Row]:                      http://docs.datastax.com/en/drivers/java-dse/1.2/com/datastax/driver/core/Row.html
[NettyOptions]:             http://docs.datastax.com/en/drivers/java-dse/1.2/com/datastax/driver/core/NettyOptions.html
[QueryOptions]:             http://docs.datastax.com/en/drivers/java-dse/1.2/com/datastax/driver/core/QueryOptions.html
[SocketOptions]:            http://docs.datastax.com/en/drivers/java-dse/1.2/com/datastax/driver/core/SocketOptions.html
[Host.StateListener]:       http://docs.datastax.com/en/drivers/java-dse/1.2/com/datastax/driver/core/Host.StateListener.html
[LatencyTracker]:           http://docs.datastax.com/en/drivers/java-dse/1.2/com/datastax/driver/core/LatencyTracker.html
[SchemaChangeListener]:     http://docs.datastax.com/en/drivers/java-dse/1.2/com/datastax/driver/core/SchemaChangeListener.html
[NoHostAvailableException]: http://docs.datastax.com/en/drivers/java-dse/1.2/com/datastax/driver/core/exceptions/NoHostAvailableException.html
[LocalDate]:                http://docs.datastax.com/en/drivers/java-dse/1.2/com/datastax/driver/core/LocalDate.html
