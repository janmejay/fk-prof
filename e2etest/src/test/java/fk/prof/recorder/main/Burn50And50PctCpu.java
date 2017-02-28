package fk.prof.recorder.main;

import fk.prof.ClosablePerfCtx;
import fk.prof.MergeSemantics;
import fk.prof.PerfCtx;
import org.openjdk.jmh.infra.Blackhole;

/**
 * @understands burning 50% and another 50% CPU across 2 branches, but covering 100%+Single and 50%+Duplicate respectively
 */
public class Burn50And50PctCpu {
    private static final int MULTIPLIER = 97;
    @SuppressWarnings("WeakerAccess")
    public static volatile long val = 0;
    @SuppressWarnings("WeakerAccess")
    public static volatile long firstHalfBurnTime = 0;
    @SuppressWarnings("WeakerAccess")
    public static volatile long secondHalfBurnTime = 0;

    @SuppressWarnings("InfiniteLoopStatement")
    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("firstBurnTime = " + firstHalfBurnTime);
                System.out.println("secondBurnTime = " + secondHalfBurnTime);
                System.out.println("val = " + val);
            }
        }));
        Burn50And50PctCpu burner = new Burn50And50PctCpu();
        for (int i = 0; ; i++) {
            long l = System.currentTimeMillis();
            burner.immolate(i);
            val += System.currentTimeMillis() - l;
        }
    }

    private final PerfCtx s100 = new PerfCtx("100_pct_single_inferno", 100);
    private final PerfCtx d50 = new PerfCtx("50_pct_duplicate_inferno", 50);
    private final PerfCtx d50Child = new PerfCtx("50_pct_duplicate_inferno_child", 50, MergeSemantics.DUPLICATE);

    private void immolate(int i) {
        try(ClosablePerfCtx pctx = s100.open()) {
            long start = System.currentTimeMillis();
            Blackhole.consumeCPU((i * MULTIPLIER) % 10000);
            firstHalfBurnTime += System.currentTimeMillis() - start;
        }

        try(ClosablePerfCtx pctx = d50.open()) {
            d50Child.begin();
            long start = System.currentTimeMillis();
            Blackhole.consumeCPU((i * MULTIPLIER) % 10000);
            secondHalfBurnTime += System.currentTimeMillis() - start;
            d50Child.end();
        }
    }
}
