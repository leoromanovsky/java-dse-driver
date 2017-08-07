# DataStax Enterprise Java driver

*If you're reading this on github.com, please note that this is the readme
for the development version and that some features described here might
not yet have been released. You can find the documentation for the latest
version through the [Java driver
docs](http://docs.datastax.com/en/developer/java-driver-dse/latest/index.html) or via the release tags,
[e.g.
1.3.0](https://github.com/datastax/java-driver-dse/tree/1.3.0).*

_**Feeback requested!** Help us focus our efforts, provide your input on 
the [Platform and Runtime Survey](http://goo.gl/forms/qwUE6qnL7U) (we kept it short)._

A modern, [feature-rich](manual/) and highly tunable Java client
library for DataStax Enterprise, built on top of the robust, efficient and highly 
configurable DataStax Java driver for Apache Cassandra®. The DataStax Enterprise Java 
driver is open source and can only be used with DataStax Enterprise as per [its license](#license).

**Features:**

* [Sync](manual/) and [Async](manual/async/) API
* [Simple](manual/statements/simple/), [Prepared](manual/statements/prepared/), and [Batch](manual/statements/batch/)
  statements
* Asynchronous IO, parallel execution, request pipelining
* [Connection pooling](manual/pooling/)
* Auto node discovery
* Automatic reconnection
* Configurable [load balancing](manual/load_balancing/) and [retry policies](manual/retries/)
* Works with any cluster size
* [Query builder](manual/statements/built/)
* [Object mapper](manual/object_mapper/)
* [`Authenticator` implementations](manual/auth/) that use the authentication scheme negotiation in the server-side
  `DseAuthenticator`;
* value classes for [geospatial types](manual/geo_types/), and type codecs that integrate them seamlessly with the
  driver;
* [DSE graph integration](manual/graph/).

The driver architecture is based on layers. At the bottom lies the driver core.
This core handles everything related to the connections to a DataStax Enterprise
cluster (for example, connection pool, discovering new nodes, etc.) and exposes a simple,
relatively low-level API on top of which higher level layers can be built.

The driver contains the following modules:

- driver-core: the core layer.
- driver-mapping: the object mapper.
- driver-extras: optional features for the Java driver.
- driver-examples: example applications using the other modules which are
  only meant for demonstration purposes.
- driver-tests: tests for the java-driver.
- driver-graph: the DSE graph and Tinkerpop compatibility module.

**Useful links:**

- JIRA (bug tracking): https://datastax-oss.atlassian.net/browse/JAVA
- MAILING LIST: https://groups.google.com/a/lists.datastax.com/forum/#!forum/java-driver-user
- DATASTAX ACADEMY SLACK: #datastax-drivers on https://academy.datastax.com/slack 
- TWITTER: [@dsJavaDriver](https://twitter.com/dsJavaDriver) tweets Java
  driver releases and important announcements (low frequency).
  [@DataStaxEng](https://twitter.com/datastaxeng) has more news including
  other drivers, Cassandra, and DSE.
- DOCS: the [manual](http://docs.datastax.com/en/developer/java-driver-dse/1.4/manual) has quick
  start material and technical details about the driver and its features.
- API: http://docs.datastax.com/en/drivers/java-dse/1.4/
- GITHUB REPOSITORY: https://github.com/datastax/java-driver-dse
- [changelog](changelog/)
- [binary tarball](http://downloads.datastax.com/java-driver/dse/)

## Getting the driver

The driver is available from Maven central:

```xml
<dependency>
  <groupId>com.datastax.dse</groupId>
  <artifactId>dse-java-driver-core</artifactId>
  <version>1.4.0</version>
</dependency>
```

## Compatibility

The DataStax Enterprise Java driver is currently tested and supported against DataStax Enterprise 4.8+.

The DSE Graph module is only usable against DSE 5.0+.

__Disclaimer__: Some DataStax/DataStax Enterprise products might partially work on 
big-endian systems, but DataStax does not officially support these systems.

## Upgrading from previous versions

If you are upgrading from a previous version of the driver, be sure to have a look at
the [upgrade guide](/upgrade_guide/).

The DSE Java Driver is a drop-in replacement of the Java driver for Apache Cassandra and does not
require a large adaptation effort to upgrade from the Java driver for Apache Cassandra.
For more information see [the FAQ](/faq/).

## License

Copyright (C) 2012-2017 DataStax Inc.

The full license terms are available at http://www.datastax.com/terms/datastax-dse-driver-license-terms
