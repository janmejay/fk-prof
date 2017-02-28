package fk.prof.recorder.main;

/**
 * @understands calling cpu burning runnable to burn CPU directly on main thread 
 */
public class BurnCpuUsingRunnable {
    public static void main(String[] args) {
        CpuBurningRunnable cpuBurner = new CpuBurningRunnable();
        cpuBurner.run();
    }
}
