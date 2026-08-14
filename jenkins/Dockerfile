FROM jenkins/jenkins:lts-jdk17

USER root

# Install Java 21
RUN apt-get update && \
    apt-get install -y wget apt-transport-https && \
    wget -O /tmp/temurin21.tar.gz https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.3%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.3_9.tar.gz && \
    mkdir -p /opt/java/jdk-21 && \
    tar -xzf /tmp/temurin21.tar.gz -C /opt/java/jdk-21 --strip-components=1 && \
    rm /tmp/temurin21.tar.gz && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Set Java 21 available for Gradle toolchain auto-detection
ENV JAVA_21_HOME=/opt/java/jdk-21

USER jenkins
