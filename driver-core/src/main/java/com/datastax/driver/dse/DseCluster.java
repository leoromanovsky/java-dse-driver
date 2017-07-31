/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.driver.dse;

import com.datastax.driver.core.*;
import com.datastax.driver.core.exceptions.AuthenticationException;
import com.datastax.driver.core.exceptions.CodecNotFoundException;
import com.datastax.driver.core.exceptions.InvalidQueryException;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.datastax.driver.core.policies.*;
import com.datastax.driver.dse.geometry.codecs.LineStringCodec;
import com.datastax.driver.dse.geometry.codecs.PointCodec;
import com.datastax.driver.dse.geometry.codecs.PolygonCodec;
import com.datastax.driver.dse.graph.GraphOptions;
import com.datastax.driver.dse.graph.GraphStatement;
import com.datastax.driver.dse.search.DateRangeCodec;
import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.ResourceBundle;
import java.util.concurrent.Future;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Information and known state of a DSE cluster.
 * <p/>
 * This is the main entry point of the DSE driver. It extends the CQL driver's {@link Cluster} object with DSE-specific
 * features. A simple example of access to a DSE cluster would be:
 * <pre>
 *   DseCluster cluster = DseCluster.builder().addContactPoint("192.168.0.1").build();
 *   DseSession session = cluster.connect("db1");
 *
 *   for (Row row : session.execute("SELECT * FROM table1"))
 *       // do something ...
 * </pre>
 */
public class DseCluster extends DelegatingCluster {

    private static final ResourceBundle dseDriverProperties = ResourceBundle.getBundle("com.datastax.driver.dse.DseDriver");

    /**
     * Returns the current version of the DSE driver.
     * <p/>
     * This is intended for products that wrap or extend the driver, as a way to check
     * compatibility if end-users override the driver version in their application.
     * <p/>
     * Note that this method returns the DSE driver version, not the core driver version.
     * If you are interested in the latter, you should call {@link Cluster#getDriverVersion()}
     * instead.
     *
     * @return the DSE driver version.
     */
    public static String getDseDriverVersion() {
        return dseDriverProperties.getString("dse.driver.version");
    }

    private final Cluster delegate;

    /**
     * Helper class to build {@link DseCluster} instances.
     */
    public static class Builder extends Cluster.Builder {

        private GraphOptions graphOptions;

        private boolean geospatialCodecs = true;

        private boolean searchCodecs = true;

        public Builder() {
            this.withLoadBalancingPolicy(DseConfiguration.defaultLoadBalancingPolicy());
        }

        /**
         * Sets the default options to use with graph queries.
         * <p/>
         * If you don't provide this, the new cluster will get a {@link GraphOptions} instance with the default values.
         *
         * @param graphOptions the graph options.
         * @return this builder (for method chaining).
         */
        public DseCluster.Builder withGraphOptions(GraphOptions graphOptions) {
            this.graphOptions = graphOptions;
            return this;
        }

        /**
         * Prevents the registration of {@link com.datastax.driver.dse.geometry.codecs geospatial codecs} with the
         * new cluster.
         * <p/>
         * If this method is not called, those codecs will be registered by default.
         *
         * @return this builder (for method chaining).
         * @see CodecRegistry
         * @see TypeCodec
         */
        public DseCluster.Builder withoutGeospatialCodecs() {
            this.geospatialCodecs = false;
            return this;
        }

        /**
         * Prevents the registration of search codecs with the
         * new cluster.
         * <p/>
         * Currently only {@link DateRangeCodec} is affected by this method.
         * If this method is not called, this codec will be registered by default.
         *
         * @return this builder (for method chaining).
         * @see CodecRegistry
         * @see TypeCodec
         */
        public DseCluster.Builder withoutSearchCodecs() {
            this.searchCodecs = false;
            return this;
        }

        @Override
        public DseCluster.Builder withClusterName(String name) {
            return (DseCluster.Builder) super.withClusterName(name);
        }

        @Override
        public DseCluster.Builder withPort(int port) {
            return (DseCluster.Builder) super.withPort(port);
        }

        @Override
        public DseCluster.Builder withMaxSchemaAgreementWaitSeconds(int maxSchemaAgreementWaitSeconds) {
            return (DseCluster.Builder) super.withMaxSchemaAgreementWaitSeconds(maxSchemaAgreementWaitSeconds);
        }

