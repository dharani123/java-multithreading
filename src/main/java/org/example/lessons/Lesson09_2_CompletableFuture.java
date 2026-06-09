package org.example.lessons;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * LESSON 9.2 — CompletableFuture (chain async work WITHOUT blocking)
 *
 * Future (9.1) has one big weakness: the only way to use its result is get(), which
 * BLOCKS. If you want to take a result and do a follow-up step, you must:
 *     int a = f.get();        // block the current thread, wasting it
 *     ... then do step 2 ...
 * You can't say "WHEN this finishes, automatically run that next" — you have to sit and
 * wait. With many dependent steps this gets ugly and wastes threads.
 *
 * CompletableFuture (CF) is a Future you can BUILD A PIPELINE on. You describe the
 * steps and how they connect; each step runs automatically when its input is ready.
 * Nobody blocks in the middle.
 *
 * STARTING a pipeline:
 *   CompletableFuture.supplyAsync(() -> value)   // run this in the background, produces a value
 *   CompletableFuture.runAsync(() -> {...})      // background task with no result
 *
 * CHAINING (the core verbs):
 *   .thenApply(fn)     -> transform the result: T -> R   (like map)
 *   .thenAccept(fn)    -> consume the result: T -> void  (a final side-effect)
 *   .thenCompose(fn)   -> chain ANOTHER async step: T -> CompletableFuture<R> (flatMap)
 *   .thenCombine(other, fn) -> wait for TWO CFs, combine their results
 *   .exceptionally(fn) -> recover from a failure anywhere upstream
 *   .thenApplyAsync(..., pool) -> run the step on a chosen pool / another thread
 *
 * Each stage hands its output to the next automatically. The main thread is free the
 * whole time — it only blocks at the very end (join()) if it needs the final answer.
 */
public class Lesson09_2_CompletableFuture {

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);

        simpleChain(pool);
        twoInParallelThenCombine(pool);
        recoverFromError();
        composeDependentCalls(pool);

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
    }

    /** Produce a value, then transform it through stages — no get() in the middle. */
    static void simpleChain(ExecutorService pool) {
        System.out.println("=== 1) A simple async pipeline ===");

        CompletableFuture<String> pipeline =
            CompletableFuture
                .supplyAsync(() -> {                 // stage 1: produce a number (background)
                    sleep(200);
                    System.out.println("  [stage1] produced 10 on " + thread());
                    return 10;
                }, pool)
                .thenApply(n -> {                    // stage 2: transform 10 -> 20
                    System.out.println("  [stage2] doubling on " + thread());
                    return n * 2;
                })
                .thenApply(n -> "result = " + n);    // stage 3: transform 20 -> a String

        // main thread did NOT block during the stages. Block only now for the final value.
        System.out.println("  final: " + pipeline.join() + "\n");
    }

    /** Run two independent calls in parallel, then combine when BOTH are done. */
    static void twoInParallelThenCombine(ExecutorService pool) {
        System.out.println("=== 2) Two in parallel, then combine ===");
        long t0 = System.currentTimeMillis();

        CompletableFuture<Integer> price = CompletableFuture.supplyAsync(() -> {
            sleep(500); return 100;                  // e.g. fetch a price (500ms)
        }, pool);

        CompletableFuture<Integer> tax = CompletableFuture.supplyAsync(() -> {
            sleep(500); return 18;                   // e.g. fetch tax rate (500ms), at same time
        }, pool);

        CompletableFuture<Integer> total = price.thenCombine(tax, (p, t) -> p + t);

        System.out.println("  total = " + total.join()
                + "  (took ~" + (System.currentTimeMillis() - t0) + " ms — both ran together)\n");
    }

    /** A failure anywhere upstream is caught by exceptionally(), which supplies a fallback. */
    static void recoverFromError() {
        System.out.println("=== 3) Recover from a failure ===");

        String result = CompletableFuture
            .supplyAsync(() -> {
                if (true) throw new RuntimeException("service unavailable");
                return "real data";
            })
            .thenApply(s -> s.toUpperCase())          // skipped, because upstream failed
            .exceptionally(ex -> {                     // catches it, returns a fallback value
                System.out.println("  upstream failed: " + ex.getMessage() + " -> using fallback");
                return "DEFAULT";
            })
            .join();

        System.out.println("  ended with: " + result + "\n");
    }

    /**
     * thenCompose: stage 2 itself returns a CompletableFuture (a second async call that
     * depends on the first's result). Compose flattens it so you don't get CF<CF<...>>.
     */
    static void composeDependentCalls(ExecutorService pool) {
        System.out.println("=== 4) Dependent async calls with thenCompose ===");

        CompletableFuture<String> pipeline =
            lookupUserId("dharani")                          // async: name -> id
                .thenCompose(id -> fetchEmailForId(id, pool)); // async: id -> email (needs the id)

        System.out.println("  email: " + pipeline.join() + "\n");
    }

    // Two fake "async services" returning CompletableFutures.
    static CompletableFuture<Integer> lookupUserId(String name) {
        return CompletableFuture.supplyAsync(() -> { sleep(200); return name.length() * 7; });
    }
    static CompletableFuture<String> fetchEmailForId(int id, ExecutorService pool) {
        return CompletableFuture.supplyAsync(() -> { sleep(200); return "user" + id + "@example.com"; }, pool);
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
    static String thread() { return Thread.currentThread().getName(); }
}
