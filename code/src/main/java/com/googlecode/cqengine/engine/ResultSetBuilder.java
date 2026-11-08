/**
 * Copyright 2012-2015 Niall Gallagher
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.cqengine.engine;

import com.googlecode.cqengine.query.Query;
import com.googlecode.cqengine.query.logical.And;
import com.googlecode.cqengine.query.logical.Or;
import com.googlecode.cqengine.query.option.QueryOptions;
import com.googlecode.cqengine.resultset.ResultSet;
import com.googlecode.cqengine.resultset.common.CostCachingResultSet;
import com.googlecode.cqengine.resultset.connective.ResultSetDifference;
import com.googlecode.cqengine.resultset.connective.ResultSetIntersection;
import com.googlecode.cqengine.resultset.connective.ResultSetUnion;
import com.googlecode.cqengine.resultset.connective.ResultSetUnionAll;
import com.googlecode.cqengine.resultset.filter.FilteringResultSet;

import java.util.Collection;

/**
 * Composes the {@link ResultSet}s produced for individual query branches into the composite {@link
 * ResultSet} which represents the whole query: intersections for {@link And}, unions for {@link Or},
 * differences for {@link com.googlecode.cqengine.query.logical.Not}, plus the cost-caching and
 * collection-scan fallback wrappers.
 *
 * <p>This is a pure factory: all decisions about <i>whether</i> to deduplicate or <i>whether</i> to
 * use the index merge strategy are made by {@link QueryOptimizer} and passed in here as flags.
 *
 * <p>This logic was previously part of {@link QueryOptimizer}; it is a pure move with no
 * behavioural change.
 *
 * @param <O> The type of the objects in the collection
 */
class ResultSetBuilder<O> {

  /** Wraps a {@link ResultSet} so its retrieval and merge costs are computed at most once. */
  ResultSet<O> costCaching(ResultSet<O> resultSet) {
    return new CostCachingResultSet<O>(resultSet);
  }

  /** Builds the intersection (logical {@code AND}) of the given result sets. */
  ResultSet<O> intersection(
      Iterable<ResultSet<O>> resultSets,
      Query<O> query,
      QueryOptions queryOptions,
      boolean useIndexMergeStrategy) {
    return new ResultSetIntersection<O>(resultSets, query, queryOptions, useIndexMergeStrategy);
  }

  /**
   * Builds the union (logical {@code OR}) of the given result sets. When {@code logicalElimination}
   * is true a {@link ResultSetUnion} is returned, which suppresses duplicate objects via set
   * theory; otherwise a {@link ResultSetUnionAll} is returned, which simply concatenates. {@code
   * useIndexMergeStrategy} is only consulted for the {@link ResultSetUnion} case.
   */
  ResultSet<O> union(
      Iterable<ResultSet<O>> resultSets,
      Query<O> query,
      QueryOptions queryOptions,
      boolean logicalElimination,
      boolean useIndexMergeStrategy) {
    if (logicalElimination) {
      return new ResultSetUnion<O>(resultSets, query, queryOptions, useIndexMergeStrategy);
    }
    return new ResultSetUnionAll<O>(resultSets, query, queryOptions);
  }

  /**
   * Builds the difference of {@code collection} minus {@code resultSetToNegate} (logical {@code
   * NOT}).
   */
  ResultSet<O> difference(
      ResultSet<O> collection,
      ResultSet<O> resultSetToNegate,
      Query<O> query,
      QueryOptions queryOptions,
      boolean useIndexMergeStrategy) {
    return new ResultSetDifference<O>(
        collection, resultSetToNegate, query, queryOptions, useIndexMergeStrategy);
  }

  /**
   * Builds a {@link ResultSet} which scans {@code collection} once and returns the objects which
   * match {@code query}. Used as a fallback for an {@link Or} query when indexes are unavailable for
   * some branches, to avoid scanning the collection once per branch.
   */
  ResultSet<O> filteredCollectionScan(
      ResultSet<O> collection, final Query<O> query, QueryOptions queryOptions) {
    return new FilteringResultSet<O>(collection, query, queryOptions) {
      @Override
      public boolean isValid(O object, QueryOptions queryOptions) {
        return query.matches(object, queryOptions);
      }
    };
  }

  /**
   * Combines the given queries into a single query, working around type erasure. Returns the sole
   * query if the collection contains only one; otherwise wraps them all in an {@link Or} (if {@code
   * disjunctive}) or an {@link And} (if not).
   *
   * @param queries The queries to combine
   * @param disjunctive True to combine with {@link Or}, false to combine with {@link And}
   * @return A single query representing the combination of the given queries
   */
  @SuppressWarnings("unchecked")
  Query<O> combineQueries(Collection<? extends Query<O>> queries, boolean disjunctive) {
    Collection<Query<O>> queriesTyped = (Collection<Query<O>>) queries;
    if (queriesTyped.size() == 1) {
      return queriesTyped.iterator().next();
    }
    return disjunctive ? new Or<O>(queriesTyped) : new And<O>(queriesTyped);
  }

  /**
   * Indicates if the engine should use the index merge strategy.
   *
   * <p>This will return true if comparativeQueriesPresent is true, because it is necessary to use
   * the index merge strategy with comparative queries, because comparative queries do not support
   * filtering.
   *
   * <p>Otherwise, if there are no comparative queries involved, this will return true if indexes
   * are available for all of the given result sets AND the index merge strategy was requested.
   */
  static <O> boolean shouldUseIndexMergeStrategy(
      boolean strategyRequested,
      boolean comparativeQueriesPresent,
      Iterable<ResultSet<O>> resultSetsToMerge) {
    if (comparativeQueriesPresent) {
      return true;
    }
    return strategyRequested && indexesAvailableForAllResultSets(resultSetsToMerge);
  }

  static <O> boolean indexesAvailableForAllResultSets(Iterable<ResultSet<O>> resultSetsToMerge) {
    for (ResultSet<O> resultSet : resultSetsToMerge) {
      if (resultSet.getRetrievalCost() == Integer.MAX_VALUE) {
        return false;
      }
    }
    return true;
  }
}
