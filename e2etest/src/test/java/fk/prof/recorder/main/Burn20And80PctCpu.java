package fk.prof.recorder.main;

import fk.prof.PerfCtx;
import fk.prof.recorder.cpuburn.Burn20Of100;
import fk.prof.recorder.cpuburn.Burn80Of100;
import fk.prof.recorder.cpuburn.WrapperA;

public class Burn20And80PctCpu {
    private static final int MULTIPLIER = 10000;
    @SuppressWarnings("WeakerAccess")
    public static volatile long val = 0;

    @SuppressWarnings("InfiniteLoopStatement")
    public static void main(String[] args) {
        Burn20And80PctCpu burner = new Burn20And80PctCpu();
        while (true) {
            long l = System.currentTimeMillis();
            burner.burnCpu();
            val += System.currentTimeMillis() - l;
        }
    }
    private final Burn20Of100 twentyPct = new Burn20Of100(MULTIPLIER);
    private final WrapperA eightyPct = new WrapperA(new Burn80Of100(MULTIPLIER));
    private final PerfCtx pctx = new PerfCtx("inferno", 100);

    private void burnCpu() {
        pctx.begin();
        twentyPct.burn();
        eightyPct.burnSome((short) 42);
        pctx.end();
    }
}
