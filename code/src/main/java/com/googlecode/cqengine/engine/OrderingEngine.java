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

import com.googlecode.concurrenttrees.common.LazyIterator;
import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.attribute.OrderControlAttribute;
import com.googlecode.cqengine.attribute.OrderMissingFirstAttribute;
import com.googlecode.cqengine.attribute.OrderMissingLastAttribute;
import com.googlecode.cqengine.attribute.SimpleAttribute;
import com.googlecode.cqengine.attribute.SimpleNullableAttribute;
import com.googlecode.cqengine.engine.IndexManager;
import com.googlecode.cqengine.index.Index;
import com.googlecode.cqengine.index.support.CloseableIterator;
import com.googlecode.cqengine.index.support.CloseableRequestResources;
import com.googlecode.cqengine.index.support.CloseableRequestResources.CloseableResourceGroup;
import com.googlecode.cqengine.index.support.KeyValue;
import com.googlecode.cqengine.index.support.SortedKeyStatisticsAttributeIndex;
import com.googlecode.cqengine.index.support.SortedKeyStatisticsIndex;
import com.googlecode.cqengine.query.Query;
import com.googlecode.cqengine.query.logical.And;
import com.googlecode.cqengine.query.logical.Not;
import com.googlecode.cqengine.query.option.AttributeOrder;
import com.googlecode.cqengine.query.option.DeduplicationOption;
import com.googlecode.cqengine.query.option.EngineFlags;
import com.googlecode.cqengine.query.option.EngineThresholds;
import com.googlecode.cqengine.query.option.OrderByOption;
import com.googlecode.cqengine.query.option.QueryLog;
import com.googlecode.cqengine.query.option.QueryOptions;
import com.googlecode.cqengine.query.option.Thresholds;
import com.googlecode.cqengine.query.simple.Between;
import com.googlecode.cqengine.query.simple.GreaterThan;
import com.googlecode.cqengine.query.simple.LessThan;
import com.googlecode.cqengine.query.simple.SimpleQuery;
import com.googlecode.cqengine.resultset.ResultSet;
import com.googlecode.cqengine.resultset.closeable.CloseableResultSet;
import com.googlecode.cqengine.resultset.filter.FilteringIterator;
import com.googlecode.cqengine.resultset.filter.MaterializedDeduplicatedIterator;
import com.googlecode.cqengine.resultset.iterator.ConcatenatingIterator;
import com.googlecode.cqengine.resultset.iterator.IteratorUtil;
import com.googlecode.cqengine.resultset.order.AttributeOrdersComparator;
import com.googlecode.cqengine.resultset.order.MaterializedDeduplicatedResultSet;
import com.googlecode.cqengine.resultset.order.MaterializedOrderedResultSet;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import static com.googlecode.cqengine.query.QueryFactory.has;
import static com.googlecode.cqengine.query.QueryFactory.not;
import static com.googlecode.cqengine.query.option.EngineFlags.INDEX_ORDERING_ALLOW_FAST_ORDERING_OF_MULTI_VALUED_ATTRIBUTES;
import static com.googlecode.cqengine.query.option.EngineFlags.PREFER_INDEX_MERGE_STRATEGY;
import static com.googlecode.cqengine.query.option.FlagsEnabled.isFlagEnabled;
import static com.googlecode.cqengine.resultset.iterator.IteratorUtil.concatenate;
import static com.googlecode.cqengine.resultset.iterator.IteratorUtil.groupAndSort;

/**
 * Retrieves results for a query and applies any requested ordering, choosing between two
 * strategies: using a sorted index to drive the ordering, or materializing all results and sorting
 * them afterwards. The strategy is selected based on the estimated selectivity of the query
 * relative to the {@link EngineThresholds#INDEX_ORDERING_SELECTIVITY} threshold.
 *
 * <p>This logic was previously part of {@link CollectionQueryEngine}; it is a pure move with no
 * behavioural change.
 *
 * @param <O> The type of the objects in the collection
 */
class OrderingEngine<O> {

  private final IndexManager<O> indexManager;
  private final QueryOptimizer<O> queryOptimizer;

