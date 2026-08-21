pipeline {
    agent any

    environment {
        JAVA_HOME = '/usr/lib/jvm/temurin-21-jdk'
        PATH = "${JAVA_HOME}/bin:${env.PATH}"
        GRADLE_OPTS = '-Dorg.gradle.daemon=true -Dorg.gradle.caching=true -Dorg.gradle.parallel=true -Dorg.gradle.configureondemand=true -Xmx2048m'
        GRADLE_USER_HOME = '/var/jenkins_home/.gradle'
        SONAR_URL = 'http://sonarqube:9000'
        REGISTRY = 'ocardenasmartinez1984'
        IMAGE_TAG = "${BUILD_NUMBER}"
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
                    docker compose up -d
                    echo "Waiting for services..."
                    sleep 40
                    curl -sf http://localhost:8761/actuator/health > /dev/null && echo "✅ Eureka OK" || echo "❌ Eureka FAIL"
                    curl -sf http://localhost:8080/actuator/health > /dev/null && echo "✅ Gateway OK" || echo "❌ Gateway FAIL"
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
