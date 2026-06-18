package ts;

import java.util.Comparator;

class TSCompared {
}

class TSVarargItem {
}

public class TreeSitterParameterGenericInterfaceSample implements Comparator<TSCompared> {
    @Override
    public int compare(TSCompared left, TSCompared right) {
        return 0;
    }

    public void accept(TSVarargItem... items) {
    }
}
