pipeline {
    agent { docker { image 'maven:3-alpine' } }
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
