package com.customization.oaones.appsyn.msg.dingtalk.sub;

import com.customization.oaones.appsyn.common.OaonesLog;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 推送线程池：标准消息中心回调进入本队列后异步推送，不阻塞 OA 业务流程。
 *
 * <p>队列有界（默认 5000），队列满时由调用方降级为同步推送并在日志里记录，
 * 保证消息不丢。</p>
 */
public final class MsgPushQueue {

    private static final int QUEUE_SIZE = 5000;
    private static final int POOL_SIZE = 2;

    private static final BlockingQueue<Runnable> QUEUE = new LinkedBlockingQueue<Runnable>(QUEUE_SIZE);

    private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(
            POOL_SIZE, POOL_SIZE, 60L, TimeUnit.SECONDS, QUEUE, new ThreadFactory() {
        private final AtomicInteger SEQ = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "oaones-msg-push-" + SEQ.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    });

    private MsgPushQueue() {
    }

    /** 提交异步推送任务；返回 false 表示队列已满（调用方应降级同步处理）。 */
    public static boolean submit(Runnable task) {
        if (task == null) {
            return true;
        }
        try {
            POOL.execute(task);
            return true;
        } catch (RejectedExecutionException e) {
            OaonesLog.error("[msg] 推送队列已满，降级为同步推送", e);
            return false;
        }
    }

    /** 队列状态（页面展示用）。 */
    public static String status() {
        return "pool=" + POOL.getPoolSize() + ", active=" + POOL.getActiveCount()
                + ", queued=" + QUEUE.size() + ", completed=" + POOL.getCompletedTaskCount();
    }
}
