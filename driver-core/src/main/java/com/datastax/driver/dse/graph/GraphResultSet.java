/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.driver.dse.graph;

import com.datastax.driver.core.ExecutionInfo;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.exceptions.DriverException;
import com.datastax.driver.dse.DseSession;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Iterator;
import java.util.List;

/**
 * The result of a graph query.
 */
public class GraphResultSet implements Iterable<GraphNode> {

    static final Function<Row, GraphNode> ROW_TO_DEFAULTGRAPHNODE = new Function<Row, GraphNode>() {

        @Override
        public GraphNode apply(Row row) {
            // Seems like sometimes traversals can return empty rows
            if (row != null) {
                String jsonString = row.getString("gremlin");
                try {
                    return GraphJsonUtils.readStringAsTree(jsonString);
                } catch (RuntimeException e) {
                    throw new DriverException("Could not parse the result returned by the Graph server as a JSON string : " + jsonString, e);
                }
            } else {
                return null;
            }
        }
    };

    private long bulk = 0;
    private GraphNode lastGraphNode = null;

    private final ResultSet wrapped;
    private final Function<Row, GraphNode> transformResultFunction;

    /**
     * This constructor is intended for internal use only, users should normally obtain instances from
     * {@link DseSession#executeGraph(GraphStatement)}.
     */
    public GraphResultSet(ResultSet wrapped) {
        this(wrapped, ROW_TO_DEFAULTGRAPHNODE);
    }

    public GraphResultSet(ResultSet wrapped, Function<Row, GraphNode> transformResultFunction) {
        this.wrapped = wrapped;
        this.transformResultFunction = transformResultFunction;
    }

    /**
     * Returns whether there are more results.
     *
     * @return whether there are more results.
     */
    public boolean isExhausted() {
        return wrapped.isExhausted() && bulk <= 1;
    }

    /**
     * Returns the next result.
     *
     * @return the next result, or {@code null} if there are no more of them.
     */
    public GraphNode one() {
        if (bulk > 1) {
            bulk--;
            // TODO: return a copy? Not sure it's useful because the content of this is supposed to be immutable.
            return lastGraphNode;
        }
        GraphNode container = this.transformResultFunction.apply(wrapped.one());

        if (container == null) {
            return null;
        }

        if (container.get(GraphSONTokens.BULK) != null) {
            bulk = container.get(GraphSONTokens.BULK).asLong();
        }

        GraphNode results = container.get("result");
        lastGraphNode = results;
        return results;
    }

    /**
     * Returns all the remaining results as a list.
     *
     * @return a list containing the remaining results. The result set will be exhausted after a call to this method.
     */
    public List<GraphNode> all() {
        return ImmutableList.copyOf(iterator());
    }

