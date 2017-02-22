package fk.prof.recorder.cpuburn;

import org.openjdk.jmh.infra.Blackhole;

public class Burn20Of100 implements Burner {

    private final int multiplier;

    public Burn20Of100(int multiplier) {
        this.multiplier = 20 * multiplier;
    }

    @Override
    public void burn() {
        Blackhole.consumeCPU(multiplier);
    }
}
