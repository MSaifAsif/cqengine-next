# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- Upgraded `slf4j-simple` from `1.7.36` to `2.0.17` (test scope)
- Upgraded `sqlite-jdbc` from `3.45.0.0` to `3.51.3.0` (latest stable, released Mar 16, 2026)


## [Released 1.0.0] - 2025-12-21
- Initial release of maintained fork `io.github.msaifasif:cqengine:1.0.0` based on original `com.googlecode.cqengine:cqengine:3.6.0` with all changes from [1.0.0-SNAPSHOT](#100-snapshot---2025-12-19) included.

## [1.0.0-SNAPSHOT] - 2025-12-19

### Added

#### Documentation & Release Management
- **RELEASE_CHECKLIST.md** - Comprehensive release preparation guide with Maven Central deployment instructions
- **README.md** - Complete project documentation with verified links, quick start guide, migration instructions, and Docker testing guide
- **nexus-staging-maven-plugin 1.7.0** - Essential plugin for automated Maven Central deployment via OSSRH

#### Build & Testing
- **maven-assembly-plugin 3.6.0** - Creates fat jar with all dependencies (16 MB) in ~5 seconds, full Java 21 compatibility
- **Docker-based integration tests** - Testcontainers support for OS-independent testing (30 total: 29 passing, 1 skipped)
  - `DockerSQLiteIntegrationTest` - 7 tests for SQLite persistence, concurrency, large datasets
  - `DockerDiskPersistenceIntegrationTest` - 8 tests for disk persistence, compaction, WAL mode
  - `DockerOffHeapPersistenceIntegrationTest` - 8 tests for off-heap memory, container limits, concurrent access
  - `DockerCompositePersistenceIntegrationTest` - 7 tests for multi-tier persistence (6 passing, 1 skipped)
  - Tests run in isolated Alpine Linux containers with mounted volumes and memory limits
  - Real filesystem I/O, native memory, and multi-tier architecture testing
  - **Known Limitation**: `testCompositePersistence_AllThreeLayers` skipped due to StackOverflow when combining DiskPersistence + OffHeapIndex on same primary key (SQLite recursion issue)

#### License Management
- **License headers** - Applied "Copyright 2025 Saif Asif" to 13 files that had no existing license header
  - `src/main/java/com/googlecode/cqengine/codegen/MemberFilter.java`
  - `src/main/java/com/googlecode/cqengine/persistence/support/serialization/PojoSerializer.java`
  - `src/main/java/com/googlecode/cqengine/persistence/support/serialization/KryoSerializer.java`
  - `src/main/java/com/googlecode/cqengine/persistence/support/serialization/PersistenceConfig.java`
  - `src/test/java/com/googlecode/cqengine/query/comparative/LongestPrefixTest.java`
  - `src/main/java/com/googlecode/cqengine/query/comparative/Max.java`
  - `src/main/java/com/googlecode/cqengine/query/comparative/Min.java`
  - `src/main/java/com/googlecode/cqengine/query/comparative/SimpleComparativeQuery.java`
  - `src/main/java/com/googlecode/cqengine/query/comparative/LongestPrefix.java`
  - `src/main/java/com/googlecode/cqengine/query/simple/StringIsPrefixOf.java`
  - `src/main/java/com/googlecode/cqengine/query/ComparativeQuery.java`
  - `src/main/java/com/googlecode/cqengine/metadata/KeyFrequency.java`
  - `src/main/java/com/googlecode/cqengine/metadata/AttributeMetadata.java`

### Changed

#### Java Version & Compilation
- **Java version requirement** - Updated from Java 8 to Java 21
- **Maven compiler plugin** - Updated to 3.11.0 with Java 21 target
- **Package namespace** - Maven GroupId changed from `com.googlecode.cqengine` to `io.github.msaifasif` (Java packages unchanged for compatibility)

#### Release Plugins (2025-12-19)
- **maven-source-plugin** - Updated from 3.3.0 to 3.3.1 (latest stable)
- **maven-javadoc-plugin** - Updated from 3.6.0 to 3.10.1 (latest stable, Java 21 compatible)
- **maven-gpg-plugin** - Retained at 3.2.8 (latest stable)
- **maven-release-plugin** - Updated from 2.5.3 to 3.1.1 (major version update for modern Maven)
- **license-maven-plugin** - Replaced deprecated `maven-license-plugin 1.10.b1` with modern `license-maven-plugin 4.6`
  - Configuration updated to use `skipExistingHeaders=true` to preserve original author credits
  - New files get "Copyright 2025 Saif Asif", existing files retain "Copyright 2012-2015 Niall Gallagher"
  - Email updated to maintainer's contact
- **License header template** - Updated `src/etc/header.txt` to use variables (${owner}, ${year}) for dynamic substitution

#### Dependency Updates (2025-12-17)
- **ByteBuddy** - Updated from 1.9.10 to 1.14.11 (Java 21 bytecode support, class file version 65)
- **EqualsVerifier** - Updated from 3.15.4 to 3.16.1 (Java 21 compatibility, fixes class file major version 65 error)
- **SQLite JDBC** - Updated from 3.42.0.0 to 3.45.0.0 (CVE-2023-32697 security fix, ARM64 Mac support)
- **Kryo Serialization** - Updated from 4.0.0 to 5.0.0-RC1 (Java 21 support, modern serialization)
- **Javassist** - Updated from 3.29.0-GA to 3.30.2-GA (latest stable release)
- **Testcontainers** - Added 1.19.3 for Docker-based integration testing

### Fixed

#### Test Compatibility Issues
- **EqualsVerifier compatibility** - Fixed "Unsupported class file major version 65" error in `QueriesEqualsAndHashCodeTest`
- **SQLite native library** - Fixed ARM64 Mac compatibility (resolved "No native library found for os.name=Mac and os.arch=aarch64" error)
- **Lambda type erasure** - Fixed "Could not resolve sufficient generic type information" in `DiskSharedCacheConcurrencyTest`
- **ReflectiveAttribute equality** - Fixed reflexivity test by suppressing `Warning.REFERENCE_EQUALITY` for intentional field comparison
- **Docker test logging** - Configured SLF4J with Simple Logger to eliminate "Failed to load StaticLoggerBinder" warnings

### Removed

#### Build Configuration
- **maven-shade-plugin** - Commented out due to infinite loop issue on Java 21, replaced with maven-assembly-plugin
  - Shade plugin was hanging indefinitely during dependency analysis phase
  - Assembly plugin provides equivalent fat jar creation without relocation (acceptable for most use cases)

### Security

- **CVE-2023-32697** - Fixed by updating SQLite JDBC from 3.42.0.0 to 3.45.0.0
  - Severity: Moderate
  - Impact: Potential security vulnerability in SQLite JDBC driver
  - Resolution: Upgraded to patched version 3.45.0.0

- **CVE-2023-2976** - Fixed by updating guava-testlib from 27.1-jre to 33.5.0-jre
- **CVE-2020-8908** - Fixed by updating guava-testlib from 27.1-jre to 33.5.0-jre
---

## Maintenance Notice

This is a maintained fork of the original [CQEngine](https://github.com/npgall/cqengine) project by Niall Gallagher.

### Original Project Information
- **Original Author**: Niall Gallagher
- **Original Repository**: https://github.com/npgall/cqengine
- **Original GroupId**: com.googlecode.cqengine
- **License**: Apache License 2.0

### This Fork
- **Maintainer**: Saif Asif
- **Repository**: https://github.com/MSaifAsif/cqengine-next
- **GroupId**: io.github.msaifasif
- **Purpose**: Continued maintenance, Java 21+ support, security updates, and modern tooling

### Attribution

All credit for the original CQEngine architecture, design, and implementation goes to Niall Gallagher and the original contributors. This fork maintains backward compatibility while adding:
- Modern Java support (Java 21)
- Updated dependencies with security fixes
- Docker-based integration testing
- Continued maintenance and support

---

## Migration Guide

### From Original CQEngine (com.googlecode.cqengine)

**Good News**: Migration is trivial! Only the Maven coordinates change.

#### Before (Original)
```xml
<dependency>
    <groupId>com.googlecode.cqengine</groupId>
    <artifactId>cqengine</artifactId>
    <version>3.6.0</version>
</dependency>
```

#### After (This Fork)
```xml
<dependency>
    <groupId>io.github.msaifasif</groupId>
    <artifactId>cqengine</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**No code changes required!** All package names (`com.googlecode.cqengine.*`) remain the same for full backward compatibility.

### System Requirements

- **Minimum Java Version**: Java 21 (LTS)
- **Maven**: 3.6.0 or higher
- **Docker**: Optional, only required for running Docker-based integration tests

---

## Links

- **Repository**: https://github.com/MSaifAsif/cqengine-next
- **Issues**: https://github.com/MSaifAsif/cqengine-next/issues
- **Discussions**: https://github.com/MSaifAsif/cqengine-next/discussions
- **Original Project**: https://github.com/npgall/cqengine
- **Maven Central**: https://central.sonatype.com/artifact/io.github.msaifasif/cqengine

---

[Unreleased]: https://github.com/MSaifAsif/cqengine-next/compare/v1.0.0...HEAD
[1.0.0-SNAPSHOT]: https://github.com/MSaifAsif/cqengine-next/releases/tag/v1.0.0-SNAPSHOT

