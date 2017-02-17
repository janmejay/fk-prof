package fk.prof.recorder.main;

import fk.prof.PerfCtx;
import org.openjdk.jmh.infra.Blackhole;

public class CpuBurningRunnable implements Runnable {
    private static final int MULTIPLIER = 97;

    private final PerfCtx aCtx = new PerfCtx("inferno", 100);
    
    public static volatile long val = 0;
    
    @Override
    public void run() {
        sleep1Mil();
        aCtx.begin();
        for (int i = 0; ; i++) {
            long l = System.currentTimeMillis();
            Blackhole.consumeCPU((i * MULTIPLIER) % 10000);
            val += System.currentTimeMillis() - l;
            if (i % 967 == 0) {
                sleep1Mil();
            }
        }
    }

    private void sleep1Mil() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) { }
    }
}
