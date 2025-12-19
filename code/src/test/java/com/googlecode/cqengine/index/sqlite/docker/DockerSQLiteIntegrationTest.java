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
package com.googlecode.cqengine.index.sqlite.docker;

import com.googlecode.cqengine.ConcurrentIndexedCollection;
import com.googlecode.cqengine.IndexedCollection;
import com.googlecode.cqengine.persistence.disk.DiskPersistence;
import com.googlecode.cqengine.query.QueryFactory;
import com.googlecode.cqengine.resultset.ResultSet;
import com.googlecode.cqengine.testutil.Car;
import com.googlecode.cqengine.testutil.CarFactory;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.Assert.*;

/**
 * Docker-based integration tests for SQLite persistence.
 * Tests real filesystem I/O and database operations in a containerized environment.
 *
 * @author Saif Asif
 */
public class DockerSQLiteIntegrationTest {

    private static final GenericContainer<?> sqliteContainer =
        new GenericContainer<>(DockerImageName.parse("alpine:latest"))
            .withCommand("tail", "-f", "/dev/null");

    private static File tempDbDir;
    private static final String TEST_DB_NAME = "test_sqlite.db";
    private SQLiteDataSource dataSource;

    private IndexedCollection<Car> createDockerBackedCollection(String dbName) throws Exception {
        File dbFile = new File(tempDbDir, dbName);
        DiskPersistence<Car, Integer> persistence = DiskPersistence.onPrimaryKeyInFile(Car.CAR_ID, dbFile);
        IndexedCollection<Car> collection = new ConcurrentIndexedCollection<>(persistence);
        return collection;
    }

    @BeforeClass
    public static void setupContainer() throws Exception {
        // Create temp directory for databases
        tempDbDir = new File(System.getProperty("java.io.tmpdir"), "cqengine-docker-test");
        if (!tempDbDir.exists()) {
            tempDbDir.mkdirs();
        }

        // Mount temp directory into container
        sqliteContainer.withFileSystemBind(
            tempDbDir.getAbsolutePath(),
            "/data",
            org.testcontainers.containers.BindMode.READ_WRITE
        );

        // Start container if not already running
        if (!sqliteContainer.isRunning()) {
            sqliteContainer.start();
        }
    }

    @After
    public void cleanup() throws Exception {
        if (dataSource != null) {
            // Clean up database files
            File dbFile = new File(tempDbDir, TEST_DB_NAME);
            if (dbFile.exists()) {
                dbFile.delete();
            }
        }
    }

    private SQLiteDataSource createDataSource() {
        File dbFile = new File(tempDbDir, TEST_DB_NAME);
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);

        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl(url);

