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

import javax.cache.Cache;
import org.apache.ignite.Ignite;
import org.apache.ignite.cache.CacheInterceptorAdapter;
import org.apache.ignite.lang.IgniteUuid;
import org.apache.ignite.resources.IgniteInstanceResource;

class AsyncTaskSubmitInterceptor extends CacheInterceptorAdapter<IgniteUuid, AsyncTask> {
    @IgniteInstanceResource
    private transient Ignite ignite;

    @Override public void onAfterPut(Cache.Entry<IgniteUuid, AsyncTask> entry) {
        IgniteUuid id = entry.getKey();
        AsyncTask task = entry.getValue();

        if (!task.isProcessed() && isPrimary(id))
            Util.submitTask(ignite, id, task.getTask());
    }

    private boolean isPrimary(IgniteUuid id) {
        return ignite.affinity("async-tasks").isPrimary(ignite.cluster().localNode(), id);
    }
}