        @Override
        public DseCluster.Builder withProtocolVersion(ProtocolVersion version) {
            return (DseCluster.Builder) super.withProtocolVersion(version);
        }

        @Override
        public DseCluster.Builder addContactPoint(String address) {
            return (Builder) super.addContactPoint(address);
        }

        @Override
        public DseCluster.Builder addContactPoints(String... addresses) {
            return (Builder) super.addContactPoints(addresses);
        }

        @Override
        public DseCluster.Builder addContactPoints(InetAddress... addresses) {
            return (Builder) super.addContactPoints(addresses);
        }

        @Override
        public DseCluster.Builder addContactPoints(Collection<InetAddress> addresses) {
            return (Builder) super.addContactPoints(addresses);
        }

        @Override
        public DseCluster.Builder addContactPointsWithPorts(Collection<InetSocketAddress> addresses) {
            return (Builder) super.addContactPointsWithPorts(addresses);
        }

        /**
         * Configures the load balancing policy to use for the new DSE cluster.
         * <p/>
         * If you use graph analytics queries, it should be wrapped into a {@link DseLoadBalancingPolicy} to
         * ensure that queries are routed optimally.
         * <p/>
         * If no load balancing policy is set through this method, the default is to use a
         * {@link Policies#defaultLoadBalancingPolicy()} wrapped into a {@code TargetingLoadBalancingPolicy}.
         *
         * @param policy the load balancing policy to use.
         * @return this builder.
         */
        @Override
        public DseCluster.Builder withLoadBalancingPolicy(LoadBalancingPolicy policy) {
            return (DseCluster.Builder) super.withLoadBalancingPolicy(policy);
        }

        @Override
        public DseCluster.Builder withReconnectionPolicy(ReconnectionPolicy policy) {
            return (DseCluster.Builder) super.withReconnectionPolicy(policy);
        }

        @Override
        public DseCluster.Builder withRetryPolicy(RetryPolicy policy) {
            return (DseCluster.Builder) super.withRetryPolicy(policy);
        }

        @Override
        public DseCluster.Builder withAddressTranslator(AddressTranslator translator) {
            return (DseCluster.Builder) super.withAddressTranslator(translator);
        }

        @Override
        public DseCluster.Builder withTimestampGenerator(TimestampGenerator timestampGenerator) {
            return (DseCluster.Builder) super.withTimestampGenerator(timestampGenerator);
        }

        @Override
        public DseCluster.Builder withSpeculativeExecutionPolicy(SpeculativeExecutionPolicy policy) {
            return (DseCluster.Builder) super.withSpeculativeExecutionPolicy(policy);
        }

        @Override
        public DseCluster.Builder withCodecRegistry(CodecRegistry codecRegistry) {
            return (DseCluster.Builder) super.withCodecRegistry(codecRegistry);
        }

        @Override
        public DseCluster.Builder withCredentials(String username, String password) {
            return (DseCluster.Builder) super.withCredentials(username, password);
        }

        @Override
        public DseCluster.Builder withAuthProvider(AuthProvider authProvider) {
            return (DseCluster.Builder) super.withAuthProvider(authProvider);
        }

        @Override
        public DseCluster.Builder withCompression(ProtocolOptions.Compression compression) {
            return (DseCluster.Builder) super.withCompression(compression);
        }

        @Override
        public DseCluster.Builder withoutMetrics() {
            return (DseCluster.Builder) super.withoutMetrics();
        }

        @Override
        public DseCluster.Builder withSSL() {
            return (DseCluster.Builder) super.withSSL();
        }

        @Override
        public DseCluster.Builder withSSL(SSLOptions sslOptions) {
            return (DseCluster.Builder) super.withSSL(sslOptions);
        }

        @Override
        public DseCluster.Builder withInitialListeners(Collection<Host.StateListener> listeners) {
            return (DseCluster.Builder) super.withInitialListeners(listeners);
        }

        @Override
        public DseCluster.Builder withoutJMXReporting() {
            return (DseCluster.Builder) super.withoutJMXReporting();
        }

        @Override
        public DseCluster.Builder withPoolingOptions(PoolingOptions options) {
            return (DseCluster.Builder) super.withPoolingOptions(options);
        }

        @Override
        public DseCluster.Builder withSocketOptions(SocketOptions options) {
            return (DseCluster.Builder) super.withSocketOptions(options);
        }

