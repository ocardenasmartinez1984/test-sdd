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

# Install Docker CLI
RUN apt-get update && \
    apt-get install -y ca-certificates gnupg && \
    install -m 0755 -d /etc/apt/keyrings && \
    curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg && \
    chmod a+r /etc/apt/keyrings/docker.gpg && \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian $(. /etc/os-release && echo $VERSION_CODENAME) stable" > /etc/apt/sources.list.d/docker.list && \
    apt-get update && \
    apt-get install -y docker-ce-cli docker-compose-plugin && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Pre-install Gradle 8.9 (avoids download on every build)
RUN wget -O /tmp/gradle.zip https://services.gradle.org/distributions/gradle-8.9-bin.zip && \
    unzip -q /tmp/gradle.zip -d /opt && \
    ln -s /opt/gradle-8.9/bin/gradle /usr/local/bin/gradle && \
    rm /tmp/gradle.zip

# Pre-populate Gradle wrapper dist so ./gradlew doesn't download it
RUN mkdir -p /var/jenkins_home/.gradle/wrapper/dists/gradle-8.9-bin && \
    cp /tmp/gradle.zip /var/jenkins_home/.gradle/wrapper/dists/gradle-8.9-bin/ 2>/dev/null || true

# Make Java 21 the default JDK
ENV JAVA_HOME=/usr/lib/jvm/temurin-21-jdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
ENV GRADLE_USER_HOME=/var/jenkins_home/.gradle
ENV GRADLE_OPTS="-Dorg.gradle.daemon=true -Dorg.gradle.caching=true -Dorg.gradle.parallel=true -Xmx2048m"

# Add jenkins user to docker group
RUN groupadd -f docker && usermod -aG docker jenkins

USER jenkins

# Pre-install the plugins needed for the pipeline so the first boot does not
# depend on the setup wizard downloading them (which fails if the container has
# no/intermittent internet, e.g. "No such plugin: cloudbees-folder"). Plugins
# are baked into the image at build time (build needs internet once).
RUN jenkins-plugin-cli --plugins \
    cloudbees-folder \
    configuration-as-code \
    git \
    workflow-aggregator \
    pipeline-stage-view \
    docker-workflow \
    gradle \
    nodejs \
    credentials-binding \
    timestamper \
    ws-cleanup \
    junit \
    matrix-auth

# Skip the interactive setup wizard: plugins are already baked in and an admin
# user is provisioned via env (see docker-compose.yml). This avoids the
# "initial setup is required" / plugin-download step entirely.
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"

# Groovy init hook that creates the admin user / security realm on first boot
# (needed because the setup wizard is disabled).
COPY jenkins/init.groovy.d/ /usr/share/jenkins/ref/init.groovy.d/
