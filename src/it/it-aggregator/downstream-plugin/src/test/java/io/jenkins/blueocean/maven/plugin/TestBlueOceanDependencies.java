package io.jenkins.blueocean.maven.plugin;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * Validates all the expected files have been copied to the proper locations
 */
public class TestBlueOceanDependencies {
    @Test
    public void test() throws Exception {
        File cwd = new File(System.getProperty("user.dir"));
        File node_modules = new File(cwd, "node_modules");
        List<String> files = Arrays.asList(node_modules.list());
        System.out.println(node_modules.getPath() + " list: " + files);
        Assert files.size() > 0 : "Should have entries in node_modules";
        assert files.contains("@jenkins-cd") : "Should have upstream-1 / @jenkins-cd";
        assert files.contains("upstream-2") : "Should have upstream-2";
    }
}
