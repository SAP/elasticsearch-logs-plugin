package io.jenkins.plugins.pipeline_elasticsearch_logs.write.fluentd;

import static io.jenkins.plugins.pipeline_elasticsearch_logs.EventFieldNames.TIMESTAMP;
import static java.lang.String.format;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.komamitsu.fluency.EventTime;
import org.komamitsu.fluency.Fluency;
import org.komamitsu.fluency.RetryableException;
import org.komamitsu.fluency.fluentd.FluencyBuilderForFluentd;

import io.jenkins.plugins.pipeline_elasticsearch_logs.write.EventWriter;

/**
 * An {@link EventWriter} that sends events via Fluentd Forward Protocol.
 */
public class FluentdEventWriter implements EventWriter {

    private static final Logger LOGGER = Logger.getLogger(FluentdEventWriter.class.getName());

    private transient Fluency fluentd;
    private transient FluentdErrorHandler errorHandler;

    private ExecutorService executor = Executors.newCachedThreadPool();

    FluentdEventWriterConfig config;

    // mutex of push() and close(), with concurrent calls to push()
    ReadWriteLock lock = new ReentrantReadWriteLock();

    private boolean isClosed;

    FluentdEventWriter(@Nonnull FluentdEventWriterConfig config) {
        this.config = config;
        initFluentdLogger();
    }

    private void initFluentdLogger() {
        FluencyBuilderForFluentd builder = new FluencyBuilderForFluentd();

        builder.setAckResponseMode(true);
        builder.setSenderBaseRetryIntervalMillis(config.getSenderBaseRetryIntervalMillis());
        builder.setSenderMaxRetryIntervalMillis(config.getSenderMaxRetryIntervalMillis());
        builder.setSenderMaxRetryCount(config.getSenderMaxRetryCount());
        builder.setConnectionTimeoutMilli(config.getConnectionTimeoutMillis());
        builder.setReadTimeoutMilli(config.getReadTimeoutMillis());

        builder.setJvmHeapBufferMode(true);
        builder.setWaitUntilBufferFlushed(config.getMaxWaitSecondsUntilBufferFlushed());
        builder.setWaitUntilFlusherTerminated(config.getMaxWaitSecondsUntilFlusherTerminated());
        builder.setBufferChunkInitialSize(config.getBufferChunkInitialSize());
        builder.setBufferChunkRetentionSize(config.getBufferChunkRetentionSize());
        builder.setMaxBufferSize(config.getMaxBufferSize());
        builder.setBufferChunkRetentionTimeMillis(config.getBufferChunkRetentionTimeMillis());
        builder.setFlushAttemptIntervalMillis(config.getFlushAttemptIntervalMillis());

        errorHandler = new FluentdErrorHandler();
        builder.setErrorHandler(errorHandler);
        fluentd = builder.build(config.getHost(), config.getPort());
        LOGGER.finer(format("Created new %s, host=%s, port=%s, hashCode=%s",
                fluentd, config.getHost(), config.getPort(), fluentd.hashCode()));
    }

    /**
     * Logs the given object to Fluentd asynchronously.
     *
     * @param data The data to post
     * @throws IOException if something failed permanently
     */
    @Override
    public void push(Map<String, Object> data) throws IOException {
        try {
            this.lock.readLock().lock();
            failIfClosed();
            emitData(config.getTag(), data);
        }
        finally {
            this.lock.readLock().unlock();
        }
    }

