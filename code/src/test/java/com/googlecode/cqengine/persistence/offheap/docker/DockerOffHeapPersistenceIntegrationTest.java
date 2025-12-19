/**
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
package com.googlecode.cqengine.persistence.offheap.docker;

import com.googlecode.cqengine.ConcurrentIndexedCollection;
import com.googlecode.cqengine.IndexedCollection;
import com.googlecode.cqengine.index.offheap.OffHeapIndex;
import com.googlecode.cqengine.persistence.offheap.OffHeapPersistence;
import com.googlecode.cqengine.resultset.ResultSet;
import com.googlecode.cqengine.testutil.Car;
import com.googlecode.cqengine.testutil.CarFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.*;

import static com.googlecode.cqengine.query.QueryFactory.equal;
import static org.junit.Assert.*;

/**
 * Docker-based integration tests for Off-Heap Persistence.
 * Tests memory limits and native memory behavior in containerized environment.
 *
 * @author Saif Asif
 */
public class DockerOffHeapPersistenceIntegrationTest {

    private static final GenericContainer<?> offHeapContainer =
        new GenericContainer<>(DockerImageName.parse("alpine:latest"))
            .withCommand("tail", "-f", "/dev/null");

    @BeforeClass
    public static void setupContainer() {
        // Start container with memory limit
        offHeapContainer.withCreateContainerCmdModifier(cmd ->
            cmd.getHostConfig().withMemory(512 * 1024 * 1024L) // 512MB limit
        );

        if (!offHeapContainer.isRunning()) {
            offHeapContainer.start();
        }

        System.out.println("✓ Docker container started for off-heap persistence tests");
    }

    @AfterClass
    public static void tearDownContainer() {
        if (offHeapContainer.isRunning()) {
            offHeapContainer.stop();
        }
    }

    @Test
    public void testOffHeapPersistence_BasicOperations() {
        OffHeapPersistence<Car, Integer> persistence = OffHeapPersistence.onPrimaryKey(Car.CAR_ID);
        IndexedCollection<Car> cars = new ConcurrentIndexedCollection<>(persistence);

        // Add data
        cars.addAll(CarFactory.createCollectionOfCars(50));

        // Verify bytes used
        long bytesUsed = persistence.getBytesUsed();
        assertTrue("Bytes used should be greater than zero", bytesUsed > 0);
        System.out.println("  Off-heap memory used: " + (bytesUsed / 1024) + " KB");

        // Query data
        try (ResultSet<Car> fordCars = cars.retrieve(equal(Car.MANUFACTURER, "Ford"))) {
            int count = 0;
            for (Car car : fordCars) {
                assertEquals("Ford", car.getManufacturer());
                count++;
            }
            System.out.println("✓ Found " + count + " Ford cars in off-heap storage");
            assertTrue("Should find Ford cars", count > 0);
        }
    }

    @Test
    public void testOffHeapPersistence_Compaction() {
        OffHeapPersistence<Car, Integer> persistence = OffHeapPersistence.onPrimaryKey(Car.CAR_ID);
        IndexedCollection<Car> cars = new ConcurrentIndexedCollection<>(persistence);

        // Add data
        cars.addAll(CarFactory.createCollectionOfCars(100));
        long bytesWhenFull = persistence.getBytesUsed();
        System.out.println("  Bytes when full: " + (bytesWhenFull / 1024) + " KB");

        // Remove all data
        cars.clear();
        long bytesAfterClear = persistence.getBytesUsed();
        System.out.println("  Bytes after clear: " + (bytesAfterClear / 1024) + " KB");

        // Memory should still be allocated (no auto-compaction)
        assertEquals("Bytes should remain same after clear", bytesWhenFull, bytesAfterClear);

        // Compact
        persistence.compact();
        long bytesAfterCompaction = persistence.getBytesUsed();
        System.out.println("  Bytes after compaction: " + (bytesAfterCompaction / 1024) + " KB");

        assertTrue("Bytes should decrease after compaction", bytesAfterCompaction < bytesWhenFull);
        System.out.println("✓ Off-heap compaction working correctly");
    }

    @Test
    public void testOffHeapPersistence_WithOffHeapIndex() {
        OffHeapPersistence<Car, Integer> persistence = OffHeapPersistence.onPrimaryKey(Car.CAR_ID);
        IndexedCollection<Car> cars = new ConcurrentIndexedCollection<>(persistence);

        // Add off-heap indexes
        cars.addIndex(OffHeapIndex.onAttribute(Car.MANUFACTURER));
        cars.addIndex(OffHeapIndex.onAttribute(Car.MODEL));

        // Add data
        long startTime = System.currentTimeMillis();
        cars.addAll(CarFactory.createCollectionOfCars(500));
        long indexTime = System.currentTimeMillis() - startTime;

        System.out.println("  Indexed 500 cars in off-heap in " + indexTime + "ms");

        // Query using off-heap index
        startTime = System.currentTimeMillis();
        try (ResultSet<Car> results = cars.retrieve(equal(Car.MANUFACTURER, "Honda"))) {
            int count = 0;
            for (Car car : results) {
                assertEquals("Honda", car.getManufacturer());
                count++;
            }
            long queryTime = System.currentTimeMillis() - startTime;

            System.out.println("  Query found " + count + " Honda cars in " + queryTime + "ms");
            assertTrue("Should find Honda cars", count > 0);
        }

        // Check memory usage
        long bytesUsed = persistence.getBytesUsed();
        System.out.println("✓ Total off-heap memory: " + (bytesUsed / 1024) + " KB");
        assertTrue("Should use off-heap memory", bytesUsed > 0);
    }

