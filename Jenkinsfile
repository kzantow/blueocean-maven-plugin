pipeline {
    agent none
    stages {
        stage('Build & Test') {
            agent {
                docker {
                    image 'maven:3.5.0-jdk-8-alpine'
                    args '-v $HOME/.m2:/root/.m2'
                }
            }
            steps {
                sh 'mvn package'
            }
        }
        /*
        stage('Release') {
            agent {
                docker {
                    image 'maven:3.5.0-jdk-8-alpine'
                    args '-v $HOME/.m2:/root/.m2'
                }
            }
            steps {
                input message: 'Release?', ok: 'Go!'
                sh 'mvn release:prepare release:perform'
            }
        }
        */
    }
}
