pipeline {
    agent {
        docker {
            image 'maven:3-jdk-8'
            args '-v $HOME/.m2:/root/.m2'
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
