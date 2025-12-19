# Release Guide for CQEngine Next

This document provides instructions for releasing CQEngine Next to Maven Central.

## Prerequisites

Before releasing to Maven Central, ensure you have:

1. **Sonatype OSSRH Account**
   - Create an account at https://issues.sonatype.org/
   - Request access to the `io.github.msaifasif` groupId
   - Follow the guide at: https://central.sonatype.org/publish/publish-guide/

2. **GPG Key for Signing**
   - Install GPG: `brew install gnupg` (macOS) or `apt-get install gnupg` (Linux)
   - Generate a key pair: `gpg --gen-key`
   - List keys: `gpg --list-keys`
   - Publish public key to key server: `gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID`
   - Alternative key servers: `keys.openpgp.org`, `pgp.mit.edu`

3. **Maven Settings Configuration**
   - Add OSSRH credentials to `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>ossrh</id>
      <username>YOUR_SONATYPE_USERNAME</username>
      <password>YOUR_SONATYPE_PASSWORD</password>
    </server>
  </servers>
  <profiles>
    <profile>
      <id>ossrh</id>
      <activation>
        <activeByDefault>true</activeByDefault>
      </activation>
      <properties>
        <gpg.executable>gpg</gpg.executable>
        <gpg.passphrase>YOUR_GPG_PASSPHRASE</gpg.passphrase>
      </properties>
    </profile>
  </profiles>
</settings>
```

## Release Process

### 1. Prepare the Release

Ensure all changes are committed and the version is updated:

```bash
# Navigate to the code directory
cd code

# Verify the version is set correctly (should be 1.0.0, not SNAPSHOT)
grep "<version>" pom.xml | head -1

# Clean build and run all tests
mvn clean verify
```

### 2. Build and Deploy to Maven Central

Deploy the release artifacts using the release profile:

```bash
# Build with the release profile (includes GPG signing)
mvn clean deploy -Prelease-sign-artifacts -DperformRelease=true

# If you encounter GPG issues, you can specify the key explicitly:
# mvn clean deploy -Prelease-sign-artifacts -DperformRelease=true -Dgpg.keyname=YOUR_KEY_ID
```

This will:
- Compile the project
- Run all tests
- Generate source JAR
- Generate Javadoc JAR
- Generate the fat JAR with dependencies
- Sign all artifacts with GPG
- Upload to OSSRH staging repository

### 3. Release from OSSRH Staging

1. Login to https://oss.sonatype.org/
2. Navigate to "Staging Repositories"
3. Find your staging repository (e.g., `iogiothubmsaifasif-XXXX`)
4. Select it and click "Close" to validate the artifacts
5. Wait for validation to complete (check "Activity" tab)
6. Once validated, click "Release" to publish to Maven Central
7. Artifacts will sync to Maven Central within 10-30 minutes

### 4. Create a Git Tag

After successful release:

```bash
# Create and push a tag
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```

### 5. Create GitHub Release

1. Go to https://github.com/MSaifAsif/cqengine-next/releases
2. Click "Draft a new release"
3. Select the tag `v1.0.0`
4. Title: "CQEngine Next 1.0.0 - First Official Release"
5. Copy release notes from CHANGELOG.md
6. Attach the JAR files from `target/`:
   - `cqengine-1.0.0.jar`
   - `cqengine-1.0.0-sources.jar`
   - `cqengine-1.0.0-javadoc.jar`
   - `cqengine-1.0.0-jar-with-dependencies.jar`
7. Publish the release

## Verification

After release, verify the artifacts are available on Maven Central:

1. Search on Maven Central: https://search.maven.org/artifact/io.github.msaifasif/cqengine/1.0.0/jar
2. Test downloading the artifact:
   ```bash
   mvn dependency:get -Dartifact=io.github.msaifasif:cqengine:1.0.0
   ```

## Post-Release Steps

1. **Update version for next development cycle**
   - Update `pom.xml` version to `1.0.1-SNAPSHOT`
   - Update `README.md` and `CHANGELOG.md` to reference the new snapshot version

2. **Announce the release**
   - Update project documentation
   - Announce on relevant channels (social media, forums, etc.)

## Troubleshooting

### GPG Signing Issues

If you get GPG errors during deployment:

```bash
# Ensure GPG agent is running
gpgconf --kill gpg-agent
gpgconf --launch gpg-agent

# Test signing manually
gpg --sign test.txt

# List available keys
gpg --list-secret-keys
```

### OSSRH Access Issues

If you can't access OSSRH or get 401 errors:
- Verify credentials in `~/.m2/settings.xml`
- Ensure you have been granted access to the `io.github.msaifasif` groupId
- Wait 2-3 business days after requesting access

### Validation Failures

Common validation failures in OSSRH:
- Missing POM metadata (name, description, URL, licenses, SCM, developers)
- Missing source JAR or Javadoc JAR
- Missing GPG signatures
- Invalid GPG signatures (key not published to key server)

All of these requirements should be satisfied by the current `pom.xml` configuration.

## Additional Resources

- [Sonatype OSSRH Guide](https://central.sonatype.org/publish/publish-guide/)
- [Maven Central Requirements](https://central.sonatype.org/pages/requirements.html)
- [GPG Signing Guide](https://central.sonatype.org/publish/requirements/gpg/)
- [Original CQEngine Project](https://github.com/npgall/cqengine)
