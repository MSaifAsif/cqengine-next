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

import com.googlecode.cqengine.engine.IndexManager;
import com.googlecode.cqengine.index.Index;
import com.googlecode.cqengine.index.compound.CompoundIndex;
import com.googlecode.cqengine.index.compound.support.CompoundQuery;
import com.googlecode.cqengine.index.standingquery.StandingQueryIndex;
import com.googlecode.cqengine.query.ComparativeQuery;
import com.googlecode.cqengine.query.Query;
import com.googlecode.cqengine.query.logical.And;
import com.googlecode.cqengine.query.logical.LogicalQuery;
import com.googlecode.cqengine.query.logical.Not;
import com.googlecode.cqengine.query.logical.Or;
import com.googlecode.cqengine.query.option.DeduplicationOption;
import com.googlecode.cqengine.query.option.DeduplicationStrategy;
import com.googlecode.cqengine.query.option.QueryOptions;
import com.googlecode.cqengine.query.simple.SimpleQuery;
import com.googlecode.cqengine.resultset.ResultSet;
import com.googlecode.cqengine.resultset.iterator.UnmodifiableIterator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static com.googlecode.cqengine.query.QueryFactory.equal;
import static com.googlecode.cqengine.query.QueryFactory.forStandingQuery;
import static com.googlecode.cqengine.query.option.EngineFlags.PREFER_INDEX_MERGE_STRATEGY;
import static com.googlecode.cqengine.query.option.FlagsEnabled.isFlagEnabled;

/**
 * Plans how a {@link Query} should be evaluated against the indexes registered with an {@link
 * IndexManager}: it decomposes compound queries into their branches, and for each branch selects
 * the index with the lowest retrieval cost which supports it.
 *
 * <p>This logic was previously part of {@link CollectionQueryEngine}; it is a pure move with no
 * behavioural change.
 *
 * @param <O> The type of the objects in the collection
 */
class QueryOptimizer<O> {

  private final IndexManager<O> indexManager;
  private final ResultSetBuilder<O> resultSetBuilder = new ResultSetBuilder<>();

  QueryOptimizer(IndexManager<O> indexManager) {
    this.indexManager = indexManager;
  }

  /**
   * Returns a {@link ResultSet} from the index with the lowest retrieval cost which supports the
   * given query.
   *
   * <p>For a definition of retrieval cost see {@link ResultSet#getRetrievalCost()}.
   *
   * @param query The query which refers to an attribute
   * @param queryOptions Optional parameters for the query
   * @return A {@link ResultSet} from the index with the lowest retrieval cost which supports the
   *     given query
   */
  <A> ResultSet<O> retrieveSimpleQuery(SimpleQuery<O, A> query, QueryOptions queryOptions) {
    // First, check if a standing query index is available for the query...
    ResultSet<O> lowestCostResultSet =
        retrieveFromStandingQueryIndexIfAvailable(query, queryOptions);
    if (lowestCostResultSet != null) {
      // A standing query index is available for this query.
      // Results from standing query indexes are considered to have the lowest cost, so we simply
      // return these results...
      return lowestCostResultSet;
    }
    // At this point, no standing query indexes were available,
    // so we proceed to check for other indexes on the attribute...

    // Check if a UniqueIndex is available, as this will have the lowest cost of attribute-based
    // indexes...
    Index<O> uniqueIndex = indexManager.getUniqueIndex(query.getAttribute());
    if (uniqueIndex != null && uniqueIndex.supportsQuery(query, queryOptions)) {
      return uniqueIndex.retrieve(query, queryOptions);
    }

    // At this point, we did not find any UniqueIndex, so we now check for other attribute-based
    // indexes
    // and we determine which one has the lowest retrieval cost...

    // Examine other (non-unique) indexes and choose the one with the lowest retrieval cost...
    Iterable<Index<O>> indexesOnAttribute =
        indexManager.getIndexesOnAttribute(query.getAttribute());
    return retrieveFromLowestCostIndex(query, indexesOnAttribute, queryOptions);
  }

