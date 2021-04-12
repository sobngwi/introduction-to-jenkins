import javaposse.jobdsl.dsl.DslFactory

DslFactory factory =this;
List<String> repos = ['a', 'b', 'c', 'd', 'e']
repos.each {
    factory.job("${it}-job-created") {
       steps {
           shell('echo "Execution of step  in job job ${it}"')
       }
    }
}
