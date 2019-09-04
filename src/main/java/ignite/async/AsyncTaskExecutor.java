package ignite.async;

import java.util.concurrent.CompletableFuture;
import org.apache.ignite.lang.IgniteRunnable;
import ignite.async.impl.AsyncTaskExecutorImpl;

public interface AsyncTaskExecutor extends AutoCloseable {
    CompletableFuture<Void> execute(IgniteRunnable task);

    static AsyncTaskExecutor create() {
        return new AsyncTaskExecutorImpl();
    }
}
