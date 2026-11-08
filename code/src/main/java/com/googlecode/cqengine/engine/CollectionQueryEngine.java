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
import com.googlecode.cqengine.index.sqlite.SQLiteIdentityIndex;
import com.googlecode.cqengine.persistence.Persistence;
import com.googlecode.cqengine.persistence.support.ObjectSet;
import com.googlecode.cqengine.persistence.support.ObjectStore;
import com.googlecode.cqengine.persistence.support.sqlite.SQLiteObjectStore;
import com.googlecode.cqengine.query.Query;
import com.googlecode.cqengine.query.option.QueryOptions;
import com.googlecode.cqengine.resultset.ResultSet;

/**
 * The main component of {@code CQEngine} - maintains a set of indexes on a collection and accepts
 * queries which it performs and optimizes for those indexes.
 *
 * @author Niall Gallagher
 */
public class CollectionQueryEngine<O> implements QueryEngineInternal<O> {

  // A key used to store the root query in the QueryOptions, so it may be accessed by partial
  // indexes...
  public static final String ROOT_QUERY = "ROOT_QUERY";

  private volatile Persistence<O, ? extends Comparable> persistence;
  private volatile ObjectStore<O> objectStore;
  private volatile IndexManager<O> indexManager;
  private volatile QueryOptimizer<O> queryOptimizer;
  private volatile OrderingEngine<O> orderingEngine;

  public CollectionQueryEngine() {}

  @Override
  public void init(final ObjectStore<O> objectStore, final QueryOptions queryOptions) {
    this.objectStore = objectStore;
    @SuppressWarnings("unchecked")
    Persistence<O, ? extends Comparable> persistenceFromQueryOptions =
        getPersistenceFromQueryOptions(queryOptions);
    this.persistence = persistenceFromQueryOptions;
    this.indexManager = new IndexManager<>(objectStore, this.persistence, this);
    this.queryOptimizer = new QueryOptimizer<>(this.indexManager);
    this.orderingEngine = new OrderingEngine<>(this.indexManager, this.queryOptimizer);
    if (objectStore instanceof SQLiteObjectStore sqLiteObjectStore) {
      // If the collection is backed by a SQLiteObjectStore, add the backing index of the
      // SQLiteObjectStore
      // so that it can also be used as a regular index to accelerate queries...
      // NOTE: Using pattern matching with raw type here to avoid complex
      // generic type safety issues with Java 21 type erasure.
      @SuppressWarnings("unchecked")
      SQLiteIdentityIndex<? extends Comparable<?>, O> backingIndex =
          (SQLiteIdentityIndex<? extends Comparable<?>, O>) sqLiteObjectStore.getBackingIndex();
      addIndex(backingIndex, queryOptions);
    }

    forEachIndexDo(
        new IndexOperation<O>() {
          @Override
          public boolean perform(Index<O> index) {
            queryOptions.put(QueryEngine.class, CollectionQueryEngine.this);
            queryOptions.put(Persistence.class, persistence);
            index.init(objectStore, queryOptions);
            return true;
          }
        });
  }

  /**
   * This is a no-op here, as currently the {@link ModificationListener#destroy(QueryOptions)}
   * method is only used by indexes.
   *
   * @param queryOptions Optional parameters for the update
   */
  @Override
  public void destroy(QueryOptions queryOptions) {
    // No-op
  }

  // -------------------- Methods for adding indexes --------------------

  /** {@inheritDoc} */
  @Override
  public void addIndex(Index<O> index, QueryOptions queryOptions) {
    indexManager.addIndex(index, queryOptions);
  }

  // -------------------- Methods for removing indexes --------------------

  /** {@inheritDoc} */
  @Override
  public void removeIndex(Index<O> index, QueryOptions queryOptions) {
    indexManager.removeIndex(index, queryOptions);
  }

  // -------------------- Method for accessing indexes --------------------

  /** {@inheritDoc} */
  @Override
  public Iterable<Index<O>> getIndexes() {
    return indexManager.getIndexes();
  }

  // -------------------- Methods for query processing --------------------

  /**
   * {@inheritDoc}
   *
   * <p>Delegates ordering-strategy selection and retrieval to {@link OrderingEngine}.
   */
  @Override
  public ResultSet<O> retrieve(final Query<O> query, final QueryOptions queryOptions) {
    return orderingEngine.retrieve(query, queryOptions);
  }

  static <O, A extends Comparable<A>> Persistence<O, A> getPersistenceFromQueryOptions(
      QueryOptions queryOptions) {
    @SuppressWarnings("unchecked")
    Persistence<O, A> persistence = (Persistence<O, A>) queryOptions.get(Persistence.class);
    if (persistence == null) {
      throw new IllegalStateException(
          "A required Persistence object was not supplied in query options");
    }
    return persistence;
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

  /** {@inheritDoc} */
  @Override
  public boolean addAll(final ObjectSet<O> objectSet, final QueryOptions queryOptions) {
    ensureMutable();
    final FlagHolder modified = new FlagHolder();
    forEachIndexDo(
        new IndexOperation<O>() {
          @Override
          public boolean perform(Index<O> index) {
            modified.value |= index.addAll(objectSet, queryOptions);
            return true;
          }
        });
    return modified.value;
  }

  /** {@inheritDoc} */
  @Override
  public boolean removeAll(final ObjectSet<O> objectSet, final QueryOptions queryOptions) {
    ensureMutable();
    final FlagHolder modified = new FlagHolder();
    forEachIndexDo(
        new IndexOperation<O>() {
          @Override
          public boolean perform(Index<O> index) {
            modified.value |= index.removeAll(objectSet, queryOptions);
            return true;
          }
        });
    return modified.value;
  }

  /**
   * {@inheritDoc}
   *
   * @param queryOptions
   */
  @Override
  public void clear(final QueryOptions queryOptions) {
    ensureMutable();
    forEachIndexDo(
        new IndexOperation<O>() {
          @Override
          public boolean perform(Index<O> index) {
            index.clear(queryOptions);
            return true;
          }
        });
  }

  /** {@inheritDoc} */
  @Override
  public boolean isMutable() {
    return indexManager.isMutable();
  }

  /** Throws an {@link IllegalStateException} if all indexes are not mutable. */
  void ensureMutable() {
    if (!indexManager.isMutable()) {
      throw new IllegalStateException("Cannot modify indexes, an immutable index has been added.");
    }
  }

  /**
   * A closure/callback object invoked for each index in turn by method {@link
   * CollectionQueryEngine#forEachIndexDo(IndexOperation)}.
   */
  interface IndexOperation<O> {
    /**
     * @param index The index to be processed
     * @return Operation can return true to continue iterating through all indexes, false to stop
     *     iterating
     */
    boolean perform(Index<O> index);
  }

  /**
   * Iterates through all indexes and for each index invokes the given index operation. If the
   * operation returns false for any index, stops iterating and returns false. If the operation
   * returns true for every index, returns true after all indexes have been iterated.
   *
   * @param indexOperation The operation to perform on each index.
   * @return true if the operation returned true for all indexes and so all indexes were iterated,
   *     false if the operation returned false for any index and so iteration was stopped
   */
  boolean forEachIndexDo(IndexOperation<O> indexOperation) {
    for (Index<O> index : indexManager.getAllIndexesIncludingFallback()) {
      boolean continueIterating = indexOperation.perform(index);
      if (!continueIterating) {
        return false;
      }
    }
    return true;
  }

  static class FlagHolder {
    boolean value = false;
  }

  static String getClassNameNullSafe(Object object) {
    return object == null ? null : object.getClass().getName();
  }
}
