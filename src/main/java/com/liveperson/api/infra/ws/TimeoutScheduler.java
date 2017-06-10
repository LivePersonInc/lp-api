/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.liveperson.api.infra.ws;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author eitanya
 */
public class TimeoutScheduler {
    private static final ScheduledExecutorService scheduler
            = Executors.newScheduledThreadPool(
                    1,
                    new ThreadFactory() {
                AtomicInteger i = new AtomicInteger();
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    t.setName("failAfter-" + i.incrementAndGet());
                    return t;
                }
            });
    
    public static CompletableFuture<Void> withIn(Duration d) {
        CompletableFuture<Void> cf = new CompletableFuture<>();
        ScheduledFuture<Boolean> schedule = scheduler.schedule(()->cf.complete(null), 
                d.toMillis(), TimeUnit.MILLISECONDS);        
        return cf;
    }
    
}
