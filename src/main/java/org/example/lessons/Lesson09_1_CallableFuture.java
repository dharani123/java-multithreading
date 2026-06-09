package org.example.lessons;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * LESSON 9.1 — Callable & Future (tasks that RETURN a value or throw)
 *
 * In Lesson 8 we submitted Runnables. A Runnable's run() is `void` — it can't return
 * a result and can't throw checked exceptions. So "go fetch this and give me the answer"
 * was impossible; we cheated by writing into a shared array (`sink`).
 *
 * CALLABLE fixes that. It's like Runnable but its single method RETURNS a value:
 *     Runnable:  void  run()
 *     Callable:  V     call() throws Exception      // returns V, may throw
 *
 * But there's a timing problem: the task runs on ANOTHER thread, in the future. When you
 * submit it, the answer isn't ready yet. So submit() hands you a FUTURE<V> immediately —
 * think of it as a CLAIM TICKET / receipt for a result that will exist later.
 *
 *     Future<Integer> f = pool.submit(callable);  // returns NOW, task runs in background
 *     int answer = f.get();                        // BLOCKS here until the result is ready
 *
 * Future's key methods:
 *     get()              -> wait (block) until done, then return the value
 *     get(timeout, unit) -> wait up to a limit, else throw TimeoutException
 *     isDone()           -> true if finished (success, exception, or cancelled) — no blocking
 *     cancel(mayInterrupt) -> try to cancel the task
 *
 * EXCEPTIONS: if the task throws, get() re-throws it wrapped in ExecutionException.
 * The error travels from the worker thread back to YOU at the get() call. Powerful:
 * you don't lose exceptions that happen on other threads.
 *
 * THE PARALLEL PATTERN (important!):
 *   submit ALL tasks first (they start running concurrently), THEN get() them.
 *   If you submit-then-immediately-get one at a time, you serialize them — no parallelism.
 */
public class Lesson09_1_CallableFuture {

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);

        singleResult(pool);
        parallelResults(pool);
        exceptionTravels(pool);
        timeoutDemo(pool);

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
    }

    /** A Callable returns a value; the Future is the receipt we redeem with get(). */
    static void singleResult(ExecutorService pool) throws Exception {
        System.out.println("=== 1) One Callable that returns a value ===");

        Callable<Integer> task = () -> {
            Thread.sleep(300);          // pretend this takes work (a calculation, a fetch)
            return 6 * 7;
        };

        Future<Integer> future = pool.submit(task);   // returns immediately
        System.out.println("  submitted. isDone right away? " + future.isDone());
        System.out.println("  calling get() — this blocks until the answer is ready...");
        int answer = future.get();                    // waits ~300ms, then returns 42
        System.out.println("  got the answer: " + answer + "\n");
    }

    /**
     * The real win: run 5 "slow" tasks AT THE SAME TIME. Each sleeps 500ms.
     * Serial would be ~2500ms; submitting all then getting all is ~500ms.
     */
    static void parallelResults(ExecutorService pool) throws Exception {
        System.out.println("=== 2) Five tasks in parallel, collect all results ===");

        long t0 = System.currentTimeMillis();

        // Phase 1: submit them all -> all 5 start running now (pool has 4 workers).
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            final int n = i;
            futures.add(pool.submit(() -> {
                Thread.sleep(500);      // simulated slow work
                return n * n;           // return n squared
            }));
        }

        // Phase 2: now collect. get() blocks, but they're all already running together.
        int sum = 0;
        for (Future<Integer> f : futures) {
            sum += f.get();
        }

        long ms = System.currentTimeMillis() - t0;
        System.out.println("  sum of squares 1..5 = " + sum + " (expected 55)");
        System.out.println("  took ~" + ms + " ms — 5 tasks of 500ms ran together, not 2500ms.\n");
    }

    /** An exception on the worker thread comes back to us, wrapped, at get(). */
    static void exceptionTravels(ExecutorService pool) {
        System.out.println("=== 3) Exceptions travel back through get() ===");

        Future<Integer> future = pool.submit(() -> {
            if (true) throw new IllegalStateException("boom on the worker thread");
            return 1;
        });

        try {
            future.get();
        } catch (ExecutionException e) {
            // get() wraps the real cause inside ExecutionException
            System.out.println("  caught ExecutionException, real cause = " + e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println();
    }

    /** get(timeout) gives up waiting instead of blocking forever. */
    static void timeoutDemo(ExecutorService pool) {
        System.out.println("=== 4) get() with a timeout ===");

        Future<String> future = pool.submit(() -> {
            Thread.sleep(2000);         // takes 2s
            return "finally done";
        });

        try {
            String r = future.get(300, TimeUnit.MILLISECONDS); // only willing to wait 300ms
            System.out.println("  got: " + r);
        } catch (java.util.concurrent.TimeoutException e) {
            System.out.println("  timed out after 300ms — the task is still running in the pool.");
            future.cancel(true);        // ask to cancel it (interrupts the sleeping worker)
            System.out.println("  asked to cancel it. cancelled? " + future.isCancelled());
        } catch (Exception e) {
            System.out.println("  other error: " + e);
        }
        System.out.println();
    }
}
