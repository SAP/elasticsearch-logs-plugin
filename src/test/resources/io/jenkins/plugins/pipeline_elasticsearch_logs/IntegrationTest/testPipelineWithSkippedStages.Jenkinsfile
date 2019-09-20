pipeline {
    agent any
    stages {
        stage('S1') {
            steps {
                withCredentials([usernameColonPassword(credentialsId: 'doesNotExist', variable: 'x')]) {
                    echo 'S1'
                }
            }
        }
        stage('S2') {
            steps {
                echo 'S2'
            }
        }
        stage('S3') {
            steps {
                echo 'S3'
            }
        }
    }  
}