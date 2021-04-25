
node 'xxx'{
    agent any

    triggers {
        pollSCM('*/5 * * * *')
    }
    when {
        not {
            branch != 'master'
        }
    }
    stages {
        stage('Compile') {
            steps {
                gradlew('clean', 'compile')
            }
        }
        stage('Unit Tests') {
            steps {
                mvnw('test')
            }
            post {
                always {
                    junit '**/build/test-results/test/TEST-*.xml'
                }
            }
        }

        stage('Assemble') {
            steps {
                mvnw('assemble')
                stash includes: '**/build/libs/*.war', name: 'app'
            }
        }
        stage('Promotion') {
            steps {
                timeout(time: 1, unit:'DAYS') {
                    input 'Deploy to Production?'
                }
            }
        }

    }
    post {
        failure {
            mail to: 'sobngwi@gmail.com', subject: 'Build failed', body: 'Please fix!'
        }
    }
    environment {

    }
}

def mvnw(String... args) {
    sh "./mvnw ${args.join(' ')} -X"
}