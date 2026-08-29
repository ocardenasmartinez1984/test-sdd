pipeline {
    agent any

    environment {
        JAVA_HOME = '/usr/lib/jvm/temurin-21-jdk'
        PATH = "${JAVA_HOME}/bin:${env.PATH}"
        GRADLE_OPTS = '-Dorg.gradle.caching=true -Dorg.gradle.parallel=true -Dorg.gradle.configureondemand=true -Xmx2048m'
        GRADLE_USER_HOME = '/var/jenkins_home/.gradle'
        SONAR_URL = 'http://sonarqube:9000'
        REGISTRY = 'ocardenasmartinez1984'
        IMAGE_TAG = "${BUILD_NUMBER}"
        // Enable BuildKit so Docker builds reuse the shared Gradle/npm cache mounts.
        DOCKER_BUILDKIT = '1'
        COMPOSE_DOCKER_CLI_BUILD = '1'
    }

    options {
        timeout(time: 10, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
    }

    stages {
        stage('Checkout') {
            steps {
                git url: 'https://github.com/ocardenasmartinez1984/test-sdd.git', branch: 'main'
                sh 'chmod +x gradlew'
            }
        }

        stage('Build & Test') {
            steps {
                sh './gradlew build --build-cache -x jacocoTestCoverageVerification -x :stress-test:test -x :stress-test:build'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/build/test-results/test/*.xml'
                }
            }
        }

        stage('Quality & Docker') {
            parallel {
                stage('SonarQube') {
                    steps {
                        withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                            sh './gradlew sonar -Dsonar.host.url=$SONAR_URL -Dsonar.token=$SONAR_TOKEN --build-cache'
                        }
                    }
                }
                stage('Docker Images') {
                    steps {
                        sh '''
                            docker build -f eureka-server/Dockerfile.ci -t ${REGISTRY}/eureka-server:${IMAGE_TAG} . &
                            docker build -f api-gateway/Dockerfile.ci -t ${REGISTRY}/api-gateway:${IMAGE_TAG} . &
                            docker build -f auth-service/Dockerfile.ci -t ${REGISTRY}/auth-service:${IMAGE_TAG} . &
                            docker build -f stock-service/Dockerfile.ci -t ${REGISTRY}/stock-service:${IMAGE_TAG} . &
                            docker build -f venta-service/Dockerfile.ci -t ${REGISTRY}/venta-service:${IMAGE_TAG} . &
                            docker build -f despacho-service/Dockerfile.ci -t ${REGISTRY}/despacho-service:${IMAGE_TAG} . &
                            docker build -f pos-frontend/Dockerfile -t ${REGISTRY}/pos-frontend:${IMAGE_TAG} . &
                            docker build -f ventas-mantenedor/Dockerfile -t ${REGISTRY}/ventas-mantenedor:${IMAGE_TAG} ./ventas-mantenedor &
                            docker build -f users-mantenedor/Dockerfile -t ${REGISTRY}/users-mantenedor:${IMAGE_TAG} ./users-mantenedor &
                            wait
                        '''
                    }
                }
            }
        }

        stage('Push & Deploy') {
            when { branch 'main' }
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    sh '''
                        echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin
                        for svc in eureka-server api-gateway auth-service stock-service venta-service despacho-service pos-frontend ventas-mantenedor users-mantenedor; do
                            docker push ${REGISTRY}/${svc}:${IMAGE_TAG} &
                        done
                        wait
                    '''
                }
                sh '''
                    docker compose down --remove-orphans || true
                    # Profiles keep heavy tooling (jenkins/sonar/prometheus/grafana/zipkin)
                    # out of the deploy; only the 13 essential containers come up.
                    docker compose up -d

                    echo "Waiting for services to become healthy..."
                    # Poll the gateway health endpoint instead of a fixed sleep.
                    ok=0
                    for i in $(seq 1 40); do
                        if curl -sf http://localhost:8080/actuator/health > /dev/null; then
                            ok=1; break
                        fi
                        sleep 5
                    done

                    if [ "$ok" != "1" ]; then
                        echo "❌ Gateway did not become healthy in time"
                        docker compose ps
                        exit 1
                    fi

                    curl -sf http://localhost:8761/actuator/health > /dev/null && echo "✅ Eureka OK" || { echo "❌ Eureka FAIL"; exit 1; }
                    curl -sf http://localhost:8080/actuator/health > /dev/null && echo "✅ Gateway OK" || { echo "❌ Gateway FAIL"; exit 1; }
                '''
            }
        }
    }

    post {
        success { echo '✅ Pipeline completed!' }
        failure { echo '❌ Pipeline failed!' }
        always { sh 'docker system prune -f --filter "until=24h" 2>/dev/null || true' }
    }
}
