pipeline {
    agent any

    environment {
        JAVA_HOME = '/usr/lib/jvm/temurin-21-jdk'
        PATH = "${JAVA_HOME}/bin:${PATH}"
        GRADLE_OPTS = '-Dorg.gradle.daemon=false'
        SONAR_URL = 'http://sonarqube:9000'
        REGISTRY = 'ocardenasmartinez1984'
        IMAGE_TAG = "${BUILD_NUMBER}"
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
                sh './gradlew clean build -x test'
            }
        }

        stage('Unit Tests') {
            steps {
                sh './gradlew test'
            }
            post {
                always {
                    junit '**/build/test-results/test/*.xml'
                    jacoco execPattern: '**/build/jacoco/test.jacocoExec'
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                sh "./gradlew sonar -Dsonar.host.url=${SONAR_URL}"
            }
        }

        stage('Docker Build') {
            parallel {
                stage('eureka-server') {
                    steps {
                        sh "docker build -f eureka-server/Dockerfile -t ${REGISTRY}/eureka-server:${IMAGE_TAG} ."
                    }
                }
                stage('api-gateway') {
                    steps {
                        sh "docker build -f api-gateway/Dockerfile -t ${REGISTRY}/api-gateway:${IMAGE_TAG} ."
                    }
                }
                stage('auth-service') {
                    steps {
                        sh "docker build -f auth-service/Dockerfile -t ${REGISTRY}/auth-service:${IMAGE_TAG} ."
                    }
                }
                stage('stock-service') {
                    steps {
                        sh "docker build -f stock-service/Dockerfile -t ${REGISTRY}/stock-service:${IMAGE_TAG} ."
                    }
                }
                stage('venta-service') {
                    steps {
                        sh "docker build -f venta-service/Dockerfile -t ${REGISTRY}/venta-service:${IMAGE_TAG} ."
                    }
                }
                stage('despacho-service') {
                    steps {
                        sh "docker build -f despacho-service/Dockerfile -t ${REGISTRY}/despacho-service:${IMAGE_TAG} ."
                    }
                }
                stage('frontend') {
                    steps {
                        sh "docker build -f pos-frontend/Dockerfile -t ${REGISTRY}/pos-frontend:${IMAGE_TAG} ."
                    }
                }
            }
        }

        stage('Docker Push') {
            when {
                branch 'main'
            }
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    sh '''
                        echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
                        docker push ${REGISTRY}/eureka-server:${IMAGE_TAG}
                        docker push ${REGISTRY}/api-gateway:${IMAGE_TAG}
                        docker push ${REGISTRY}/auth-service:${IMAGE_TAG}
                        docker push ${REGISTRY}/stock-service:${IMAGE_TAG}
                        docker push ${REGISTRY}/venta-service:${IMAGE_TAG}
                        docker push ${REGISTRY}/despacho-service:${IMAGE_TAG}
                        docker push ${REGISTRY}/pos-frontend:${IMAGE_TAG}
                    '''
                }
            }
        }

        stage('Deploy') {
            when {
                branch 'main'
            }
            steps {
                sh '''
                    docker compose down
                    docker compose up -d
                '''
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
        always {
            cleanWs()
        }
    }
}
