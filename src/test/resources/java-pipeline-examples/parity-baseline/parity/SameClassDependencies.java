package parity;

// UTF-8 marker: åäö
public class SameClassDependencies extends SameClassDependenciesBase {
    private static final ParityResource SHARED = new ParityResource();
    private final String name;
    private int count;

    public SameClassDependencies(String name) {
        this.name = name;
    }

    public int entry(int value) {
        count = helper(value);
        return helper(count);
    }

    public int assignOnly(int value) {
        int local = value;
        local = helper(local);
        return local;
    }

    private int helper(int value) {
        return value + count;
    }

    public void resourceCall() {
        try (ParityResource resource = new ParityResource()) {
            resource.touch();
        }
    }

    public void fieldReceiverCall() {
        SHARED.touch();
    }

    public void castReceiverCall(Object value) {
        ((ParityResource) value).touch();
    }

    public void newReceiverCall() {
        new ParityResource().touch();
    }

    public int inheritedCall(int value) {
        return inheritedHelper(value);
    }

    public int nestedInheritedCall(int value) {
        return new HoldsValue(inheritedHelper(value)).read();
    }

    public String classLiteralCall() {
        return NamedThing.class.getName();
    }

    public void arrayReceiverCall() {
        ParityResource[] resources = new ParityResource[] { new ParityResource() };
        resources[0].touch();
    }

    public int castedValueReceiverCall() {
        return ((Number) new ValueThing().getValue()).intValue();
    }

    public short castCall(int value) {
        return (short) widen(value);
    }

    public boolean sameTypeReferences(SameClassDependencies other, Object value) {
        SameClassDependencies casted = (SameClassDependencies) value;
        return other == casted;
    }

    private int widen(int value) {
        return value;
    }

    static class ParityResource implements AutoCloseable {
        void touch() {
        }

        @Override
        public void close() {
        }
    }

    static class NamedThing {
        String getName() {
            return "named";
        }
    }

    static class HoldsValue {
        private final int value;

        HoldsValue(int value) {
            this.value = value;
        }

        int read() {
            return value;
        }
    }

    static class ValueThing {
        Number getValue() {
            return Integer.valueOf(1);
        }
    }
}

class SameClassDependenciesBase {
    int inheritedHelper(int value) {
        return value;
    }
}
