pipeline {
    agent none
    stages {
        stage('Build & Test') {
            agent {
                docker {
                    image 'cloudbees/java-build-tools'
                }
            }
            steps {
                sh 'mvn package'
            }
        }
    }
}
