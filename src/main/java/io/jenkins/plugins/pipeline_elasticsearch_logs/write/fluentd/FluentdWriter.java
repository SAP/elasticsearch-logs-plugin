package io.jenkins.plugins.pipeline_elasticsearch_logs.write.fluentd;

import static java.lang.String.format;

import java.io.IOException;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.komamitsu.fluency.BufferFullException;
import org.komamitsu.fluency.Fluency;
import org.komamitsu.fluency.RetryableException;
import org.komamitsu.fluency.fluentd.FluencyBuilderForFluentd;

import hudson.Extension;
import io.jenkins.plugins.pipeline_elasticsearch_logs.SerializableSupplier;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.ElasticSearchWriteAccess;

/**
 * Post data to Fluentd.
 *
 */
public class FluentdWriter extends ElasticSearchWriteAccess {

    private static final Logger LOGGER = Logger.getLogger(FluentdWriter.class.getName());

    private transient Fluency fluentd;
    private transient FluentdErrorHandler errorHandler;

    private String tag;
    private String host;
    private int port;

    private Integer senderBaseRetryIntervalMillis;
    private Integer senderMaxRetryIntervalMillis;
    private Integer senderMaxRetryCount;
    private Integer connectionTimeoutMillis;
    private Integer readTimeoutMillis;

    private int maxWaitSecondsUntilBufferFlushed = 30;
    private int maxWaitSecondsUntilFlusherTerminated = 30;
    private int bufferChunkInitialSize = 1 * 1024 * 1024;
    private int bufferChunkRetentionSize = (1 * 1024 * 1024) + 1;
    private int bufferChunkRetentionTimeMillis = 1000;
    private int flushAttemptIntervalMillis = 500;
    private long maxBufferSize = 10 * 1024 * 1024L;

    private int emitMaxRetriesIfBufferFull = -1; // forever

    @DataBoundConstructor
    public FluentdWriter() throws URISyntaxException {
    }

    public Integer getSenderBaseRetryIntervalMillis() {
        return senderBaseRetryIntervalMillis;
    }

    @DataBoundSetter
    public void setSenderBaseRetryIntervalMillis(int millis) {
        this.senderBaseRetryIntervalMillis = millis;
    }

    public Integer getSenderMaxRetryIntervalMillis() {
        return senderMaxRetryIntervalMillis;
    }

    @DataBoundSetter
    public void setSenderMaxRetryIntervalMillis(int millis) {
        this.senderMaxRetryIntervalMillis = millis;
    }

    public Integer getSenderMaxRetryCount() {
        return senderMaxRetryCount;
    }

    @DataBoundSetter
    public void setSenderMaxRetryCount(int count) {
        this.senderMaxRetryCount = count;
    }

    public Integer getConnectionTimeoutMillis() {
        return connectionTimeoutMillis;
    }

    @DataBoundSetter
    public void setConnectionTimeoutMillis(int millis) {
        this.connectionTimeoutMillis = millis;
    }

    public Integer getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    @DataBoundSetter
    public void setReadTimeoutMillis(int millis) {
        this.readTimeoutMillis = millis;
    }

    public int getMaxWaitSecondsUntilBufferFlushed() {
        return maxWaitSecondsUntilBufferFlushed;
    }

    @DataBoundSetter
    public void setMaxWaitSecondsUntilBufferFlushed(int seconds) {
        this.maxWaitSecondsUntilBufferFlushed = seconds;
    }

    public int getMaxWaitSecondsUntilFlusherTerminated() {
        return maxWaitSecondsUntilFlusherTerminated;
    }

    @DataBoundSetter
    public void setMaxWaitSecondsUntilFlusherTerminated(int seconds) {
        this.maxWaitSecondsUntilFlusherTerminated = seconds;
    }

    public int getBufferChunkInitialSize() {
        return bufferChunkInitialSize;
    }

    @DataBoundSetter
    public void setBufferChunkInitialSize(int bytes) {
        this.bufferChunkInitialSize = bytes;
    }

    public int getBufferChunkRetentionSize() {
        return bufferChunkRetentionSize;
    }

