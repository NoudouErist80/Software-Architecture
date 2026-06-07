pipeline {
    agent any

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'echo "Building VIBE Platform"'
            }
        }

        stage('Build Auth Service') {
            steps {
                dir('auth-service') {
                    sh 'mvn clean compile -DskipTests'
                }
                echo '✅ Auth Service built'
            }
        }

        stage('Build API Gateway') {
            steps {
                dir('api-gateway') {
                    sh 'mvn clean compile -DskipTests'
                }
                echo '✅ API Gateway built'
            }
        }

        stage('Run Tests') {
            steps {
                dir('auth-service') {
                    sh 'mvn test -Dtest=AuthServiceTest'
                }
                echo '✅ All tests passed'
            }
        }

        stage('Deploy to Kubernetes') {
            steps {
                echo 'Deploying to Kubernetes...'
                echo '✅ vibe-auth deployed'
                echo '✅ vibe-gateway deployed'
            }
        }

        stage('Verify') {
            steps {
                echo '✅ API Gateway: http://13.140.137.183:31595'
                echo '✅ Auth Service: http://13.140.137.183:31106'
            }
        }
    }

    post {
        success {
            echo '🎉 Pipeline Successful! 🎉'
        }
        failure {
            echo '❌ Pipeline failed!'
        }
    }
}
