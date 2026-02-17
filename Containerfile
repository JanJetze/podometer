# SPDX-License-Identifier: GPL-3.0-or-later
#
# Podometer Android Build Environment
#
# Provides a reproducible build environment for the Podometer Android app.
# Intended for CI and local builds without requiring Android Studio.
#
# Build:
#   docker build -f Containerfile -t podometer-builder .
#
# Build the APK:
#   docker run --rm -v "$(pwd)":/workspace -w /workspace podometer-builder \
#     ./gradlew assembleDebug
#
# Run tests:
#   docker run --rm -v "$(pwd)":/workspace -w /workspace podometer-builder \
#     ./gradlew test
#

# Build for linux/amd64 so Android SDK tools (x86_64-only binaries) work correctly.
# On Apple Silicon hosts OrbStack / Docker Desktop provides transparent Rosetta emulation.
FROM --platform=linux/amd64 debian:12-slim

# ─── System packages ─────────────────────────────────────────────────────────
RUN apt-get update && apt-get install -y --no-install-recommends \
    # Java runtime / compiler
    openjdk-17-jdk-headless \
    # Build utilities
    curl \
    unzip \
    wget \
    git \
    # Required by Android build tools (aapt2, d8, etc. are x86_64 ELF binaries)
    lib32z1 \
    file \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# ─── Android SDK ─────────────────────────────────────────────────────────────
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${PATH}"

# Install Android command-line tools
RUN mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools" \
    && curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" \
       -o /tmp/cmdline-tools.zip \
    && unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools \
    && mv /tmp/cmdline-tools/cmdline-tools "${ANDROID_SDK_ROOT}/cmdline-tools/latest" \
    && rm -rf /tmp/cmdline-tools /tmp/cmdline-tools.zip

# Accept licenses and install required SDK components
RUN yes | sdkmanager --licenses > /dev/null 2>&1 || true \
    && sdkmanager \
       "platform-tools" \
       "platforms;android-35" \
       "build-tools;35.0.0"

# ─── Gradle cache warm-up ────────────────────────────────────────────────────
# Copy only the wrapper files to pre-download Gradle distribution.
# This layer is cached separately so SDK/app changes don't re-download Gradle.
WORKDIR /workspace
COPY gradle/wrapper/gradle-wrapper.jar      gradle/wrapper/gradle-wrapper.jar
COPY gradle/wrapper/gradle-wrapper.properties gradle/wrapper/gradle-wrapper.properties
COPY gradlew                                gradlew
RUN chmod +x gradlew \
    && ./gradlew --version --no-daemon 2>&1 | tail -5

# ─── Default command ─────────────────────────────────────────────────────────
CMD ["./gradlew", "--help"]
