/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ignite.async;

import java.util.Collections;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.lang.IgniteRunnable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class Tests {
    private static final int SERVER_CNT = 4;

    private static final int TASK_CNT = 10_000;

    private AsyncTaskExecutor executor;

    private Set<CompletableFuture<Void>> started;

    private Set<CompletableFuture<Void>> completed;

    @Before
    public void before() throws Exception {
        for (int i = 0; i < SERVER_CNT; i++)
            startServer(i);

        started = Collections.newSetFromMap(new ConcurrentHashMap<>());
        completed = Collections.newSetFromMap(new ConcurrentHashMap<>());

        executor = AsyncTaskExecutor.create();
    }

    @After
    public void after() throws Exception {
        executor.close();

        for (int i = 0; i < SERVER_CNT; i++)
            Ignition.stop("server-" + i, false);
    }

    @Test
    public void test() throws Exception {
        for (int i = 0; i < TASK_CNT; i++) {
            CompletableFuture<Void> future = executor.execute(new TestTask());

            started.add(future);

            future.thenApply(o -> completed.add(future));
        }

        Assert.assertEquals(TASK_CNT, started.size());

        Thread restartThread = new Thread(() -> {
            Random rnd = new Random();

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(2_000);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }

                restartServer(rnd.nextInt(SERVER_CNT));
            }
        }, "restart-thread");

        restartThread.start();

        while (true) {
            try {
                Thread.sleep(1_000);
            }
            catch (InterruptedException ignored) {
                break;
            }

            int startedCnt = started.size();
            int completedCnt = completed.size();

            System.out.println("Stat [started=" + startedCnt + ", completed=" + completedCnt + ']');

            Assert.assertTrue(completedCnt <= startedCnt);

            if (completedCnt == startedCnt)
                break;
        }

        restartThread.interrupt();
        restartThread.join();

        Assert.assertEquals(started, completed);

        for (CompletableFuture<Void> future : started)
            Assert.assertTrue(future.isDone());
    }

    private void startServer(int idx) {
        IgniteConfiguration cfg = Ignition.loadSpringBean("config/ignite-server.xml", "ignite.cfg");

        cfg.setIgniteInstanceName("server-" + idx);

        Ignition.start(cfg);
    }

    private void restartServer(int idx) {
        Ignition.stop("server-" + idx, true);

        startServer(idx);
    }

    private static class TestTask implements IgniteRunnable {
        @Override public void run() {
            try {
                Thread.sleep(new Random().nextInt(1_000) + 1_000);
            }
            catch (InterruptedException ignored) {
            }
        }
    }
}