  OrderingEngine(IndexManager<O> indexManager, QueryOptimizer<O> queryOptimizer) {
    this.indexManager = indexManager;
    this.queryOptimizer = queryOptimizer;
  }

  /**
   * Retrieves results for the given query, ordering them if an {@link OrderByOption} is present in
   * the query options.
   *
   * <p>Implementation note: this method pre-processes the QueryOption arguments, decides on an
   * ordering strategy, and then delegates to {@link #retrieveRecursive(Query, QueryOptions)} to
   * evaluate the query itself.
   */
  ResultSet<O> retrieve(final Query<O> query, final QueryOptions queryOptions) {
    @SuppressWarnings("unchecked")
    OrderByOption<O> orderByOption = (OrderByOption<O>) queryOptions.get(OrderByOption.class);

    // Store the root query in the queryOptions, so that when retrieveRecursive() examines child
    // branches, that
    // both the branch query and the root query will be available to PartialIndexes so they may
    // determine if they
    // can be used to accelerate the overall query...
    queryOptions.put(CollectionQueryEngine.ROOT_QUERY, query);

    // Log decisions made to the query log, if provided...
    final QueryLog queryLog = queryOptions.get(QueryLog.class); // might be null

    SortedKeyStatisticsAttributeIndex<?, O> indexForOrdering = null;
    if (orderByOption != null) {
      // Results must be ordered. Determine the ordering strategy to use: i.e. if we should use an
      // index to order
      // results, or if we should retrieve results and sort them afterward instead.

      Double selectivityThreshold =
          Thresholds.getThreshold(queryOptions, EngineThresholds.INDEX_ORDERING_SELECTIVITY);
      if (selectivityThreshold == null) {
        selectivityThreshold = EngineThresholds.INDEX_ORDERING_SELECTIVITY.getThresholdDefault();
      }
      final List<AttributeOrder<O>> allSortOrders = orderByOption.getAttributeOrders();
      if (selectivityThreshold != 0.0) {
        // Index ordering can be used.
        // Check if an index is actually available to support it.
        AttributeOrder<O> firstOrder = allSortOrders.iterator().next();
        @SuppressWarnings("unchecked")
        Attribute<O, Comparable> firstAttribute =
            (Attribute<O, Comparable>) firstOrder.getAttribute();
        if (firstAttribute instanceof OrderControlAttribute orderControlAttribute) {
          @SuppressWarnings("unchecked")
          Attribute<O, Comparable> firstAttributeDelegate =
              orderControlAttribute.getDelegateAttribute();
          firstAttribute = firstAttributeDelegate;
        }

        // Before we check if an index is available to support index ordering, we need to account
        // for the fact
        // that even if such an index is available, it might not contain all objects in the
        // collection.
        //
        // An index built on a SimpleAttribute, is guaranteed to contain all objects in the
        // collection, because
        // SimpleAttribute is guaranteed to return a value for every object.
        // OTOH an index built on a non-SimpleAttribute, is not guaranteed to contain all objects in
        // the
        // collection, because non-SimpleAttributes are permitted to return *zero* or more values
        // for any
        // object. Objects for which non-SimpleAttributes return zero values, will be omitted from
        // the index.
        //
        // Therefore, if we will use an index to order results, we must ensure that the collection
        // also has a
        // suitable index to allow the objects which are not in the index to be retrieved as well.
        // When
        // ordering results, we must return those objects either before or after the objects which
        // are found in
        // the index. Here we proceed to locate a suitable index to use for ordering results, only
        // if we will
        // also be able to retrieve the objects missing from that index efficiently as well...
        if (firstAttribute instanceof SimpleAttribute
            || indexManager.getStandingQueryIndex(not(has(firstAttribute))) != null) {
          // Either we are sorting by a SimpleAttribute, or we are sorting by a non-SimpleAttribute
          // and we
          // also will be able to retrieve objects which do not have values for the
          // non-SimpleAttribute
          // efficiently. Now check if an index exists which would allow index ordering...
          for (Index<O> index : indexManager.getIndexesOnAttribute(firstAttribute)) {
            if (index instanceof SortedKeyStatisticsAttributeIndex<?, O> ifo
                && !index.isQuantized()) {
              indexForOrdering = ifo;
              break;
            }
          }
        }

        if (queryLog != null) {
          queryLog.log(
              "indexForOrdering: "
                  + (indexForOrdering == null
                      ? null
                      : indexForOrdering.getClass().getSimpleName()));
        }
        // At this point we might have found an appropriate indexForOrdering, or it might still be
        // null.
        if (indexForOrdering != null) {
          // We found an appropriate index.
          // Determine if the selectivity of the query is below the selectivity threshold to use
          // index ordering...
          final double querySelectivity;
          if (selectivityThreshold == 1.0) {
            // Index ordering has been requested explicitly.
            // Don't bother calculating query selectivity, assign low selectivity so we will use the
            // index...
            querySelectivity = 0.0;
          } else if (!indexForOrdering.supportsQuery(has(firstAttribute), queryOptions)) {
            // Index ordering was not requested explicitly, and we cannot calculate the selectivity.
            // In this case even though we have an index which supports index ordering,
            // we don't have enough information to say that it would be beneficial.
            // Assign high selectivity so that the materialize strategy will be used instead...
            querySelectivity = 1.0;
          } else {
            // The index supports has() queries, which allows us to calculate selectivity.
            // Calculate query selectivity, based on the query cardinality and index cardinality...
            final int queryCardinality = retrieveRecursive(query, queryOptions).getMergeCost();
            final int indexCardinality =
                indexForOrdering.retrieve(has(firstAttribute), queryOptions).getMergeCost();
            if (queryLog != null) {
              queryLog.log("queryCardinality: " + queryCardinality);
              queryLog.log("indexCardinality: " + indexCardinality);
            }
            if (indexCardinality == 0) {
              // Handle edge case where the index is empty.
              querySelectivity =
                  1.0; // treat is as if the query has high selectivity (tend to use materialize).
            } else if (queryCardinality > indexCardinality) {
              // Handle edge case where query cardinality is greater than index cardinality.
              querySelectivity =
                  0.0; // treat is as if the query has low selectivity (tend to use index ordering).
            } else {
              querySelectivity = 1.0 - queryCardinality / (double) indexCardinality;
            }
          }

          if (queryLog != null) {
            queryLog.log("querySelectivity: " + querySelectivity);
            queryLog.log("selectivityThreshold: " + selectivityThreshold);
          }
          if (querySelectivity > selectivityThreshold) {
            // Selectivity is too high for index ordering strategy.
            // Use the materialize ordering strategy instead.
            indexForOrdering = null;
          }
          // else: querySelectivity <= selectivityThreshold, so we use the index ordering strategy.
        }
      }
    }
    ResultSet<O> resultSet;
    if (indexForOrdering != null) {
      // Retrieve results, using an index to accelerate ordering...
      resultSet = retrieveWithIndexOrdering(query, queryOptions, orderByOption, indexForOrdering);
      if (queryLog != null) {
        queryLog.log("orderingStrategy: index");
      }
    } else {
      // Retrieve results, without using an index to accelerate ordering...
      resultSet = retrieveWithoutIndexOrdering(query, queryOptions, orderByOption);
      if (queryLog != null) {
        queryLog.log("orderingStrategy: materialize");
      }
    }

    // Return the results, ensuring that the close() method will close any resources which were
    // opened...
    // This wrapping is kept intentionally to guarantee close() is called, even though
    // IndexedCollections may also handle closing in some cases.
    return new CloseableResultSet<O>(resultSet, query, queryOptions) {
      @Override
      public void close() {
        super.close();
        CloseableRequestResources.closeForQueryOptions(queryOptions);
      }
    };
  }

