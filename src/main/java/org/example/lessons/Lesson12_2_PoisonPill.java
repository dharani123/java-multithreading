package org.example.lessons;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LESSON 12.2 — Many producers, many consumers, and a CLEAN shutdown (the poison pill)
 *
 * 12.1 had one producer and one consumer. Real systems have N of each: several threads
 * accepting chat messages, a pool of workers delivering them. A BlockingQueue handles
 * that out of the box — it's safe for ANY number of putters and takers at once. Here we
 * run 3 producers and 4 consumers sharing one queue and watch the totals come out exact.
 *
 * THE HARD PART is stopping. Consumers sit in `while (true) { queue.take(); }`. take()
 * BLOCKS forever when the queue is empty — so when the producers are done, the consumers
 * are stuck waiting for work that will never come. How do you tell them "we're finished,
 * go home" without yanking threads or polling a flag?
 *
 * THE POISON PILL pattern:
 *   Put a special sentinel item on the queue that means "STOP". A consumer that take()s a
 *   poison pill breaks out of its loop and exits. To stop N consumers, put N pills — one
 *   per consumer — AFTER all the real work. Because the queue is FIFO and the pills go in
 *   last, every real item is guaranteed to be consumed before any consumer sees its pill.
 *
 * This is graceful shutdown: no Thread.stop(), no interrupt() to untangle, no busy flag —
 * the shutdown signal travels through the SAME queue as the work, so it can't overtake it.
 *
 * (Java's ExecutorService.shutdown() does essentially this internally. Now you can see the
 * machinery, and you can build it yourself when a plain pool isn't enough.)
 */
public class Lesson12_2_PoisonPill {

    // A unique sentinel object. Reference identity (==) is how we recognize it — no real
    // message could ever equal this exact instance.
    private static final String POISON_PILL = "__STOP__";

    public static void main(String[] args) throws InterruptedException {
        int producerCount = 3;
        int consumerCount = 4;
        int itemsPerProducer = 10;                 // 3 x 10 = 30 real items total

        // Unbounded LinkedBlockingQueue here (no fixed cap) — contrast with 12.1's bounded
        // ArrayBlockingQueue. Pick bounded when you want backpressure, unbounded when you
        // trust the producers not to outrun memory.
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();

        AtomicInteger totalProduced = new AtomicInteger();
        AtomicInteger totalConsumed = new AtomicInteger();
        // Per-consumer tally, to prove the work spread across all 4 workers.
        AtomicInteger[] perConsumer = new AtomicInteger[consumerCount];
        for (int i = 0; i < consumerCount; i++) perConsumer[i] = new AtomicInteger();

        // --- Start the consumers first so they're ready and waiting on take(). ---
        Thread[] consumers = new Thread[consumerCount];
        for (int c = 0; c < consumerCount; c++) {
            final int id = c;
            consumers[c] = new Thread(() -> {
                try {
                    while (true) {
                        String item = queue.take();              // blocks until something arrives
                        if (item == POISON_PILL) {               // == identity check: our stop signal
                            System.out.printf("  consumer-%d got the poison pill -> exiting%n", id);
                            return;                              // leave the loop & end the thread
                        }
                        Thread.sleep(20);                        // pretend to process the message
                        perConsumer[id].incrementAndGet();
                        totalConsumed.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "consumer-" + c);
            consumers[c].start();
        }

        // --- Start the producers. ---
        Thread[] producers = new Thread[producerCount];
        for (int p = 0; p < producerCount; p++) {
            final int id = p;
            producers[p] = new Thread(() -> {
                try {
                    for (int i = 1; i <= itemsPerProducer; i++) {
                        queue.put("p" + id + "-item" + i);       // safe with many producers at once
                        totalProduced.incrementAndGet();
                        Thread.sleep(10);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "producer-" + id);
            producers[p].start();
        }

        // --- Wait for every producer to finish putting all real work. ---
        for (Thread p : producers) p.join();
        System.out.println("All producers done. Total produced = " + totalProduced.get());

        // --- NOW add one poison pill per consumer, after all real items. ---
        // FIFO guarantees these sit behind every real message, so nothing is lost.
        for (int i = 0; i < consumerCount; i++) {
            queue.put(POISON_PILL);
        }

        // --- Wait for all consumers to drain the queue and hit their pill. ---
        for (Thread c : consumers) c.join();

        System.out.println("\nAll consumers exited cleanly.");
        System.out.println("Total consumed = " + totalConsumed.get()
                + " (expected " + (producerCount * itemsPerProducer) + ")");
        System.out.print("Work split across consumers: ");
        for (int i = 0; i < consumerCount; i++) {
            System.out.print("c" + i + "=" + perConsumer[i].get() + "  ");
        }
        System.out.println("\n\nTakeaway: one thread-safe queue carries BOTH the work and the");
        System.out.println("shutdown signal. N pills stop N consumers, after every real item — a");
        System.out.println("clean, race-free shutdown with no interrupts and no shared stop-flag.");
    }
}
