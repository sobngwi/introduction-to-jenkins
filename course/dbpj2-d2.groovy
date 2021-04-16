//@Library('Utilities2')_
node('worker_node1'){
    stage('Get Source and statsh tests directories'){
        git  'git@diyvb2:/home/git/repositories/workshop.git' // default master branch
        //stash name:'test-sources', includes:'build.gradle,src/test/'
        stash name:'ws-src', includes:'build.gradle,api/**,dataaccess/**,util/**,web/**,settings.gradle'
    }
    stage('Compile') {
        gbuild4 'clean compileJava -x test'
        gbuild4 ':util:test'
        gbuild4 '-D test.single=TestExample1* :api:test'
        gbuild4 '-D test.single=TestExample2* :api:test'
    }
    stage('Unit Test'){
        parallel(
                tester1: { node ('worker_node1') {
                    // always run with a new workspace
                    cleanWs()
                    unstash 'ws-src'
                    gbuild4 ':util:test'
                }},
                tester2: { node ('worker_node2'){
                    // always run with a new workspace
                    cleanWs()
                    unstash 'ws-src'
                    gbuild4 '-D test.single=TestExample1* :api:test'
                }},
                tester3: { node ('worker_node3'){
                    // always run with a new workspace
                    cleanWs()
                    unstash 'ws-src'
                    gbuild4 '-D test.single=TestExample2* :api:test'
                }},

        )
    }

    stage('integration Test'){
        sh 'mysql -uadmin -padmin registry_test < registry_test.sql'
        gbuild4 'IntegrationTest'
    }
    stage('Analysis-Sonar'){
        withSonarQubeEnv('Local SonarQube'){
            sh '/opt/sonar-runner/bin/sonar-runner -X -e'
        }

        step([$class: 'JacocoPublisher',
              execPattern:'**/**.exec',
              classPattern: '**/classes/java/integrationTest/com/demo/dao,**/classes/java/main/com/demo/dao,' +
                      '**/classes/java/main/com/demo/pipeline/registry',
              sourcePattern: '**/src/main/java/com/demo/util,**/src/main/java/com/demo/dao/**/*,' +
                      '**/src/main/com/demo/pipeline/registry/**/*',
              exclusionPattern: '**/*Test*.class'])


        timeout(time:30, unit:'SECONDS'){
            waitUntil {
                try {
                    def qg = waitForQualityGate()
                    return true
                }
                catch (exception){
                    echo "Pipeline aborted due to quality gate failure : ${qg.status}"
                    return false
                }
            }
        }
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
        gbuild4 '-x test build web:assemble'
    }
    // lab 6
    stage('Publish Artifacts'){
        def  server = Artifactory.server "LocalArtifactory"
        def artifactoryGradle = Artifactory.newGradleBuild()
        artifactoryGradle.tool = "gradle4"
        artifactoryGradle.deployer repo:'libs-snapshot-local', server: server
        artifactoryGradle.resolver repo:'remote-repos', server: server

        def buildInfo = Artifactory.newBuildInfo()
        buildInfo.env.capture = true
        artifactoryGradle.deployer.deployMavenDescriptors = true
        artifactoryGradle.deployer.artifactDeploymentPatterns.addExclude("*.jar")
        artifactoryGradle.usesPlugin = false

        artifactoryGradle.run buildFile: 'build.gradle', tasks: 'clean artifactoryPublish', buildInfo: buildInfo
        server.publishBuildInfo buildInfo
    }
    stage('Retrieve Latest Artifact '){
        getLatestScript = libraryResource 'ws-get-latest.sh'
        echo "The latest Script : $getLatestScript"
        writeFile file: 'ws-get-latest.sh', text: "$getLatestScript"
        sh 'chmod +x ws-get-latest.sh && ./ws-get-latest.sh'
        // keep the latest artifact
        stash includes:'*.war', name:'latest-warfile'
    }
    stage ('Build and Deploy the artifact to Docker'){
        node('worker_node3'){
            git 'git@diyvb2:/home/git/repositories/roarv2-docker.git'
            unstash('latest-warfile')
            sh 'ls -rtl'
            sh "docker stop `docker ps -a --format '{{.Names}} \n\n'` || true"
            sh "docker rm -f  `docker ps -a --format '{{.Names}} \n\n'` || true"
            sh "docker rmi -f `docker ps -a --format '{{.Names}} \n\n'` || true"
            dbImage = docker.build("roar-db-image", "-f Dockerfile_roar_db_image .")
            webImage = docker.build("roar-web-image", "--build-arg warFile=web*.war -f Dockerfile_roar_web_image .")
            dbContainer =dbImage.run("-p 3308:3306 -e MYSQL_DATABASE='registry' -e MYSQL_ROOT_PASSWORD='root+1' -e MYSQL_USER='admin' -e MYSQL_PASSWORD='admin'")
            webContainer = webImage.run("--link ${dbContainer.id}:mysql -p 8089:8080")
            sh "docker inspect --format '{{.Name}} is available @ http://{{.NetworkSettings.IPAddress }}:8080/roar' \$(docker ps -q -l)"
        }
    }

}
node('worker_node2'){
    stage('Parallel Demo'){
        stepsToRun = [:]
        [1,2,3,4,5].each {
            stepsToRun["Steps{$it}"] = {
                node {
                    echo "Steps{$it} Starts"
                    sleep 5
                    echo "Steps{$it} finished"
                }
            }
        }
        parallel stepsToRun
    }
    stage('Parallel Demo2') {
        parallel(

                master: { node ('master'){
                    sh 'for i in 1 2  3 ; do  echo "Task $i completed"; done'
                }},
                worker2: { node ('worker_node2'){
                    sh 'for i in 1 2  3 ; do  echo "Task $i completed"; done'
                }},
        )
    }
}
node('worker_node3'){
    def urlRepo= 'git@diyvb2:/home/git/repositories/workshop.git'
    stage('Get Source'){
        git 'git@diyvb2:/home/git/repositories/workshop.git' // default master branch
    }
    stage('Update Source'){
        sh 'git config user.name diyuser2'
        sh 'git config user.email sobngwi@diyvb2'
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'diyuser2-cred',
                          usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASS']]){
            sh "git tag -a ${env.BUILD_TAG} -m 'demonstrate push of tags'"
            sh 'git push git@diyvb2:/home/git/repositories/workshop.git --tags'
        }
    }
}