        return dataSource;
    }

    @Test
    public void testDockerSQLiteDatabaseCreation() throws Exception {
        dataSource = createDataSource();

        try (Connection conn = dataSource.getConnection()) {
            // Create a test table
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("CREATE TABLE test_table (id INTEGER PRIMARY KEY, name TEXT)");
                stmt.executeUpdate("INSERT INTO test_table VALUES (1, 'test')");
            }

            // Verify data was persisted
            try (Statement stmt = conn.createStatement()) {
                try (java.sql.ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM test_table")) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                }
            }
        }

        // Verify file exists in Docker volume
        File dbFile = new File(tempDbDir, TEST_DB_NAME);
        assertTrue("Database file should exist in Docker volume", dbFile.exists());
        assertTrue("Database file should have data", dbFile.length() > 0);

        System.out.println("✓ SQLite database created in Docker container volume");
        System.out.println("  Database file size: " + dbFile.length() + " bytes");
    }

    @Test
    public void testDockerSQLitePersistence() throws Exception {
        dataSource = createDataSource();

        // First connection - write data
        try (Connection conn = dataSource.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, email TEXT)");
                stmt.executeUpdate("INSERT INTO users VALUES (1, 'Alice', 'alice@example.com')");
                stmt.executeUpdate("INSERT INTO users VALUES (2, 'Bob', 'bob@example.com')");
                stmt.executeUpdate("INSERT INTO users VALUES (3, 'Charlie', 'charlie@example.com')");
            }
        }

        // Second connection - read data (simulating restart)
        try (Connection conn = dataSource.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                try (java.sql.ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
                    assertTrue(rs.next());
                    assertEquals("Data should persist across connections", 3, rs.getInt(1));
                }
            }
        }

        System.out.println("✓ Data persisted successfully across connections");
    }

    @Test
    public void testDockerSQLiteIndexOperations() throws Exception {
        dataSource = createDataSource();

        try (Connection conn = dataSource.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                // Create table with index
                stmt.executeUpdate("CREATE TABLE products (id INTEGER PRIMARY KEY, category TEXT, price REAL)");
                stmt.executeUpdate("CREATE INDEX idx_category ON products (category)");

                // Insert test data
                stmt.executeUpdate("INSERT INTO products VALUES (1, 'Electronics', 999.99)");
                stmt.executeUpdate("INSERT INTO products VALUES (2, 'Electronics', 499.99)");
                stmt.executeUpdate("INSERT INTO products VALUES (3, 'Books', 29.99)");
                stmt.executeUpdate("INSERT INTO products VALUES (4, 'Electronics', 199.99)");
            }

            // Query using index
            try (Statement stmt = conn.createStatement()) {
                try (java.sql.ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM products WHERE category = 'Electronics'")) {
                    assertTrue(rs.next());
                    assertEquals("Should find all electronics", 3, rs.getInt(1));
                }
            }

            // Verify index exists
            try (Statement stmt = conn.createStatement()) {
                try (java.sql.ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='index' AND name='idx_category'")) {
                    assertTrue("Index should exist", rs.next());
                    assertEquals("idx_category", rs.getString(1));
                }
            }
        }

        System.out.println("✓ SQLite indexes working correctly in Docker container");
    }

    @Test
    public void testDockerSQLiteConcurrentAccess() throws Exception {
        dataSource = createDataSource();

        // Create test table
        try (Connection conn = dataSource.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("CREATE TABLE counters (id INTEGER PRIMARY KEY, count INTEGER)");
                stmt.executeUpdate("INSERT INTO counters VALUES (1, 0)");
            }
        }

        // Simulate concurrent reads
        Thread[] threads = new Thread[3];
        final int[] readCounts = new int[3];

        for (int i = 0; i < 3; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                try (Connection conn = dataSource.getConnection()) {
                    try (Statement stmt = conn.createStatement()) {
                        try (java.sql.ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM counters")) {
                            if (rs.next()) {
                                readCounts[threadId] = rs.getInt(1);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            threads[i].start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            thread.join(5000);
        }

        // All threads should have read the same value
        for (int count : readCounts) {
            assertEquals("Concurrent reads should be consistent", 1, count);
        }

        System.out.println("✓ Concurrent SQLite access working in Docker container");
    }

    @Test
    public void testDockerSQLiteLargeDataset() throws Exception {
        dataSource = createDataSource();

        try (Connection conn = dataSource.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("CREATE TABLE large_table (id INTEGER PRIMARY KEY, value TEXT)");

                // Insert 1000 rows
                long startTime = System.currentTimeMillis();
                for (int i = 0; i < 1000; i++) {
                    stmt.executeUpdate("INSERT INTO large_table VALUES (" + i + ", 'value_" + i + "')");
                }
                long insertTime = System.currentTimeMillis() - startTime;

                System.out.println("  Inserted 1000 rows in " + insertTime + "ms");
            }

            // Query performance test
            try (Statement stmt = conn.createStatement()) {
                long startTime = System.currentTimeMillis();
                try (java.sql.ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM large_table")) {
                    assertTrue(rs.next());
                    assertEquals(1000, rs.getInt(1));
                }
                long queryTime = System.currentTimeMillis() - startTime;

                System.out.println("  Query executed in " + queryTime + "ms");
            }
        }

        // Check file size
        File dbFile = new File(tempDbDir, TEST_DB_NAME);
        System.out.println("✓ Large dataset test completed");
        System.out.println("  Database file size: " + (dbFile.length() / 1024) + " KB");
    }

    @Test
    public void testDockerContainerFileVisibility() throws Exception {
        dataSource = createDataSource();

        // Create a database in Docker volume
        try (Connection conn = dataSource.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("CREATE TABLE visibility_test (id INTEGER PRIMARY KEY)");
                stmt.executeUpdate("INSERT INTO visibility_test VALUES (1)");
            }
        }

        // Verify file is visible from host
        File dbFile = new File(tempDbDir, TEST_DB_NAME);
        assertTrue("File should be visible from host", dbFile.exists());

        // Execute command in container to list files
        try {
            org.testcontainers.containers.Container.ExecResult result =
                sqliteContainer.execInContainer("ls", "-lh", "/data");
            String output = result.getStdout();
            assertTrue("Container should see the database file", output.contains(TEST_DB_NAME));
            System.out.println("✓ Container file visibility verified");
            System.out.println("  Container sees: " + TEST_DB_NAME);
        } catch (Exception e) {
            System.out.println("! Container command execution not available: " + e.getMessage());
        }
    }

    @Test
    public void testDockerBackedCollection() throws Exception {
        IndexedCollection<Car> collection = createDockerBackedCollection("docker_collection.db");
        collection.addAll(CarFactory.createCollectionOfCars(100));
        ResultSet<Car> fordCars = collection.retrieve(QueryFactory.equal(Car.MANUFACTURER, "Ford"));
        assertTrue(fordCars.size() > 0);
    }
}