        @Override
        public DseCluster.Builder withQueryOptions(QueryOptions options) {
            return (DseCluster.Builder) super.withQueryOptions(options);
        }

        @Override
        public DseCluster.Builder withNettyOptions(NettyOptions nettyOptions) {
            return (DseCluster.Builder) super.withNettyOptions(nettyOptions);
        }

        @Override
        public DseCluster build() {
            DseCluster dseCluster = new DseCluster(super.build());
            CodecRegistry codecRegistry = dseCluster.getConfiguration().getCodecRegistry();
            if (geospatialCodecs) {
                registerGeospatialCodecs(codecRegistry);
            }
            if (searchCodecs) {
                registerSearchCodecs(codecRegistry);
            }
            return dseCluster;
        }

        @Override
        public DseConfiguration getConfiguration() {
            return new DseConfiguration(super.getConfiguration(),
                    graphOptions != null ? graphOptions : new GraphOptions()
            );
        }

        private static void registerGeospatialCodecs(CodecRegistry codecRegistry) {
            registerCodecIfNotPresent(codecRegistry, LineStringCodec.INSTANCE);
            registerCodecIfNotPresent(codecRegistry, PointCodec.INSTANCE);
            registerCodecIfNotPresent(codecRegistry, PolygonCodec.INSTANCE);
        }

        private static void registerSearchCodecs(CodecRegistry codecRegistry) {
            registerCodecIfNotPresent(codecRegistry, DateRangeCodec.INSTANCE);
        }

        private static void registerCodecIfNotPresent(CodecRegistry codecRegistry, TypeCodec<?> codec) {
            try {
                codecRegistry.codecFor(codec.getCqlType(), codec.getJavaType());
            } catch (CodecNotFoundException e) {
                codecRegistry.register(codec);
            }
        }

    }

    /**
     * Creates a new {@link DseCluster.Builder} instance.
     * <p/>
     * This is a convenience method for {@code new GraphCluster.Builder()}.
     *
     * @return the new cluster builder.
     */
    public static DseCluster.Builder builder() {
        return new DseCluster.Builder();
    }

    private DseCluster(Cluster delegate) {
        checkArgument(delegate.getConfiguration() instanceof DseConfiguration, "Cannot create a GraphCluster without a GraphConfiguration");
        this.delegate = delegate;
    }

    @Override
    public DseConfiguration getConfiguration() {
        return (DseConfiguration) super.getConfiguration();
    }

    @Override
    protected Cluster delegate() {
        return delegate;
    }

    /**
     * Creates a new DSE session on this cluster but does not initialize it.
     * <p/>
     * Because this method does not perform any initialization, it cannot fail.
     * The initialization of the session (the connection of the session to the
     * Cassandra nodes) will occur if either the {@link DseSession#init} method is
     * called explicitly, or whenever the returned object is used.
     * <p/>
     * Once a session returned by this method gets initialized (see above), it
     * will be set to no keyspace. If you want to set such session to a
     * keyspace, you will have to explicitly execute a 'USE mykeyspace' query.
     * <p/>
     * Note that if you do not particularly need to defer initialization, it is
     * simpler to use one of the {@code connect()} methods of this class.
     *
     * @return a new, non-initialized DSE session on this cluster.
     */
    @Override
    public DseSession newSession() {
        return new DefaultDseSession(super.newSession(), this);
    }

    /**
     * Creates a new DSE session on this cluster and initializes it.
     * <p/>
     * This method will initialize the newly created session, trying
     * to connect to the Cassandra nodes before returning. If you only want to
     * create a session without initializing it right away, see
     * {@link #newSession}.
     *
     * @return a new session on this cluster set to no keyspace.
     * @throws NoHostAvailableException if the Cluster has not been initialized
     *                                  yet ({@link #init} has not be called and this is the first connect call)
     *                                  and no host amongst the contact points can be reached.
     * @throws AuthenticationException  if an authentication error occurs while
     *                                  contacting the initial contact points.
     * @throws IllegalStateException    if the Cluster was closed prior to calling
     *                                  this method. This can occur either directly (through {@link #close()} or
     *                                  {@link #closeAsync()}), or as a result of an error while initializing the
     *                                  Cluster.
     */
    @Override
    public DseSession connect() {
        return new DefaultDseSession(super.connect(), this);
    }

