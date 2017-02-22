package fk.prof.recorder.main;

/**
 * @understands burning 50% and another 50% CPU across 2 branches, but covering 100%+Single and 50%+Duplicate respectively
 */
public class MultiThreadedCpuBurner {
    @SuppressWarnings("InfiniteLoopStatement")
    public static void main(String[] args) throws InterruptedException {
        MultiThreadedCpuBurner burner = new MultiThreadedCpuBurner();
        Runnable r = new CpuBurningRunnable();
        Thread thdFoo = new Thread(r, "foo-the-thd");
        thdFoo.setPriority(6);
        Thread thdBar= new Thread(r, "bar-the-thd");
        thdBar.setDaemon(true);

        thdFoo.start();
        thdBar.start();
        thdFoo.join();
    }
}
