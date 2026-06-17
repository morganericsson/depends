package ts;

class TSExprA {
    void foo() {
    }
}

class TSExprBase {
    TSExprBase(String name) {
    }
}

class TSExprChild extends TSExprBase {
    TSExprChild(String name) {
        super(name);
    }
}

public class TreeSitterExpressionSample {
    void test(Object in) {
        TSExprA a = new TSExprA();
        a.foo();
        TSExprA b = (TSExprA) in;
        b = new TSExprA();
        TSExprA[] values = new TSExprA[] { new TSExprA() };
    }
}
