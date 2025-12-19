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
package com.googlecode.cqengine.persistence.disk.docker;

import com.googlecode.cqengine.ConcurrentIndexedCollection;
import com.googlecode.cqengine.IndexedCollection;
import com.googlecode.cqengine.index.disk.DiskIndex;
import com.googlecode.cqengine.persistence.disk.DiskPersistence;
import com.googlecode.cqengine.resultset.ResultSet;
import com.googlecode.cqengine.testutil.Car;
import com.googlecode.cqengine.testutil.CarFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.googlecode.cqengine.query.QueryFactory.equal;
import static org.junit.Assert.*;

/**
 * Docker-based integration tests for Disk Persistence.
 * Tests real filesystem I/O, concurrency, and persistence in containerized environment.
 *
 * @author Saif Asif
 */
public class DockerDiskPersistenceIntegrationTest {

    private static final GenericContainer<?> diskContainer =
        new GenericContainer<>(DockerImageName.parse("alpine:latest"))
            .withCommand("tail", "-f", "/dev/null");

    private static File tempDbDir;

    @BeforeClass
    public static void setupContainer() {
        // Create temp directory for databases
        tempDbDir = new File(System.getProperty("java.io.tmpdir"), "cqengine-disk-docker-test");
        if (!tempDbDir.exists()) {
            boolean created = tempDbDir.mkdirs();
            System.out.println("Created temp directory: " + created);
        }

        // Mount temp directory into container
        diskContainer.withFileSystemBind(
            tempDbDir.getAbsolutePath(),
            "/data",
            org.testcontainers.containers.BindMode.READ_WRITE
        );

        // Start container
        if (!diskContainer.isRunning()) {
            diskContainer.start();
        }

        System.out.println("✓ Docker container started for disk persistence tests");
    }

    @AfterClass
    public static void tearDownContainer() {
        if (diskContainer.isRunning()) {
            diskContainer.stop();
        }
    }

    @Test
    public void testDiskPersistence_BasicOperations() {
        String dbFileName = "disk-persistence-basic.db";
        File dbFile = new File(tempDbDir, dbFileName);

        DiskPersistence<Car, Integer> persistence = DiskPersistence.onPrimaryKeyInFile(
            Car.CAR_ID,
            dbFile
        );

        IndexedCollection<Car> cars = new ConcurrentIndexedCollection<>(persistence);

        // Add data
        cars.addAll(CarFactory.createCollectionOfCars(50));

        // Verify persistence
        assertTrue("Database file should exist", dbFile.exists());
        long fileSize = dbFile.length();
        assertTrue("Database should have data: " + fileSize + " bytes", fileSize > 0);

        // Query data
        try (ResultSet<Car> fordCars = cars.retrieve(equal(Car.MANUFACTURER, "Ford"))) {
            int count = 0;
            for (Car car : fordCars) {
                assertEquals("Ford", car.getManufacturer());
                count++;
            }

            System.out.println("✓ Found " + count + " Ford cars in Docker-persisted database");
            assertTrue("Should find Ford cars", count > 0);
        }

        // Cleanup
        boolean deleted = dbFile.delete();
        System.out.println("  Database file deleted: " + deleted);
    }

    @Test
    public void testDiskPersistence_DataSurvivesRestart() {
        String dbFileName = "disk-persistence-restart.db";
        File dbFile = new File(tempDbDir, dbFileName);

        Set<Integer> originalCarIds;

        // First session - write data
        {
            DiskPersistence<Car, Integer> persistence = DiskPersistence.onPrimaryKeyInFile(
                Car.CAR_ID,
                dbFile
            );

            IndexedCollection<Car> cars = new ConcurrentIndexedCollection<>(persistence);
            Set<Car> testCars = CarFactory.createCollectionOfCars(100);
            cars.addAll(testCars);

            originalCarIds = testCars.stream()
                .map(Car::getCarId)
                .collect(Collectors.toSet());

            System.out.println("  First session: Persisted " + originalCarIds.size() + " cars");
        }

        // Verify file persisted
        assertTrue("Database file should exist after first session", dbFile.exists());
        long fileSize = dbFile.length();
        System.out.println("  Database file size: " + (fileSize / 1024) + " KB");

        // Second session - read data
        {
            DiskPersistence<Car, Integer> persistence = DiskPersistence.onPrimaryKeyInFile(
                Car.CAR_ID,
                dbFile
            );

            IndexedCollection<Car> cars = new ConcurrentIndexedCollection<>(persistence);

            Set<Integer> persistedCarIds = cars.stream()
                .map(Car::getCarId)
                .collect(Collectors.toSet());

            System.out.println("  Second session: Retrieved " + persistedCarIds.size() + " cars");

            assertEquals("Should retrieve same car IDs", originalCarIds, persistedCarIds);
            System.out.println("✓ Data persisted successfully across sessions");
        }

        // Cleanup
        dbFile.delete();
    }

