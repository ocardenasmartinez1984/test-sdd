pipeline {
    agent any

    environment {
        JAVA_HOME = '/usr/lib/jvm/temurin-21-jdk'
        PATH = "${JAVA_HOME}/bin:${PATH}"
        GRADLE_OPTS = '-Dorg.gradle.daemon=false'
        SONAR_URL = 'http://sonarqube:9000'
        REGISTRY = 'ocardenasmartinez1984'
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
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                sh "./gradlew sonar -Dsonar.host.url=${env.SONAR_URL}"
            }
        }

        stage('Docker Build') {
            parallel {
                stage('eureka-server') {
                    steps {
                        sh "docker build -f eureka-server/Dockerfile -t ${env.REGISTRY}/eureka-server:${env.BUILD_NUMBER} ."
                    }
                }
                stage('api-gateway') {
                    steps {
                        sh "docker build -f api-gateway/Dockerfile -t ${env.REGISTRY}/api-gateway:${env.BUILD_NUMBER} ."
                    }
                }
                stage('auth-service') {
                    steps {
                        sh "docker build -f auth-service/Dockerfile -t ${env.REGISTRY}/auth-service:${env.BUILD_NUMBER} ."
                    }
                }
                stage('stock-service') {
                    steps {
                        sh "docker build -f stock-service/Dockerfile -t ${env.REGISTRY}/stock-service:${env.BUILD_NUMBER} ."
                    }
                }
                stage('venta-service') {
                    steps {
                        sh "docker build -f venta-service/Dockerfile -t ${env.REGISTRY}/venta-service:${env.BUILD_NUMBER} ."
                    }
                }
                stage('despacho-service') {
                    steps {
                        sh "docker build -f despacho-service/Dockerfile -t ${env.REGISTRY}/despacho-service:${env.BUILD_NUMBER} ."
                    }
                }
                stage('frontend') {
                    steps {
                        sh "docker build -f pos-frontend/Dockerfile -t ${env.REGISTRY}/pos-frontend:${env.BUILD_NUMBER} ."
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
                    sh "echo ${DOCKER_PASS} | docker login -u ${DOCKER_USER} --password-stdin"
                    sh "docker push ${env.REGISTRY}/eureka-server:${env.BUILD_NUMBER}"
                    sh "docker push ${env.REGISTRY}/api-gateway:${env.BUILD_NUMBER}"
                    sh "docker push ${env.REGISTRY}/auth-service:${env.BUILD_NUMBER}"
                    sh "docker push ${env.REGISTRY}/stock-service:${env.BUILD_NUMBER}"
                    sh "docker push ${env.REGISTRY}/venta-service:${env.BUILD_NUMBER}"
                    sh "docker push ${env.REGISTRY}/despacho-service:${env.BUILD_NUMBER}"
                    sh "docker push ${env.REGISTRY}/pos-frontend:${env.BUILD_NUMBER}"
                }
            }
        }

        stage('Deploy') {
            when {
                branch 'main'
            }
            steps {
                sh 'docker compose down'
                sh 'docker compose up -d'
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
