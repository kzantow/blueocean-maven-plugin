pipeline {
    agent none
    stages {
        stage('Build & Test') {
            agent {
                docker {
                    image 'maven:3.5.0-jdk-8-alpine'
                }
            }
            steps {
                sh 'mvn package -s settings-azure.xml'
            }
        }
    }
}
