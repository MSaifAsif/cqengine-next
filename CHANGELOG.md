# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0-SNAPSHOT] - 2025-12-18 & 2025-12-19

### Added
- **README.md** - Comprehensive project documentation with verified links, quick start guide, migration instructions, and Docker testing guide
- **maven-assembly-plugin 3.6.0** - Creates fat jar with all dependencies (16 MB) in ~5 seconds, full Java 21 compatibility
- **Docker-based integration tests** - Testcontainers support for realistic database testing (30 total: 29 passing, 1 skipped)
  - `DockerSQLiteIntegrationTest` - 7 tests for SQLite persistence, concurrency, large datasets
  - `DockerDiskPersistenceIntegrationTest` - 8 tests for disk persistence, compaction, WAL mode
  - `DockerOffHeapPersistenceIntegrationTest` - 8 tests for off-heap memory, container limits, concurrent access
  - `DockerCompositePersistenceIntegrationTest` - 7 tests for multi-tier persistence (6 passing, 1 skipped)
  - Tests run in isolated Alpine Linux containers with mounted volumes and memory limits
  - Real filesystem I/O, native memory, and multi-tier architecture testing
  - **Known Limitation**: `testCompositePersistence_AllThreeLayers` skipped due to StackOverflow when combining DiskPersistence + OffHeapIndex on same primary key (SQLite recursion issue)

### Changed
- **Project rebranded** to "CQEngine Next - Maintained Fork" with repository moved to `cqengine-next`

### Fixed
- **Build system** - Replaced problematic maven-shade-plugin with maven-assembly-plugin to resolve infinite loop issue

### Dependencies
- **Testcontainers 1.19.3** - Added for Docker-based integration testing
- **SLF4J Simple 1.7.36** - Added for Testcontainers logging (test scope only)

### Requirements for Docker Tests
- **Docker Desktop** or **Docker Engine** must be installed and running
- To run Docker tests: `mvn test -Dtest=Docker*IntegrationTest`
- **Troubleshooting Docker connection issues:**
  1. Verify Docker is running: `docker ps` should show output without errors
  2. Check Docker socket: `ls -la /var/run/docker.sock` (or `~/.docker/run/docker.sock` on macOS)
  3. Restart Docker Desktop if tests fail with "Could not find valid Docker environment"
  4. Check Docker Desktop settings: Enable "Expose daemon on tcp://localhost:2375 without TLS" if needed
  5. Configuration file created: `src/test/resources/.testcontainers.properties`
  6. Set DOCKER_HOST if needed: `export DOCKER_HOST=unix:///var/run/docker.sock`

---

## [1.0.0-SNAPSHOT] - 2025-12-17

### 🎉 Major Release - Maintained Fork Initialized

First release of the maintained fork with Java 21 support and modern dependencies.

### Added
- **NOTICE** file for Apache 2.0 compliance with proper attribution to original author Niall Gallagher
- **README.md** with maintenance notice, quick start guide, and migration instructions
- **Distribution management** for Maven Central (OSSRH) deployment
- Verification scripts for build validation and rebranding checks

### Changed
- **Maven GroupId**: `com.googlecode.cqengine` → `io.github.msaifasif`
- **Version**: `3.6.1-SNAPSHOT` → `1.0.0-SNAPSHOT` (semantic versioning)
- **Project URL**: Updated to `https://github.com/MSaifAsif/cqengine-next`
- **Java Target**: Upgraded from JDK 1.8 → JDK 21
- **Maven Plugins**: Updated 8 plugins for Java 21 compatibility (compiler, surefire, javadoc, jacoco, etc.)
- **Dependencies**: Updated 11 dependencies including ByteBuddy 1.14.11, EqualsVerifier 3.16.1, SQLite JDBC 3.45.0.0

### Fixed
- **EqualsVerifier** - Java 21 bytecode compatibility (updated to 3.16.1 with ByteBuddy 1.14.11)
- **SQLite** - Native library loading on Mac ARM64 (updated to 3.45.0.0, fixes CVE-2023-32697)
- **Lambda Type Erasure** - Generic type resolution in Java 21 (replaced lambdas with explicit attributes)
- **ReflectiveAttribute** - Equality verification with EqualsVerifier 3.16.1

### Removed
- **maven-shade-plugin** - Replaced with maven-assembly-plugin due to infinite loop with Java 21 (see v1.0.1)

### Known Issues
- **maven-shade-plugin** causes infinite loop with Java 21 - replaced with maven-assembly-plugin in v1.0.1

---

## 🔄 Backward Compatibility

**100% API Compatible** - All Java packages (`com.googlecode.cqengine.*`), APIs, and runtime behavior unchanged. Only Maven coordinates changed.

**Migration**: Update GroupId from `com.googlecode.cqengine` to `io.github.msaifasif`. No code changes required.

---

## 📋 Build & Test

**Build**: `mvn clean package -DskipTests` (~4 seconds)  
**Artifacts**: Standard jar (1 MB), sources jar, javadoc jar  
**Test Results**: All compilation successful (232 main + 155 test files), all tests passing

---

## 📚 Dependencies

**Runtime** (7): concurrent-trees 2.6.1, javassist 3.30.2-GA, sqlite-jdbc 3.45.0.0, kryo 5.0.0-RC1, kryo-serializers 0.45, antlr4-runtime 4.10.1, typetools 0.6.1

**Test** (6): junit 4.13.1, mockito 2.27.0, equalsverifier 3.16.1, byte-buddy 1.14.11, guava-testlib 27.1-jre, junit-dataprovider 1.13.1

---

## 🔐 Security

- **CVE-2023-32697** (SQLite) - Fixed in sqlite-jdbc 3.45.0.0
- **JUnit** - Updated to 4.13.1 with security fixes
- **License** - Apache 2.0 compliance maintained with proper attribution

---

## 📝 Notes

**For Users**: Update Maven coordinates only - no code changes needed. Java 21+ required.

**For Contributors**: Preserve backward compatibility, add @author attribution, follow Keep a Changelog format, run tests before committing.

---

**For changes in the original CQEngine project (versions < 3.6.1), see the [original repository](https://github.com/npgall/cqengine).**

