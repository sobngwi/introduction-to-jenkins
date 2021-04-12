import javaposse.jobdsl.dsl.DslFactory

DslFactory factory = this
factory.job("seed-job"){
    triggers { githubPush() }
    scm { github("sobngwi/simple-maven-boot")}
    steps {
        shell("./mvnw clean install")
        dsl {
            external("job_dsl.groovy")
            removeAction("DISABLE")
            removeViewAction("DELETE")
            ignoreExisting(false)
        }
    }
}