  /**
   * Returns a {@link ResultSet} from the index with the lowest retrieval cost which supports the
   * given query.
   *
   * <p>For a definition of retrieval cost see {@link ResultSet#getRetrievalCost()}.
   *
   * @param query The query which refers to an attribute
   * @param queryOptions Optional parameters for the query
   * @return A {@link ResultSet} from the index with the lowest retrieval cost which supports the
   *     given query
   */
  <A> ResultSet<O> retrieveComparativeQuery(
      ComparativeQuery<O, A> query, QueryOptions queryOptions) {
    // Determine which of the indexes on the query's attribute has the lowest retrieval cost...
    Iterable<Index<O>> indexesOnAttribute =
        indexManager.getIndexesOnAttribute(query.getAttribute());
    return retrieveFromLowestCostIndex(query, indexesOnAttribute, queryOptions);
  }

  /**
   * Chooses the index with the lowest retrieval cost from the given candidate indexes, and wraps
   * its results in a {@link CostCachingResultSet}.
   *
   * <p>For a definition of retrieval cost see {@link ResultSet#getRetrievalCost()}.
   *
   * @param query The query for which an index is required
   * @param indexesOnAttribute The candidate indexes on the query's attribute
   * @param queryOptions Optional parameters for the query
   * @return A {@link ResultSet} from the index with the lowest retrieval cost which supports the
   *     given query
   * @throws IllegalStateException if none of the given indexes supports the query
   */
  ResultSet<O> retrieveFromLowestCostIndex(
      Query<O> query, Iterable<Index<O>> indexesOnAttribute, QueryOptions queryOptions) {
    int lowestRetrievalCost = 0;
    ResultSet<O> lowestCostResultSet = null;
    for (Index<O> index : indexesOnAttribute) {
      if (index.supportsQuery(query, queryOptions)) {
        ResultSet<O> thisIndexResultSet = index.retrieve(query, queryOptions);
        int thisIndexRetrievalCost = thisIndexResultSet.getRetrievalCost();
        if (lowestCostResultSet == null || thisIndexRetrievalCost < lowestRetrievalCost) {
          lowestCostResultSet = thisIndexResultSet;
          lowestRetrievalCost = thisIndexRetrievalCost;
        }
      }
    }

    if (lowestCostResultSet == null) {
      // This should never happen (would indicate a bug);
      // the fallback index should have been selected in worst case...
      throw new IllegalStateException("Failed to locate an index supporting query: " + query);
    }
    return resultSetBuilder.costCaching(lowestCostResultSet);
  }