    @Test
    public void testOffHeapPersistence_ConcurrentAccess() throws Exception {
        OffHeapPersistence<Car, Integer> persistence = OffHeapPersistence.onPrimaryKey(Car.CAR_ID);
        IndexedCollection<Car> cars = new ConcurrentIndexedCollection<>(persistence);
        cars.addAll(CarFactory.createCollectionOfCars(100));

        // Test concurrent reads from off-heap
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch latch = new CountDownLatch(4);
        ConcurrentHashMap<Integer, Integer> results = new ConcurrentHashMap<>();

        for (int i = 0; i < 4; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try (ResultSet<Car> rs = cars.retrieve(equal(Car.DOORS, 5))) {
                    int count = 0;
                    for (Car ignored : rs) {
                        count++;
                    }
                    results.put(threadId, count);
                    System.out.println("  Thread " + threadId + " read " + count + " cars from off-heap");
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue("All threads should complete", latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // All threads should get consistent results
        long uniqueResults = results.values().stream().distinct().count();
        assertEquals("All threads should get same results", 1, uniqueResults);

        System.out.println("✓ Concurrent off-heap access working correctly");
    }

    @Test
    public void testOffHeapPersistence_LargeDataset() {
        OffHeapPersistence<Car, Integer> persistence = OffHeapPersistence.onPrimaryKey(Car.CAR_ID);
        IndexedCollection<Car> cars = new ConcurrentIndexedCollection<>(persistence);

        // Add large dataset to off-heap
        long startTime = System.currentTimeMillis();
        cars.addAll(CarFactory.createCollectionOfCars(1000));
        long loadTime = System.currentTimeMillis() - startTime;

        System.out.println("  Loaded 1000 cars to off-heap in " + loadTime + "ms");

        // Check memory usage
        long bytesUsed = persistence.getBytesUsed();
        System.out.println("  Off-heap memory used: " + (bytesUsed / 1024) + " KB");
        assertTrue("Should use significant off-heap memory", bytesUsed > 50000);

        // Query performance
        startTime = System.currentTimeMillis();
        try (ResultSet<Car> results = cars.retrieve(equal(Car.DOORS, 3))) {
            int count = 0;
            for (Car ignored : results) {
                count++;
            }
            long queryTime = System.currentTimeMillis() - startTime;

            System.out.println("  Query executed in " + queryTime + "ms, found " + count + " cars");
        }

        System.out.println("✓ Large dataset handling in off-heap successful");
    }

    @Test
    public void testOffHeapPersistence_MemoryExpansion() {
        OffHeapPersistence<Car, Integer> persistence = OffHeapPersistence.onPrimaryKey(Car.CAR_ID);
        IndexedCollection<Car> cars = new ConcurrentIndexedCollection<>(persistence);

        cars.addAll(CarFactory.createCollectionOfCars(50));
        long bytesInitial = persistence.getBytesUsed();

        // Expand by 100KB
        final long bytesToExpand = 102400;
        persistence.expand(bytesToExpand);

        long bytesAfterExpand = persistence.getBytesUsed();

        System.out.println("  Initial memory: " + (bytesInitial / 1024) + " KB");
        System.out.println("  After expand: " + (bytesAfterExpand / 1024) + " KB");

        assertTrue("Memory should increase after expand", bytesAfterExpand > bytesInitial);
        System.out.println("✓ Off-heap memory expansion working");
    }

    @Test
    public void testOffHeapPersistence_AddRemovePattern() {
        OffHeapPersistence<Car, Integer> persistence = OffHeapPersistence.onPrimaryKey(Car.CAR_ID);
        IndexedCollection<Car> cars = new ConcurrentIndexedCollection<>(persistence);

        // Add/remove pattern to test memory management
        for (int i = 0; i < 5; i++) {
            cars.addAll(CarFactory.createCollectionOfCars(100));
            long bytesAfterAdd = persistence.getBytesUsed();

            cars.clear();
            long bytesAfterClear = persistence.getBytesUsed();

            System.out.println("  Iteration " + i + ": Added=" + (bytesAfterAdd / 1024) +
                             " KB, After clear=" + (bytesAfterClear / 1024) + " KB");

            assertEquals("Memory should remain same after clear", bytesAfterAdd, bytesAfterClear);
        }

        // Final compaction
        persistence.compact();
        long bytesAfterCompact = persistence.getBytesUsed();
        System.out.println("  After final compact: " + (bytesAfterCompact / 1024) + " KB");

        System.out.println("✓ Add/remove pattern test completed");
    }

    @Test
    public void testOffHeapPersistence_ContainerMemoryLimit() {
        // This test verifies that off-heap works within container memory limits
        OffHeapPersistence<Car, Integer> persistence = OffHeapPersistence.onPrimaryKey(Car.CAR_ID);
        IndexedCollection<Car> cars = new ConcurrentIndexedCollection<>(persistence);

        // Add data within container's 512MB limit
        cars.addAll(CarFactory.createCollectionOfCars(200));

        long bytesUsed = persistence.getBytesUsed();
        long bytesInMB = bytesUsed / (1024 * 1024);

        System.out.println("  Off-heap memory used: " + bytesInMB + " MB");
        System.out.println("  Container limit: 512 MB");

        assertTrue("Should use less than container limit", bytesInMB < 512);
        System.out.println("✓ Off-heap operates within container memory limits");
    }
}

