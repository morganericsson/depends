package depends.extractor.java;

import depends.deptypes.DependencyType;
import depends.entity.Entity;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;

public class JavaTreeSitterParameterGenericInterfaceTest extends JavaTreeSitterParserTest {
    @Before
    public void setUp() {
        super.init();
    }

    @Test
    public void test_should_resolve_parameters_without_implementing_generic_arguments() throws IOException {
        String src = "./src/test/resources/java-code-examples/TreeSitterParameterGenericInterfaceSample.java";
        createParser().parse(src);
        resolveAllBindings();

        Entity type = entityRepo.getEntity("ts.TreeSitterParameterGenericInterfaceSample");
        Entity compare = entityRepo.getEntity("ts.TreeSitterParameterGenericInterfaceSample.compare");
        Entity accept = entityRepo.getEntity("ts.TreeSitterParameterGenericInterfaceSample.accept");

        assertNotNull(type);
        assertNotNull(compare);
        assertNotNull(accept);
        assertNotContainsRelation(type, DependencyType.IMPLEMENT, "ts.TSCompared");
        assertContainsRelation(compare, DependencyType.PARAMETER, "ts.TSCompared");
        assertContainsRelation(accept, DependencyType.PARAMETER, "ts.TSVarargItem");
    }
}
