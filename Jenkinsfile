pipeline {
    agent any

    environment {
        GRADLE_OPTS = '-Dorg.gradle.daemon=false'
        JAVA_HOME = '/usr/lib/jvm/temurin-21-jdk'
        PATH = "${JAVA_HOME}/bin:${PATH}"
    }

    stages {
        stage('Checkout') {
            steps {
                git url: 'https://github.com/ocardenasmartinez1984/test-sdd.git', branch: 'main'
            }
        }

        stage('Build') {
            steps {
                sh 'chmod +x gradlew'
                sh './gradlew clean build -x test'
            }
        }

        stage('SonarQube Analysis') {
            steps {
                sh './gradlew sonar --info'
            }
        }
    }

    post {
        success {
            echo '✅ SonarQube analysis completed!'
        }
        failure {
            echo '❌ Failed!'
        }
        always {
            cleanWs()
        }
    }
}
