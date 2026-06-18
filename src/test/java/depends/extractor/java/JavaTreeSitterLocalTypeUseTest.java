package depends.extractor.java;

import depends.deptypes.DependencyType;
import depends.entity.Entity;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class JavaTreeSitterLocalTypeUseTest extends JavaTreeSitterParserTest {
    @Before
    public void setUp() {
        super.init();
    }

    @Test
    public void test_should_emit_use_for_local_declared_and_instanceof_types() throws IOException {
        String src = "./src/test/resources/java-code-examples/TreeSitterLocalTypeUseSample.java";
        createParser().parse(src);
        resolveAllBindings();

        Entity method = entityRepo.getEntity("ts.TreeSitterLocalTypeUseSample.run");

        assertNotNull(method);
        assertContainsRelation(method, DependencyType.USE, "ts.TSLocalType");
        assertContainsRelation(method, DependencyType.USE, "ts.TSGenericUse");
        assertContainsRelation(method, DependencyType.USE, "ts.TSInstanceUse");

        Entity parameter = entityRepo.getEntity("ts.TreeSitterLocalTypeUseSample.run.items");
        assertNotNull(parameter);
        assertEquals(Integer.valueOf(34), parameter.getLine());
    }

    @Test
    public void test_should_emit_use_for_marked_resolved_expression_types() throws IOException {
        String src = "./src/test/resources/java-code-examples/TreeSitterLocalTypeUseSample.java";
        createParser().parse(src);
        resolveAllBindings();

        Entity method = entityRepo.getEntity("ts.TreeSitterLocalTypeUseSample.useResolvedTypes");

        assertNotNull(method);
        assertContainsRelation(method, DependencyType.USE, "ts.TSRhsCallUse");
        assertContainsRelation(method, DependencyType.USE, "ts.TSRhsFieldUse");
        assertContainsRelation(method, DependencyType.USE, "ts.TSAssertThrowsCallUse");
        assertContainsRelation(method, DependencyType.USE, "ts.TSAssertThrowsCreateUse");
    }
}