  /**
   * Implements the bulk of query processing.
   *
   * <p>This method is recursive.
   *
   * <p>When processing a {@link SimpleQuery}, the method will simply delegate to the helper methods
   * {@link #retrieveIntersectionOfSimpleQueries(Collection, QueryOptions, boolean)} and {@link
   * #retrieveUnionOfSimpleQueries(Collection, QueryOptions)} and will return their results.
   *
   * <p>When processing a descendant of {@link CompoundQuery} ({@link And}, {@link Or}, {@link
   * Not}), the method will extract separately from those objects the child queries which are {@link
   * SimpleQuery}s and the child queries which are compound queries. It will call the helper methods
   * above to process the child {@link SimpleQuery}s, and the method will call itself recursively to
   * process the child compound queries. Once the method has results for both the child {@link
   * SimpleQuery}s and the child compound queries, it will return them in a {@link
   * ResultSetIntersection}, {@link ResultSetUnion} or {@link ResultSetDifference} object as
   * appropriate for {@link And}, {@link Or}, {@link Not} respectively. These {@link ResultSet}
   * objects will take care of performing intersections or unions etc. on the child {@link
   * ResultSet}s.
   *
   * @param query A query representing some assertions which sought objects must match
   * @param queryOptions Optional parameters for the query supplied specifying strategy {@link
   *     DeduplicationStrategy#LOGICAL_ELIMINATION}
   * @return A {@link ResultSet} which provides objects matching the given query
   */
  ResultSet<O> retrieveRecursive(Query<O> query, final QueryOptions queryOptions) {
    final boolean indexMergeStrategyEnabled =
        isFlagEnabled(queryOptions, PREFER_INDEX_MERGE_STRATEGY);

    // Check if we can process this query from a standing query index...
    ResultSet<O> resultSetFromStandingQueryIndex =
        retrieveFromStandingQueryIndexIfAvailable(query, queryOptions);
    if (resultSetFromStandingQueryIndex != null) {
      // A standing query index was available, return its results...
      return resultSetFromStandingQueryIndex;
    }
    // ..else no standing query index was available, process the query normally...

    if (query instanceof SimpleQuery<O, ?> simpleQuery) {
      // No deduplication required for a single SimpleQuery.
      // Return the ResultSet from the index with the lowest retrieval cost which supports
      // this query and the attribute on which it is based...
      return retrieveSimpleQuery(simpleQuery, queryOptions);
    } else if (query instanceof ComparativeQuery<O, ?> comparativeQuery) {
      // Return the ResultSet from the index with the lowest retrieval cost which supports
      // this query and the attribute on which it is based...
      return retrieveComparativeQuery(comparativeQuery, queryOptions);
    } else if (query instanceof And<O> and) {
      // Check if we can process this And query from a compound index...
      if (indexManager.hasCompoundIndexes()) {
        // Compound indexes exist. Check if any can be used for this And query...
        CompoundQuery<O> compoundQuery = CompoundQuery.fromAndQueryIfSuitable(and);
        if (compoundQuery != null) {
          CompoundIndex<O> compoundIndex =
              indexManager.getCompoundIndex(compoundQuery.getCompoundAttribute());
          if (compoundIndex != null && compoundIndex.supportsQuery(compoundQuery, queryOptions)) {
            // No deduplication required for retrievals from compound indexes.
            return compoundIndex.retrieve(compoundQuery, queryOptions);
          }
        }
      } // else no suitable compound index exists, process the And query normally...

      // No deduplication required for intersections.
      Iterable<ResultSet<O>> resultSetsToMerge =
          new Iterable<ResultSet<O>>() {
            @Override
            public Iterator<ResultSet<O>> iterator() {
              return new UnmodifiableIterator<ResultSet<O>>() {

                boolean needToProcessSimpleQueries = and.hasSimpleQueries();
                boolean needToProcessComparativeQueries = and.hasComparativeQueries();
                Iterator<LogicalQuery<O>> logicalQueriesIterator =
                    and.getLogicalQueries().iterator();

                @Override
                public boolean hasNext() {
                  return needToProcessSimpleQueries
                      || needToProcessComparativeQueries
                      || logicalQueriesIterator.hasNext();
                }

                @Override
                public ResultSet<O> next() {
                  if (needToProcessSimpleQueries) {
                    needToProcessSimpleQueries = false;
                    // Retrieve results for simple queries from indexes...
                    return retrieveIntersectionOfSimpleQueries(
                        and.getSimpleQueries(), queryOptions, indexMergeStrategyEnabled);
                  }
                  if (needToProcessComparativeQueries) {
                    needToProcessComparativeQueries = false;
                    // Retrieve results for comparative queries from indexes...
                    return retrieveIntersectionOfComparativeQueries(
                        and.getComparativeQueries(), queryOptions);
                  }
                  // Recursively call this method for logical queries...
                  return retrieveRecursive(logicalQueriesIterator.next(), queryOptions);
                }
              };
            }
          };
      boolean useIndexMergeStrategy =
          ResultSetBuilder.shouldUseIndexMergeStrategy(
              indexMergeStrategyEnabled, and.hasComparativeQueries(), resultSetsToMerge);
      return resultSetBuilder.intersection(
          resultSetsToMerge, query, queryOptions, useIndexMergeStrategy);
    } else if (query instanceof Or<O> or) {
      // If the Or query indicates child queries are disjoint,
      // ignore any instruction to perform deduplication in the queryOptions supplied...
      final QueryOptions queryOptionsForOrUnion;
      if (or.isDisjoint()) {
        // The Or query is disjoint, so there is no need to perform deduplication on its results.
        // Wrap the QueryOptions object in another which omits the DeduplicationOption if it is
        // requested
        // when evaluating this Or statement...
        queryOptionsForOrUnion =
            new QueryOptions(queryOptions.getOptions()) {
              @Override
              public Object get(Object key) {
                return DeduplicationOption.class.equals(key) ? null : super.get(key);
              }
            };
      } else {
        // Use the supplied queryOptions...
        queryOptionsForOrUnion = queryOptions;
      }
      Iterable<ResultSet<O>> resultSetsToUnion =
          new Iterable<ResultSet<O>>() {
            @Override
            public Iterator<ResultSet<O>> iterator() {
              return new UnmodifiableIterator<ResultSet<O>>() {

                boolean needToProcessSimpleQueries = or.hasSimpleQueries();
                boolean needToProcessComparativeQueries = or.hasComparativeQueries();
                Iterator<LogicalQuery<O>> logicalQueriesIterator =
                    or.getLogicalQueries().iterator();

                @Override
                public boolean hasNext() {
                  return needToProcessSimpleQueries
                      || needToProcessComparativeQueries
                      || logicalQueriesIterator.hasNext();
                }

                @Override
                public ResultSet<O> next() {
                  if (needToProcessSimpleQueries) {
                    needToProcessSimpleQueries = false;
                    // Retrieve results for simple queries from indexes...
                    return retrieveUnionOfSimpleQueries(
                        or.getSimpleQueries(), queryOptionsForOrUnion);
                  }
                  if (needToProcessComparativeQueries) {
                    needToProcessComparativeQueries = false;
                    // Retrieve results for comparative queries from indexes...
                    return retrieveUnionOfComparativeQueries(
                        or.getComparativeQueries(), queryOptionsForOrUnion);
                  }
                  // Recursively call this method for logical queries.
                  // Note we supply the original queryOptions for recursive calls...
                  return retrieveRecursive(logicalQueriesIterator.next(), queryOptions);
                }
              };
            }
          };
      // *** Deduplication can be required for unions... ***
      boolean logicalElimination =
          DeduplicationOption.isLogicalElimination(queryOptionsForOrUnion);
      boolean useIndexMergeStrategy =
          logicalElimination
              && ResultSetBuilder.shouldUseIndexMergeStrategy(
                  indexMergeStrategyEnabled, or.hasComparativeQueries(), resultSetsToUnion);
      ResultSet<O> union =
          resultSetBuilder.union(
              resultSetsToUnion,
              query,
              queryOptionsForOrUnion,
              logicalElimination,
              useIndexMergeStrategy);

      if (union.getRetrievalCost() == Integer.MAX_VALUE && !or.hasComparativeQueries()) {
        // Either no indexes are available for any branches of the or() query, or indexes are only
        // available
        // for some of the branches.
        // If we were to delegate to the FallbackIndex to retrieve results for any of the branches
        // which
        // don't have indexes, then the FallbackIndex would scan the entire collection to locate
        // results.
        // This would happen for *each* of the branches which don't have indexes - so the entire
        // collection
        // could be scanned multiple times.
        // So to avoid that, here we will scan the entire collection once, to find all objects which
        // match
        // all of the child branches in a single scan.
        // Note: there is no need to deduplicate results which were fetched this way.
        union =
            resultSetBuilder.filteredCollectionScan(
                indexManager.getEntireCollectionAsResultSet(query, queryOptionsForOrUnion),
                or,
                queryOptionsForOrUnion);
      }
      return union;
    } else if (query instanceof Not<O> not) {
      // No deduplication required for negation (the entire collection is a Set, contains no
      // duplicates).
      // Retrieve the ResultSet for the negated query, by calling this method recursively...
      ResultSet<O> resultSetToNegate = retrieveRecursive(not.getNegatedQuery(), queryOptions);
      // Return the negation of this result set, by subtracting it from the entire collection of
      // objects...
      return resultSetBuilder.difference(
          indexManager.getEntireCollectionAsResultSet(query, queryOptions),
          resultSetToNegate,
          query,
          queryOptions,
          indexMergeStrategyEnabled);
    } else {
      throw new IllegalStateException(
          "Unexpected type of query object: " + CollectionQueryEngine.getClassNameNullSafe(query));
    }
  }

