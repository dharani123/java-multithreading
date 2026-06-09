package org.example.lessons;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LESSON 8 — ExecutorService & thread pools (stop creating threads by hand)
 *
 * Until now EVERY lesson did the same chore:
 *     new Thread(task); start(); ... join();   // create, launch, wait — manually
 *
 * That has real problems once you leave toy examples:
 *   1. CREATING a thread is expensive (≈1 MB stack + an OS thread each). Spinning up
 *      a fresh thread PER task and throwing it away is wasteful.
 *   2. UNBOUNDED: 10,000 incoming tasks => 10,000 threads => the machine falls over.
 *   3. You hand-write the same start()/join() bookkeeping every time.
 *
 * THE FIX — a THREAD POOL:
 *   Create a small set of worker threads ONCE, then keep handing them tasks. A worker
 *   finishes a task, then immediately picks up the next from a queue. Threads are
 *   REUSED, not recreated. This is the standard way real Java does concurrency.
 *
 *   ExecutorService pool = Executors.newFixedThreadPool(4);
 *   pool.submit(task);     // drop a task in the queue; some worker will run it
 *   pool.shutdown();       // stop accepting new tasks; finish what's queued
 *   pool.awaitTermination(...);  // wait for the queue to drain
 *
 * This lesson proves REUSE by printing which worker ran each task: you'll see only a
 * handful of distinct thread names handling MANY tasks.
 *
 * Common pool factories (all return an ExecutorService):
 *   newFixedThreadPool(n)   -> exactly n workers (great for CPU-bound: n ≈ cores)
 *   newCachedThreadPool()   -> grows/shrinks on demand, reuses idle threads (I/O-ish)
 *   newSingleThreadExecutor() -> one worker; tasks run one-at-a-time in order
 *   newVirtualThreadPerTaskExecutor() (Java 21+) -> a cheap virtual thread per task
 */
public class Lesson08_ExecutorService {

    public static void main(String[] args) throws InterruptedException {
        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("Cores: " + cores + "\n");

        demoReuse();
        demoThroughput(cores);
    }

    /**
     * Submit 12 quick tasks to a pool of only 4 workers and print who ran each.
     * You'll see just 4 distinct worker names — the SAME threads reused 12 times.
     */
    static void demoReuse() throws InterruptedException {
        System.out.println("=== Reuse demo: 12 tasks, pool of 4 workers ===");

        ExecutorService pool = Executors.newFixedThreadPool(4);

        for (int i = 1; i <= 12; i++) {
            final int taskId = i;
            pool.submit(() -> {
                String who = Thread.currentThread().getName();
                System.out.printf("  task %2d ran on %s%n", taskId, who);
                sleep(50); // pretend the task takes a little time
            });
        }

        // Orderly shutdown: refuse new tasks, let the 12 queued ones finish, then wait.
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        System.out.println("  -> Notice only 4 distinct worker names did all 12 tasks.\n");
    }

    /**
     * Same fixed CPU work as Lesson 7, but submitted to a pool instead of hand-rolling
     * threads. Shows the pool gives the same parallel speedup with far less ceremony.
     */
    static void demoThroughput(int cores) throws InterruptedException {
        System.out.println("=== Throughput: 64 CPU chunks through pools of different sizes ===");
        int chunks = 64;
        long workPerChunk = 5_000_000L;

        for (int poolSize : new int[]{1, cores, 64}) {
            ExecutorService pool = Executors.newFixedThreadPool(poolSize);
            double[] sink = new double[chunks];
            AtomicInteger done = new AtomicInteger();

            long t0 = System.currentTimeMillis();
            for (int c = 0; c < chunks; c++) {
                final int idx = c;
                pool.submit(() -> {
                    double acc = 0;
                    long from = (long) idx * workPerChunk;
                    long to = from + workPerChunk;
                    for (long k = from; k < to; k++) acc += Math.sqrt(k) * Math.sin(k);
                    sink[idx] = acc;
                    done.incrementAndGet();
                });
            }
            pool.shutdown();
            pool.awaitTermination(60, TimeUnit.SECONDS);
            long ms = System.currentTimeMillis() - t0;

            System.out.printf("  pool size %3d -> %5d ms  (%d chunks done)%n", poolSize, ms, done.get());
        }
        System.out.println("\nTakeaway: define WORK as tasks, hand them to a pool, let it manage threads.");
        System.out.println("For CPU-bound work the pool size ≈ cores is the sweet spot (Lesson 7).");
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
