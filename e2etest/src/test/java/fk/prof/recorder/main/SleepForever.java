package fk.prof.recorder.main;

public class SleepForever {
    public static void main(String[] args) throws InterruptedException {
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                //ignore
            }
        }
    }
}
