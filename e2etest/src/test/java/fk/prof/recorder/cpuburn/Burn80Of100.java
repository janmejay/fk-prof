package fk.prof.recorder.cpuburn;

import org.openjdk.jmh.infra.Blackhole;

public class Burn80Of100 implements Burner {
    private final int multiplier;

    public Burn80Of100(int multiplier) {
        this.multiplier = 80 * multiplier;
    }

    @Override
    public void burn() {
        Blackhole.consumeCPU(multiplier);        
    }
}
