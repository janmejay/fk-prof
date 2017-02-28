package fk.prof.recorder.cpuburn;

public class WrapperA<T extends Burner> {
    private final T t;

    public WrapperA(T t) {
        this.t = t;
    }

    public void burnSome(short noise) {
        t.burn();
    }
}