    /**
     * Returns an iterator over the results.
     * <p/>
     * The {@link Iterator#next} method is equivalent to calling {@link #one}. After a full iteration, the result set
     * will be exhausted.
     * <p/>
     * The returned iterator does not support the {@link Iterator#remove} method.
     *
     * @return an iterator that will consume and return the remaining results.
     */
    public Iterator<GraphNode> iterator() {
        return new Iterator<GraphNode>() {
            @Override
            public boolean hasNext() {
                return !isExhausted();
            }

            @Override
            public GraphNode next() {
                return one();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * This method has been deprecated because it may return incorrect numbers. Since paging
     * is not implemented in DSE Graph, using {@link #all()} is recommended instead to gather all
     * the results coming back from a DSE Graph query.
     * <p/>
     * The number of results that can be retrieved without blocking to fetch.
     *
     * @return the number of results readily available. If {@link #isFullyFetched()}, this is the total number of
     * results remaining, otherwise going past that limit will trigger background fetches.
     */
    @Deprecated
    public int getAvailableWithoutFetching() {
        return wrapped.getAvailableWithoutFetching();
    }

    /**
     * This method has been deprecated because paging is not implemented for DSE Graph.
     * <p/>
     * Whether all results have been fetched from the database.
     * <p/>
     * If {@code isFullyFetched()}, then {@link #getAvailableWithoutFetching} will return the number of results
     * remaining before exhaustion.
     * <p/>
     * But {@code !isFullyFetched()} does not necessarily mean that the result set is not exhausted (you should call
     * {@code isExhausted()} to verify it).
     *
     * @return whether all results have been fetched.
     */
    @Deprecated
    public boolean isFullyFetched() {
        return wrapped.isFullyFetched();
    }

    /**
     * This method has been deprecated because paging is not implemented for DSE Graph.
     * <p/>
     * Force fetching the next page of results for this result set, if any.
     * <p/>
     * This method is entirely optional. It will be called automatically while
     * the result set is consumed (through {@link #one}, {@link #all} or iteration)
     * when needed (i.e. when {@code getAvailableWithoutFetching() == 0} and
     * {@code isFullyFetched() == false}).
     * <p/>
     * You can however call this method manually to force the fetching of the
     * next page of results. This can allow to prefetch results before they are
     * strictly needed. For instance, if you want to prefetch the next page of
     * results as soon as there is less than 100 rows readily available in this
     * result set, you can do:
     * <pre>
     *   GraphResultSet rs = session.executeGraph(...);
     *   Iterator&lt;GraphNode&gt; iter = rs.iterator();
     *   while (iter.hasNext()) {
     *       if (rs.getAvailableWithoutFetching() == 100 &amp;&amp; !rs.isFullyFetched())
     *           rs.fetchMoreResults();
     *       GraphNode result = iter.next()
     *       ... process the result ...
     *   }
     * </pre>
     * This method is not blocking, so in the example above, the call to {@code
     * fetchMoreResults} will not block the processing of the 100 currently available
     * results (but {@code iter.hasNext()} will block once those results have been processed
     * until the fetch query returns, if it hasn't yet).
     * <p/>
     * Only one page of results (for a given result set) can be
     * fetched at any given time. If this method is called twice and the query
     * triggered by the first call has not returned yet when the second one is
     * performed, then the 2nd call will simply return a future on the currently
     * in progress query.
     *
     * @return a future on the completion of fetching the next page of results.
     * If the result set is already fully retrieved ({@code isFullyFetched() == true}),
     * then the returned future will return immediately, but not particular error will be
     * thrown (you should thus call {@code isFullyFetched() to know if calling this
     * method can be of any use}).
     */
    @Deprecated
    public ListenableFuture<GraphResultSet> fetchMoreResults() {
        return Futures.transform(wrapped.fetchMoreResults(), new Function<ResultSet, GraphResultSet>() {
            @Override
            public GraphResultSet apply(ResultSet input) {
                return new GraphResultSet(input, transformResultFunction);
            }
        });
    }

    /**
     * Returns information on the execution of the last query made for this result set.
     * <p/>
     * Note that in most cases, a result set is fetched with only one query, but large
     * result sets can be paged and thus be retrieved by multiple queries. In that
     * case this method return the {@code ExecutionInfo} for the last query
     * performed. To retrieve the information for all queries, use {@link #getAllExecutionInfo}.
     * <p/>
     * The returned object includes basic information such as the queried hosts,
     * but also the Cassandra query trace if tracing was enabled for the query.
     *
     * @return the execution info for the last query made for this GraphResultSet.
     */
    public ExecutionInfo getExecutionInfo() {
        return wrapped.getExecutionInfo();
    }

    /**
     * Returns the execution information for all queries made to retrieve this result set.
     * <p/>
     * If paging was used, the returned list contains the {@code ExecutionInfo} for all the queries done to obtain the
     * results (at the time of the call), in the order those queries were made.
     * <p/>
     * If no paging was used (because the result set was small enough), the list only contains one element.
     *
     * @return a list of the execution info for all the queries made for this GraphResultSet.
     */
    public List<ExecutionInfo> getAllExecutionInfo() {
        return wrapped.getAllExecutionInfo();
    }
}
