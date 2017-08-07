# Java driver for DataStax Enterprise

This driver is based on the DataStax driver for Apache Cassandra, with specific extensions for DSE:

* [`Authenticator` implementations](manual/auth/) that use the authentication scheme negotiation in the server-side
  `DseAuthenticator`;
* value classes for [geospatial types](manual/geo_types/), and type codecs that integrate them seamlessly with the
  driver;
* [DSE graph integration](manual/graph/).

*The Java DataStax Enterprise Driver can be used solely with DataStax Enterprise. Please consult
[the license](#license).*


## Getting the driver

The driver is available from Maven central:

```xml
<dependency>
  <groupId>com.datastax.dse</groupId>
  <artifactId>dse-java-driver-core</artifactId>
  <version>1.4.0</version>
</dependency>
```

## Reporting issues

Create a [JIRA](https://datastax-oss.atlassian.net/browse/JAVA) ticket with the "Component/s" field set to "DSE" (or contact DataStax support if you are a DSE customer).

## License

Copyright (C) 2012-2017 DataStax Inc.

The full license terms are available at http://www.datastax.com/terms/datastax-dse-driver-license-terms
