package depends.cli;

import depends.Main;
import multilang.depends.util.file.TemporaryFile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JavaFrontendParityTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void tree_sitter_should_match_antlr_for_same_class_calls_and_field_uses() throws Exception {
        File treeSitterOutput = folder.newFolder("tree-sitter");
        File antlrOutput = folder.newFolder("antlr");
        String sourceDir = "./src/test/resources/java-pipeline-examples/parity-baseline";

        run("java", sourceDir, "parity-ts", treeSitterOutput);
        run("java-antlr", sourceDir, "parity-antlr", antlrOutput);

        DependencyGraphSnapshot treeSitterMethod = DependencyGraphSnapshot.read(
                new File(treeSitterOutput, "parity-ts-method.json"));
        DependencyGraphSnapshot antlrMethod = DependencyGraphSnapshot.read(
                new File(antlrOutput, "parity-antlr-method.json"));
        DependencyGraphSnapshot treeSitterStructure = DependencyGraphSnapshot.read(
                new File(treeSitterOutput, "parity-ts-structure.json"));
        DependencyGraphSnapshot antlrStructure = DependencyGraphSnapshot.read(
                new File(antlrOutput, "parity-antlr-structure.json"));

        assertEquals("method node count should match ANTLR",
                antlrMethod.nodeCount(), treeSitterMethod.nodeCount());
        assertEquals("structure node count should match ANTLR",
                antlrStructure.nodeCount(), treeSitterStructure.nodeCount());

        assertTrue("entry should call helper",
                treeSitterMethod.containsEdgeEndingWith(
                        "parity.SameClassDependencies.entry)",
                        "parity.SameClassDependencies.helper)",
                        "Call"));
        assertTrue("assignment RHS should call helper",
                treeSitterMethod.containsEdgeEndingWith(
                        "parity.SameClassDependencies.assignOnly)",
                        "parity.SameClassDependencies.helper)",
                        "Call"));
        assertTrue("try-with-resources variable should call resource method",
                treeSitterMethod.containsEdgeEndingWith(
                        "parity.SameClassDependencies.resourceCall)",
                        "parity.SameClassDependencies.ParityResource.touch)",
                        "Call"));
        assertTrue("cast value should call helper method",
                treeSitterMethod.containsEdgeEndingWith(
                        "parity.SameClassDependencies.castCall)",
                        "parity.SameClassDependencies.widen)",
                        "Call"));
        assertTrue("unqualified subclass call should resolve inherited method",
                treeSitterMethod.containsEdgeEndingWith(
                        "parity.SameClassDependencies.inheritedCall)",
                        "parity.SameClassDependenciesBase.inheritedHelper)",
                        "Call"));
        assertTrue("nested receiver argument should resolve inherited method",
                treeSitterMethod.containsEdgeEndingWith(
                        "parity.SameClassDependencies.nestedInheritedCall)",
                        "parity.SameClassDependenciesBase.inheritedHelper)",
                        "Call"));
        assertTrue("class literal receiver should match ANTLR method resolution",
                treeSitterMethod.containsEdgeEndingWith(
                        "parity.SameClassDependencies.classLiteralCall)",
                        "parity.SameClassDependencies.NamedThing.getName)",
                        "Call"));
        assertTrue("array access receiver should resolve element method",
                treeSitterMethod.containsEdgeEndingWith(
                        "parity.SameClassDependencies.arrayReceiverCall)",
                        "parity.SameClassDependencies.ParityResource.touch)",
                        "Call"));
        assertTrue("casted receiver should still walk casted method call",
                treeSitterMethod.containsEdgeEndingWith(
                        "parity.SameClassDependencies.castedValueReceiverCall)",
                        "parity.SameClassDependencies.ValueThing.getValue)",
                        "Call"));
        assertTrue("entry should use count",
                treeSitterStructure.containsEdgeEndingWith(
                        "parity.SameClassDependencies.entry|Function",
                        "parity.SameClassDependencies.count|Var",
                        "Use"));
        assertTrue("helper should use count",
                treeSitterStructure.containsEdgeEndingWith(
                        "parity.SameClassDependencies.helper|Function",
                        "parity.SameClassDependencies.count|Var",
                        "Use"));

        Set<DependencyGraphSnapshot.TypedEdge> missingMethodEdges = treeSitterMethod.missingFrom(antlrMethod);
        Set<DependencyGraphSnapshot.TypedEdge> missingStructureEdges = treeSitterStructure.missingFrom(antlrStructure);
        assertTrue("Tree-sitter method graph should not miss ANTLR typed edges: " + missingMethodEdges,
                missingMethodEdges.isEmpty());
        assertTrue("Tree-sitter structure graph should not miss ANTLR typed edges: " + missingStructureEdges,
                missingStructureEdges.isEmpty());
    }

    private void run(String lang, String sourceDir, String outputName, File outputDir) throws Exception {
        TemporaryFile.reset();
        Main.run(new String[]{
                lang,
                sourceDir,
                outputName,
                "-d", outputDir.getAbsolutePath(),
                "-f", "json",
                "-g", "method,structure",
                "--strip-leading-path"
        });
    }
}
