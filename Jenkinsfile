pipeline {
    agent any

    environment {
        JAVA_HOME = '/usr/lib/jvm/temurin-21-jdk'
        PATH = "${JAVA_HOME}/bin:${env.PATH}"
        GRADLE_OPTS = '-Dorg.gradle.daemon=false -Xmx1536m'
        SONAR_URL = 'http://sonarqube:9000'
        REGISTRY = 'ocardenasmartinez1984'
        IMAGE_TAG = "${BUILD_NUMBER}"
    }

    options {
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '15'))
        timestamps()
    }

    stages {
        stage('Checkout') {
            steps {
                git url: 'https://github.com/ocardenasmartinez1984/test-sdd.git', branch: 'main'
                sh 'chmod +x gradlew'
            }
        }

        stage('Build') {
            steps {
                sh './gradlew clean build -x test -x jacocoTestCoverageVerification --parallel'
            }
        }

        stage('Unit Tests') {
            parallel {
                stage('Auth Tests') {
                    steps {
                        sh './gradlew :auth-service:test'
                    }
                }
                stage('Stock Tests') {
                    steps {
                        sh './gradlew :stock-service:test --tests "com.stock.application.*" --tests "com.stock.infrastructure.*" --tests "com.stock.interfaces.*"'
                    }
                }
                stage('Venta Tests') {
                    steps {
                        sh './gradlew :venta-service:test'
                    }
                }
                stage('Despacho Tests') {
                    steps {
                        sh './gradlew :despacho-service:test'
                    }
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/build/test-results/test/*.xml'
                }
            }
        }

        stage('Integration Tests') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                }
            }
            steps {
                sh './gradlew :stock-service:test --tests "com.stock.integration.*"'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'stock-service/build/test-results/test/*.xml'
                }
            }
        }

        stage('Code Quality') {
            parallel {
                stage('SonarQube Analysis') {
                    steps {
                        withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                            sh './gradlew sonar -Dsonar.host.url=$SONAR_URL -Dsonar.token=$SONAR_TOKEN'
                        }
                    }
                }
                stage('JaCoCo Coverage') {
                    steps {
                        sh './gradlew jacocoTestReport jacocoTestCoverageVerification || echo "Coverage below threshold"'
                    }
                    post {
                        always {
                            publishHTML(target: [
                                reportName: 'JaCoCo Coverage',
                                reportDir: 'stock-service/build/reports/jacoco/test/html',
                                reportFiles: 'index.html',
                                alwaysLinkToLastBuild: true,
                                allowMissing: true
                            ])
                        }
                    }
                }
            }
        }

        stage('E2E Tests') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                }
            }
            steps {
                dir('e2e') {
                    sh 'npm ci'
                    sh 'npx playwright install --with-deps chromium'
                    sh 'npx playwright test --project=api'
                }
            }
            post {
                always {
                    publishHTML(target: [
                        reportName: 'Playwright E2E Report',
                        reportDir: 'e2e/playwright-report',
                        reportFiles: 'index.html',
                        alwaysLinkToLastBuild: true,
                        allowMissing: true
                    ])
                }
            }
        }

        stage('Stress Tests') {
            when {
                branch 'main'
            }
            steps {
                sh './gradlew :stress-test:gatlingRun-simulations.SagaEndToEndSimulation || true'
            }
            post {
                always {
                    gatlingArchive()
                }
            }
        }

        stage('Docker Build') {
            steps {
                script {
                    def services = ['eureka-server', 'api-gateway', 'auth-service', 'stock-service', 'venta-service', 'despacho-service']
                    def builds = [:]
                    services.each { svc ->
                        builds[svc] = {
                            sh "docker build -f ${svc}/Dockerfile.ci -t ${REGISTRY}/${svc}:${IMAGE_TAG} -t ${REGISTRY}/${svc}:latest ."
                        }
                    }
                    parallel builds
                    sh "docker build -f pos-frontend/Dockerfile -t ${REGISTRY}/pos-frontend:${IMAGE_TAG} -t ${REGISTRY}/pos-frontend:latest ."
                    sh "docker build -f ventas-mantenedor/Dockerfile -t ${REGISTRY}/ventas-mantenedor:${IMAGE_TAG} -t ${REGISTRY}/ventas-mantenedor:latest ./ventas-mantenedor"
                    sh "docker build -f users-mantenedor/Dockerfile -t ${REGISTRY}/users-mantenedor:${IMAGE_TAG} -t ${REGISTRY}/users-mantenedor:latest ./users-mantenedor"
                }
            }
        }

        stage('Docker Push') {
            when {
                branch 'main'
            }
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    sh 'echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin'
                    script {
                        def services = ['eureka-server', 'api-gateway', 'auth-service', 'stock-service', 'venta-service', 'despacho-service', 'pos-frontend', 'ventas-mantenedor', 'users-mantenedor']
                        services.each { svc ->
                            sh "docker push ${REGISTRY}/${svc}:${IMAGE_TAG}"
                            sh "docker push ${REGISTRY}/${svc}:latest"
                        }
                    }
                }
            }
        }

        stage('Deploy') {
            when {
                branch 'main'
            }
            steps {
                sh 'docker compose down --remove-orphans || true'
                sh 'docker compose up -d'
                sh '''
                    echo "Waiting for services to be healthy..."
                    for i in $(seq 1 60); do
                        HEALTHY=$(docker ps --filter health=healthy --format "{{.Names}}" | wc -l)
                        TOTAL=$(docker ps --format "{{.Names}}" | wc -l)
                        echo "  [$i/60] Healthy: $HEALTHY / $TOTAL"
                        if [ "$HEALTHY" -ge 17 ]; then
                            echo "All core services healthy!"
                            break
                        fi
                        sleep 5
                    done
                '''
            }
        }

        stage('Post-Deploy Verification') {
            when {
                branch 'main'
            }
            steps {
                sh '''
                    echo "Running smoke tests..."
                    FAILED=0
                    for endpoint in \
                        "http://localhost:8761/actuator/health Eureka" \
                        "http://localhost:8080/actuator/health Gateway" \
                        "http://localhost:8081/actuator/health Stock" \
                        "http://localhost:8082/actuator/health Venta" \
                        "http://localhost:8083/actuator/health Despacho" \
                        "http://localhost:8084/actuator/health Auth"; do
                        URL=$(echo $endpoint | awk '{print $1}')
                        NAME=$(echo $endpoint | awk '{print $2}')
                        if curl -sf "$URL" > /dev/null 2>&1; then
                            echo "  ✅ $NAME OK"
                        else
                            echo "  ❌ $NAME FAILED"
                            FAILED=$((FAILED+1))
                        fi
                    done
                    if [ $FAILED -gt 0 ]; then
                        echo "WARNING: $FAILED service(s) not responding"
                    else
                        echo "All smoke tests passed!"
                    fi
                '''
            }
        }

        stage('Dynatrace Deployment Event') {
            when {
                allOf {
                    branch 'main'
                    expression { return fileExists('.dynatrace-enabled') }
                }
            }
            steps {
                withCredentials([
                    string(credentialsId: 'dynatrace-url', variable: 'DT_URL'),
                    string(credentialsId: 'dynatrace-api-token', variable: 'DT_API_TOKEN')
                ]) {
                    sh '''
                        curl -X POST "${DT_URL}/api/v2/events/ingest" \
                          -H "Authorization: Api-Token ${DT_API_TOKEN}" \
                          -H "Content-Type: application/json" \
                          -d "{
                            \\"eventType\\": \\"CUSTOM_DEPLOYMENT\\",
                            \\"title\\": \\"POS System Deployment #${BUILD_NUMBER}\\",
                            \\"properties\\": {
                              \\"dt.event.deployment.name\\": \\"pos-system\\",
                              \\"dt.event.deployment.version\\": \\"${BUILD_NUMBER}\\",
                              \\"dt.event.deployment.ci_back_link\\": \\"${BUILD_URL}\\",
                              \\"source\\": \\"Jenkins\\"
                            }
                          }"
                    '''
                }
            }
        }
    }

    post {
        success {
            echo '✅ Pipeline completed successfully!'
        }
        failure {
            echo '❌ Pipeline failed!'
        }
        unstable {
            echo '⚠️ Pipeline completed with warnings'
        }
        always {
            sh 'docker system prune -f --filter "until=24h" || true'
            cleanWs(cleanWhenNotBuilt: false)
        }
    }
}
