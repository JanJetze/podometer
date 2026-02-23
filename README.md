# Podometer

A privacy-first Android step counter. No network access, no data collection — all
activity data stays on device. Distributed via F-Droid.

## Features

- Passive step counting via the built-in hardware step sensor
- Activity type tracking (walking, cycling)
- Local-only storage using Room and DataStore
- No permissions beyond `ACTIVITY_RECOGNITION` and `FOREGROUND_SERVICE`

## Build Prerequisites

- Docker (or a compatible OCI runtime such as Podman)
- The `podometer-builder` image (see below)

No Android SDK or JDK installation is required on the host — the Docker image
provides a fully reproducible build environment.

## Building the Docker Image

Build the image once from the project root:

```bash
docker build -f Containerfile -t podometer-builder .
```

## Building the APK

### Debug build

```bash
docker run --rm -v "$(pwd)":/workspace -w /workspace podometer-builder \
    ./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

### Release build

Release builds require a signing keystore supplied via environment variables:

```bash
docker run --rm \
    -v "$(pwd)":/workspace \
    -w /workspace \
    -e RELEASE_KEYSTORE_PATH=/workspace/release.jks \
    -e RELEASE_KEYSTORE_PASSWORD=<password> \
    -e RELEASE_KEY_ALIAS=<alias> \
    -e RELEASE_KEY_PASSWORD=<password> \
    podometer-builder \
    ./gradlew assembleRelease
```

The release APK is written to `app/build/outputs/apk/release/`.

## Running Tests

```bash
docker run --rm -v "$(pwd)":/workspace -w /workspace podometer-builder \
    ./gradlew testDebugUnitTest
```

## Dependency Locking

All dependency configurations are locked via Gradle dependency locking. The
lockfile is stored at `app/gradle.lockfile` and is committed to version control.

### Verifying locked dependencies

Gradle automatically verifies the lockfile on every build. If a dependency
version drifts from what is recorded, the build fails with a descriptive error.

### Regenerating lockfiles

Run the following command after updating any dependency version in
`gradle/libs.versions.toml`:

```bash
docker run --rm -v "$(pwd)":/workspace -w /workspace podometer-builder \
    ./gradlew app:dependencies --write-locks
```

Commit the updated `app/gradle.lockfile` together with the version catalog
change.

## Reproducible Builds (F-Droid)

Podometer targets [F-Droid](https://f-droid.org) and is configured for
reproducible builds:

- All dependency versions are pinned in `gradle/libs.versions.toml`.
- All resolved transitive dependencies are locked in `app/gradle.lockfile`.
- Archive tasks set `isPreserveFileTimestamps = false` and
  `isReproducibleFileOrder = true` to produce bit-for-bit identical APKs
  across build environments.
- The `Containerfile` pins the Debian 12 base image, JDK 17, Android SDK 35,
  and the Gradle distribution to ensure a hermetic build environment.

### F-Droid build server instructions

The F-Droid build server must use the `Containerfile` at the repository root to
produce a reproducible binary. The expected invocation is:

```bash
# 1. Build the image
docker build -f Containerfile -t podometer-builder .

# 2. Build the release APK (keystore provided by F-Droid infrastructure)
docker run --rm \
    -v "$(pwd)":/workspace \
    -w /workspace \
    -e RELEASE_KEYSTORE_PATH=/workspace/release.jks \
    -e RELEASE_KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD}" \
    -e RELEASE_KEY_ALIAS="${KEY_ALIAS}" \
    -e RELEASE_KEY_PASSWORD="${KEY_PASSWORD}" \
    podometer-builder \
    ./gradlew assembleRelease
```

The release APK will be at `app/build/outputs/apk/release/app-release.apk`.

## License

SPDX-License-Identifier: GPL-3.0-or-later
