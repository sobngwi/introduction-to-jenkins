//@Library('Utilities2')_
node('worker_node1'){
    stage('Get Source and statsh tests directories'){
        git 'git@diyvb2:/home/git/repositories/workshop.git' // default master branch
        stash name:'test-sources', includes:'build.gradle,src/test/'
        stash name:'ws-src', includes:'build.gradle,api/**,dataaccess/**,util/**,web/**,settings.gradle'
    }
    stage('Compile') {
        gbuild4 'clean compileJava -x test'
    }
    stage('Unit Test'){
        parallel(
                tester1: { node ('worker_node2') {
                    // always run with a new workspace
                    cleanWs()
                    unstash 'ws-src'
                    gbuild4 ':util:test'
                }},
                tester2: { node ('worker_node3'){
                    // always run with a new workspace
                    cleanWs()
                    unstash 'ws-src'
                    gbuild4 '-D test.single=TestExample1* :api:test'
                }},
                tester3: { node ('worker_node2'){
                    // always run with a new workspace
                    cleanWs()
                    unstash 'ws-src'
                    gbuild4 '-D test.single=TestExample2* :api:test'
                }}
        )
    }
    stage('integration Test'){
        sh 'mysql -uadmin -padmin registry_test < registry_test.sql'
        gbuild4 'IntegrationTest'
    }

}
node('worker_node2'){
    stage('Parallel Demo'){
        stepsToRun = [:]
        [1,2,3,4,5].each {
            stepsToRun["Steps{$it}"] = {
                node ('worker_node2') {
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
        git urlRepo//'git@diyvb2:/home/git/repositories/workshop.git' // default master branch
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
