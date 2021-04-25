import javaposse.jobdsl.dsl.DslScriptLoader;
import javaposse.jobdsl.dsl.MemoryJobManagement;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class JobScriptsTest {

    @Test
    public void should_compile_scripts() throws IOException {
        // Simulate running Jenkins
        MemoryJobManagement memoryJobManagement = new MemoryJobManagement();
        DslScriptLoader scriptLoader = new DslScriptLoader(memoryJobManagement);
        String scriptText = new String(Files.readAllBytes(
                new File("course/JenkisFileIntroductionToGroovy.groovy").toPath()));
         scriptLoader.runScript(scriptText);
    }
}
