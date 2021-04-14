// Simple Pipeline
node('worker_node1') {
    stage('Source') { // Get code
        // Get code from our git repository
        git 'git@diyvb2:/home/git/repositories/workshop'
        stash includes: 'api/**, dataaccess/**,util/**, build.gradle ,settings.gradle', name: 'ws-src'
    }
    stage('Compile') { // Compile and do unit testing
        // Run gradle to execute compile and unit testing
        sh 'gradle clean build -x test'
        // sh 'gradle clean build'
    }

    // remaining pipeline pieces go here
    stage ('Unit Test') {
        parallel(
                tester1: { node ('worker_node2') {
                    // always run with a new workspace
                    cleanWs()
                    unstash 'ws-src'
                    sh 'gradle :util:test'
                    //   sh 'gradle clean'
                }},
                tester2: { node ('worker_node3'){
                    // always run with a new workspace
                    cleanWs()
                    unstash 'ws-src'
                    sh 'gradle -D test.single=TestExample1* :api:test'
                }},
                tester3: { node ('worker_node2'){
                    // always run with a new workspace
                    cleanWs()
                    unstash 'ws-src'
                    sh 'gradle -D test.single=TestExample2* :api:test'
                }},
        )
    }
    stage('Integration Test'){
        sh 'mysql -uadmin -padmin registry_test <registry_test.sql'
        sh 'gradle integrationTest'
    }
    // stage('Build reports'){
    //    junit 'api/build/test-results/**/*.xml'
    //}
    stage('Print environement variables'){
        echo  "env === $env"
        echo "params ===$params"
        sh 'printenv'
    }
    stage('Assemble'){
        def workspace = env.WORKSPACE
        def setPropertiesProc = fileLoader.fromGit('jenkins/pipeline/updateGradleProperties',
                'https://github.com/brentlaster/utilities.git', 'master', null, '')

        setPropertiesProc.updateGradleProperties("${workspace}/gradle.properties",
                "${params.MAJOR_VERSION}",
                "${params.MINOR_VERSION}",
                "${params.PATCH_VERSION}",
                "${params.BUILD_STAGE}")

        sh 'gradle clean -x test build assemble'

        stash includes:'web/build/libs/*.war', name:'latest-warfile'
    }

    stage('Package'){
        node('worker_node1'){
            cleanWs()
            git 'git@diyvb2:/home/git/repositories/roarv2-docker.git'
            unstash 'latest-warfile'
            sh "docker build -t roar-db-image-${params.DEPLOYMENT_ID} -f Dockerfile_roar_db_image ."
            sh "docker build -t roar-web-image-${params.DEPLOYMENT_ID} --build-arg warFile=web/build/libs/web*.war -f   Dockerfile_roar_web_image ."

        }
    }

}

