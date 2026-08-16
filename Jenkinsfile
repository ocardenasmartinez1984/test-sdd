pipeline {
    agent any

    environment {
        JAVA_HOME = '/usr/lib/jvm/temurin-21-jdk'
        PATH = "${JAVA_HOME}/bin:${PATH}"
        GRADLE_OPTS = '-Dorg.gradle.daemon=false'
    }

    stages {
        stage('Checkout') {
            steps {
                git url: 'https://github.com/ocardenasmartinez1984/test-sdd.git', branch: 'main'
            }
        }

        stage('SonarQube Analysis') {
            steps {
                sh '''
                    export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk
                    export PATH=$JAVA_HOME/bin:$PATH
                    chmod +x gradlew
                    ./gradlew sonar -Dsonar.host.url=http://saga-sonarqube:9000 --info
                '''
            }
        }
    }

    post {
        success {
            echo '✅ SonarQube analysis completed!'
        }
        failure {
            echo '❌ SonarQube analysis failed!'
        }
        always {
            cleanWs()
        }
    }
}