  /**
   * Retrieves an intersection of the objects matching {@link SimpleQuery}s.
   *
   * <p><i>Definitions: For a definition of <u>retrieval cost</u> see {@link
   * ResultSet#getRetrievalCost()}. For a definition of <u>merge cost</u> see {@link
   * ResultSet#getMergeCost()}. </i>
   *
   * <p>The algorithm employed by this method is as follows.
   *
   * <p>For each {@link SimpleQuery} supplied, retrieves a {@link ResultSet} for that {@link
   * SimpleQuery} from the index with the lowest <u>retrieval cost</u> which supports that {@link
   * SimpleQuery}.
   *
   * <p>The algorithm then determines the {@link ResultSet} with the <i>lowest</i> <u>merge
   * cost</u>, and the {@link SimpleQuery} which was associated with that {@link ResultSet}. It also
   * assembles a list of the <i>other</i> {@link SimpleQuery}s which had <i>more expensive</i>
   * <u>merge costs</u>.
   *
   * <p>The algorithm then returns a {@link FilteringResultSet} which iterates the {@link ResultSet}
   * with the <i>lowest</i> <u>merge cost</u>. During iteration, this {@link FilteringResultSet}
   * calls a {@link FilteringResultSet#isValid(Object, QueryOptions)} method for each object. This
   * algorithm implements that method to return true if the object matches all of the {@link
   * SimpleQuery}s which had the <i>more expensive</i> <u>merge costs</u>.
   *
   * <p>As such the {@link ResultSet} which had the lowest merge cost drives the iteration. Note
   * therefore that this method <i>does <u>not</u> perform set intersections in the conventional
   * sense</i> (i.e. using {@link Set#contains(Object)}). It has been tested empirically that it is
   * usually cheaper to invoke {@link Query#matches(Object, QueryOptions)} to test each object in
   * the smallest set against queries which would match the more expensive sets, rather than perform
   * several hash lookups and equality tests between multiple sets.
   *
   * @param queries A collection of {@link SimpleQuery} objects to be retrieved and intersected
   * @param queryOptions Optional parameters for the query
   * @return A {@link ResultSet} which provides objects matching the intersection of results for
   *     each of the {@link SimpleQuery}s
   */
  <A> ResultSet<O> retrieveIntersectionOfSimpleQueries(
      Collection<SimpleQuery<O, ?>> queries,
      QueryOptions queryOptions,
      boolean indexMergeStrategyEnabled) {
    List<ResultSet<O>> resultSets = new ArrayList<ResultSet<O>>(queries.size());
    for (SimpleQuery query : queries) {
      // Work around type erasure...
      @SuppressWarnings({"unchecked"})
      SimpleQuery<O, A> queryTyped = (SimpleQuery<O, A>) query;
      ResultSet<O> resultSet = retrieveSimpleQuery(queryTyped, queryOptions);
      resultSets.add(resultSet);
    }
    Query<O> query = resultSetBuilder.combineQueries(queries, false);

    boolean useIndexMergeStrategy =
        indexMergeStrategyEnabled
            && ResultSetBuilder.indexesAvailableForAllResultSets(resultSets);
    return resultSetBuilder.intersection(resultSets, query, queryOptions, useIndexMergeStrategy);
  }

