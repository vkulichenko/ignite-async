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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.MutableEntry;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.compute.ComputeJob;
import org.apache.ignite.compute.ComputeJobAdapter;
import org.apache.ignite.compute.ComputeJobResult;
import org.apache.ignite.compute.ComputeJobResultPolicy;
import org.apache.ignite.compute.ComputeTask;
import org.apache.ignite.compute.ComputeTaskSession;
import org.apache.ignite.compute.ComputeTaskSessionFullSupport;
import org.apache.ignite.lang.IgniteRunnable;
import org.apache.ignite.lang.IgniteUuid;
import org.apache.ignite.resources.IgniteInstanceResource;
import org.apache.ignite.resources.TaskSessionResource;
import org.jetbrains.annotations.Nullable;

@ComputeTaskSessionFullSupport
class AsyncTaskCompute implements ComputeTask<Object, Object> {
    public static final String ASYNC_TASK_ID_ATTR = "id";

    private final IgniteUuid id;

    private final IgniteRunnable impl;

    @IgniteInstanceResource
    private transient Ignite ignite;

    @TaskSessionResource
    private transient ComputeTaskSession session;

    AsyncTaskCompute(IgniteUuid id, IgniteRunnable impl) {
        this.id = id;
        this.impl = impl;
    }

    @Nullable @Override public Map<? extends ComputeJob, ClusterNode> map(List<ClusterNode> nodes, @Nullable Object arg)
        throws IgniteException {
        session.setAttribute(ASYNC_TASK_ID_ATTR, id);

        return Collections.singletonMap(
            new AsyncTaskComputeJob(id, impl),
            ignite.affinity("async-tasks").mapKeyToNode(id));
    }

    @Override public ComputeJobResultPolicy result(ComputeJobResult res, List<ComputeJobResult> received)
        throws IgniteException {
        return ComputeJobResultPolicy.WAIT;
    }

    @Nullable @Override public Object reduce(List<ComputeJobResult> results) throws IgniteException {
        return null;
    }

    private static class AsyncTaskComputeJob extends ComputeJobAdapter {
        private static final EntryProcessor<IgniteUuid, AsyncTask, Object> UPDATER = new StateUpdater();

        private final IgniteUuid id;

        private final IgniteRunnable impl;

        @IgniteInstanceResource
        private transient Ignite ignite;

        AsyncTaskComputeJob(IgniteUuid id, IgniteRunnable impl) {
            this.id = id;
            this.impl = impl;
        }

        @Override public Object execute() throws IgniteException {
            impl.run();

            ignite.<IgniteUuid, AsyncTask>cache("async-tasks").invoke(id, UPDATER);

            return null;
        }
    }

    private static class StateUpdater implements EntryProcessor<IgniteUuid, AsyncTask, Object> {
        @Override public Object process(MutableEntry<IgniteUuid, AsyncTask> entry, Object... args) {
            AsyncTask task = entry.getValue();

            if (task.markProcessed())
                entry.setValue(task);

            return null;
        }
    }
}