  /** Retrieve results and then sort/deduplicate them afterward (if required). */
  ResultSet<O> retrieveWithoutIndexOrdering(
      Query<O> query, QueryOptions queryOptions, OrderByOption<O> orderByOption) {
    ResultSet<O> resultSet = retrieveRecursive(query, queryOptions);

    // Check if we need to order results...
    if (orderByOption != null) {
      // Wrap the results in a MaterializedOrderedResultSet.
      Comparator<O> comparator =
          new AttributeOrdersComparator<O>(orderByOption.getAttributeOrders(), queryOptions);
      resultSet = new MaterializedOrderedResultSet<O>(resultSet, comparator);
    }
    // Check if we need to deduplicate results (deduplicate using MATERIALIZE rather than
    // LOGICAL_ELIMINATION strategy)...
    if (DeduplicationOption.isMaterialize(queryOptions)) {
      // Wrap the results in a MaterializedDeduplicatedResultSet.
      resultSet = new MaterializedDeduplicatedResultSet<O>(resultSet);
    }
    return resultSet;
  }

  /** Use an index to order results. */
  ResultSet<O> retrieveWithIndexOrdering(
      final Query<O> query,
      final QueryOptions queryOptions,
      final OrderByOption<O> orderByOption,
      final SortedKeyStatisticsIndex<?, O> indexForOrdering) {
    final List<AttributeOrder<O>> allSortOrders = orderByOption.getAttributeOrders();

    final AttributeOrder<O> primarySortOrder = allSortOrders.get(0);

    // If the client wrapped the first attribute by which results should be ordered in an
    // OrderControlAttribute,
    // assign it here...
    @SuppressWarnings("unchecked")
    final OrderControlAttribute<O> orderControlAttribute =
        (primarySortOrder.getAttribute() instanceof OrderControlAttribute oca) ? oca : null;

    // If the first attribute by which results should be ordered was wrapped, unwrap it, and assign
    // it here...
    @SuppressWarnings("unchecked")
    final Attribute<O, Comparable> primarySortAttribute =
        (orderControlAttribute == null)
            ? (Attribute<O, Comparable>) primarySortOrder.getAttribute()
            : (Attribute<O, Comparable>) orderControlAttribute.getDelegateAttribute();

    final boolean primarySortDescending = primarySortOrder.isDescending();

    final boolean attributeCanHaveZeroValues = !(primarySortAttribute instanceof SimpleAttribute);
    final boolean attributeCanHaveMoreThanOneValue =
        !(primarySortAttribute instanceof SimpleAttribute
            || primarySortAttribute instanceof SimpleNullableAttribute);

    @SuppressWarnings("unchecked")
    final RangeBounds<?> rangeBoundsFromQuery = getBoundsFromQuery(query, primarySortAttribute);

    return new ResultSet<O>() {
      @Override
      public Iterator<O> iterator() {
        Iterator<O> mainResults =
            retrieveWithIndexOrderingMainResults(
                query,
                queryOptions,
                indexForOrdering,
                allSortOrders,
                rangeBoundsFromQuery,
                attributeCanHaveMoreThanOneValue,
                primarySortDescending);

        // Combine the results from the index ordered search, with objects which would be missing
        // from that
        // index, which is possible in the case that the primary sort attribute is nullable or
        // multivalued...

        Iterator<O> combinedResults;
        if (attributeCanHaveZeroValues) {
          Iterator<O> missingResults =
              retrieveWithIndexOrderingMissingResults(
                  query,
                  queryOptions,
                  primarySortAttribute,
                  allSortOrders,
                  attributeCanHaveMoreThanOneValue);

          // Concatenate the main results and the missing objects, accounting for which batch should
          // come first...
          if (orderControlAttribute instanceof OrderMissingFirstAttribute) {
            combinedResults =
                ConcatenatingIterator.concatenate(Arrays.asList(missingResults, mainResults));
          } else if (orderControlAttribute instanceof OrderMissingLastAttribute) {
            combinedResults =
                ConcatenatingIterator.concatenate(Arrays.asList(mainResults, missingResults));
          } else if (primarySortOrder.isDescending()) {
            combinedResults =
                ConcatenatingIterator.concatenate(Arrays.asList(mainResults, missingResults));
          } else {
            combinedResults =
                ConcatenatingIterator.concatenate(Arrays.asList(missingResults, mainResults));
          }
        } else {
          combinedResults = mainResults;
        }

        if (attributeCanHaveMoreThanOneValue) {
          // Deduplicate results in case the same object could appear in more than one bucket
          // and so otherwise could be returned more than once...
          combinedResults = new MaterializedDeduplicatedIterator<O>(combinedResults);
        }
        return combinedResults;
      }

      @Override
      public boolean contains(O object) {
        ResultSet<O> rs = retrieveWithoutIndexOrdering(query, queryOptions, null);
        try {
          return rs.contains(object);
        } finally {
          rs.close();
        }
      }

      @Override
      public boolean matches(O object) {
        return query.matches(object, queryOptions);
      }

      @Override
      public Query<O> getQuery() {
        return query;
      }

      @Override
      public QueryOptions getQueryOptions() {
        return queryOptions;
      }

      @Override
      public int getRetrievalCost() {
        ResultSet<O> rs = retrieveWithoutIndexOrdering(query, queryOptions, null);
        try {
          return rs.getRetrievalCost();
        } finally {
          rs.close();
        }
      }

      @Override
      public int getMergeCost() {
        ResultSet<O> rs = retrieveWithoutIndexOrdering(query, queryOptions, null);
        try {
          return rs.getMergeCost();
        } finally {
          rs.close();
        }
      }

      @Override
      public int size() {
        ResultSet<O> rs = retrieveWithoutIndexOrdering(query, queryOptions, null);
        try {
          return rs.size();
        } finally {
          rs.close();
        }
      }

      @Override
      public void close() {}
    };
  }

