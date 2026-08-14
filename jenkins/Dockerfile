FROM jenkins/jenkins:lts-jdk17

USER root

# Install Java 21
RUN apt-get update && \
    apt-get install -y wget apt-transport-https curl && \
    wget -O /tmp/temurin21.tar.gz https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.3%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.3_9.tar.gz && \
    mkdir -p /usr/lib/jvm/temurin-21-jdk && \
    tar -xzf /tmp/temurin21.tar.gz -C /usr/lib/jvm/temurin-21-jdk --strip-components=1 && \
    rm /tmp/temurin21.tar.gz && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Install Node.js 20
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Make Java 21 the default JDK
ENV JAVA_HOME=/usr/lib/jvm/temurin-21-jdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

USER jenkins
