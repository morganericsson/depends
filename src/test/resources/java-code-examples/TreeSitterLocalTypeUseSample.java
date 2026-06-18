package ts;

import java.util.List;

class TSLocalType {}
class TSGenericUse {}
class TSInstanceUse {}
class TSRhsCallUse {}
class TSRhsFieldUse {}
class TSAssertThrowsCallUse {}
class TSAssertThrowsCreateUse {}

class TSUseFactory {
    TSRhsCallUse make() {
        return null;
    }
}

class TSUseFields {
    static TSRhsFieldUse FIELD;
}

class TSUseAssertions {
    static void assertThrows(Class<?> exceptionType, Runnable action) {}
}

class TSAssertThrowsSource {
    static TSAssertThrowsCallUse call() {
        return null;
    }
}

public class TreeSitterLocalTypeUseSample {
    public void run(Object value, List<? extends TSGenericUse> items) {
        TSLocalType local;
        List<? extends TSGenericUse> copy = items;
        if (value instanceof TSInstanceUse) {
            local = new TSLocalType();
        }
    }

    public void useResolvedTypes(TSUseFactory factory) {
        Object result;
        result = factory.make();
        result = TSUseFields.FIELD;
        TSUseAssertions.assertThrows(RuntimeException.class, () -> {
            TSAssertThrowsSource.call();
            new TSAssertThrowsCreateUse();
        });
    }
}
