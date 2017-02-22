package fk.prof.recorder.main;

import fk.prof.MergeSemantics;
import fk.prof.PerfCtx;
import org.openjdk.jmh.infra.Blackhole;

/**
 * @understands burning 50% and another 50% CPU across 2 branches, but covering 100%+Single and 50%+Duplicate respectively
 */
public class BurnCpuStacked {
    private static final int MULTIPLIER = 97;
    @SuppressWarnings("WeakerAccess")
    public static volatile long val = 0;
    @SuppressWarnings("WeakerAccess")
    public static volatile long firstBurnTime = 0, secondBurnTime = 0, thirdBurnTime = 0;

    @SuppressWarnings("InfiniteLoopStatement")
    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("firstBurnTime = " + firstBurnTime);
                System.out.println("secondBurnTime = " + secondBurnTime);
                System.out.println("thirdBurnTime = " + thirdBurnTime);
                System.out.println("val = " + val);
            }
        }));
        BurnCpuStacked burner = new BurnCpuStacked();
        for (int i = 0; ; i++) {
            long l = System.currentTimeMillis();
            burner.immolate(i);
            val += System.currentTimeMillis() - l;
        }
    }

    private final PerfCtx p50Ctx = new PerfCtx("p50", 50, MergeSemantics.PARENT_SCOPED);//parent's ctx can be anything, doesn't matter
    private final PerfCtx c100Ctx = new PerfCtx("c100", 100, MergeSemantics.STACK_UP);

    private void immolate(int i) {
        try {//this is how it is actually supposed to be written, not the way we do in other tests (we are just being lazy elsewhere because we know it doesn't throw any exceptions)
            p50Ctx.begin();
            long start = System.currentTimeMillis();
            Blackhole.consumeCPU((i * MULTIPLIER) % 10000);
            long start2 = System.currentTimeMillis();
            firstBurnTime += start2 - start;
            try {
                c100Ctx.begin();
                Blackhole.consumeCPU((i * MULTIPLIER) % 10000);
                secondBurnTime += System.currentTimeMillis() - start;
            } finally {
                c100Ctx.end();
            }
        } finally {
            p50Ctx.end();
        }

    }
}