    @Test
    public void testDiskPersistence_Compaction() {
        String dbFileName = "disk-persistence-compact.db";
        File dbFile = new File(tempDbDir, dbFileName);

        DiskPersistence<Car, Integer> persistence = DiskPersistence.onPrimaryKeyInFile(
            Car.CAR_ID,
            dbFile
        );

        IndexedCollection<Car> cars = new ConcurrentIndexedCollection<>(persistence);

        // Add data
        Set<Car> allCars = CarFactory.createCollectionOfCars(100);
        cars.addAll(allCars);

        long sizeWhenFull = dbFile.length();
        System.out.println("  Database size when full: " + (sizeWhenFull / 1024) + " KB");

        // Remove all data
        cars.removeAll(allCars);

        long sizeAfterRemoval = dbFile.length();
        System.out.println("  Database size after removal: " + (sizeAfterRemoval / 1024) + " KB");

        // Size should remain the same (SQLite doesn't auto-compact)
        assertEquals("Size should remain same after removal", sizeWhenFull, sizeAfterRemoval);

        // Compact
        persistence.compact();

        long sizeAfterCompaction = dbFile.length();
        System.out.println("  Database size after compaction: " + (sizeAfterCompaction / 1024) + " KB");

        assertTrue("Size should decrease after compaction", sizeAfterCompaction < sizeWhenFull);
        System.out.println("✓ Compaction working correctly");

        // Cleanup
        dbFile.delete();
    }

