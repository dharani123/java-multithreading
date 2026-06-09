package org.example.lessons;

/**
 * LESSON 7 — CPU-bound vs I/O-bound work (the key to choosing thread counts)
 *
 * Every task a thread does is mostly one of two kinds:
 *
 *   CPU-BOUND : the thread is BUSY COMPUTING (math, parsing, compression).
 *               The core is doing real work every cycle.
 *   I/O-BOUND : the thread is mostly WAITING for something external — a network
 *               reply, disk read, database query. The core sits IDLE during the wait.
 *
 * WHY THIS DECIDES YOUR THREAD COUNT:
 *
 *   CPU-bound: a core can only compute one thread at a time. If you already have
 *              16 threads busy on 16 cores, adding more threads CAN'T compute faster —
 *              they just fight over the same cores (context-switching overhead).
 *              => sweet spot ≈ number of cores.
 *
 *   I/O-bound: while thread A waits for the network, its core is FREE. Thread B can
 *              use that idle core to start ITS request. So 100 threads can all be
 *              "waiting" at once on 16 cores. More threads = more overlap = huge win.
 *              => sweet spot can be MANY times the core count.
 *
 * This program runs BOTH workloads at 1, 16, and 100 threads and times them.
 * Watch how the two react in OPPOSITE ways to adding threads.
 */
public class Lesson07_CpuVsIoBound {

    public static void main(String[] args) throws InterruptedException {
        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("Cores: " + cores + "\n");

        // ---------------- CPU-BOUND ----------------
        // Fixed total amount of math, split across N threads.
        System.out.println("=== CPU-BOUND work (heavy math, total fixed) ===");
        System.out.println("Expectation: improves up to ~" + cores + " threads, then FLAT or WORSE.\n");
        for (int threads : new int[]{1, cores, 100}) {
            long ms = runCpuBound(threads, 200_000_000L);
            System.out.printf("  %4d threads -> %5d ms%n", threads, ms);
        }

        // ---------------- I/O-BOUND ----------------
        // 200 tasks, each "waits" 100ms (simulating a network/disk call), no real CPU.
        System.out.println("\n=== I/O-BOUND work (200 tasks, each waits 100ms) ===");
        System.out.println("Expectation: keeps getting FASTER with more threads, far past core count.\n");
        for (int threads : new int[]{1, cores, 200}) {
            long ms = runIoBound(threads, 200, 100);
            System.out.printf("  %4d threads -> %5d ms%n", threads, ms);
        }

        System.out.println("\nTakeaway:");
        System.out.println("  CPU-bound  -> threads ≈ cores. Extra threads can't add compute power.");
        System.out.println("  I/O-bound  -> threads >> cores. Idle cores get reused during waits.");
    }

    /** Split a fixed amount of CPU math across `threads` worker threads; return elapsed ms. */
    static long runCpuBound(int threads, long total) throws InterruptedException {
        long chunk = total / threads;
        Thread[] ts = new Thread[threads];
        double[] sink = new double[threads]; // each thread writes its own slot (no race)

        long t0 = System.currentTimeMillis();
        for (int i = 0; i < threads; i++) {
            final int idx = i;
            final long from = i * chunk;
            final long to = (i == threads - 1) ? total : from + chunk;
            ts[i] = new Thread(() -> {
                double acc = 0;
                for (long k = from; k < to; k++) acc += Math.sqrt(k) * Math.sin(k);
                sink[idx] = acc; // keep the JIT from deleting the loop
            });
            ts[i].start();
        }
        for (Thread t : ts) t.join();
        return System.currentTimeMillis() - t0;
    }

    /**
     * Run `taskCount` tasks that each sleep `waitMs` (simulating I/O), using `threads`
     * worker threads that pull tasks from a shared counter. Return elapsed ms.
     */
    static long runIoBound(int threads, int taskCount, long waitMs) throws InterruptedException {
        java.util.concurrent.atomic.AtomicInteger nextTask = new java.util.concurrent.atomic.AtomicInteger(0);
        Thread[] ts = new Thread[threads];

        long t0 = System.currentTimeMillis();
        for (int i = 0; i < threads; i++) {
            ts[i] = new Thread(() -> {
                // Each worker grabs tasks until they're all taken.
                while (nextTask.getAndIncrement() < taskCount) {
                    try {
                        Thread.sleep(waitMs); // <-- the "I/O wait": core is IDLE here
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
            ts[i].start();
        }
        for (Thread t : ts) t.join();
        return System.currentTimeMillis() - t0;
    }
}
