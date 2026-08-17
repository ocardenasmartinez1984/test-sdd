pipeline {
    agent any

    environment {
        JAVA_HOME = '/usr/lib/jvm/temurin-21-jdk'
        PATH = "${JAVA_HOME}/bin:${env.PATH}"
        GRADLE_OPTS = '-Dorg.gradle.daemon=false'
        SONAR_URL = 'http://sonarqube:9000'
        REGISTRY = 'ocardenasmartinez1984'
        DYNATRACE_URL = credentials('dynatrace-url')
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
                    junit allowEmptyResults: true, testResults: '**/build/test-results/test/*.xml'
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                sh './gradlew sonar -Dsonar.host.url=$SONAR_URL'
            }
        }

        stage('Stress Tests') {
            steps {
                sh './gradlew :stress-test:gatlingRun'
            }
            post {
                always {
                    gatlingArchive()
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh 'docker build -f eureka-server/Dockerfile.ci -t $REGISTRY/eureka-server:$BUILD_NUMBER .'
                sh 'docker build -f api-gateway/Dockerfile.ci -t $REGISTRY/api-gateway:$BUILD_NUMBER .'
                sh 'docker build -f auth-service/Dockerfile.ci -t $REGISTRY/auth-service:$BUILD_NUMBER .'
                sh 'docker build -f stock-service/Dockerfile.ci -t $REGISTRY/stock-service:$BUILD_NUMBER .'
                sh 'docker build -f venta-service/Dockerfile.ci -t $REGISTRY/venta-service:$BUILD_NUMBER .'
                sh 'docker build -f despacho-service/Dockerfile.ci -t $REGISTRY/despacho-service:$BUILD_NUMBER .'
                sh 'docker build -f pos-frontend/Dockerfile -t $REGISTRY/pos-frontend:$BUILD_NUMBER .'
            }
        }

        stage('Docker Push') {
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

        stage('Deploy') {
            when {
                branch 'main'
            }
            steps {
                sh 'docker compose down'
                sh 'docker compose up -d'
            }
        }

        stage('Dynatrace Deployment Event') {
            when {
                branch 'main'
            }
            steps {
                withCredentials([string(credentialsId: 'dynatrace-api-token', variable: 'DT_API_TOKEN')]) {
                    sh '''
                        curl -X POST "${DYNATRACE_URL}/api/v2/events/ingest" \
                          -H "Authorization: Api-Token ${DT_API_TOKEN}" \
                          -H "Content-Type: application/json" \
                          -d "{
                            \\"eventType\\": \\"CUSTOM_DEPLOYMENT\\",
                            \\"title\\": \\"POS System Deployment #${BUILD_NUMBER}\\",
                            \\"properties\\": {
                              \\"dt.event.deployment.name\\": \\"pos-system\\",
                              \\"dt.event.deployment.version\\": \\"${BUILD_NUMBER}\\",
                              \\"dt.event.deployment.ci_back_link\\": \\"${BUILD_URL}\\",
                              \\"dt.event.deployment.remediation_action_link\\": \\"${BUILD_URL}\\",
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
            echo 'Pipeline completed successfully!'
        }
        failure {
            echo 'Pipeline failed!'
        }
        always {
            cleanWs()
        }
    }
}
