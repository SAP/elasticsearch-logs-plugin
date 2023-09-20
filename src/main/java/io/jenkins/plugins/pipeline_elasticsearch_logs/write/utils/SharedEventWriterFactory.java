package io.jenkins.plugins.pipeline_elasticsearch_logs.write.utils;

import java.io.IOException;
import java.util.Map;

import javax.annotation.Nonnull;

import io.jenkins.plugins.pipeline_elasticsearch_logs.write.EventWriter;

/**
 * A decorator for {@link EventWriterFactory} that provides its clients with
 * proxies of a shared {@link EventWriter} instance which ensure that the shared
 * instance doesn't get closed as long as it is still in use.
 * <p>
 * Each call of {@link createEventWriter} increases a reference counter and
 * returns a proxy for the shared instance. If the reference counter was zero,
 * i.e. no unclosed proxy objects exist, than a new shared instance gets created
 * by calling the underlying factory.
 * </p>
 * <p>
 * When {@link EventWriterProxy#close()} is called, the reference counter is
 * decreased. If it reaches zero, i.e. the last proxy was closed, also the
 * shared instance gets closed.
 * </p>
 */
public final class SharedEventWriterFactory implements EventWriterFactory {

    private final EventWriterFactory factory;

    private EventWriter sharedInstance;
    private int refCount = 0;

    /**
     * @param factory The underlying factory.
     */
    public SharedEventWriterFactory(@Nonnull EventWriterFactory factory) {
        this.factory = factory;
    }

    @Override
    public synchronized EventWriter createEventWriter() {
        if (this.refCount == 0) {
            this.sharedInstance = factory.createEventWriter();
        }
        this.refCount++;
        return new EventWriterProxy();
    }

    protected synchronized void releaseEventWriter() throws Exception {
        if (this.refCount == 0) {
            // should never happen
            throw new IllegalStateException("releaseEventWriter() called although ref count is zero");
        }

        this.refCount--;
        if (this.refCount == 0) {
            EventWriter w = this.sharedInstance;
            this.sharedInstance = null;
            w.close();
        }
    }

    private class EventWriterProxy implements EventWriter {

        private volatile boolean isClosed = false;

        @Override
        public void push(Map<String, Object> data) throws IOException {
            if (this.isClosed) {
                throw new IllegalStateException("object is closed already");
            }
            SharedEventWriterFactory.this.sharedInstance.push(data);
        }

        @Override
        public synchronized void close() throws Exception {
            if (this.isClosed) {
                throw new IllegalStateException("object is closed already");
            }

            this.isClosed = true;
            SharedEventWriterFactory.this.releaseEventWriter();
        }
    }
}
