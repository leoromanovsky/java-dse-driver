/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.driver.core;

import java.io.Closeable;
import java.io.File;
import java.net.InetSocketAddress;
import java.util.Map;

public interface CCMAccess extends Closeable {

    // Inspection methods

    /**
     * @return The CCM cluster name.
     */
    String getClusterName();

    /**
     * Returns the Cassandra version of this CCM cluster.  If {@link #getDSEVersion()} is non-null it is assumed
     * that this value is only used for representing the compatible Cassandra version for that DSE version.
     * <p/>
     *
     * @return The version of this CCM cluster.
     */
    VersionNumber getCassandraVersion();

    /**
     * Returns the DSE version of this CCM cluster if this is a DSE cluster, otherwise null.
     * <p/>
     *
     * @return The version of this CCM cluster.
     */
    VersionNumber getDSEVersion();

    /**
     * @return The config directory for this CCM cluster.
     */
    File getCcmDir();

    /**
     * @return The cluster directory for this CCM cluster.
     */
    File getClusterDir();

    /**
     * @param n the node number, starting with 1.
     * @return The node directory for this CCM cluster.
     */
    File getNodeDir(int n);

    /**
     * @param n the node number, starting with 1.
     * @return The node config directory for this CCM cluster.
     */
    File getNodeConfDir(int n);

    /**
     * @return The storage port for this CCM cluster.
     */
    int getStoragePort();

    /**
     * @return The binary port for this CCM cluster.
     */
    int getBinaryPort();

    /**
     * Signals that logs for this CCM cluster should be kept after the cluster is stopped.
     */
    void setKeepLogs(boolean keepLogs);

    /**
     * Returns the address of the {@code nth} host in the CCM cluster (counting from 1, i.e.,
     * {@code addressOfNode(1)} returns the address of the first node.
     * <p/>
     * In multi-DC setups, nodes are numbered in ascending order of their datacenter number.
     * E.g. with 2 DCs and 3 nodes in each DC, the first node in DC 2 is number 4.
     *
     * @return the address of the {@code nth} host in the cluster.
     */
    InetSocketAddress addressOfNode(int n);


    // Methods altering the whole cluster

    /**
     * Starts the cluster.
     */
    void start();

    /**
     * Stops the cluster.
     */
    void stop();

    /**
     * Aggressively stops the cluster.
     */
    void forceStop();

    /**
     * Alias for {@link #stop()}.
     */
    @Override
    void close();

    /**
     * Removes this CCM cluster and deletes all of its files.
     */
    void remove();

    /**
     * Updates the config files for all nodes in the CCM cluster.
     */
    void updateConfig(Map<String, Object> configs);

    /**
     * Updates the DSE config files for all nodes in the CCM cluster.
     */
    void updateDSEConfig(Map<String, Object> configs);

    /**
     * Checks for errors in the logs of all nodes in the cluster.
     */
    String checkForErrors();

    // Methods altering nodes

    /**
     * Starts the {@code nth} host in the CCM cluster.
     *
     * @param n the node number (starting from 1).
     */
    void start(int n);

    /**
     * Stops the {@code nth} host in the CCM cluster.
     *
     * @param n the node number (starting from 1).
     */
    void stop(int n);

    /**
     * Aggressively stops the {@code nth} host in the CCM cluster.
     *
     * @param n the node number (starting from 1).
     */
    void forceStop(int n);

    /**
     * Pauses the {@code nth} host in the CCM cluster.
     *
     * @param n the node number (starting from 1).
     */
    void pause(int n);

    /**
     * Resumes the {@code nth} host in the CCM cluster.
     *
     * @param n the node number (starting from 1).
     */
    void resume(int n);

    /**
     * Removes the {@code nth} host in the CCM cluster.
     *
     * @param n the node number (starting from 1).
     */
    void remove(int n);

    /**
     * Adds the {@code nth} host in the CCM cluster.
     *
     * @param n the node number (starting from 1).
     */
    void add(int n);

    /**
     * Adds the {@code nth} host in the CCM cluster.
     *
     * @param n the node number (starting from 1).
     */
    void add(int dc, int n);

    /**
     * Decommissions the {@code nth} host in the CCM cluster.
     *
     * @param n the node number (starting from 1).
     */
    void decommission(int n);

    /**
     * Updates the {@code nth} host's config file in the CCM cluster.
     *
     * @param n the node number (starting from 1).
     */
    void updateNodeConfig(int n, String key, Object value);

    /**
     * Updates the {@code nth} host's config file in the CCM cluster.
     *
     * @param n the node number (starting from 1).
     */
    void updateNodeConfig(int n, Map<String, Object> configs);

    /**
     * Updates the {@code nth} host's dse config file in the CCM cluster.
     *
     * @param n the node number (starting from 1).
     */
    void updateDSENodeConfig(int n, String key, Object value);

    /**
     * Updates the {@code nth} host's dse config file in the CCM cluster.
     *
     * @param n the node number (starting from 1).
     */
    void updateDSENodeConfig(int n, Map<String, Object> configs);

    /**
     * Sets the workload(s) for the {@code nth} host in the CCM cluster.
     *
     * @param n the node number (starting from 1).
     */
    void setWorkload(int n, String... workload);


    // Methods blocking until nodes are up or down

    /**
     * Waits for a host to be up by pinging the TCP socket directly, without using the Java driver's API.
     */
    void waitForUp(int node);

    /**
     * Waits for a host to be down by pinging the TCP socket directly, without using the Java driver's API.
     */
    void waitForDown(int node);

    /**
     * Executes dsetool with the given arguments.
     */
    void dsetool(int node, String... args);

    /**
     * Executes dsetool reload_core keyspace.table reindex=reindex.  This is considered a non-dirty change.
     */
    void reloadCore(int node, String keyspace, String table, boolean reindex);

    /**
     * @return The target protocolVersion to use when connecting to this CCM cluster.
     * <p/>
     * This should be based on the highest protocol version that both the cluster and driver support.
     * <p/>
     * For example, C* 2.0.17 should return {@link ProtocolVersion#V2} since C* supports up to V2 and the driver
     * supports that version.
     */
    ProtocolVersion getProtocolVersion();

    /**
     * @param maximumAllowed The maximum protocol version to use.
     * @return The target protocolVersion or maximumAllowed if {@link #getProtocolVersion} is greater.
     */
    ProtocolVersion getProtocolVersion(ProtocolVersion maximumAllowed);
}
