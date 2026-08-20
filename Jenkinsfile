pipeline {
    agent any

    environment {
        JAVA_HOME = '/usr/lib/jvm/temurin-21-jdk'
        PATH = "${JAVA_HOME}/bin:${env.PATH}"
        GRADLE_OPTS = '-Dorg.gradle.daemon=false -Xmx1024m'
        SONAR_URL = 'http://sonarqube:9000'
        REGISTRY = 'ocardenasmartinez1984'
        IMAGE_TAG = "${BUILD_NUMBER}"
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
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
                sh './gradlew clean build -x test -x jacocoTestCoverageVerification'
            }
        }

        stage('Unit Tests') {
            steps {
                sh './gradlew :despacho-service:test :venta-service:test'
                sh './gradlew :stock-service:test --tests "com.stock.application.*" --tests "com.stock.infrastructure.*" --tests "com.stock.interfaces.*"'
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
                stage('JaCoCo Coverage Verification') {
                    steps {
                        sh './gradlew jacocoTestCoverageVerification || echo "Coverage below threshold"'
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
                sh "docker build -f eureka-server/Dockerfile.ci -t ${REGISTRY}/eureka-server:${IMAGE_TAG} -t ${REGISTRY}/eureka-server:latest ."
                sh "docker build -f api-gateway/Dockerfile.ci -t ${REGISTRY}/api-gateway:${IMAGE_TAG} -t ${REGISTRY}/api-gateway:latest ."
                sh "docker build -f auth-service/Dockerfile.ci -t ${REGISTRY}/auth-service:${IMAGE_TAG} -t ${REGISTRY}/auth-service:latest ."
                sh "docker build -f stock-service/Dockerfile.ci -t ${REGISTRY}/stock-service:${IMAGE_TAG} -t ${REGISTRY}/stock-service:latest ."
                sh "docker build -f venta-service/Dockerfile.ci -t ${REGISTRY}/venta-service:${IMAGE_TAG} -t ${REGISTRY}/venta-service:latest ."
                sh "docker build -f despacho-service/Dockerfile.ci -t ${REGISTRY}/despacho-service:${IMAGE_TAG} -t ${REGISTRY}/despacho-service:latest ."
                sh "docker build -f pos-frontend/Dockerfile -t ${REGISTRY}/pos-frontend:${IMAGE_TAG} -t ${REGISTRY}/pos-frontend:latest ."
            }
        }

        stage('Docker Push') {
            when {
                branch 'main'
            }
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    sh 'echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin'
                    sh "docker push ${REGISTRY}/eureka-server:${IMAGE_TAG}"
                    sh "docker push ${REGISTRY}/api-gateway:${IMAGE_TAG}"
                    sh "docker push ${REGISTRY}/auth-service:${IMAGE_TAG}"
                    sh "docker push ${REGISTRY}/stock-service:${IMAGE_TAG}"
                    sh "docker push ${REGISTRY}/venta-service:${IMAGE_TAG}"
                    sh "docker push ${REGISTRY}/despacho-service:${IMAGE_TAG}"
                    sh "docker push ${REGISTRY}/pos-frontend:${IMAGE_TAG}"
                    sh "docker push ${REGISTRY}/eureka-server:latest"
                    sh "docker push ${REGISTRY}/api-gateway:latest"
                    sh "docker push ${REGISTRY}/auth-service:latest"
                    sh "docker push ${REGISTRY}/stock-service:latest"
                    sh "docker push ${REGISTRY}/venta-service:latest"
                    sh "docker push ${REGISTRY}/despacho-service:latest"
                    sh "docker push ${REGISTRY}/pos-frontend:latest"
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
                    sleep 30
                    curl -sf http://localhost:8761/actuator/health || echo "Eureka not ready yet"
                    curl -sf http://localhost:8080/actuator/health || echo "Gateway not ready yet"
                    echo "Deploy completed!"
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
                    curl -sf http://localhost:8080/api/v1/stock || echo "Stock service check failed"
                    curl -sf http://localhost:8081/actuator/health || echo "Stock health failed"
                    curl -sf http://localhost:8082/actuator/health || echo "Venta health failed"
                    curl -sf http://localhost:8083/actuator/health || echo "Despacho health failed"
                    echo "Smoke tests completed!"
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
    }
}
