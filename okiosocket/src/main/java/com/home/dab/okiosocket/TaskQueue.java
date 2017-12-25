package com.home.dab.okiosocket;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Deque;
import java.util.LinkedList;

/**
 * Created by dab on 2017/9/11 0011 15:39
 */

public class TaskQueue<T> {
    private final Deque<T> deque;

    TaskQueue() {
        this.deque = new LinkedList<>();
    }

    /**
     * 添加一个任务
     *
     * @param task
     * @return
     */
    TaskQueue addTask(@NonNull T task) {
        synchronized (this.deque) {
            this.deque.push(task);
            this.deque.notifyAll();
        }

        return this;
    }

    /**
     * 移除任务
     *
     * @param task
     * @return
     */
    boolean remove(T task) {
        synchronized (this.deque) {
            return this.deque.remove(task);
        }
    }

    /**
     * 取出第一个并移除
     * 如果没有,就阻塞线程
     *
     * @return
     */
    T popTask() {
        synchronized (this.deque) {
            while (this.deque.isEmpty()) {
                try {
                    this.deque.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            return this.deque.pollFirst();
        }
    }

    /**
     * 取出第一个并移除,
     * 如果没有,就阻塞,如果ms毫秒内仍然没有,就返回null
     *
     * @param ms
     * @return
     */
    @Nullable
    public T popTask(long ms) {
        synchronized (this.deque) {
            if (this.deque.isEmpty()) {
                try {
                    this.deque.wait(ms);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (this.deque.isEmpty()) {
                return null;//time out
            }
            return this.deque.pollFirst();
        }
    }


    void clear() {
        synchronized (this.deque) {
            this.deque.clear();
        }
    }
}