  Iterator<O> retrieveWithIndexOrderingMainResults(
      final Query<O> query,
      QueryOptions queryOptions,
      SortedKeyStatisticsIndex<?, O> indexForOrdering,
      List<AttributeOrder<O>> allSortOrders,
      RangeBounds<?> rangeBoundsFromQuery,
      boolean attributeCanHaveMoreThanOneValue,
      boolean primarySortDescending) {
    // Ensure that at the end of processing the request, that we close any resources we opened...
    final CloseableResourceGroup closeableResourceGroup =
        CloseableRequestResources.forQueryOptions(queryOptions).addGroup();

    final List<AttributeOrder<O>> sortOrdersForBucket =
        determineAdditionalSortOrdersForIndexOrdering(
            allSortOrders, attributeCanHaveMoreThanOneValue, indexForOrdering, queryOptions);

    final CloseableIterator<? extends KeyValue<? extends Comparable<?>, O>> keysAndValuesInRange =
        getKeysAndValuesInRange(
            indexForOrdering, rangeBoundsFromQuery, primarySortDescending, queryOptions);

    // Ensure this CloseableIterator gets closed...
    closeableResourceGroup.add(keysAndValuesInRange);

    final Iterator<O> sorted;
    if (sortOrdersForBucket.isEmpty()) {
      sorted =
          new LazyIterator<O>() {
            @Override
            protected O computeNext() {
              return keysAndValuesInRange.hasNext()
                  ? keysAndValuesInRange.next().getValue()
                  : endOfData();
            }
          };
    } else {
      sorted =
          concatenate(
              groupAndSort(
                  keysAndValuesInRange,
                  new AttributeOrdersComparator<O>(sortOrdersForBucket, queryOptions)));
    }

    return filterIndexOrderingCandidateResults(sorted, query, queryOptions);
  }