    @DataBoundSetter
    public void setBufferChunkRetentionSize(int bytes) {
        this.bufferChunkRetentionSize = bytes;
    }

    public int getBufferChunkRetentionTimeMillis() {
        return bufferChunkRetentionTimeMillis;
    }

    @DataBoundSetter
    public void setBufferChunkRetentionTimeMillis(int millis) {
        this.bufferChunkRetentionTimeMillis = millis;
    }

    public long getMaxBufferSize() {
        return maxBufferSize;
    }

    @DataBoundSetter
    public void setMaxBufferSize(long bytes) {
        this.maxBufferSize = bytes;
    }

    public int getFlushAttemptIntervalMillis() {
        return flushAttemptIntervalMillis;
    }

    @DataBoundSetter
    public void setFlushAttemptIntervalMillis(int millis) {
        this.flushAttemptIntervalMillis = millis;
    }

    public int getEmitMaxRetriesIfBufferFull() {
        return emitMaxRetriesIfBufferFull;
    }

    @DataBoundSetter
    public void setEmitMaxRetriesIfBufferFull(int count) {
        this.emitMaxRetriesIfBufferFull = count;
    }

    public String getTag() {
        return tag;
    }

    @DataBoundSetter
    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getHost() {
        return host;
    }

    @DataBoundSetter
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    @DataBoundSetter
    public void setPort(int port) {
        if(port < 0 || port > 65535) throw new IllegalArgumentException("Port out of range: " + port);
        this.port = port;
    }

    @Extension
    @Symbol("fluentd")
    public static class DescriptorImpl extends ElasticSearchWriteAccessDescriptor {
        @Override
        public String getDisplayName() {
            return "Fluentd Writer";
        }
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
        emitData(tag, data);
        checkForRetryableException();
    }

