/*
 * Copyright 2025 Saif Asif
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.cqengine.query.comparative;

import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.attribute.SimpleAttribute;
import com.googlecode.cqengine.persistence.support.ObjectSet;
import com.googlecode.cqengine.query.option.QueryOptions;

import java.util.HashSet;
import java.util.Set;

import static com.googlecode.cqengine.query.simple.SimpleQuery.asLiteral;

/**
 * A comparative query which matches objects in the collection whose attribute values sort higher than all others.
 */
public class Max<O, A extends Comparable<A>> extends SimpleComparativeQuery<O, A> {

    /**
     * Creates a new {@link SimpleComparativeQuery} initialized to make assertions on values of the specified attribute
     *
     * @param attribute The attribute on which the assertion is to be made
     */
    public Max(Attribute<O, A> attribute) {
        super(attribute);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Max)) return false;

        Max max = (Max) o;

        return super.attribute.equals(max.attribute);
    }

    @Override
    protected int calcHashCode() {
        return super.attribute.hashCode();
    }

    @Override
    public Iterable<O> getMatchesForSimpleAttribute(SimpleAttribute<O, A> attribute, ObjectSet<O> objectsInCollection, QueryOptions queryOptions) {
        A maximumValue = null;
        Set<O> results = new HashSet<>();
        for (O object : objectsInCollection) {
            A attributeValue = attribute.getValue(object, queryOptions);
            maximumValue = evaluate(object, attributeValue, maximumValue, (Set<O>) results);
        }
        return results;
    }

    @Override
    public Iterable<O> getMatchesForNonSimpleAttribute(Attribute<O, A> attribute, ObjectSet<O> objectsInCollection, QueryOptions queryOptions) {
        A maximumValue = null;
        Set<O> results = new HashSet<>();
        for (O object : objectsInCollection) {
            Iterable<A> attributeValues = attribute.getValues(object, queryOptions);
            for (A attributeValue : attributeValues) {
                maximumValue = evaluate(object, attributeValue, maximumValue, results);
            }
        }
        return results;
    }

    /**
     * Helper method which evaluates each attribute value encountered. Adds the object to the given set of
     * results if the attribute value equals the max, or clears the results if a new maximum value is detected.
     *
     * @return The new maximum value
     */
    A evaluate(O currentObject, A currentAttributeValue, A currentMaximumValue, Set<O> results) {
        if (currentMaximumValue == null) {
            currentMaximumValue = currentAttributeValue;
            results.add(currentObject);
            return currentMaximumValue;
        }
        final int cmp = currentAttributeValue.compareTo(currentMaximumValue);
        if (cmp == 0) {
            // We found another object whose attribute value is the same as the current maximum value.
            // Add that object to the set of results...
            results.add(currentObject);
        } else if (cmp > 0) {
            // We found an object whose attribute value is greater than the maximum value found so far.
            // Clear all results encountered so far, and add this object to the set of results...
            currentMaximumValue = currentAttributeValue;
            results.clear();
            results.add(currentObject);
        }
        return currentMaximumValue;
    }

    @Override
    public String toString() {
        return "max(" + asLiteral(super.getAttributeName()) + ")";
    }
}
