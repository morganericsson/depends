package ts;

class TSAsserted {}
class TSActualOnly {}

public class TreeSitterAssertionArgumentSample {
    public void run(TSAsserted first, TSActualOnly second) {
        assertNotNull(first);
        assertEquals(first, second);
    }

    private void assertNotNull(Object value) {}

    private void assertEquals(Object expected, Object actual) {}
}
