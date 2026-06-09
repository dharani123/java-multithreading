package org.example.lessons;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LESSON 11.2 — The scalability proof: virtual vs platform threads on I/O work
 *
 * Lesson 7 told us I/O-bound work wants MANY more threads than cores, because threads
 * spend their time waiting. Lesson 11.1 said virtual threads make "many" mean MILLIONS.
 * Here we MEASURE it.
 *
 * Demo A — same workload, two executors:
 *   10,000 tasks, each "waits" 100ms (simulated I/O).
 *     - Fixed PLATFORM pool of 200 : only 200 wait at once -> 50 batches -> ~5 seconds.
 *     - VIRTUAL thread per task     : all 10,000 wait at once -> ~0.1s + overhead.
 *
 * Demo B — raw scale:
 *   Launch 500,000 virtual threads at once, each sleeping 100ms. Finishes in well under
 *   a second. The platform-thread equivalent would need ~500 GB of stack and crash the
 *   machine — so one-thread-per-task was simply impossible before virtual threads.
 *
 * The point: virtual threads let you keep the SIMPLE "one thread per task, just block"
 * model (no async callbacks) AND scale to massive concurrency.
 */
public class Lesson11_2_VirtualScale {

    public static void main(String[] args) throws InterruptedException {
        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("Cores: " + cores + "\n");

        demoSameWorkload();
        demoRawScale();
    }

    /** 10,000 I/O tasks (each 100ms): bounded platform pool vs virtual-thread-per-task. */
    static void demoSameWorkload() throws InterruptedException {
        int taskCount = 10_000;
        long waitMs = 100;
        System.out.println("=== Demo A: " + taskCount + " I/O tasks, each waits " + waitMs + "ms ===");

        // 1) A "traditional" bounded platform thread pool (a common server setup).
        ExecutorService platformPool = Executors.newFixedThreadPool(200);
        long a = runAll(platformPool, taskCount, waitMs);
        System.out.printf("  platform pool (200 threads)   -> %6d ms%n", a);

        // 2) One VIRTUAL thread per task — no pool sizing to guess at.
        ExecutorService virtualExec = Executors.newVirtualThreadPerTaskExecutor();
        long b = runAll(virtualExec, taskCount, waitMs);
        System.out.printf("  virtual-thread-per-task       -> %6d ms%n", b);

        System.out.printf("  -> virtual was ~%.0fx faster: all tasks waited at once, not in batches.%n%n",
                (double) a / Math.max(b, 1));
    }

    /** Submit taskCount tasks (each sleeps waitMs) to the executor, wait for all, return ms. */
    static long runAll(ExecutorService exec, int taskCount, long waitMs) throws InterruptedException {
        AtomicInteger done = new AtomicInteger();
        long t0 = System.currentTimeMillis();
        try (exec) {                                  // try-with-resources: close() awaits all tasks (Java 19+)
            for (int i = 0; i < taskCount; i++) {
                exec.submit(() -> {
                    try { Thread.sleep(waitMs); }      // the simulated I/O wait
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    done.incrementAndGet();
                });
            }
        } // <- close() blocks here until every submitted task has finished
        long ms = System.currentTimeMillis() - t0;
        if (done.get() != taskCount) System.out.println("  (warning: only " + done.get() + " finished)");
        return ms;
    }

    /** Launch a huge number of virtual threads at once to show how cheap they are. */
    static void demoRawScale() throws InterruptedException {
        int count = 500_000;
        System.out.println("=== Demo B: " + count + " virtual threads at once, each sleeps 100ms ===");
        AtomicInteger done = new AtomicInteger();

        long t0 = System.currentTimeMillis();
        // newVirtualThreadPerTaskExecutor makes a brand-new virtual thread for each task.
        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < count; i++) {
                exec.submit(() -> {
                    try { Thread.sleep(Duration.ofMillis(100)); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    done.incrementAndGet();
                });
            }
        } // wait for all 500,000 to finish
        long ms = System.currentTimeMillis() - t0;

        System.out.printf("  %d virtual threads finished in %d ms%n", done.get(), ms);
        System.out.println("  (500,000 PLATFORM threads would need ~500 GB of stack — impossible.)");
        System.out.println("\nTakeaway: I/O-bound + virtual threads = simple blocking code at massive scale.");
    }
}