    private void emitData(String tag, Map<String, Object> data) throws IOException {
        LOGGER.log(Level.FINEST, "Emitting log event: {0}", new Object[] { data });

        boolean isThreadInterrupted = false;
        try {
            // run Fluency.emit() in another thread to protect it from
            // thread interrupts by Jenkins.
            Future<Void> future = executor.submit(new FluencyEmitTask(tag, data));
            while (true) {
                try {
                    future.get(); // block until task is finished
                    break;
                }
                catch (InterruptedException ex) {
                    // remember that the thread has been interrupted,
                    // but still wait for completion of the emit task
                    isThreadInterrupted = true;
                }
                catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    if (cause instanceof IOException) {
                        throw (IOException)cause;
                    }
                    throw new RuntimeException(cause);
                }
            }
        }
        finally {
            if (isThreadInterrupted) {
                // restore thread's interrupted status
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * A tasks that emits data via Fluency.<p/>
     *
     * It is supposed to be executed in a separate thread to protect it from
     * thread interrupts by Jenkins. Fluency has a bug that leads to loss of
     * buffered event data in case of thread interrupt.
     */
    private class FluencyEmitTask implements Callable<Void> {
        private String tag;
        private Map<String, Object> data;
        private EventTime eventTime;

        FluencyEmitTask(String tag, Map<String, Object> data) {
            this.data = data;
            this.tag = tag;
            eventTime = getEventTime(data);
        }

        public Void call() throws IOException {
            final int timeoutMillis = config.getEmitTimeoutMillis();
            final long startTimeNanos = System.nanoTime();


            boolean isThreadInterrupted = false;
            try {
                IOException lastException = null;
                long retryCount = 0;
                while (true) {
                    try {
                        fluentd.emit(tag, eventTime, data);
                        long elapsedTimeNanos = System.nanoTime() - startTimeNanos;
                        LOGGER.log(Level.FINEST, "Log event emitted after {0} nanoseconds", new Object[] { elapsedTimeNanos });
                        break;
                    } catch (IOException ex) {
                        lastException = ex;
                    }

                    long elapsedMillis = (System.nanoTime() - startTimeNanos) / 1_000_000;
                    if (timeoutMillis >= 0 && elapsedMillis > timeoutMillis) {
                        throw new IOException("Failed to emit log event after " + timeoutMillis + " milliseconds. Giving up.", lastException);
                    }

                    try {
                        // Use exponential back-off that grows slowly and is
                        // capped at a not too high value so that the retry
                        // rate is high enough to prevent longer delays.
                        // It's expected that Fluency's buffers run full
                        // frequently and retrying must happen here without
                        // adding much delay.
                        long delayMicros = (long) (10_000.0 * Math.pow(1.3, (double) Math.min(retryCount, 18)));
                        TimeUnit.MICROSECONDS.sleep(delayMicros);
                    } catch (InterruptedException ex) {
                        isThreadInterrupted = true;
                    }
                    retryCount++;
                }
            } finally {
                if (isThreadInterrupted) {
                    // restore thread's interrupted status
                    Thread.currentThread().interrupt();
                }
            }
            return null;
        }

        private EventTime getEventTime(Map<String, Object> data) {
            Instant instant = Instant.parse((String) data.get(TIMESTAMP));
            long epochSeconds = instant.getEpochSecond();
            long nanoSeconds = instant.getNano();
            return EventTime.fromEpoch(epochSeconds, nanoSeconds);
        }
    }

    @Override
    public void close() throws Exception
    {
        try {
            this.lock.writeLock().lock();
            failIfClosed();

            boolean isThreadInterrupted = false;
            try {
                // run Fluency.emit() in another thread to protect it from
                // thread interrupts by Jenkins.
                Future<Void> future = executor.submit(new FluencyShutdownTask());
                while (true) {
                    try {
                        future.get(); // block until task is finished
                        break;
                    }
                    catch (InterruptedException ex) {
                        // remember that the thread has been interrupted,
                        // but still wait for completion of the emit task
                        isThreadInterrupted = true;
                    }
                    catch (ExecutionException ex) {
                        Throwable cause = ex.getCause();
                        if (cause instanceof Exception) {
                            throw (Exception)cause;
                        }
                        throw new RuntimeException(cause);
                    }
                }
            } finally {
                if (isThreadInterrupted) {
                    // restore thread's interrupted status
                    Thread.currentThread().interrupt();
                }
            }

            checkForRetryableException();
        }
        finally {
            this.executor.shutdown();
            this.executor = null;
            this.fluentd = null;
            this.lock.writeLock().unlock();
        }
    }

    /**
     * A tasks that shuts down Fluency.<p/>
     *
     * It is supposed to be executed in a separate thread to protect it from
     * thread interrupts by Jenkins. Fluency has a bug that leads to loss of
     * buffered event data in case of thread interrupt.
     */
    private class FluencyShutdownTask implements Callable<Void> {

        @Override
        public Void call() throws Exception {
            boolean isThreadInterrupted = false;
            try {
                fluentd.flush();
                try {
                    if (!fluentd.waitUntilAllBufferFlushed(config.getMaxWaitSecondsUntilBufferFlushed())) {
                        throw new Exception("Not all data could be flushed.");
                    }
                } catch (InterruptedException e) {
                    isThreadInterrupted = true;
                }

                fluentd.close();
                try {
                    if (!fluentd.waitUntilFlusherTerminated(config.getMaxWaitSecondsUntilFlusherTerminated())) {
                        throw new Exception("Flusher not terminated.");
                    }
                } catch (InterruptedException e) {
                    isThreadInterrupted = true;
                }
            } finally {
                try {
                    fluentd.clearBackupFiles();
                }
                catch (Throwable t) {
                    LOGGER.fine("Failed to clear Fluency backup files: " + t.getMessage());
                }

                if (isThreadInterrupted) {
                    // restore thread's interrupted status
                    Thread.currentThread().interrupt();
                }
            }
            return null;
        }
    }

    private void checkForRetryableException() throws IOException {
        RetryableException re = errorHandler.findRetryError();
        if (re != null) {
            throw new IOException("Some data couldn't be sent.", re);
        }
    }

    private void failIfClosed() throws IllegalStateException {
        if (this.isClosed) {
            throw new IllegalStateException("object is closed already");
        }
    }
}
