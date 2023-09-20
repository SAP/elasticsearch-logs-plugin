package io.jenkins.plugins.pipeline_elasticsearch_logs.write.fluentd;

import static io.jenkins.plugins.pipeline_elasticsearch_logs.ElasticSearchFieldNames.TIMESTAMP;
import static java.lang.String.format;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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

    FluentdEventWriterGlobalConfig config;

    FluentdEventWriter(@Nonnull FluentdEventWriterGlobalConfig config) {
        this.config = config;
    }

    /**
     * Logs the given object to Fluentd asynchronously
     * Checks if there have been any data that couldn't be sent after several retries and then fails.
     *
     * @param data The data to post
     * @throws IOException if something failed permanently
     */
    @Override
    public void push(Map<String, Object> data) throws IOException {
        if (fluentd == null)
          initFluentdLogger();
        emitData(config.getTag(), data);
        checkForRetryableException();
    }

    private EventTime getEventTime(Map<String, Object> data) {
        Instant instant = Instant.parse((String) data.get(TIMESTAMP));
        long epochSeconds = instant.getEpochSecond();
        long nanoSeconds = instant.getNano();
        return EventTime.fromEpoch(epochSeconds, nanoSeconds);
    }

    private void emitData(String tag, Map<String, Object> data) throws IOException {
        int count = 0;

        while (true) {
            LOGGER.log(Level.FINEST, "Emitting data: Try {0} Data: {1}", new Object[] {count, data });
            try {
                fluentd.emit(tag, getEventTime(data), data);
                break;
            } catch (BufferFullException e) {
                LOGGER.log(Level.WARNING, "Fluency's buffer is full. Retrying", e);
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e1) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            count++;
            if (config.getEmitMaxRetriesIfBufferFull() >= 0 && count > config.getEmitMaxRetriesIfBufferFull()) {
                throw new IOException("Not able to emit data after " + count + " tries.");
            }
        }
    }

    private void checkForRetryableException() throws IOException {
        RetryableException re = errorHandler.findRetryError();
        if (re != null) {
            throw new IOException("Some data couldn't be sent.", re);
        }
    }

    private void initFluentdLogger() {
        if(fluentd == null) {
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
    }

    @Override
    public void close() throws IOException
    {
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

}