  Iterator<O> retrieveWithIndexOrderingMissingResults(
      final Query<O> query,
      QueryOptions queryOptions,
      Attribute<O, Comparable> primarySortAttribute,
      List<AttributeOrder<O>> allSortOrders,
      boolean attributeCanHaveMoreThanOneValue) {
    // Ensure that at the end of processing the request, that we close any resources we opened...
    final CloseableResourceGroup closeableResourceGroup =
        CloseableRequestResources.forQueryOptions(queryOptions).addGroup();

    // Retrieve missing objects from the secondary index on objects which don't have a value for the
    // primary sort attribute...
    Not<O> missingValuesQuery = not(has(primarySortAttribute));
    ResultSet<O> missingResults = retrieveRecursive(missingValuesQuery, queryOptions);

    // Ensure that this is closed...
    closeableResourceGroup.add(missingResults);

    Iterator<O> missingResultsIterator = missingResults.iterator();
    // Filter the objects from the secondary index, to ensure they match the query...
    missingResultsIterator =
        filterIndexOrderingCandidateResults(missingResultsIterator, query, queryOptions);

    // Determine if we need to sort the missing objects...
    Index<O> indexForMissingObjects = indexManager.getStandingQueryIndex(missingValuesQuery);
    final List<AttributeOrder<O>> sortOrdersForBucket =
        determineAdditionalSortOrdersForIndexOrdering(
            allSortOrders, attributeCanHaveMoreThanOneValue, indexForMissingObjects, queryOptions);

    if (!sortOrdersForBucket.isEmpty()) {
      // We do need to sort the missing objects...
      Comparator<O> comparator =
          new AttributeOrdersComparator<O>(sortOrdersForBucket, queryOptions);
      missingResultsIterator = IteratorUtil.materializedSort(missingResultsIterator, comparator);
    }

    return missingResultsIterator;
  }

