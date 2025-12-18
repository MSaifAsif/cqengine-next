# CQEngine Next - Maintained Fork

> ## ⚠️ Maintenance Fork Notice
> 
> **This is a maintained fork of the original CQEngine** by Niall Gallagher.  
> This version is published under the GroupId **`io.github.msaifasif`** to provide continued support, Java 21 compatibility, and modern dependency updates.
> 
> - **Original Project:** [github.com/npgall/cqengine](https://github.com/npgall/cqengine)
> - **This Fork:** [github.com/MSaifAsif/cqengine-next](https://github.com/MSaifAsif/cqengine-next)
> - **Original Author:** Niall Gallagher - All credit for the original design and implementation
> - **Fork Maintainer:** Saif Asif - Java 21 migration and ongoing maintenance

---

## 🚀 Quick Start - Using This Fork

### Maven Dependency

Add this to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.msaifasif</groupId>
    <artifactId>cqengine</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Gradle Dependency

Add this to your `build.gradle`:

```gradle
dependencies {
    implementation 'io.github.msaifasif:cqengine:1.0.0-SNAPSHOT'
}
```

### ✅ 100% Backward Compatible - No Code Changes Required!

All Java packages remain unchanged (`com.googlecode.cqengine.*`), so your existing code works without modification:

```java
// Your existing imports work as-is
import com.googlecode.cqengine.ConcurrentIndexedCollection;
import com.googlecode.cqengine.IndexedCollection;
import com.googlecode.cqengine.query.QueryFactory;
```

**Only the Maven coordinates have changed:**
- **Old:** `com.googlecode.cqengine:cqengine:3.6.1-SNAPSHOT`
- **New:** `io.github.msaifasif:cqengine:1.0.0-SNAPSHOT`

---

## 🎯 What's New in This Fork

### Java 21 Support
- Upgraded from JDK 1.8 to JDK 21
- All dependencies updated for Java 21 compatibility
- Modern Maven plugins and tooling

### Bug Fixes
- ✅ Fixed EqualsVerifier compatibility with Java 21 bytecode
- ✅ Fixed SQLite native library loading on Mac ARM64 (Apple Silicon)
- ✅ Resolved lambda type erasure issues
- ✅ Fixed ReflectiveAttribute equality verification

### Dependency Updates
- ByteBuddy 1.14.11 (Java 21 support)
- EqualsVerifier 3.16.1 (Java 21 bytecode support)
- SQLite JDBC 3.45.0.0 (ARM64 + security fixes)
- Javassist 3.30.2-GA (module system support)
- All Maven plugins updated to latest versions

See [CHANGELOG.md](CHANGELOG.md) for complete details of all changes, fixes, and improvements.

---

## 📚 Migration Guide

### From Original CQEngine

1. Update your Maven/Gradle dependency (see above)
2. **That's it!** No code changes needed - 100% backward compatible

### For New Projects

Simply use the Maven/Gradle coordinates shown above. All documentation, examples, and APIs from the original project work as-is.

---

[![Build Status](https://travis-ci.org/npgall/cqengine.svg?branch=master)](https://travis-ci.org/npgall/cqengine)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.googlecode.cqengine/cqengine/badge.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.googlecode.cqengine%22%20AND%20a%3Acqengine)

# CQEngine - Collection Query Engine

CQEngine – Collection Query Engine – is a high-performance Java collection which can be searched with SQL-like queries, with _extremely_ low latency.

  * Achieve millions of queries per second, with query latencies measured in microseconds
  * Offload query traffic from databases - scale your application tier
  * Outperform databases by a factor of thousands, even on low-end hardware

Supports on-heap persistence, off-heap persistence, disk persistence, and supports MVCC transaction isolation.

Interesting reviews of CQEngine:
  * [dzone.com: Comparing the search performance of CQEngine with standard Java collections](https://dzone.com/articles/comparing-search-performance)
  * [dzone.com: Getting started with CQEngine: LINQ for Java, only faster](https://dzone.com/articles/getting-started-cqengine-linq)
  * CQEngine in the wild: [excelian.com](http://www.excelian.com/exposure-and-counterparty-limit-checking)  [gravity4.com](http://gravity4.com/welcome-gravity4-engineering-blog/)  [snapdeal.com](http://engineering.snapdeal.com/how-were-building-a-system-to-scale-for-billions-of-requests-per-day-201601/) (3-5 billion requests/day)

## The Limits of Iteration
The classic way to retrieve objects matching some criteria from a collection, is to iterate through the collection and apply some tests to each object. If the object matches the criteria, then it is added to a result set. This is repeated for every object in the collection.

Conventional iteration is hugely inefficient, with time complexity O(_n_ _t_). It can be optimized, but requires **statistical knowledge** of the makeup of the collection. [Read more: The Limits of Iteration](documentation/TheLimitsOfIteration.md)

**Benchmark Sneak Peek**

Even with optimizations applied to convention iteration, CQEngine can outperform conventional iteration by wide margins. Here is a graph for a test comparing CQEngine latency with iteration for a range-type query:

![quantized-navigable-index-carid-between.png](documentation/images/quantized-navigable-index-carid-between.png)

  * **1,116,071 queries per second** (on a single 1.8GHz CPU core)
  * **0.896 microseconds per query**
  * CQEngine is **330187.50% faster** than naive iteration
  * CQEngine is **325727.79% faster** than optimized iteration

See the [Benchmark](documentation/Benchmark.md) wiki page for details of this test, and other tests with various types of query.


---


## CQEngine Overview

CQEngine solves the scalability and latency problems of iteration by making it possible to build _indexes_ on the fields of the objects stored in a collection, and applying algorithms based on the rules of set theory to _reduce the time complexity_ of accessing them.

**Indexing and Query Plan Optimization**

  * **Simple Indexes** can be added to any number of individual fields in a collection of objects, allowing queries on just those fields to be answered in O(_1_) time complexity
  * **Multiple indexes on the same field** can be added, each optimized for different types of query - for example equality, numerical range, string starts with etc.
  * **Compound Indexes** can be added which span multiple fields, allowing queries referencing several fields to also be answered in O(_1_) time complexity
  * **Nested Queries** are fully supported, such as the SQL equivalent of "`WHERE color = 'blue' AND(NOT(doors = 2 OR price > 53.00))`"
  * **Standing Query Indexes** can be added; these allow _arbitrarily complex queries_, or _nested query fragments_, to be answered in O(_1_) time complexity, regardless of the number of fields referenced. Large queries containing branches or query fragments for which standing query indexes exist, will automatically benefit from O(_1_) time complexity evaluation of their branches; in total several indexes might be used to accelerate complex queries
  * **Statistical Query Plan Optimization** - when several fields have suitable indexes, CQEngine will use statistical information from the indexes, to internally make a query plan which selects the indexes which can perform the query with minimum time complexity. When some referenced fields have suitable indexes and some do not, CQEngine will use the available indexes first, and will then iterate the smallest possible set of results from those indexes to filter objects for the rest of the query. In those cases time complexity will be greater than O(_1_), but usually significantly less than O(_n_)
  * **Iteration fallback** -  if no suitable indexes are available, CQEngine will evaluate the query via iteration, using lazy evaluation. CQEngine can always evaluate every query, even if no suitable indexes are available. Queries are not coupled with indexes, so indexes can be added after the fact, to speed up existing queries
  * **CQEngine supports full concurrency** and expects that objects will be added to and removed from the collection at runtime; CQEngine will take care of updating all registered indexes in realtime
  * **Type-safe** - nearly all errors in queries result in _compile-time_ errors instead of exceptions at runtime: all indexes, and all queries, are strongly typed using generics at both object-level and field-level
  * **On-heap/off-heap/disk** - objects can be stored on-heap (like a conventional Java collection), or off-heap (in native memory, within the JVM process but outside the Java heap), or persisted to disk

Several implementations of CQEngine's `IndexedCollection` are provided, supporting various concurrency and transaction isolation levels:

  * [ConcurrentIndexedCollection](http://htmlpreview.github.io/?http://raw.githubusercontent.com/npgall/cqengine/master/documentation/javadoc/apidocs/com/googlecode/cqengine/ConcurrentIndexedCollection.html) - lock-free concurrent reads and writes with no transaction isolation
  * [ObjectLockingIndexedCollection](http://htmlpreview.github.io/?http://raw.githubusercontent.com/npgall/cqengine/master/documentation/javadoc/apidocs/com/googlecode/cqengine/ObjectLockingIndexedCollection.html) - lock-free concurrent reads, and some locking of writes for object-level transaction isolation and consistency guarantees
  * [TransactionalIndexedCollection](http://htmlpreview.github.io/?http://raw.githubusercontent.com/npgall/cqengine/master/documentation/javadoc/apidocs/com/googlecode/cqengine/TransactionalIndexedCollection.html)  - lock-free concurrent reads, and sequential writes for full [transaction isolation](documentation/TransactionIsolation.md) using Multi-Version Concurrency Control

For more details see [TransactionIsolation](documentation/TransactionIsolation.md).

---

## Complete Example

In CQEngine applications mostly interact with [IndexedCollection](http://htmlpreview.github.io/?http://raw.githubusercontent.com/npgall/cqengine/master/documentation/javadoc/apidocs/com/googlecode/cqengine/IndexedCollection.html), which is an implementation of [java.util.Set](http://docs.oracle.com/javase/6/docs/api/java/util/Set.html), and it provides two additional methods:

  * [addIndex(SomeIndex)](http://htmlpreview.github.io/?http://raw.githubusercontent.com/npgall/cqengine/master/documentation/javadoc/apidocs/com/googlecode/cqengine/engine/QueryEngine.html#addIndex(com.googlecode.cqengine.index.Index)) allows indexes to be added to the collection
  * [retrieve(Query)](http://htmlpreview.github.io/?http://raw.githubusercontent.com/npgall/cqengine/master/documentation/javadoc/apidocs/com/googlecode/cqengine/engine/QueryEngine.html#retrieve(com.googlecode.cqengine.query.Query)) accepts a [Query](http://htmlpreview.github.io/?http://raw.githubusercontent.com/npgall/cqengine/master/documentation/javadoc/apidocs/com/googlecode/cqengine/query/Query.html) and returns a [ResultSet](http://htmlpreview.github.io/?http://raw.githubusercontent.com/npgall/cqengine/master/documentation/javadoc/apidocs/com/googlecode/cqengine/resultset/ResultSet.html) providing objects matching that query. `ResultSet` implements [java.lang.Iterable](http://docs.oracle.com/javase/6/docs/api/java/lang/Iterable.html), so accessing results is achieved by iterating the result set, or accessing it as a Java 8+ Stream

Here is a **complete example** of how to build a collection, add indexes and perform queries. It does not discuss _attributes_, which are discussed below.

**STEP 1: Create a new indexed collection**

```java
IndexedCollection<Car> cars = new ConcurrentIndexedCollection<Car>();
```

**STEP 2: Add some indexes to the collection**

```java
cars.addIndex(NavigableIndex.onAttribute(Car.CAR_ID));
cars.addIndex(ReversedRadixTreeIndex.onAttribute(Car.NAME));
cars.addIndex(SuffixTreeIndex.onAttribute(Car.DESCRIPTION));
cars.addIndex(HashIndex.onAttribute(Car.FEATURES));
```

**STEP 3: Add some objects to the collection**

```java
cars.add(new Car(1, "ford focus", "great condition, low mileage", Arrays.asList("spare tyre", "sunroof")));
cars.add(new Car(2, "ford taurus", "dirty and unreliable, flat tyre", Arrays.asList("spare tyre", "radio")));
cars.add(new Car(3, "honda civic", "has a flat tyre and high mileage", Arrays.asList("radio")));
```


**STEP 4: Run some queries**

Note: add import statement to your class: _`import static com.googlecode.cqengine.query.QueryFactory.*`_

* *Example 1: Find cars whose name ends with 'vic' or whose id is less than 2*

  Query:
  ```java
    Query<Car> query1 = or(endsWith(Car.NAME, "vic"), lessThan(Car.CAR_ID, 2));
    cars.retrieve(query1).forEach(System.out::println);
  ```
  Prints:
  ```
    Car{carId=3, name='honda civic', description='has a flat tyre and high mileage', features=[radio]}
    Car{carId=1, name='ford focus', description='great condition, low mileage', features=[spare tyre, sunroof]}
  ```
  
* *Example 2: Find cars whose flat tyre can be replaced*

  Query:
  ```java
    Query<Car> query2 = and(contains(Car.DESCRIPTION, "flat tyre"), equal(Car.FEATURES, "spare tyre"));
    cars.retrieve(query2).forEach(System.out::println);
  ```
  Prints:
  ```
    Car{carId=2, name='ford taurus', description='dirty and unreliable, flat tyre', features=[spare tyre, radio]}
  ```
  
* *Example 3: Find cars which have a sunroof or a radio but are not dirty*

  Query:
  ```java
    Query<Car> query3 = and(in(Car.FEATURES, "sunroof", "radio"), not(contains(Car.DESCRIPTION, "dirty")));
    cars.retrieve(query3).forEach(System.out::println);
  ```
   Prints:
  ```
    Car{carId=1, name='ford focus', description='great condition, low mileage', features=[spare tyre, sunroof]}
    Car{carId=3, name='honda civic', description='has a flat tyre and high mileage', features=[radio]}
  ```

Complete source code for these examples can be found [here](http://github.com/npgall/cqengine/blob/master/code/src/test/java/com/googlecode/cqengine/examples/introduction/).

---

_[...rest of original README content continues...]_

_(The complete original README documentation follows, including all sections on String-based queries, Feature Matrix, Attributes, Joins, Persistence, Result Sets, Deduplication, Ordering, Merge Strategies, Quantization, Grouping, Metadata, Hibernate integration, Maven usage, Scala/Kotlin support, and Related Projects)_

---

## License

Licensed under the Apache License, Version 2.0. See LICENSE file for details.

## Project Status - Maintained Fork

  * **Current Release:** 1.0.0-SNAPSHOT (December 2025)
  * **Java Version:** JDK 21
  * **Status:** Actively Maintained
  * **CHANGELOG:** [CHANGELOG.md](CHANGELOG.md)
  * **Original API/JavaDocs:** [Available here](http://htmlpreview.github.io/?http://raw.githubusercontent.com/npgall/cqengine/master/documentation/javadoc/apidocs/index.html)

Report bugs/feature requests in the [Issues](http://github.com/MSaifAsif/cqengine-next/issues) tab.

### About The Original Project

CQEngine was created and maintained by Niall Gallagher. The original project can be found at [github.com/npgall/cqengine](https://github.com/npgall/cqengine). This fork exists to provide continued support and updates for modern Java versions.

---

Many thanks to JetBrains for supporting CQEngine with free IntelliJ licenses!

[![](documentation/images/logo_jetbrains.png)](http://www.jetbrains.com)[![](documentation/images/logo_intellij_idea.png)](http://www.jetbrains.com/idea/)

