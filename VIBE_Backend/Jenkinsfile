pipeline {
    agent any

    environment {
        DOCKER_HUB_REPO    = 'tchango-noudou/vibe'
        DOCKER_CREDENTIALS = credentials('dockerhub-credentials')
        JWT_SECRET         = credentials('vibe-jwt-secret')
        CLAUDE_API_KEY     = credentials('claude-api-key')
        KUBECONFIG         = credentials('oracle-vps-kubeconfig')
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                sh 'echo "Building VIBE Platform — Joseph Tchango Noudou"'
                sh 'git log --oneline -5'
            }
        }

        stage('Build & Test') {
            parallel {
                stage('Auth Service') {
                    steps {
                        dir('auth-service') {
                            sh 'mvn clean verify -Dmaven.test.failure.ignore=false'
                        }
                    }
                    post {
                        always {
                            junit 'auth-service/target/surefire-reports/*.xml'
                            publishHTML([
                                allowMissing: false,
                                reportDir: 'auth-service/target/site/jacoco',
                                reportFiles: 'index.html',
                                reportName: 'Auth Service Coverage'
                            ])
                        }
                    }
                }
                stage('Messaging Service') {
                    steps {
                        dir('messaging-service') {
                            sh 'mvn clean verify -Dmaven.test.failure.ignore=false'
                        }
                    }
                }
                stage('Rewards Service') {
                    steps {
                        dir('rewards-service') {
                            sh 'mvn clean verify -Dmaven.test.failure.ignore=false'
                        }
                    }
                }
            }
        }

        stage('Code Coverage Check') {
            steps {
                sh '''
                    echo "Verifying 80%+ code coverage across all services..."
                    mvn jacoco:check -pl auth-service,messaging-service,rewards-service
                '''
            }
        }

        stage('Docker Build') {
            steps {
                sh """
                    docker build -t ${DOCKER_HUB_REPO}-auth:${BUILD_NUMBER} ./auth-service
                    docker build -t ${DOCKER_HUB_REPO}-messaging:${BUILD_NUMBER} ./messaging-service
                    docker build -t ${DOCKER_HUB_REPO}-rewards:${BUILD_NUMBER} ./rewards-service
                    docker tag ${DOCKER_HUB_REPO}-auth:${BUILD_NUMBER} ${DOCKER_HUB_REPO}-auth:latest
                    docker tag ${DOCKER_HUB_REPO}-messaging:${BUILD_NUMBER} ${DOCKER_HUB_REPO}-messaging:latest
                    docker tag ${DOCKER_HUB_REPO}-rewards:${BUILD_NUMBER} ${DOCKER_HUB_REPO}-rewards:latest
                """
            }
        }

        stage('Security Scan') {
            steps {
                sh """
                    docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \\
                        aquasec/trivy:latest image --exit-code 0 --severity HIGH,CRITICAL \\
                        ${DOCKER_HUB_REPO}-auth:latest
                """
            }
        }

        stage('Push to Docker Hub') {
            steps {
                sh "docker login -u ${DOCKER_CREDENTIALS_USR} -p ${DOCKER_CREDENTIALS_PSW}"
                sh """
                    docker push ${DOCKER_HUB_REPO}-auth:${BUILD_NUMBER}
                    docker push ${DOCKER_HUB_REPO}-auth:latest
                    docker push ${DOCKER_HUB_REPO}-messaging:${BUILD_NUMBER}
                    docker push ${DOCKER_HUB_REPO}-messaging:latest
                    docker push ${DOCKER_HUB_REPO}-rewards:${BUILD_NUMBER}
                    docker push ${DOCKER_HUB_REPO}-rewards:latest
                """
            }
        }

        stage('Deploy to Oracle VPS') {
            steps {
                sh """
                    kubectl set image deployment/auth-service \\
                        auth-service=${DOCKER_HUB_REPO}-auth:${BUILD_NUMBER}
                    kubectl set image deployment/messaging-service \\
                        messaging-service=${DOCKER_HUB_REPO}-messaging:${BUILD_NUMBER}
                    kubectl set image deployment/rewards-service \\
                        rewards-service=${DOCKER_HUB_REPO}-rewards:${BUILD_NUMBER}
                    kubectl rollout status deployment/auth-service
                    kubectl rollout status deployment/messaging-service
                    kubectl rollout status deployment/rewards-service
                """
            }
        }
    }

    post {
        success {
            echo '✅ VIBE deployment successful! Platform is live.'
        }
        failure {
            echo '❌ VIBE deployment failed. Check logs above.'
        }
        always {
            sh 'docker system prune -f --filter "until=24h"'
        }
    }
}
