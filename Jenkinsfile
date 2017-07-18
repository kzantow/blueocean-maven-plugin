pipeline {
    agent {
        docker {
            image 'maven:3.5.0-jdk-8-alpine'
            args '-v $HOME/.m2:/root/.m2'
        }
    }
    stages {
        stage('Build') {
            steps {
                sh 'find . -type f'
                sh 'cat findbugs-excludes.xml'
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
