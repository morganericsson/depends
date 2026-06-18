package depends.extractor.java;

import depends.deptypes.DependencyType;
import depends.entity.Entity;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;

public class JavaTreeSitterAssertionArgumentTest extends JavaTreeSitterParserTest {
    @Before
    public void setUp() {
        super.init();
    }

    @Test
    public void test_should_match_antlr_assertion_argument_type_calls() throws IOException {
        String src = "./src/test/resources/java-code-examples/TreeSitterAssertionArgumentSample.java";
        createParser().parse(src);
        resolveAllBindings();

        Entity method = entityRepo.getEntity("ts.TreeSitterAssertionArgumentSample.run");

        assertNotNull(method);
        assertContainsRelation(method, DependencyType.CALL, "ts.TSAsserted");
        assertNotContainsRelation(method, DependencyType.CALL, "ts.TSActualOnly");
    }
}
