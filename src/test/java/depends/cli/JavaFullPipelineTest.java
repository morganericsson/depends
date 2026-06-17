package depends.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import depends.Main;
import depends.format.json.JCellObject;
import depends.format.json.JDepObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class JavaFullPipelineTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void should_run_java_tree_sitter_pipeline_to_json_outputs() throws Exception {
        File outputDir = folder.newFolder("depends-output");
        String sourceDir = "./src/test/resources/java-pipeline-examples/simple-call";

        Main.run(new String[]{
                "java",
                sourceDir,
                "pipeline",
                "-d", outputDir.getAbsolutePath(),
                "-f", "json",
                "-g", "file,method",
                "--strip-leading-path"
        });

        File fileJson = new File(outputDir, "pipeline-file.json");
        File methodJson = new File(outputDir, "pipeline-method.json");
        assertTrue("file-level JSON should be written", fileJson.isFile());
        assertTrue("method-level JSON should be written", methodJson.isFile());

        ObjectMapper mapper = new ObjectMapper();
        JDepObject fileMatrix = mapper.readValue(fileJson, JDepObject.class);
        JDepObject methodMatrix = mapper.readValue(methodJson, JDepObject.class);

        assertTrue("file matrix should include Caller.java",
                fileMatrix.getVariables().stream().anyMatch(name -> name.endsWith("Caller.java")));
        assertTrue("file matrix should include Callee.java",
                fileMatrix.getVariables().stream().anyMatch(name -> name.endsWith("Callee.java")));
        assertTrue("file matrix should include a Call dependency", containsDependency(fileMatrix, "Call"));

        assertTrue("method matrix should include Caller.run",
                methodMatrix.getVariables().stream().anyMatch(name -> name.contains("pipeline.Caller.run")));
        assertTrue("method matrix should include Callee.answer",
                methodMatrix.getVariables().stream().anyMatch(name -> name.contains("pipeline.Callee.answer")));
        assertTrue("method matrix should include a Call dependency", containsDependency(methodMatrix, "Call"));
    }

    private boolean containsDependency(JDepObject matrix, String type) {
        if (matrix.getCells() == null) return false;
        for (JCellObject cell : matrix.getCells()) {
            for (Map.Entry<String, Float> entry : cell.getValues().entrySet()) {
                if (type.equals(entry.getKey()) && entry.getValue() > 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
