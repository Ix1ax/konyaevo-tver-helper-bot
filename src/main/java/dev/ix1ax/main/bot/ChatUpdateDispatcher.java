package dev.ix1ax.main.bot;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Один чат сохраняет порядок действий; разные чаты обрабатываются параллельно. */
public final class ChatUpdateDispatcher implements AutoCloseable {
    private final ThreadPoolExecutor[] workers;
    public ChatUpdateDispatcher(int concurrency) {
        workers=new ThreadPoolExecutor[Math.max(4, Math.min(64, concurrency))];
        AtomicInteger number=new AtomicInteger();
        for (int i=0; i<workers.length; i++) {
            workers[i]=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(512), task -> {
                Thread thread=new Thread(task,"bot-chat-"+number.incrementAndGet()); thread.setDaemon(true); return thread;
            }, (task, executor) -> {
                if (executor.isShutdown()) throw new RejectedExecutionException("Бот остановлен");
                // При переполнении ждём свободного места, не теряя нажатия и не накапливая память.
                try { executor.getQueue().put(task); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RejectedExecutionException(e); }
            });
            workers[i].allowCoreThreadTimeOut(true);
        }
    }
    public void dispatch(long chatId, Runnable task) {
        workers[Math.floorMod(Long.hashCode(chatId),workers.length)].execute(task);
    }
    @Override public void close() { for (ThreadPoolExecutor worker: workers) worker.shutdown(); }
}
