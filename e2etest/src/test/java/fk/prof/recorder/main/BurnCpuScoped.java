package fk.prof.recorder.main;

import fk.prof.MergeSemantics;
import fk.prof.PerfCtx;
import org.openjdk.jmh.infra.Blackhole;

/**
 * @understands burning 50% and another 50% CPU across 2 branches, but covering 100%+Single and 50%+Duplicate respectively
 */
public class BurnCpuScoped {
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
                System.out.println("val = " + val);
            }
        }));
        BurnCpuScoped burner = new BurnCpuScoped();
        for (int i = 0; ; i++) {
            long l = System.currentTimeMillis();
            burner.immolate(i);
            val += System.currentTimeMillis() - l;
        }
    }

    private final PerfCtx p100Ctx = new PerfCtx("p100", 100, MergeSemantics.STACK_UP);//parent's ctx can be anything, doesn't matter
    private final PerfCtx childCtx1 = new PerfCtx("c1", 0, MergeSemantics.PARENT_SCOPED);//coverage doesn't matter
    private final PerfCtx childCtx2 = new PerfCtx("c2", 0, MergeSemantics.PARENT_SCOPED);

    private void immolate(int i) {
        try {//this is how it is actually supposed to be written, not the way we do in other tests (we are just being lazy elsewhere because we know it doesn't throw any exceptions)
            p100Ctx.begin();
            long start = System.currentTimeMillis();
            Blackhole.consumeCPU((i * MULTIPLIER) % 10000);
            long start2 = System.currentTimeMillis();
            firstBurnTime += start2 - start;
            try {
                childCtx1.begin();
                Blackhole.consumeCPU((i * MULTIPLIER) % 10000);
                secondBurnTime += System.currentTimeMillis() - start;
                try {
                    childCtx2.begin();
                    Blackhole.consumeCPU((i * MULTIPLIER) % 10000);
                    thirdBurnTime += System.currentTimeMillis() - start;
                } finally {
                    childCtx2.end();
                }
            } finally {
                childCtx1.end();
            }
        } finally {
            p100Ctx.end();
        }

    }
}
