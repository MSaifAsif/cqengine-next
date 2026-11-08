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

import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.attribute.StandingQueryAttribute;
import com.googlecode.cqengine.index.AttributeIndex;
import com.googlecode.cqengine.index.Index;
import com.googlecode.cqengine.index.compound.CompoundIndex;
import com.googlecode.cqengine.index.compound.support.CompoundAttribute;
import com.googlecode.cqengine.index.fallback.FallbackIndex;
import com.googlecode.cqengine.index.sqlite.IdentityAttributeIndex;
import com.googlecode.cqengine.index.sqlite.SimplifiedSQLiteIndex;
import com.googlecode.cqengine.index.standingquery.StandingQueryIndex;
import com.googlecode.cqengine.index.unique.UniqueIndex;
import com.googlecode.cqengine.persistence.Persistence;
import com.googlecode.cqengine.persistence.support.ObjectStore;
import com.googlecode.cqengine.query.Query;
import com.googlecode.cqengine.query.option.QueryOptions;
import com.googlecode.cqengine.resultset.ResultSet;
import com.googlecode.cqengine.resultset.iterator.ConcatenatingIterable;
import com.googlecode.cqengine.persistence.support.ObjectStoreResultSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class IndexManager<O> {

  private final ConcurrentMap<Attribute<O, ?>, Set<Index<O>>> attributeIndexes =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<Attribute<O, ?>, Index<O>> uniqueIndexes = new ConcurrentHashMap<>();
  private final ConcurrentMap<CompoundAttribute<O>, CompoundIndex<O>> compoundIndexes =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<Query<O>, Index<O>> standingQueryIndexes = new ConcurrentHashMap<>();
  private final FallbackIndex<O> fallbackIndex = new FallbackIndex<>();
  private final Set<Index<O>> immutableIndexes =
      Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final ObjectStore<O> objectStore;
  private final Persistence<O, ? extends Comparable> persistence;
  private final QueryEngine<O> owningEngine;

  IndexManager(
      ObjectStore<O> objectStore,
      Persistence<O, ? extends Comparable> persistence,
      QueryEngine<O> owningEngine) {
    this.objectStore = objectStore;
    this.persistence = persistence;
    this.owningEngine = owningEngine;
  }

  public void addIndex(Index<O> index, QueryOptions queryOptions) {
    switch (index) {
      case StandingQueryIndex<O> standingQueryIndex ->
          addStandingQueryIndex(
              standingQueryIndex, standingQueryIndex.getStandingQuery(), queryOptions);
      case CompoundIndex<O> compoundIndex -> {
        CompoundAttribute<O> compoundAttribute = compoundIndex.getAttribute();
        addCompoundIndex(compoundIndex, compoundAttribute, queryOptions);
      }
      case AttributeIndex<?, O> attributeIndex -> {
        Attribute<O, ?> indexedAttribute = attributeIndex.getAttribute();
        if (indexedAttribute instanceof StandingQueryAttribute standingQueryAttribute) {
          @SuppressWarnings("unchecked")
          Query<O> standingQuery = (Query<O>) standingQueryAttribute.getQuery();
          addStandingQueryIndex(index, standingQuery, queryOptions);
        } else {
          addAttributeIndex(attributeIndex, queryOptions);
        }
      }
      case null, default ->
          throw new IllegalStateException(
              "Unexpected type of index: " + (index == null ? null : index.getClass().getName()));
    }
    if (!index.isMutable()) {
      immutableIndexes.add(index);
    }
  }

  /**
   * Adds an {@link AttributeIndex}.
   *
   * @param attributeIndex The index to add
   * @param <A> The type of objects indexed
   */
  private <A> void addAttributeIndex(
      AttributeIndex<A, O> attributeIndex, QueryOptions queryOptions) {
    Attribute<O, A> attribute = attributeIndex.getAttribute();
    Set<Index<O>> indexesOnThisAttribute =
        attributeIndexes.computeIfAbsent(
            attribute, k -> Collections.newSetFromMap(new ConcurrentHashMap<Index<O>, Boolean>()));
    if (attributeIndex instanceof SimplifiedSQLiteIndex) {
      // Ensure there is not already an identity index added for this attribute...
      for (Index<O> existingIndex : indexesOnThisAttribute) {
        if (existingIndex instanceof IdentityAttributeIndex) {
          throw new IllegalStateException(
              "An index has already been added on the primary key attribute used for persistence, and no additional non-heap indexes are allowed on that attribute: "
                  + attribute);
        }
      }
    }
    // Add the index...
    if (!indexesOnThisAttribute.add(attributeIndex)) {
      throw new IllegalStateException(
          "An equivalent index has already been added for attribute: " + attribute);
    }
    if (attributeIndex instanceof UniqueIndex) {
      // We put UniqueIndexes in a separate map too, to access directly...
      uniqueIndexes.put(attribute, attributeIndex);
    }
    queryOptions.put(QueryEngine.class, owningEngine);
    queryOptions.put(Persistence.class, persistence);
    attributeIndex.init(objectStore, queryOptions);
  }

  /**
   * Adds either a {@link StandingQueryIndex} or a regular index build on a {@link
   * StandingQueryAttribute}.
   *
   * @param standingQueryIndex The index to add
   * @param standingQuery The query on which the index is based
   */
  private void addStandingQueryIndex(
      Index<O> standingQueryIndex, Query<O> standingQuery, QueryOptions queryOptions) {
    Index<O> existingIndex = standingQueryIndexes.putIfAbsent(standingQuery, standingQueryIndex);
    if (existingIndex != null) {
      throw new IllegalStateException(
          "An index has already been added for standing query: " + standingQuery);
    }
    queryOptions.put(QueryEngine.class, owningEngine);
    queryOptions.put(Persistence.class, persistence);
    standingQueryIndex.init(objectStore, queryOptions);
  }

  /**
   * Adds a {@link CompoundIndex}.
   *
   * @param compoundIndex The index to add
   * @param compoundAttribute The compound attribute on which the index is based
   */
  private void addCompoundIndex(
      CompoundIndex<O> compoundIndex,
      CompoundAttribute<O> compoundAttribute,
      QueryOptions queryOptions) {
    CompoundIndex<O> existingIndex = compoundIndexes.putIfAbsent(compoundAttribute, compoundIndex);
    if (existingIndex != null) {
      throw new IllegalStateException(
          "An index has already been added for compound attribute: " + compoundAttribute);
    }
    queryOptions.put(QueryEngine.class, owningEngine);
    queryOptions.put(Persistence.class, persistence);
    compoundIndex.init(objectStore, queryOptions);
  }

  // -------------------- Methods for removing indexes --------------------

  /**
   * Removes the given index from this manager and notifies the index that it has been removed so
   * that it can release any underlying storage.
   *
   * @param index The index to remove
   * @param queryOptions Optional parameters for the index
   */
  public void removeIndex(Index<O> index, QueryOptions queryOptions) {
    boolean removed;
    switch (index) {
      case StandingQueryIndex<O> standingQueryIndex ->
          removed =
              standingQueryIndexes.remove(
                  standingQueryIndex.getStandingQuery(), standingQueryIndex);
      case CompoundIndex<O> compoundIndex -> {
        CompoundAttribute<O> compoundAttribute = compoundIndex.getAttribute();

        removed = compoundIndexes.remove(compoundAttribute, compoundIndex);
      }
      case AttributeIndex<?, O> attributeIndex -> {
        Attribute<O, ?> indexedAttribute = attributeIndex.getAttribute();

        if (indexedAttribute instanceof StandingQueryAttribute standingQueryAttribute) {
          @SuppressWarnings("unchecked")
          Query<O> standingQuery = (Query<O>) standingQueryAttribute.getQuery();

          removed = standingQueryIndexes.remove(standingQuery, index);
        } else {
          Set<Index<O>> indexesOnThisAttribute = attributeIndexes.get(indexedAttribute);

          removed = indexesOnThisAttribute.remove(attributeIndex);

          if (attributeIndex instanceof UniqueIndex) {
            // Remove from UniqueIndexes as well...
            removed = uniqueIndexes.remove(indexedAttribute, attributeIndex) || removed;
          }

          if (indexesOnThisAttribute.isEmpty()) {
            // If there are no more indexes left on this attribute,
            // remove the Set which was used to store indexes on the attribute also...
            attributeIndexes.remove(indexedAttribute);
          }
        }
      }
      case null, default ->
          throw new IllegalStateException(
              "Unexpected type of index: " + (index == null ? null : index.getClass().getName()));
    }
    if (removed && !index.isMutable()) {
      // Remove from the set of immutable indexes; this is used by ensureMutable() and the
      // isMutable() method...
      immutableIndexes.remove(index);
    }
    // Notify the index that it has been removed, so that it can delete underlying storage used if
    // necessary...
    index.destroy(queryOptions);
  }

  // -------------------- Method for accessing indexes --------------------

  /**
   * Returns all indexes which have been added to this manager (attribute, compound and
   * standing-query indexes).
   *
   * @return All indexes which have been added to this manager
   */
  public Iterable<Index<O>> getIndexes() {
    List<Index<O>> indexes = new ArrayList<Index<O>>();
    for (Set<Index<O>> attributeIndexes : this.attributeIndexes.values()) {
      indexes.addAll(attributeIndexes);
    }
    indexes.addAll(this.compoundIndexes.values());
    indexes.addAll(this.standingQueryIndexes.values());
    return indexes;
  }

  /**
   * Returns an {@link Iterable} over all indexes which have been added on the given attribute,
   * including the {@link FallbackIndex} which is implicitly available on all attributes.
   *
   * @param attribute The relevant attribute
   * @return All indexes which have been added on the given attribute, including the {@link
   *     FallbackIndex}
   */
  Iterable<Index<O>> getIndexesOnAttribute(Attribute<O, ?> attribute) {
    final Set<Index<O>> indexesOnAttribute = attributeIndexes.get(attribute);
    if (indexesOnAttribute == null || indexesOnAttribute.isEmpty()) {
      // If no index is registered for this attribute, return the fallback index...
      return Collections.<Index<O>>singleton(this.fallbackIndex);
    }
    // Return an Iterable over the registered indexes and the fallback index...
    List<Iterable<Index<O>>> iterables = new ArrayList<Iterable<Index<O>>>(2);
    iterables.add(indexesOnAttribute);
    iterables.add(Collections.<Index<O>>singleton(fallbackIndex));
    return new ConcatenatingIterable<Index<O>>(iterables);
  }

  /**
   * Returns the entire collection wrapped as a {@link ResultSet}, with retrieval cost {@link
   * Integer#MAX_VALUE}.
   *
   * <p>Merge cost is the size of the collection.
   *
   * @return The entire collection wrapped as a {@link ResultSet}, with retrieval cost {@link
   *     Integer#MAX_VALUE}
   */
  ResultSet<O> getEntireCollectionAsResultSet(
      final Query<O> query, final QueryOptions queryOptions) {
    return new ObjectStoreResultSet<O>(objectStore, query, queryOptions, Integer.MAX_VALUE) {
      // Override getMergeCost() to avoid calling size(),
      // which may be expensive for custom implementations of lazy backing sets...
      @Override
      public int getMergeCost() {
        return Integer.MAX_VALUE;
      }

      @Override
      public Query<O> getQuery() {
        return query;
      }

      @Override
      public QueryOptions getQueryOptions() {
        return queryOptions;
      }
    };
  }

  /** Returns the {@link UniqueIndex} registered on the given attribute, or {@code null} if none. */
  Index<O> getUniqueIndex(Attribute<O, ?> attribute) {
    return uniqueIndexes.get(attribute);
  }

  /** Returns the standing-query index registered for the given query, or {@code null} if none. */
  Index<O> getStandingQueryIndex(Query<O> query) {
    return standingQueryIndexes.get(query);
  }

  /** Returns true if any compound index has been registered. */
  boolean hasCompoundIndexes() {
    return !compoundIndexes.isEmpty();
  }

  /**
   * Returns the compound index registered on the given compound attribute, or {@code null} if none.
   */
  CompoundIndex<O> getCompoundIndex(CompoundAttribute<O> compoundAttribute) {
    return compoundIndexes.get(compoundAttribute);
  }

  /** Returns true if all registered indexes are mutable (also true when there are no indexes). */
  boolean isMutable() {
    return immutableIndexes.isEmpty();
  }

  /**
   * Returns every registered index followed by the implicit {@link FallbackIndex}, in the order:
   * attribute indexes, compound indexes, standing-query indexes, fallback index.
   *
   * <p>This is the iteration order previously used by {@code
   * CollectionQueryEngine.forEachIndexDo(...)}.
   */
  Iterable<Index<O>> getAllIndexesIncludingFallback() {
    List<Index<O>> allIndexes = new ArrayList<Index<O>>();
    for (Set<Index<O>> indexesOnAttribute : this.attributeIndexes.values()) {
      allIndexes.addAll(indexesOnAttribute);
    }
    allIndexes.addAll(this.compoundIndexes.values());
    allIndexes.addAll(this.standingQueryIndexes.values());
    allIndexes.add(this.fallbackIndex);
    return allIndexes;
  }
}
