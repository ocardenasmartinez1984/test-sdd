pipeline {
    agent any

    environment {
        DOCKER_REGISTRY = 'docker.io'
        DOCKER_REPO = 'ocard/saga-microservices'
        DOCKER_CREDENTIALS_ID = 'docker-hub-credentials'
        GRADLE_OPTS = '-Dorg.gradle.daemon=false'
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
                git url: 'https://github.com/ocardenasmartinez1984/test-sdd.git', branch: 'main'
            }
        }

        stage('Prepare') {
            steps {
                sh 'git rev-parse --short HEAD > commit.txt'
                script {
                    env.GIT_COMMIT_SHORT = readFile('commit.txt').trim()
                    env.IMAGE_TAG = "${env.BUILD_NUMBER}-${env.GIT_COMMIT_SHORT}"
                }
                echo "Commit: ${env.GIT_COMMIT_SHORT}"
                echo "Image Tag: ${env.IMAGE_TAG}"
            }
        }

        stage('Build Backend') {
            steps {
                sh 'chmod +x gradlew'
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

        stage('Build Frontends') {
            parallel {
                stage('Frontend Admin') {
                    steps {
                        dir('frontend') {
                            sh 'npm install'
                            sh 'npm run build -- --configuration=production'
                        }
                    }
                }
                stage('Frontend POS') {
                    steps {
                        dir('pos-frontend') {
                            sh 'npm install'
                            sh 'npm run build -- --configuration=production'
                        }
                    }
                }
            }
        }

        stage('SonarQube Analysis') {
            environment {
                SONAR_HOST_URL = 'http://sonarqube:9000'
            }
            steps {
                withSonarQubeEnv('sonar') {
                    sh './gradlew sonar --info'
                }
            }
        }

        stage('Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Stress Tests') {
            when {
                expression { return false } // Enable when services are running in pipeline environment
            }
            steps {
                sh './gradlew :stress-test:gatlingRun -DbaseUrl=http://auth-service:8084'
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
                        sh "docker build -t ${DOCKER_REPO}/${service}:${IMAGE_TAG} -f ${service}/Dockerfile ."
                        sh "docker tag ${DOCKER_REPO}/${service}:${IMAGE_TAG} ${DOCKER_REPO}/${service}:latest"
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
                        sh "echo ${DOCKER_PASS} | docker login -u ${DOCKER_USER} --password-stdin"

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
                            sh "docker push ${DOCKER_REPO}/${service}:${IMAGE_TAG}"
                            sh "docker push ${DOCKER_REPO}/${service}:latest"
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
                sh 'docker-compose down || true'
                sh 'docker-compose up -d --build'
            }
        }

        stage('Deploy to Production (K8s)') {
            when {
                expression { return false } // Enable when K8s cluster is configured
            }
            steps {
                sh 'kubectl apply -f k8s/00-namespace.yaml'
                sh 'kubectl apply -f k8s/01-mongodb.yaml'
                sh 'kubectl apply -f k8s/02-postgres.yaml'
                sh 'kubectl apply -f k8s/03-kafka.yaml'
                sh 'kubectl apply -f k8s/04-microservices.yaml'
                sh 'kubectl apply -f k8s/05-frontend.yaml'
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