  /**
   * Same as {@link #retrieveIntersectionOfSimpleQueries(Collection, QueryOptions, boolean)} except
   * for {@link ComparativeQuery}.
   */
  <A> ResultSet<O> retrieveIntersectionOfComparativeQueries(
      Collection<ComparativeQuery<O, ?>> queries, QueryOptions queryOptions) {
    List<ResultSet<O>> resultSets = new ArrayList<ResultSet<O>>(queries.size());
    for (ComparativeQuery query : queries) {
      // Work around type erasure...
      @SuppressWarnings({"unchecked"})
      ComparativeQuery<O, A> queryTyped = (ComparativeQuery<O, A>) query;
      ResultSet<O> resultSet = retrieveComparativeQuery(queryTyped, queryOptions);
      resultSets.add(resultSet);
    }
    Query<O> query = resultSetBuilder.combineQueries(queries, false);

    // We always use index merge strategy to merge results for comparative queries...
    return resultSetBuilder.intersection(resultSets, query, queryOptions, true);
  }

  /**
   * Retrieves a union of the objects matching {@link SimpleQuery}s.
   *
   * <p><i>Definitions: For a definition of <u>retrieval cost</u> see {@link
   * ResultSet#getRetrievalCost()}. For a definition of <u>merge cost</u> see {@link
   * ResultSet#getMergeCost()}. </i>
   *
   * <p>The algorithm employed by this method is as follows.
   *
   * <p>For each {@link SimpleQuery} supplied, retrieves a {@link ResultSet} for that {@link
   * SimpleQuery} from the index with the lowest <u>retrieval cost</u> which supports that {@link
   * SimpleQuery}.
   *
   * <p>The method then returns these {@link ResultSet}s in either a {@link ResultSetUnion} or a
   * {@link ResultSetUnionAll} object, depending on whether {@code logicalDuplicateElimination} was
   * specified or not. These concatenate the wrapped {@link ResultSet}s when iterated. In the case
   * of {@link ResultSetUnion}, this also ensures that duplicate objects are not returned more than
   * once, by means of logical elimination via set theory rather than maintaining a record of all
   * objects iterated.
   *
   * @param queries A collection of {@link SimpleQuery} objects to be retrieved and unioned
   * @param queryOptions Optional parameters for the query supplied specifying strategy {@link
   *     DeduplicationStrategy#LOGICAL_ELIMINATION}
   * @return A {@link ResultSet} which provides objects matching the union of results for each of
   *     the {@link SimpleQuery}s
   */
  ResultSet<O> retrieveUnionOfSimpleQueries(
      final Collection<SimpleQuery<O, ?>> queries, final QueryOptions queryOptions) {
    Iterable<ResultSet<O>> resultSetsToUnion =
        new Iterable<ResultSet<O>>() {
          @Override
          public Iterator<ResultSet<O>> iterator() {
            return new UnmodifiableIterator<ResultSet<O>>() {

              Iterator<SimpleQuery<O, ?>> queriesIterator = queries.iterator();

              @Override
              public boolean hasNext() {
                return queriesIterator.hasNext();
              }

              @Override
              public ResultSet<O> next() {
                return retrieveSimpleQuery(queriesIterator.next(), queryOptions);
              }
            };
          }
        };
    Query<O> query = resultSetBuilder.combineQueries(queries, true);
    // Perform deduplication as necessary...
    boolean logicalElimination = DeduplicationOption.isLogicalElimination(queryOptions);
    // Use the index merge strategy if it was requested and indexes are available for all result
    // sets...
    boolean useIndexMergeStrategy =
        logicalElimination
            && isFlagEnabled(queryOptions, PREFER_INDEX_MERGE_STRATEGY)
            && ResultSetBuilder.indexesAvailableForAllResultSets(resultSetsToUnion);
    return resultSetBuilder.union(
        resultSetsToUnion, query, queryOptions, logicalElimination, useIndexMergeStrategy);
  }

