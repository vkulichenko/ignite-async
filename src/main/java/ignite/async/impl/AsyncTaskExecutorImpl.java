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

package ignite.async.impl;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.cache.configuration.Factory;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryEventFilter;
import ignite.async.AsyncTaskExecutor;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.query.ContinuousQuery;
import org.apache.ignite.lang.IgniteRunnable;
import org.apache.ignite.lang.IgniteUuid;

public class AsyncTaskExecutorImpl implements AsyncTaskExecutor {
    private final Ignite ignite;

    private final IgniteCache<IgniteUuid, AsyncTask> cache;

    private final Map<IgniteUuid, CompletableFuture<Void>> futures = new ConcurrentHashMap<>();

    public AsyncTaskExecutorImpl() {
        ignite = Ignition.start("config/ignite-client.xml");

        cache = ignite.cache("async-tasks");

        ContinuousQuery<IgniteUuid, AsyncTask> qry = new ContinuousQuery<>();

        qry.setRemoteFilterFactory(new ContinuousQueryFilterFactory());

        qry.setLocalListener(events -> {
            for (CacheEntryEvent<? extends IgniteUuid, ? extends AsyncTask> event : events) {
                CompletableFuture<Void> future = futures.remove(event.getKey());

                if (future != null)
                    future.complete(null);
            }
        });

        cache.query(qry);
    }

    @Override public CompletableFuture<Void> execute(IgniteRunnable task) {
        IgniteUuid id = IgniteUuid.randomUuid();

        CompletableFuture<Void> future = new CompletableFuture<>();

        futures.put(id, future);

        future.thenRun(() -> futures.remove(id));

        cache.put(id, new AsyncTask(task));

        return future;
    }

    @Override public void close() {
        ignite.close();
    }

    private static class ContinuousQueryFilterFactory implements Factory<CacheEntryEventFilter<IgniteUuid, AsyncTask>> {
        @Override public CacheEntryEventFilter<IgniteUuid, AsyncTask> create() {
            return event -> event.getValue().isProcessed();
        }
    }
}