    /**
     * Creates a new DSE session on this cluster, initializes it and sets the
     * keyspace to the provided one.
     * <p/>
     * This method will initialize the newly created session, trying
     * to connect to the Cassandra nodes before returning. If you only want to
     * create a session without initializing it right away, see
     * {@link #newSession}.
     * <p/>
     * The keyspace set through this method is only valid for CQL queries; for graph queries, the graph to target is
     * determined by the graph name specified via {@link GraphStatement#setGraphName(String)}} or
     * {@link GraphOptions#setGraphName(String)}.
     *
     * @param keyspace the name of the keyspace to use for the created {@link DseSession}.
     * @return a new session on this cluster set to keyspace {@code keyspace}.
     * @throws NoHostAvailableException if the Cluster has not been initialized
     *                                  yet ({@link #init} has not be called and this is the first connect call)
     *                                  and no host amongst the contact points can be reached, or if no host can
     *                                  be contacted to set the {@code keyspace}.
     * @throws AuthenticationException  if an authentication error occurs while
     *                                  contacting the initial contact points.
     * @throws InvalidQueryException    if the keyspace does not exist.
     * @throws IllegalStateException    if the Cluster was closed prior to calling
     *                                  this method. This can occur either directly (through {@link #close()} or
     *                                  {@link #closeAsync()}), or as a result of an error while initializing the
     *                                  Cluster.
     */
    @Override
    public DseSession connect(String keyspace) {
        return new DefaultDseSession(super.connect(keyspace), this);
    }

    /**
     * Creates a new DSE session on this cluster and initializes it asynchronously.
     * <p/>
     * This will also initialize the {@code Cluster} if needed; note that cluster
     * initialization happens synchronously on the thread that called this method.
     * Therefore it is recommended to initialize the cluster at application
     * startup, and not rely on this method to do it.
     * <p/>
     * The {@link Session} object returned by the future's {@link Future#get() get} method can be safely cast
     * to {@link DseSession}.
     *
     * @return a future that will complete when the session is fully initialized.
     * @throws NoHostAvailableException if the Cluster has not been initialized
     *                                  yet ({@link #init} has not been called and this is the first connect call)
     *                                  and no host amongst the contact points can be reached.
     * @throws IllegalStateException    if the Cluster was closed prior to calling
     *                                  this method. This can occur either directly (through {@link #close()} or
     *                                  {@link #closeAsync()}), or as a result of an error while initializing the
     *                                  Cluster.
     * @see #connect()
     */
    @Override
    public ListenableFuture<Session> connectAsync() {
        return Futures.transform(super.connectAsync(), new Function<Session, Session>() {
            @Override
            public Session apply(Session input) {
                return new DefaultDseSession(input, DseCluster.this);
            }
        });
    }

    /**
     * Creates a new DSE session on this cluster, and initializes it to the given
     * keyspace asynchronously.
     * <p/>
     * This will also initialize the {@code Cluster} if needed; note that cluster
     * initialization happens synchronously on the thread that called this method.
     * Therefore it is recommended to initialize the cluster at application
     * startup, and not rely on this method to do it.
     * <p/>
     * The keyspace set through this method is only valid for CQL queries; for graph queries, the graph to target is
     * determined by the graph name specified via {@link GraphStatement#setGraphName(String)}} or
     * {@link GraphOptions#setGraphName(String)}.
     * <p/>
     * The {@link Session} object returned by the future's {@link Future#get() get} method can be safely cast
     * to {@link DseSession}.
     *
     * @param keyspace The name of the keyspace to use for the created
     *                 {@code Session}.
     * @return a future that will complete when the session is fully initialized.
     * @throws NoHostAvailableException if the Cluster has not been initialized
     *                                  yet ({@link #init} has not been called and this is the first connect call)
     *                                  and no host amongst the contact points can be reached.
     * @throws IllegalStateException    if the Cluster was closed prior to calling
     *                                  this method. This can occur either directly (through {@link #close()} or
     *                                  {@link #closeAsync()}), or as a result of an error while initializing the
     *                                  Cluster.
     */
    @Override
    public ListenableFuture<Session> connectAsync(String keyspace) {
        return Futures.transform(super.connectAsync(keyspace), new Function<Session, Session>() {
            @Override
            public Session apply(Session input) {
                return new DefaultDseSession(input, DseCluster.this);
            }
        });
    }

}