  /**
   * Filters the given sorted candidate results to ensure they match the query, using either the
   * default merge strategy or the index merge strategy as appropriate.
   *
   * <p>This method will add any resources which need to be closed to {@link
   * CloseableRequestResources} in the query options.
   *
   * @param sortedCandidateResults The candidate results to be filtered
   * @param query The query
   * @param queryOptions The query options
   * @return A filtered iterator which returns the subset of candidate objects which match the query
   */
  Iterator<O> filterIndexOrderingCandidateResults(
      final Iterator<O> sortedCandidateResults,
      final Query<O> query,
      final QueryOptions queryOptions) {
    final boolean indexMergeStrategyEnabled =
        isFlagEnabled(queryOptions, PREFER_INDEX_MERGE_STRATEGY);
    if (indexMergeStrategyEnabled) {
      final ResultSet<O> indexAcceleratedQueryResults =
          retrieveWithoutIndexOrdering(query, queryOptions, null);
      if (indexAcceleratedQueryResults.getRetrievalCost() == Integer.MAX_VALUE) {
        // No index is available to accelerate the index merge strategy...
        indexAcceleratedQueryResults.close();
        // We fall back to filtering via query.matches() below.
      } else {
        // Ensure that indexAcceleratedQueryResults is closed at the end of processing the
        // request...
        final CloseableResourceGroup closeableResourceGroup =
            CloseableRequestResources.forQueryOptions(queryOptions).addGroup();
        closeableResourceGroup.add(indexAcceleratedQueryResults);
        // This is the index merge strategy where indexes are used to filter the sorted results...
        return new FilteringIterator<O>(sortedCandidateResults, queryOptions) {
          @Override
          public boolean isValid(O object, QueryOptions queryOptions) {
            return indexAcceleratedQueryResults.contains(object);
          }
        };
      }
    }
    // Either index merge strategy is not enabled, or no suitable indexes are available for it.
    // We filter results by examining values returned by attributes referenced in the query
    // instead...
    return new FilteringIterator<O>(sortedCandidateResults, queryOptions) {
      @Override
      public boolean isValid(O object, QueryOptions queryOptions) {
        return query.matches(object, queryOptions);
      }
    };
  }

  /**
   * Delegates to {@link QueryOptimizer#retrieveRecursive(Query, QueryOptions)} to plan and retrieve
   * results for the given query using the registered indexes.
   *
   * @param query A query representing some assertions which sought objects must match
   * @param queryOptions Optional parameters for the query
   * @return A {@link ResultSet} which provides objects matching the given query
   */
  ResultSet<O> retrieveRecursive(Query<O> query, final QueryOptions queryOptions) {
    return queryOptimizer.retrieveRecursive(query, queryOptions);
  }

