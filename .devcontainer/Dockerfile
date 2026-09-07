FROM docker.io/library/debian@sha256:5ae3c39ebd15e229dcedd5cee596b2497182493d41ff162e824ba13fc1b2b867

ARG ANDROID_COMMAND_TOOLS_VERSION=16111833
ARG ANDROID_COMMAND_TOOLS_SHA256=0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8
ARG GRADLE_VERSION=9.7.1
ARG GRADLE_SHA256=acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a
ARG TEMURIN_VERSION=25.0.4.1_1
ARG TEMURIN_SHA256=dbb698396d478e7fa2b1e50f4103324b2a99b90569ee27c33f2261f9215cf41e
ARG TYPOS_VERSION=1.50.1
ARG TYPOS_SHA256=edf0545109aee6a22751d04ddecb97c45be47d3aa0409564fb895eeeace91b1e

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_HOME=/opt/gradle \
    JAVA_HOME=/opt/java \
    LANG=C.UTF-8 \
    PATH=/opt/java/bin:/opt/gradle/bin:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:${PATH}

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install --yes --no-install-recommends \
        ca-certificates=20250419~deb12u1 \
        curl=7.88.1-10+deb12u15 \
        git=1:2.39.5-0+deb12u3 \
        gzip=1.12-1 \
        openssh-client=1:9.2p1-2+deb12u10 \
        procps=2:4.0.2-3 \
        tar=1.34+dfsg-1.2+deb12u1 \
        unzip=6.0-28+deb12u1 \
        xz-utils=5.4.1-1+deb12u1 \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --create-home --shell /bin/bash vscode \
    && install -d -o vscode -g vscode \
        /workspace \
        /home/vscode/.android \
        /home/vscode/.android/cache \
        /home/vscode/.gradle

RUN set -eux; \
    curl --fail --location --retry 3 \
      "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jdk_x64_linux_hotspot_${TEMURIN_VERSION}.tar.gz" \
      --output /tmp/temurin.tar.gz; \
    echo "${TEMURIN_SHA256}  /tmp/temurin.tar.gz" | sha256sum --check --strict; \
    mkdir -p "${JAVA_HOME}"; \
    tar -xzf /tmp/temurin.tar.gz --strip-components=1 -C "${JAVA_HOME}"; \
    rm /tmp/temurin.tar.gz; \
    java -version

RUN set -eux; \
    curl --fail --location --retry 3 \
      "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_COMMAND_TOOLS_VERSION}_latest.zip" \
      --output /tmp/android-command-tools.zip; \
    echo "${ANDROID_COMMAND_TOOLS_SHA256}  /tmp/android-command-tools.zip" | sha256sum --check --strict; \
    mkdir -p "${ANDROID_HOME}/cmdline-tools"; \
    unzip -q /tmp/android-command-tools.zip -d /tmp/android-command-tools; \
    mv /tmp/android-command-tools/cmdline-tools "${ANDROID_HOME}/cmdline-tools/latest"; \
    rm -rf /tmp/android-command-tools /tmp/android-command-tools.zip; \
    yes | sdkmanager --licenses >/dev/null; \
    sdkmanager \
      "build-tools;36.0.0" \
      "build-tools;37.0.0" \
      "platform-tools" \
      "platforms;android-36" \
      "platforms;android-37.0"; \
    chown -R root:root "${ANDROID_HOME}"; \
    chmod -R a+rX "${ANDROID_HOME}"

RUN set -eux; \
    curl --fail --location --retry 3 \
      "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
      --output /tmp/gradle.zip; \
    echo "${GRADLE_SHA256}  /tmp/gradle.zip" | sha256sum --check --strict; \
    unzip -q /tmp/gradle.zip -d /opt; \
    mv "/opt/gradle-${GRADLE_VERSION}" "${GRADLE_HOME}"; \
    rm /tmp/gradle.zip

RUN set -eux; \
    curl --fail --location --retry 3 \
      "https://github.com/crate-ci/typos/releases/download/v${TYPOS_VERSION}/typos-v${TYPOS_VERSION}-x86_64-unknown-linux-musl.tar.gz" \
      --output /tmp/typos.tar.gz; \
    echo "${TYPOS_SHA256}  /tmp/typos.tar.gz" | sha256sum --check --strict; \
    tar -xzf /tmp/typos.tar.gz -C /usr/local/bin ./typos; \
    rm /tmp/typos.tar.gz; \
    typos --version

USER vscode
WORKDIR /workspace/autospeed
