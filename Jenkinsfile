pipeline {
    agent any

    environment {
        JAVA_HOME = '/usr/lib/jvm/temurin-21-jdk'
        GRADLE_OPTS = '-Dorg.gradle.daemon=false'
        SONAR_URL = 'http://sonarqube:9000'
        REGISTRY = 'ocardenasmartinez1984'
    }

    stages {
        stage("Checkout") {
            steps {
                git url: 'https://github.com/ocardenasmartinez1984/test-sdd.git', branch: 'main'
                sh 'chmod +x gradlew'
            }
        }

        stage("Build") {
            steps {
                sh 'export PATH=$JAVA_HOME/bin:$PATH && ./gradlew clean build -x test'
            }
        }

        stage("Unit Tests") {
            steps {
                sh 'export PATH=$JAVA_HOME/bin:$PATH && ./gradlew test'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/build/test-results/test/*.xml'
                }
            }
        }

        stage("SonarQube Analysis") {
            steps {
                sh 'export PATH=$JAVA_HOME/bin:$PATH && ./gradlew sonar -Dsonar.host.url=$SONAR_URL'
            }
        }

        stage("Docker Build") {
            parallel {
                stage("eureka-server") {
                    steps {
                        sh 'docker build -f eureka-server/Dockerfile -t $REGISTRY/eureka-server:$BUILD_NUMBER .'
                    }
                }
                stage("api-gateway") {
                    steps {
                        sh 'docker build -f api-gateway/Dockerfile -t $REGISTRY/api-gateway:$BUILD_NUMBER .'
                    }
                }
                stage("auth-service") {
                    steps {
                        sh 'docker build -f auth-service/Dockerfile -t $REGISTRY/auth-service:$BUILD_NUMBER .'
                    }
                }
                stage("stock-service") {
                    steps {
                        sh 'docker build -f stock-service/Dockerfile -t $REGISTRY/stock-service:$BUILD_NUMBER .'
                    }
                }
                stage("venta-service") {
                    steps {
                        sh 'docker build -f venta-service/Dockerfile -t $REGISTRY/venta-service:$BUILD_NUMBER .'
                    }
                }
                stage("despacho-service") {
                    steps {
                        sh 'docker build -f despacho-service/Dockerfile -t $REGISTRY/despacho-service:$BUILD_NUMBER .'
                    }
                }
                stage("frontend") {
                    steps {
                        sh 'docker build -f pos-frontend/Dockerfile -t $REGISTRY/pos-frontend:$BUILD_NUMBER .'
                    }
                }
            }
        }

        stage("Docker Push") {
            when {
                branch 'main'
            }
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    sh 'echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin'
                    sh 'docker push $REGISTRY/eureka-server:$BUILD_NUMBER'
                    sh 'docker push $REGISTRY/api-gateway:$BUILD_NUMBER'
                    sh 'docker push $REGISTRY/auth-service:$BUILD_NUMBER'
                    sh 'docker push $REGISTRY/stock-service:$BUILD_NUMBER'
                    sh 'docker push $REGISTRY/venta-service:$BUILD_NUMBER'
                    sh 'docker push $REGISTRY/despacho-service:$BUILD_NUMBER'
                    sh 'docker push $REGISTRY/pos-frontend:$BUILD_NUMBER'
                }
            }
        }

        stage("Deploy") {
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
