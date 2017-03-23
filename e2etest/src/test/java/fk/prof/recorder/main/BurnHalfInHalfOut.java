package fk.prof.recorder.main;

import fk.prof.PerfCtx;
import org.openjdk.jmh.infra.Blackhole;

/**
 * @understands burning 50% and another 50% CPU across 2 branches, but covering 100%+Single and 50%+Duplicate respectively
 */
public class BurnHalfInHalfOut {
    private static final int MULTIPLIER = 97;
    @SuppressWarnings("WeakerAccess")
    public static volatile long val = 0;
    @SuppressWarnings("WeakerAccess")
    public static volatile long outBurnTime = 0, inBurnTime = 0;

    @SuppressWarnings("InfiniteLoopStatement")
    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("outBurnTime = " + outBurnTime);
                System.out.println("inBurnTime = " + inBurnTime);
                System.out.println("val = " + val);
            }
        }));
        BurnHalfInHalfOut burner = new BurnHalfInHalfOut();
        for (int i = 0; ; i++) {
            long l = System.currentTimeMillis();
            burner.immolate(i);
            val += System.currentTimeMillis() - l;
        }
    }

    private final PerfCtx c100Ctx = new PerfCtx("c100", 100);

    private void immolate(int i) {
        long start = System.currentTimeMillis();
        Blackhole.consumeCPU((i * MULTIPLIER) % 10000);
        long start2 = System.currentTimeMillis();
        outBurnTime += start2 - start;
        try {
            c100Ctx.begin();
            Blackhole.consumeCPU((i * MULTIPLIER) % 10000);
            inBurnTime += System.currentTimeMillis() - start2;
        } finally {
            c100Ctx.end();
        }
    }
}
