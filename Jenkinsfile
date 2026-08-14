pipeline {
    agent any

    environment {
        DOCKER_REGISTRY = 'docker.io'
        DOCKER_REPO = 'ocard/saga-microservices'
        DOCKER_CREDENTIALS_ID = 'docker-hub-credentials'
        GRADLE_OPTS = '-Dorg.gradle.daemon=false'
    }

    tools {
        jdk 'jdk-17'
        gradle 'gradle-8.9'
        nodejs 'node-20'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    stages {

        stage('Checkout') {
            steps {
                script {
                    checkout scm
                    def gitCommit = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    env.IMAGE_TAG = "${env.BUILD_NUMBER}-${gitCommit}"
                    env.GIT_COMMIT_SHORT = gitCommit
                }
                echo "Commit: ${env.GIT_COMMIT_SHORT}"
                echo "Image Tag: ${env.IMAGE_TAG}"
            }
        }

        stage('Build Backend') {
            steps {
                script {
                    if (isUnix()) {
                        sh 'chmod +x gradlew'
                        sh './gradlew clean build -x test'
                    } else {
                        bat 'gradlew.bat clean build -x test'
                    }
                }
            }
        }

        stage('Unit Tests') {
            steps {
                script {
                    if (isUnix()) {
                        sh './gradlew test'
                    } else {
                        bat 'gradlew.bat test'
                    }
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/build/test-results/test/*.xml'
                }
            }
        }

        stage('Build Frontends') {
            parallel {
                stage('Frontend Admin') {
                    steps {
                        dir('frontend') {
                            script {
                                if (isUnix()) {
                                    sh 'npm install'
                                    sh 'npm run build -- --configuration=production'
                                } else {
                                    bat 'npm install'
                                    bat 'npm run build -- --configuration=production'
                                }
                            }
                        }
                    }
                }
                stage('Frontend POS') {
                    steps {
                        dir('pos-frontend') {
                            script {
                                if (isUnix()) {
                                    sh 'npm install'
                                    sh 'npm run build -- --configuration=production'
                                } else {
                                    bat 'npm install'
                                    bat 'npm run build -- --configuration=production'
                                }
                            }
                        }
                    }
                }
            }
        }

        stage('SonarQube Analysis') {
            when {
                expression { return false } // Enable when SonarQube is configured
            }
            steps {
                withSonarQubeEnv('SonarQube') {
                    script {
                        if (isUnix()) {
                            sh './gradlew sonarqube'
                        } else {
                            bat 'gradlew.bat sonarqube'
                        }
                    }
                }
            }
        }

        stage('Docker Build') {
            when {
                expression { return false } // Enable when Docker is available on Jenkins agent
            }
            steps {
                script {
                    def services = [
                        'eureka-server',
                        'api-gateway',
                        'auth-service',
                        'stock-service',
                        'venta-service',
                        'despacho-service',
                        'frontend',
                        'pos-frontend'
                    ]

                    services.each { service ->
                        echo "Building Docker image for ${service}..."
                        if (isUnix()) {
                            sh "docker build -t ${DOCKER_REPO}/${service}:${IMAGE_TAG} -f ${service}/Dockerfile ."
                            sh "docker tag ${DOCKER_REPO}/${service}:${IMAGE_TAG} ${DOCKER_REPO}/${service}:latest"
                        } else {
                            bat "docker build -t ${DOCKER_REPO}/${service}:${IMAGE_TAG} -f ${service}/Dockerfile ."
                            bat "docker tag ${DOCKER_REPO}/${service}:${IMAGE_TAG} ${DOCKER_REPO}/${service}:latest"
                        }
                    }
                }
            }
        }

        stage('Docker Push') {
            when {
                expression { return false } // Enable when Docker registry credentials are configured
            }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: DOCKER_CREDENTIALS_ID,
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    script {
                        if (isUnix()) {
                            sh "echo ${DOCKER_PASS} | docker login -u ${DOCKER_USER} --password-stdin"
                        } else {
                            bat "echo ${DOCKER_PASS} | docker login -u ${DOCKER_USER} --password-stdin"
                        }

                        def services = [
                            'eureka-server',
                            'api-gateway',
                            'auth-service',
                            'stock-service',
                            'venta-service',
                            'despacho-service',
                            'frontend',
                            'pos-frontend'
                        ]

                        services.each { service ->
                            if (isUnix()) {
                                sh "docker push ${DOCKER_REPO}/${service}:${IMAGE_TAG}"
                                sh "docker push ${DOCKER_REPO}/${service}:latest"
                            } else {
                                bat "docker push ${DOCKER_REPO}/${service}:${IMAGE_TAG}"
                                bat "docker push ${DOCKER_REPO}/${service}:latest"
                            }
                        }
                    }
                }
            }
        }

        stage('Deploy to Dev') {
            when {
                expression { return false } // Enable for dev deployments
            }
            steps {
                script {
                    echo 'Deploying to Development environment...'
                    if (isUnix()) {
                        sh 'docker-compose down || true'
                        sh 'docker-compose up -d --build'
                    } else {
                        bat 'docker-compose down || exit 0'
                        bat 'docker-compose up -d --build'
                    }
                }
            }
        }

        stage('Deploy to Production (K8s)') {
            when {
                expression { return false } // Enable when K8s cluster is configured
            }
            steps {
                script {
                    echo 'Deploying to Kubernetes...'
                    if (isUnix()) {
                        sh "kubectl apply -f k8s/00-namespace.yaml"
                        sh "kubectl apply -f k8s/01-mongodb.yaml"
                        sh "kubectl apply -f k8s/02-postgres.yaml"
                        sh "kubectl apply -f k8s/03-kafka.yaml"
                        sh "kubectl apply -f k8s/04-microservices.yaml"
                        sh "kubectl apply -f k8s/05-frontend.yaml"
                    } else {
                        bat "kubectl apply -f k8s/00-namespace.yaml"
                        bat "kubectl apply -f k8s/01-mongodb.yaml"
                        bat "kubectl apply -f k8s/02-postgres.yaml"
                        bat "kubectl apply -f k8s/03-kafka.yaml"
                        bat "kubectl apply -f k8s/04-microservices.yaml"
                        bat "kubectl apply -f k8s/05-frontend.yaml"
                    }
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
            // Uncomment to enable email notifications:
            // mail to: 'team@example.com',
            //      subject: "FAILED: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
            //      body: "Check: ${env.BUILD_URL}"
        }
        cleanup {
            node('') {
                cleanWs()
            }
        }
    }
}