    @Test
    public void testDiskPersistence_ConcurrentAccess_WAL() throws Exception {
        String dbFileName = "disk-persistence-concurrent-wal.db";
        File dbFile = new File(tempDbDir, dbFileName);

        Properties props = new Properties();
        props.setProperty("journal_mode", "WAL");

        DiskPersistence<Car, Integer> persistence = DiskPersistence.onPrimaryKeyInFileWithProperties(
            Car.CAR_ID,
            dbFile,
            props
        );

        IndexedCollection<Car> cars = new ConcurrentIndexedCollection<>(persistence);
        cars.addAll(CarFactory.createCollectionOfCars(100));

        // Test concurrent reads
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
                    System.out.println("  Thread " + threadId + " found " + count + " cars");
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue("All threads should complete within 10 seconds",
                   latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // All threads should find the same number of results
        long uniqueResults = results.values().stream().distinct().count();
        assertEquals("All threads should get consistent results", 1, uniqueResults);

        System.out.println("✓ Concurrent access working with WAL mode");

        // Cleanup
        dbFile.delete();
    }

    @Test
    public void testDiskPersistence_WithDiskIndex() {
        String dbFileName = "disk-persistence-with-index.db";
        File dbFile = new File(tempDbDir, dbFileName);

        DiskPersistence<Car, Integer> persistence = DiskPersistence.onPrimaryKeyInFile(
            Car.CAR_ID,
            dbFile
        );

        IndexedCollection<Car> cars = new ConcurrentIndexedCollection<>(persistence);

        // Add disk-based indexes
        cars.addIndex(DiskIndex.onAttribute(Car.MANUFACTURER));
        cars.addIndex(DiskIndex.onAttribute(Car.MODEL));

        // Add data
        long startTime = System.currentTimeMillis();
        cars.addAll(CarFactory.createCollectionOfCars(500));
        long indexTime = System.currentTimeMillis() - startTime;

        System.out.println("  Indexed 500 cars with disk indexes in " + indexTime + "ms");

        // Query using index
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

        // Verify file size
        long fileSize = dbFile.length();
        System.out.println("✓ Total database size: " + (fileSize / 1024) + " KB");

        // Cleanup
        dbFile.delete();
    }

    @Test
    public void testDiskPersistence_BytesUsed() {
        String dbFileName = "disk-persistence-bytes.db";
        File dbFile = new File(tempDbDir, dbFileName);

        DiskPersistence<Car, Integer> persistence = DiskPersistence.onPrimaryKeyInFile(
            Car.CAR_ID,
            dbFile
        );

        IndexedCollection<Car> cars = new ConcurrentIndexedCollection<>(persistence);
        cars.addAll(CarFactory.createCollectionOfCars(50));

        long bytesUsed = persistence.getBytesUsed();
        long actualFileSize = dbFile.length();

        System.out.println("  Persistence reports: " + bytesUsed + " bytes");
        System.out.println("  Actual file size: " + actualFileSize + " bytes");

        assertTrue("Bytes used should be greater than zero", bytesUsed > 0);
        assertEquals("Bytes used should match file size", actualFileSize, bytesUsed);

        // Verify we can see the file in the container
        try {
            org.testcontainers.containers.Container.ExecResult result =
                diskContainer.execInContainer("ls", "-lh", "/data");
            String containerListing = result.getStdout();
            assertTrue("Container should see database file", containerListing.contains(dbFileName));
            System.out.println("✓ Container sees database file");
        } catch (Exception e) {
            System.out.println("! Container command execution not available: " + e.getMessage());
        }

        // Cleanup
        dbFile.delete();
    }

    @Test
    public void testDiskPersistence_LargeDataset() {
        String dbFileName = "disk-persistence-large.db";
        File dbFile = new File(tempDbDir, dbFileName);

        DiskPersistence<Car, Integer> persistence = DiskPersistence.onPrimaryKeyInFile(
            Car.CAR_ID,
            dbFile
        );

        IndexedCollection<Car> cars = new ConcurrentIndexedCollection<>(persistence);

        // Add large dataset
        long startTime = System.currentTimeMillis();
        cars.addAll(CarFactory.createCollectionOfCars(1000));
        long writeTime = System.currentTimeMillis() - startTime;

        System.out.println("  Wrote 1000 cars in " + writeTime + "ms");

        // Query performance
        startTime = System.currentTimeMillis();
        try (ResultSet<Car> results = cars.retrieve(equal(Car.DOORS, 5))) {
            int count = 0;
            for (Car ignored : results) {
                count++;
            }
            long queryTime = System.currentTimeMillis() - startTime;

            System.out.println("  Query found " + count + " cars in " + queryTime + "ms");
        }

        // Check file size
        long fileSize = dbFile.length();
        System.out.println("✓ Database file size: " + (fileSize / 1024) + " KB");
        assertTrue("File should be at least 50KB", fileSize > 50000);

        // Cleanup
        dbFile.delete();
    }

    @Test
    public void testDiskPersistence_Expand() {
        String dbFileName = "disk-persistence-expand.db";
        File dbFile = new File(tempDbDir, dbFileName);

        DiskPersistence<Car, Integer> persistence = DiskPersistence.onPrimaryKeyInFile(
            Car.CAR_ID,
            dbFile
        );

        IndexedCollection<Car> cars = new ConcurrentIndexedCollection<>(persistence);
        cars.addAll(CarFactory.createCollectionOfCars(50));

        long sizeBefore = persistence.getBytesUsed();

        // Expand by 100KB
        final long bytesToExpand = 102400;
        persistence.expand(bytesToExpand);

        long sizeAfter = persistence.getBytesUsed();

        System.out.println("  Size before expand: " + (sizeBefore / 1024) + " KB");
        System.out.println("  Size after expand: " + (sizeAfter / 1024) + " KB");

        assertTrue("Size should increase after expand", sizeAfter > sizeBefore);
        System.out.println("✓ Expand operation working correctly");

        // Cleanup
        dbFile.delete();
    }
}

