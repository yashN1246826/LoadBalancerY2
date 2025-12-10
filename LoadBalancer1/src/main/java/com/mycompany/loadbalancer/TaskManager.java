/*package com.mycompany.loadbalancer;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TaskManager {
    private static final int MAX_CONCURRENT_TRANSFERS = 3; // Limit simultaneous transfers
    private static final Semaphore semaphore = new Semaphore(MAX_CONCURRENT_TRANSFERS);
    private static final PriorityBlockingQueue<Task> taskQueue = new PriorityBlockingQueue<>();
    private static final ScheduledExecutorService agingScheduler = Executors.newScheduledThreadPool(1);
    private static final ExecutorService executorService = Executors.newFixedThreadPool(MAX_CONCURRENT_TRANSFERS);

    // ✅ Start Aging Mechanism (Run every 5 seconds)
    static {
        agingScheduler.scheduleAtFixedRate(() -> {
            for (Task task : taskQueue) {
                task.increasePriority();
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    // ✅ Add Task to Queue
    public static void addTask(Task task) {
        taskQueue.offer(task);
    }

    // ✅ Process Tasks with Semaphore Control
    public static void startProcessing() {
        while (!taskQueue.isEmpty()) {
            try {
                semaphore.acquire(); // Control concurrency
                Task task = taskQueue.poll();
                if (task != null) {
                    executorService.execute(() -> {
                        try {
                            LoggerHelper.log("FILE_TRANSFER", "🚀 Processing " + task);
                            App.transferSingleFile(task.getSource(), task.getDestination(), task.getContainer());
                        } finally {
                            semaphore.release();
                        }
                    });
                }
            } catch (InterruptedException e) {
                LoggerHelper.log("ERROR", "❌ Task execution interrupted: " + e.getMessage());
            }
        }
    }
}

// ✅ Task Class with Aging Mechanism
class Task implements Comparable<Task> {
    private static final AtomicInteger counter = new AtomicInteger(0);
    private final int id;
    private final String source;
    private final String destination;
    private final String container;
    private int priority;

    public Task(String source, String destination, String container, int priority) {
        this.id = counter.incrementAndGet();
        this.source = source;
        this.destination = destination;
        this.container = container;
        this.priority = priority;
    }

    public void increasePriority() {
        this.priority = Math.max(0, this.priority - 1); // Lower value = Higher priority
    }

    public String getSource() {
        return source;
    }

    public String getDestination() {
        return destination;
    }

    public String getContainer() {
        return container;
    }

    @Override
    public int compareTo(Task other) {
        return Integer.compare(this.priority, other.priority);
    }

    @Override
    public String toString() {
        return "Task " + id + " [" + source + " -> " + destination + "] (Priority: " + priority + ")";
    }
}
*/