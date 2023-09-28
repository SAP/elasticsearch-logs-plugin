package io.jenkins.plugins.pipeline_elasticsearch_logs.write.fluentd;

import java.io.ObjectStreamException;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Run;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.EventWriter;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.EventWriterConfig;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.EventWriterRunConfig;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.utils.SharedEventWriterFactory;

/**
 * The configuration of {@link FluentdEventWriter}.
 */
public class FluentdEventWriterConfig
    extends EventWriterConfig
    implements EventWriterRunConfig
{
    private String tag;
    private String host;
    private int port;

    private Integer senderBaseRetryIntervalMillis;
    private Integer senderMaxRetryIntervalMillis;
    private Integer senderMaxRetryCount;
    private Integer connectionTimeoutMillis;
    private Integer readTimeoutMillis;

    private static final int DEFAULT_MAX_WAIT_SECONDS_UNTIL_BUFFER_FLUSHED = 30;
    private int maxWaitSecondsUntilBufferFlushed = DEFAULT_MAX_WAIT_SECONDS_UNTIL_BUFFER_FLUSHED;

    private static final int DEFAULT_MAX_WAIT_SECONDS_UNTIL_FLUSHER_TERMINATED = 30;
    private int maxWaitSecondsUntilFlusherTerminated = DEFAULT_MAX_WAIT_SECONDS_UNTIL_FLUSHER_TERMINATED;

    private static final int DEFAULT_BUFFER_CHUNK_INITIAL_SIZE = 1 * 1024 * 1024;
    private int bufferChunkInitialSize = DEFAULT_BUFFER_CHUNK_INITIAL_SIZE;

    private static final int DEFAULT_BUFFER_CHUNK_RETENTION_SIZE = (1 * 1024 * 1024) + 1;
    private int bufferChunkRetentionSize = DEFAULT_BUFFER_CHUNK_RETENTION_SIZE;

    private static final int DEFAULT_BUFFER_CHUNK_RETENTION_TIME_MILLIS = 1000;
    private int bufferChunkRetentionTimeMillis = DEFAULT_BUFFER_CHUNK_RETENTION_TIME_MILLIS;

    private static final int DEFAULT_FLUSH_ATTEMPT_INTERVAL_MILLIS = 500;
    private int flushAttemptIntervalMillis = DEFAULT_FLUSH_ATTEMPT_INTERVAL_MILLIS;

    private static final long DEFAULT_MAX_BUFFER_SIZE = 10 * 1024 * 1024L;
    private long maxBufferSize = DEFAULT_MAX_BUFFER_SIZE;

    private static final int DEFAULT_EMIT_TIMEOUT_MILLIS = -1; // forever
    private int emitTimeoutMillis = DEFAULT_EMIT_TIMEOUT_MILLIS;

    private transient SharedEventWriterFactory sharedWriterFactory;

    @DataBoundConstructor
    public FluentdEventWriterConfig(
        String host,
        int port,
        String tag,
        Integer senderBaseRetryIntervalMillis,
        Integer senderMaxRetryIntervalMillis,
        Integer senderMaxRetryCount,
        Integer connectionTimeoutMillis,
        Integer readTimeoutMillis,
        Integer maxWaitSecondsUntilBufferFlushed,
        Integer maxWaitSecondsUntilFlusherTerminated,
        Integer bufferChunkInitialSize,
        Integer bufferChunkRetentionSize,
        Integer bufferChunkRetentionTimeMillis,
        Integer flushAttemptIntervalMillis,
        Long maxBufferSize,
        Integer emitTimeoutMillis
    ) {
        this.host = host;
        this.port = port;
        this.tag = tag;

        this.senderBaseRetryIntervalMillis = senderBaseRetryIntervalMillis;
        this.senderMaxRetryIntervalMillis = senderMaxRetryIntervalMillis;
        this.senderMaxRetryCount = senderMaxRetryCount;
        this.connectionTimeoutMillis = connectionTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;

        if (maxWaitSecondsUntilBufferFlushed != null) {
            this.maxWaitSecondsUntilBufferFlushed = maxWaitSecondsUntilBufferFlushed;
        }
        if (maxWaitSecondsUntilFlusherTerminated != null) {
            this.maxWaitSecondsUntilFlusherTerminated = maxWaitSecondsUntilFlusherTerminated;
        }
        if (bufferChunkInitialSize != null) {
            this.bufferChunkInitialSize = bufferChunkInitialSize;
        }
        if (bufferChunkRetentionSize != null) {
            this.bufferChunkRetentionSize = bufferChunkRetentionSize;
        }
        if (bufferChunkRetentionTimeMillis != null) {
            this.bufferChunkRetentionTimeMillis = bufferChunkRetentionTimeMillis;
        }
        if (flushAttemptIntervalMillis != null) {
            this.flushAttemptIntervalMillis = flushAttemptIntervalMillis;
        }
        if (maxBufferSize != null) {
            this.maxBufferSize = maxBufferSize;
        }
        if (emitTimeoutMillis != null) {
            this.emitTimeoutMillis = emitTimeoutMillis;
        }

        init();
    }

    private void init() {
        this.sharedWriterFactory = new SharedEventWriterFactory(
            () -> new FluentdEventWriter(this)
        );
    }

    protected Object readResolve() throws ObjectStreamException {
        init();
        return this;
    }


    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getTag() {
        return tag;
    }

    public Integer getSenderBaseRetryIntervalMillis() {
        return senderBaseRetryIntervalMillis;
    }

    public Integer getSenderMaxRetryIntervalMillis() {
        return senderMaxRetryIntervalMillis;
    }

    public Integer getSenderMaxRetryCount() {
        return senderMaxRetryCount;
    }

    public Integer getConnectionTimeoutMillis() {
        return connectionTimeoutMillis;
    }

    public Integer getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public int getMaxWaitSecondsUntilBufferFlushed() {
        return maxWaitSecondsUntilBufferFlushed;
    }

    public int getMaxWaitSecondsUntilFlusherTerminated() {
        return maxWaitSecondsUntilFlusherTerminated;
    }

    public int getBufferChunkInitialSize() {
        return bufferChunkInitialSize;
    }

    public int getBufferChunkRetentionSize() {
        return bufferChunkRetentionSize;
    }

    public int getBufferChunkRetentionTimeMillis() {
        return bufferChunkRetentionTimeMillis;
    }

    public long getMaxBufferSize() {
        return maxBufferSize;
    }

    public int getFlushAttemptIntervalMillis() {
        return flushAttemptIntervalMillis;
    }

    public int getEmitTimeoutMillis() {
        return emitTimeoutMillis;
    }

    @Extension
    @Symbol("fluentdEventWriter")
    public static class DescriptorImpl extends EventWriterConfigDescriptor {
        @Override
        public String getDisplayName() {
            return "Fluentd";
        }

        public int defaultMaxWaitSecondsUntilBufferFlushed() {
            return DEFAULT_MAX_WAIT_SECONDS_UNTIL_BUFFER_FLUSHED;
        }

        public int defaultMaxWaitSecondsUntilFlusherTerminated() {
            return DEFAULT_MAX_WAIT_SECONDS_UNTIL_FLUSHER_TERMINATED;
        }

        public int defaultBufferChunkInitialSize() {
            return DEFAULT_BUFFER_CHUNK_INITIAL_SIZE;
        }

        public int defaultBufferChunkRetentionSize() {
            return DEFAULT_BUFFER_CHUNK_RETENTION_SIZE;
        }

        public int defaultBufferChunkRetentionTimeMillis() {
            return DEFAULT_BUFFER_CHUNK_RETENTION_TIME_MILLIS;
        }

        public int defaultFlushAttemptIntervalMillis() {
            return DEFAULT_FLUSH_ATTEMPT_INTERVAL_MILLIS;
        }

        public long defaultMaxBufferSize() {
            return DEFAULT_MAX_BUFFER_SIZE;
        }

        public int defaultEmitTimeoutMillis() {
            return DEFAULT_EMIT_TIMEOUT_MILLIS;
        }
    }

    @Override
    public EventWriterRunConfig createRunConfig(Run<?, ?> run) {
        // currently there's no need for a separate run-specific config class
        return this;
    }

    @Override
    public EventWriter createEventWriter() {
        return this.sharedWriterFactory.createEventWriter();
    }
}
