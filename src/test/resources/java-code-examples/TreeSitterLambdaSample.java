package ts;

interface TSLambdaConsumer {
    void apply(TSLambdaTarget target);
}

interface TSLambdaFilter {
    boolean accept(TSLambdaFile file);
}

class TSLambdaFile {
}

class TSLambdaBase {
    protected TSLambdaFile file;

    boolean accepts(TSLambdaFile value) {
        return true;
    }
}

class TSLambdaTarget {
    void ping() {
    }
}

public class TreeSitterLambdaSample extends TSLambdaBase {
    void test() {
        TSLambdaConsumer consumer = (TSLambdaTarget target) -> target.ping();
        consumer.apply(new TSLambdaTarget());
    }

    void inferredParameterShadowsField() {
        TSLambdaFilter filter = file -> accepts(file);
    }
}