  /**
   * Same as {@link #retrieveUnionOfSimpleQueries(Collection, QueryOptions)} except for {@link
   * ComparativeQuery}.
   */
  ResultSet<O> retrieveUnionOfComparativeQueries(
      final Collection<ComparativeQuery<O, ?>> queries, final QueryOptions queryOptions) {
    Iterable<ResultSet<O>> resultSetsToUnion =
        new Iterable<ResultSet<O>>() {
          @Override
          public Iterator<ResultSet<O>> iterator() {
            return new UnmodifiableIterator<ResultSet<O>>() {

              Iterator<ComparativeQuery<O, ?>> queriesIterator = queries.iterator();

              @Override
              public boolean hasNext() {
                return queriesIterator.hasNext();
              }

              @Override
              public ResultSet<O> next() {
                return retrieveComparativeQuery(queriesIterator.next(), queryOptions);
              }
            };
          }
        };
    Query<O> query = resultSetBuilder.combineQueries(queries, true);
    // Perform deduplication as necessary...
    // Note: we always use the index merge strategy to merge results for comparative queries...
    boolean logicalElimination = DeduplicationOption.isLogicalElimination(queryOptions);
    return resultSetBuilder.union(resultSetsToUnion, query, queryOptions, logicalElimination, true);
  }

  /**
   * Checks if the given query can be answered from a standing query index, and if so returns a
   * {@link ResultSet} which does so. If the query cannot be answered from a standing query index,
   * returns null.
   *
   * @param query The query to evaluate
   * @param queryOptions Query options supplied for the query
   * @return A {@link ResultSet} which answers the query from a standing query index, or null if no
   *     such index is available
   */
  ResultSet<O> retrieveFromStandingQueryIndexIfAvailable(
      Query<O> query, final QueryOptions queryOptions) {
    // Check if we can process this query from a standing query index...
    Index<O> standingQueryIndex = indexManager.getStandingQueryIndex(query);
    if (standingQueryIndex != null) {
      // No deduplication required for standing queries.
      if (standingQueryIndex instanceof StandingQueryIndex) {
        return standingQueryIndex.retrieve(query, queryOptions);
      } else {
        return standingQueryIndex.retrieve(
            equal(forStandingQuery(query), Boolean.TRUE), queryOptions);
      }
    } // else no suitable standing query index exists, process the query normally...
    return null;
  }
}
