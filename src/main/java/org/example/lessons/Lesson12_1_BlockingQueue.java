package org.example.lessons;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LESSON 12.1 — The producer-consumer pattern with BlockingQueue
 *
 * THE PROBLEM this pattern solves:
 *   One (or many) threads PRODUCE work — incoming chat messages, web requests, jobs.
 *   Other threads CONSUME it — save to DB, deliver to a socket, process. Producers and
 *   consumers run at DIFFERENT speeds. You need a safe place to hand work between them
 *   without (a) racing on a shared list, or (b) busy-waiting ("is there work yet? no?
 *   ...is there work yet?") burning CPU.
 *
 * Lessons 5–6 showed a plain ArrayList shared between threads is a race waiting to happen,
 * and you'd have to synchronize every add/remove yourself. A BlockingQueue is a queue
 * that is ALREADY thread-safe AND knows how to WAIT:
 *
 *   queue.put(item)  -> if the queue is FULL, the producer BLOCKS (sleeps) until space frees.
 *   queue.take()     -> if the queue is EMPTY, the consumer BLOCKS until an item arrives.
 *
 * No locks to write, no busy-wait loop, no lost wakeups. The queue parks and wakes threads
 * for you. This is the backbone of nearly every server: a request comes in -> put on a
 * queue -> a worker takes it. (Java's own thread pools from Lesson 8 are exactly this:
 * submit() = put, the workers = take in a loop.)
 *
 * The BOUNDED size is the secret weapon: it's BACKPRESSURE. If consumers fall behind, the
 * queue fills, and put() blocks the producer — automatically slowing intake to a rate the
 * consumers can handle, instead of piling up unbounded work until you run out of memory.
 *
 * This file shows ONE producer + ONE consumer, with the consumer deliberately slower, so
 * you can WATCH the queue fill to its cap and the producer get throttled.
 */
public class Lesson12_1_BlockingQueue {

    public static void main(String[] args) throws InterruptedException {
        // Capacity 5: at most 5 items can wait in the queue at once.
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(5);
        int totalItems = 12;
        AtomicInteger produced = new AtomicInteger();
        AtomicInteger consumed = new AtomicInteger();

        System.out.println("Queue capacity = 5. Producer is FAST, consumer is SLOW.\n");

        // --- PRODUCER: makes 12 items quickly (≈40ms apart) ---
        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= totalItems; i++) {
                    String item = "msg-" + i;
                    long t0 = System.currentTimeMillis();
                    queue.put(item);                       // BLOCKS here when the queue is full
                    long waited = System.currentTimeMillis() - t0;
                    produced.incrementAndGet();
                    System.out.printf("  PRODUCED %-7s (queue now ~%d/5)%s%n",
                            item, queue.size(),
                            waited > 20 ? "   <- producer was BLOCKED " + waited + "ms (backpressure!)" : "");
                    Thread.sleep(40);                      // producer is quick
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer");

        // --- CONSUMER: processes each item slowly (≈150ms each) ---
        Thread consumer = new Thread(() -> {
            try {
                while (consumed.get() < totalItems) {
                    String item = queue.take();            // BLOCKS here when the queue is empty
                    Thread.sleep(150);                     // consumer is slow (heavy work)
                    consumed.incrementAndGet();
                    System.out.printf("            CONSUMED %-7s (queue now ~%d/5)%n", item, queue.size());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        long t0 = System.currentTimeMillis();
        consumer.start();
        producer.start();

        producer.join();
        consumer.join();
        long ms = System.currentTimeMillis() - t0;

        System.out.printf("%nDone. produced=%d consumed=%d in %d ms.%n",
                produced.get(), consumed.get(), ms);
        System.out.println("\nNotice: the producer raced ahead, filled the queue to 5, then got");
        System.out.println("BLOCKED on put() — throttled to the consumer's pace. That's backpressure,");
        System.out.println("and you wrote ZERO lock code to get it. put()/take() did all the waiting.");

        offerAndPollNote(queue);
    }

    /**
     * Quick note on the NON-blocking cousins, for when you don't want to wait forever:
     *   offer(item, timeout) -> try to add; give up after a timeout, return false.
     *   poll(timeout)        -> try to take; return null if nothing arrives in time.
     * Useful for "drop the message if we can't enqueue within 1s" or shutdown timeouts.
     */
    static void offerAndPollNote(BlockingQueue<String> queue) throws InterruptedException {
        System.out.println("\n--- bonus: offer/poll (bounded waiting instead of forever) ---");
        boolean added = queue.offer("late", 100, TimeUnit.MILLISECONDS);  // queue is empty now -> succeeds fast
        System.out.println("  offer(\"late\", 100ms) returned " + added);
        String got = queue.poll(100, TimeUnit.MILLISECONDS);              // one item present -> returns it
        System.out.println("  poll(100ms) returned       " + got);
        String none = queue.poll(100, TimeUnit.MILLISECONDS);            // now empty -> waits 100ms, gives up
        System.out.println("  poll(100ms) on empty queue returned " + none + " (gave up after the timeout)");
    }
}
