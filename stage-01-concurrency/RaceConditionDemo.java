import java.util.concurrent.atomic.AtomicInteger;

public class RaceConditionDemo {

    static AtomicInteger counter = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {

        Runnable task = () -> {
            for (int i = 0; i < 1_000_000; i++) {
                counter.incrementAndGet();
            }
        };

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);

        long start = System.nanoTime();

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        long end = System.nanoTime();

        System.out.println("Final counter: " + counter.get());
        System.out.println("Time: " + (end - start) / 1_000_000.0 + " ms");
    }
}