  /**
   * Called when using an index to order results, to determine if or how results within each bucket
   * in that index should be sorted.
   *
   * <p>We must sort results within each bucket, when:
   *
   * <ol>
   *   <li>The index is quantized.
   *   <li>The attribute can have multiple values (if object 1 values ["a"] and object 2 has values
   *       ["a", "b"] then objects 1 & 2 will both be in the same bucket, but object 1 should sort
   *       first ascending). However, this case can be suppressed with {@link
   *       EngineFlags#INDEX_ORDERING_ALLOW_FAST_ORDERING_OF_MULTI_VALUED_ATTRIBUTES}.
   *   <li>There are additional sort orders after the first one.
   * </ol>
   *
   * @param allSortOrders The user-specified sort orders
   * @param attributeCanHaveMoreThanOneValue If the primary attribute used for sorting can return
   *     more than one value
   * @param index The index from which the bucket is accessed
   * @return A list of AttributeOrder objects representing the sort order to apply to objects in the
   *     bucket
   */
  static <O> List<AttributeOrder<O>> determineAdditionalSortOrdersForIndexOrdering(
      List<AttributeOrder<O>> allSortOrders,
      boolean attributeCanHaveMoreThanOneValue,
      Index<O> index,
      QueryOptions queryOptions) {
    return (index.isQuantized()
            || (attributeCanHaveMoreThanOneValue
                && !isFlagEnabled(
                    queryOptions, INDEX_ORDERING_ALLOW_FAST_ORDERING_OF_MULTI_VALUED_ATTRIBUTES)))
        ? allSortOrders // We must re-sort on all sort orders within each bucket.
        : allSortOrders.subList(1, allSortOrders.size());
  }

  static <A extends Comparable<A>, O> CloseableIterator<KeyValue<A, O>> getKeysAndValuesInRange(
      SortedKeyStatisticsIndex<A, O> index,
      RangeBounds<?> queryBounds,
      boolean descending,
      QueryOptions queryOptions) {
    @SuppressWarnings("unchecked")
    RangeBounds<A> typedBounds = (RangeBounds<A>) queryBounds;
    if (!descending) {
      return index
          .getKeysAndValues(
              typedBounds.lowerBound,
              typedBounds.lowerInclusive,
              typedBounds.upperBound,
              typedBounds.upperInclusive,
              queryOptions)
          .iterator();
    } else {
      return index
          .getKeysAndValuesDescending(
              typedBounds.lowerBound,
              typedBounds.lowerInclusive,
              typedBounds.upperBound,
              typedBounds.upperInclusive,
              queryOptions)
          .iterator();
    }
  }

  static class RangeBounds<A extends Comparable<A>> {
    final A lowerBound;
    final boolean lowerInclusive;
    final A upperBound;
    final Boolean upperInclusive;

    public RangeBounds(A lowerBound, boolean lowerInclusive, A upperBound, Boolean upperInclusive) {
      this.lowerBound = lowerBound;
      this.lowerInclusive = lowerInclusive;
      this.upperBound = upperBound;
      this.upperInclusive = upperInclusive;
    }
  }

  static <A extends Comparable<A>, O> RangeBounds getBoundsFromQuery(
      Query<O> query, Attribute<O, A> attribute) {
    A lowerBound = null, upperBound = null;
    boolean lowerInclusive = false, upperInclusive = false;
    List<SimpleQuery<O, ?>> candidateRangeQueries = Collections.emptyList();
    if (query instanceof SimpleQuery) {
      candidateRangeQueries =
          Collections.<SimpleQuery<O, ?>>singletonList((SimpleQuery<O, ?>) query);
    } else if (query instanceof And<O> and) {
      if (and.hasSimpleQueries()) {
        candidateRangeQueries = and.getSimpleQueries();
      }
    }
    for (SimpleQuery<O, ?> candidate : candidateRangeQueries) {
      if (attribute.equals(candidate.getAttribute())) {
        if (candidate instanceof GreaterThan) {
          @SuppressWarnings("unchecked")
          GreaterThan<O, A> bound = (GreaterThan<O, A>) candidate;
          lowerBound = bound.getValue();
          lowerInclusive = bound.isValueInclusive();
        } else if (candidate instanceof LessThan) {
          @SuppressWarnings("unchecked")
          LessThan<O, A> bound = (LessThan<O, A>) candidate;
          upperBound = bound.getValue();
          upperInclusive = bound.isValueInclusive();
        } else if (candidate instanceof Between) {
          @SuppressWarnings("unchecked")
          Between<O, A> bound = (Between<O, A>) candidate;
          lowerBound = bound.getLowerValue();
          lowerInclusive = bound.isLowerInclusive();
          upperBound = bound.getUpperValue();
          upperInclusive = bound.isUpperInclusive();
        }
      }
    }
    return new RangeBounds<A>(lowerBound, lowerInclusive, upperBound, upperInclusive);
  }
}
