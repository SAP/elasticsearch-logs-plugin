package io.jenkins.plugins.pipeline_elasticsearch_logs.write.fluentd;

import static io.jenkins.plugins.pipeline_elasticsearch_logs.EventFieldNames.TIMESTAMP;
import static java.lang.String.format;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.komamitsu.fluency.BufferFullException;
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

        EventTime eventTime = getEventTime(data);
        int timeoutMillis = config.getEmitTimeoutMillis();
        IOException lastException = null;
        long startTimeNanos = System.nanoTime();
        long retryCount = 0;

        while (true) {
            try {
                fluentd.emit(tag, eventTime, data);
                long elapsedTimeNanos = System.nanoTime() - startTimeNanos;
                LOGGER.log(Level.FINEST, "Log event emitted after {0} nanoseconds", new Object[] { elapsedTimeNanos });
                break;
            } catch (BufferFullException ex) {
                lastException = ex;
            } catch (IOException ex) {
                LOGGER.log(Level.INFO, "Exception occurred while emitting data: {0}", ex);
            }

            long elapsedMillis = (System.nanoTime() - startTimeNanos) / 1_000_000;
            if (timeoutMillis >= 0 && elapsedMillis > timeoutMillis) {
                throw new IOException("Failed to emit log event after " + timeoutMillis + " milliseconds. Giving up.", lastException);
            }

            try {
                long delayMicros = (long) (10_000.0 * Math.pow(1.3, (double) Math.min(retryCount, 18)));
                TimeUnit.MICROSECONDS.sleep(delayMicros);
            } catch (InterruptedException ex) {
                LOGGER.log(Level.FINEST, "Exception occurred while sleeping: {0}", ex);
            }
            retryCount++;
        }
    }

    private EventTime getEventTime(Map<String, Object> data) {
        Instant instant = Instant.parse((String) data.get(TIMESTAMP));
        long epochSeconds = instant.getEpochSecond();
        long nanoSeconds = instant.getNano();
        return EventTime.fromEpoch(epochSeconds, nanoSeconds);
    }

    @Override
    public void close() throws IOException
    {
        try {
            this.lock.writeLock().lock();
            failIfClosed();

            if (fluentd != null) {
                try {
                    fluentd.flush();
                    try {
                        if (!fluentd.waitUntilAllBufferFlushed(config.getMaxWaitSecondsUntilBufferFlushed())) {
                            throw new IOException("Not all data could be flushed.");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    fluentd.close();
                    try {
                        if (!fluentd.waitUntilFlusherTerminated(config.getMaxWaitSecondsUntilFlusherTerminated())) {
                            throw new IOException("Flusher not terminated.");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                } finally {
                    fluentd.clearBackupFiles();
                }
                checkForRetryableException();
            }
        }
        finally {
            this.lock.writeLock().unlock();
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
