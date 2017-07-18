pipeline {
    agent {
        docker {
            image 'maven:3.5-jdk-8-alpine'
            args '-v ~/.m2:/root/.m2'
        }
    }
    stages {
        stage('Build') {
            steps {
                sh 'mvn compile'
            }
        }
        stage('Test') {
            steps {
                sh 'mvn package'
            }
        }
        stage('Publish') {
            steps {
                input message: 'Release', ok: 'Go!'
                sh 'mvn install'
            }
        }
    }
}
