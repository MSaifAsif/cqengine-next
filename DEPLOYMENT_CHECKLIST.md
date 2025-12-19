# Maven Central Deployment Checklist

This checklist provides a quick reference for deploying CQEngine Next 1.0.0 to Maven Central.

## ✅ Pre-Deployment Verification (COMPLETED)

### POM Configuration
- [x] Version updated to `1.0.0` (non-SNAPSHOT)
- [x] GroupId: `io.github.msaifasif`
- [x] ArtifactId: `cqengine`
- [x] Name: `CQEngine Next - Maintained Fork`
- [x] Description: Present and descriptive
- [x] URL: `https://github.com/MSaifAsif/cqengine-next`

### Maven Central Requirements
- [x] License: Apache License 2.0 specified
- [x] SCM information: GitHub repository configured
- [x] Developers: Both original author and maintainer listed
- [x] Distribution Management: OSSRH configured
- [x] GPG Signing: Configured in release-sign-artifacts profile

### Build Artifacts (All Generated Successfully)
- [x] Main JAR: `cqengine-1.0.0.jar` (1.1 MB)
- [x] Sources JAR: `cqengine-1.0.0-sources.jar` (568 KB)
- [x] Javadoc JAR: `cqengine-1.0.0-javadoc.jar` (3.7 MB)
- [x] Fat JAR: `cqengine-1.0.0-jar-with-dependencies.jar` (16 MB)

### Documentation
- [x] README.md updated with version 1.0.0
- [x] CHANGELOG.md updated with 1.0.0 release notes
- [x] RELEASE.md created with deployment guide

### Build Status
- [x] Clean build successful with Java 21
- [x] All artifacts generated
- [x] Code review passed
- [x] Security scan passed

## 📋 Deployment Steps (TO BE COMPLETED BY MAINTAINER)

See [RELEASE.md](RELEASE.md) for detailed step-by-step instructions. Quick summary:

### 1. Prerequisites Setup
- [ ] OSSRH account created and access granted to `io.github.msaifasif`
- [ ] GPG key generated and published to key server
- [ ] Maven settings.xml configured with OSSRH credentials

### 2. Build and Deploy
```bash
cd code
mvn clean deploy -Prelease-sign-artifacts -DperformRelease=true
```

### 3. Release from OSSRH
- [ ] Login to https://oss.sonatype.org/
- [ ] Find staging repository
- [ ] Close repository (triggers validation)
- [ ] Release to Maven Central

### 4. Create Release
- [ ] Create Git tag: `git tag -a v1.0.0 -m "Release version 1.0.0"`
- [ ] Push tag: `git push origin v1.0.0`
- [ ] Create GitHub Release with artifacts

### 5. Verification
- [ ] Verify artifact on Maven Central: https://search.maven.org/artifact/io.github.msaifasif/cqengine/1.0.0/jar
- [ ] Test download: `mvn dependency:get -Dartifact=io.github.msaifasif:cqengine:1.0.0`

## 🔧 Build Commands Reference

### Local Build and Test
```bash
cd code
export JAVA_HOME=/path/to/jdk-21
mvn clean verify
```

### Release Build (with GPG signing)
```bash
cd code
mvn clean deploy -Prelease-sign-artifacts -DperformRelease=true
```

### Test Release Profile (without deploy)
```bash
cd code
mvn clean package -Prelease-sign-artifacts -DperformRelease=true
```

## 📊 Project Information

- **Project**: CQEngine Next
- **Version**: 1.0.0
- **Java**: JDK 21
- **GroupId**: io.github.msaifasif
- **ArtifactId**: cqengine
- **Repository**: https://github.com/MSaifAsif/cqengine-next
- **License**: Apache License 2.0

## 🎯 Post-Release Steps

After successful deployment:
1. Update version to `1.0.1-SNAPSHOT` for next development cycle
2. Announce release on relevant channels
3. Update project documentation

## 📚 Additional Resources

- **RELEASE.md**: Comprehensive deployment guide
- **CHANGELOG.md**: Detailed release notes
- **README.md**: Project documentation
- **Sonatype Guide**: https://central.sonatype.org/publish/publish-guide/
- **Maven Central Requirements**: https://central.sonatype.org/pages/requirements.html