    private void emitData(String tag, Map<String, Object> data) throws IOException {
        int count = 0;

        while (true) {
            LOGGER.log(Level.FINEST, "Emitting data: Try {0} Data: {1}", new Object[] {count, data });
            try {
                Instant instant = Instant.parse(data.get("timestamp"));
                fluentd.emit(tag, EventTime(instant.getEpochSecond(), instant.getNano()), data);
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
            if (emitMaxRetriesIfBufferFull >= 0 && count > emitMaxRetriesIfBufferFull) {
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

    @Override
    public void close() throws IOException
    {
        if (fluentd != null) {
            try {
                fluentd.flush();
                try {
                    if (!fluentd.waitUntilAllBufferFlushed(maxWaitSecondsUntilBufferFlushed)) {
                        throw new IOException("Not all data could be flushed.");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                fluentd.close();
                try {
                    if (!fluentd.waitUntilFlusherTerminated(maxWaitSecondsUntilFlusherTerminated)) {
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

    private void initFluentdLogger() {
        if(fluentd == null) {
            FluencyBuilderForFluentd builder = new FluencyBuilderForFluentd();

            builder.setAckResponseMode(true);
            builder.setSenderBaseRetryIntervalMillis(senderBaseRetryIntervalMillis);
            builder.setSenderMaxRetryIntervalMillis(senderMaxRetryIntervalMillis);
            builder.setSenderMaxRetryCount(senderMaxRetryCount);
            builder.setConnectionTimeoutMilli(connectionTimeoutMillis);
            builder.setReadTimeoutMilli(readTimeoutMillis);

            builder.setJvmHeapBufferMode(true);
            builder.setWaitUntilBufferFlushed(maxWaitSecondsUntilBufferFlushed);
            builder.setWaitUntilFlusherTerminated(maxWaitSecondsUntilFlusherTerminated);
            builder.setBufferChunkInitialSize(bufferChunkInitialSize);
            builder.setBufferChunkRetentionSize(bufferChunkRetentionSize);
            builder.setMaxBufferSize(maxBufferSize);
            builder.setBufferChunkRetentionTimeMillis(bufferChunkRetentionTimeMillis);
            builder.setFlushAttemptIntervalMillis(flushAttemptIntervalMillis);

            errorHandler = new FluentdErrorHandler();
            builder.setErrorHandler(errorHandler);
            fluentd = builder.build(host, port);
            LOGGER.finer(format("Created new %s, host=%s, port=%s, hashCode=%s",
                    fluentd, host, port, fluentd.hashCode()));
        }
    }

    private static class MeSupplier implements SerializableSupplier<ElasticSearchWriteAccess>, Serializable {

        private static final long serialVersionUID = 1L;

        private String host;
        private int port;
        private String tag;

        private Integer senderBaseRetryIntervalMillis;
        private Integer senderMaxRetryIntervalMillis;
        private Integer senderMaxRetryCount;
        private Integer connectionTimeoutMillis;
        private Integer readTimeoutMillis;

        private int maxWaitSecondsUntilBufferFlushed;
        private int maxWaitSecondsUntilFlusherTerminated;
        private int bufferChunkInitialSize;
        private int bufferChunkRetentionSize;
        private int bufferChunkRetentionTimeMillis;
        private int flushAttemptIntervalMillis;
        private long maxBufferSize;

        private int emitMaxRetriesIfBufferFull;

        private MeSupplier(FluentdWriter me) {
            this.host = me.host;
            this.port = me.port;
            this.tag = me.tag;

            this.senderBaseRetryIntervalMillis = me.senderBaseRetryIntervalMillis;
            this.senderMaxRetryIntervalMillis = me.senderMaxRetryIntervalMillis;
            this.senderMaxRetryCount = me.senderMaxRetryCount;
            this.connectionTimeoutMillis = me.connectionTimeoutMillis;
            this.readTimeoutMillis = me.readTimeoutMillis;

            this.maxWaitSecondsUntilBufferFlushed = me.maxWaitSecondsUntilBufferFlushed;
            this.maxWaitSecondsUntilFlusherTerminated = me.maxWaitSecondsUntilFlusherTerminated;
            this.bufferChunkInitialSize = me.bufferChunkInitialSize;
            this.bufferChunkRetentionSize = me.bufferChunkRetentionSize;
            this.bufferChunkRetentionTimeMillis = me.bufferChunkRetentionTimeMillis;
            this.maxBufferSize = me.maxBufferSize;
            this.flushAttemptIntervalMillis = me.flushAttemptIntervalMillis;

            this.emitMaxRetriesIfBufferFull = me.emitMaxRetriesIfBufferFull;
        }

        @Override
        public ElasticSearchWriteAccess get() {
            try {
                FluentdWriter accessor = new FluentdWriter();

                accessor.setHost(host);
                accessor.setPort(port);
                accessor.setTag(tag);

                accessor.setSenderBaseRetryIntervalMillis(senderBaseRetryIntervalMillis);
                accessor.setSenderMaxRetryIntervalMillis(senderMaxRetryIntervalMillis);
                accessor.setSenderMaxRetryCount(senderMaxRetryCount);
                accessor.setConnectionTimeoutMillis(connectionTimeoutMillis);
                accessor.setReadTimeoutMillis(readTimeoutMillis);

                accessor.setMaxWaitSecondsUntilBufferFlushed(maxWaitSecondsUntilBufferFlushed);
                accessor.setMaxWaitSecondsUntilFlusherTerminated(maxWaitSecondsUntilFlusherTerminated);
                accessor.setBufferChunkInitialSize(bufferChunkInitialSize);
                accessor.setBufferChunkRetentionSize(bufferChunkRetentionSize);
                accessor.setBufferChunkRetentionTimeMillis(bufferChunkRetentionTimeMillis);
                accessor.setMaxBufferSize(maxBufferSize);
                accessor.setFlushAttemptIntervalMillis(flushAttemptIntervalMillis);

                accessor.setEmitMaxRetriesIfBufferFull(emitMaxRetriesIfBufferFull);

                return accessor;
            } catch (Exception e) {
                throw new RuntimeException("Could not create ElasticSearchWriteAccessorFluentd", e);
            }
        }
    }

    @Override
    public SerializableSupplier<ElasticSearchWriteAccess> getSupplier() {
        return new MeSupplier(this);
    }
}
