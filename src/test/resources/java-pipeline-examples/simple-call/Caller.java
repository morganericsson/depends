package pipeline;

public class Caller {
    public int run() {
        Callee callee = new Callee();
        return callee.answer();
    }
}
