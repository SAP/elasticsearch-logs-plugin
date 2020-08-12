package io.jenkins.plugins.pipeline_elasticsearch_logs.write.fluentd;

import static java.lang.String.format;

import java.io.IOException;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import hudson.Extension;
import io.jenkins.plugins.pipeline_elasticsearch_logs.ConsoleNotes;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.ElasticSearchWriteAccess;

/**
 * Post data to Fluentd.
 *
 */
public class FluentdWriter extends ElasticSearchWriteAccess {

    private static final Logger LOGGER = Logger.getLogger(FluentdWriter.class.getName());

    private transient Fluency fluentd;
    private transient FluentdErrorHandler errorHandler;


    private final static int DEFAULT_TIMEOUT_MILLIS = 3000;
    private final static int DEFAULT_RETRY_MILLIS = 1000;
    private final static int DEFAULT_BUFFER_CAPACITY = 1048576;
    private final static int DEFAULT_MAX_RETRIES = 30;
    private final static int DEFAULT_MAX_WAIT_SEC = 30;
    private final static int DEFAULT_BUFFER_RETENTION_TIME_MILLIS = 1000;

    private String tag;

    private String host;
    private int port;
    private int retryMillis = DEFAULT_RETRY_MILLIS;
    private int timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
    private int bufferCapacity = DEFAULT_BUFFER_CAPACITY;
    private int maxRetries = DEFAULT_MAX_RETRIES;
    private int maxWaitSeconds = DEFAULT_MAX_WAIT_SEC;
    private int bufferRetentionTimeMillis = DEFAULT_BUFFER_RETENTION_TIME_MILLIS;

    @DataBoundConstructor
    public FluentdWriter() throws URISyntaxException {
    }

    public int getBufferRetentionTimeMillis() {
        return bufferRetentionTimeMillis;
    }

    @DataBoundSetter
    public void setBufferRetentionTimeMillis(int bufferRetentionTimeMillis) {
        this.bufferRetentionTimeMillis = bufferRetentionTimeMillis;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    @DataBoundSetter
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getMaxWaitSeconds() {
        return maxWaitSeconds;
    }

    @DataBoundSetter
    public void setMaxWaitSeconds(int maxWaitSeconds) {
        this.maxWaitSeconds = maxWaitSeconds;
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

    public int getRetryMillis() {
        return retryMillis;
    }

    @DataBoundSetter
    public void setRetryMillis(int retryMillis) {
        if (retryMillis < 0)
            retryMillis = 0;
        this.retryMillis = retryMillis;
    }

    public int getTimeoutMillis() {
        return timeoutMillis;
    }

    @DataBoundSetter
    public void setTimeoutMillis(int timeoutMillis) {
        if(timeoutMillis < 0) throw new IllegalArgumentException("timeoutMillis less than 0");
        this.timeoutMillis = timeoutMillis;
    }

    public int getBufferCapacity() {
        return bufferCapacity;
    }

    @DataBoundSetter
    public void setBufferCapacity(int bufferCapacity) {
        if (bufferCapacity < 0)
            bufferCapacity = DEFAULT_BUFFER_CAPACITY;
        this.bufferCapacity = bufferCapacity;
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
        if(fluentd == null) initFluentdLogger();
        if (data.containsKey(ConsoleNotes.MESSAGE_KEY)) {
            String message = (String) data.get(ConsoleNotes.MESSAGE_KEY);
            if (message.length() > bufferCapacity * 2) {
                data.put("messageId",UUID.randomUUID().toString());
                LOGGER.log(Level.FINER, "Message is too big to be sent in one piece. Will split the message into several smaller chunks");
                Integer messageCount = 0;
                for(String m: Splitter.fixedLength(bufferCapacity).split(message)) {
                    Map<String, Object> d = new HashMap<>();
                    d.putAll(data);
                    d.put("messageCount", messageCount);
                    d.put(ConsoleNotes.MESSAGE_KEY, m);
                    if (messageCount > 0) {
                        d.remove(ConsoleNotes.ANNOTATIONS_KEY);
                    }
                    messageCount++;
                    emitData(tag,d);
                }
            } else {
                emitData(tag,data);
            }

        } else {
            emitData(tag,data);
        }
        checkForRetryableException();
    }


    private void emitData(String tag, Map<String, Object> data) throws IOException {
        int count = 0;

        while (true) {
            LOGGER.log(Level.FINEST, "Emitting data: Try {0} Data: {1}", new Object[] {count, data });
            try {
                fluentd.emit(tag, data);
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
            if (count > maxRetries) {
                throw new IOException("Not able to emit data after " + maxRetries + " tries.");
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
                    if (!fluentd.waitUntilAllBufferFlushed(maxWaitSeconds)) {
                        throw new IOException("Not all data could be flushed.");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                fluentd.close();
                try {
                    if (!fluentd.waitUntilFlusherTerminated(maxWaitSeconds)) {
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
            builder.setJvmHeapBufferMode(true);
            builder.setSenderMaxRetryCount(maxRetries);
            builder.setConnectionTimeoutMilli(timeoutMillis);
            builder.setReadTimeoutMilli(timeoutMillis);
            builder.setWaitUntilBufferFlushed(maxWaitSeconds);
            builder.setWaitUntilFlusherTerminated(maxWaitSeconds);
            builder.setBufferChunkInitialSize(bufferCapacity);
            builder.setBufferChunkRetentionSize(bufferCapacity * 3);
            builder.setMaxBufferSize(bufferCapacity * 10L);
            builder.setBufferChunkRetentionTimeMillis(bufferRetentionTimeMillis);
            builder.setFlushAttemptIntervalMillis(100);
            errorHandler = new FluentdErrorHandler();
            builder.setErrorHandler(errorHandler);
            fluentd = builder.build(host, port);
            LOGGER.finer(format("Created new Fluency(tag=%s, host=%s, port=%s, timeoutMillis=%s, bufferCapacity=%s) hash:%s",
                    tag, host, port, timeoutMillis, bufferCapacity, fluentd.hashCode()));
        }
    }

    private static class MeSupplier implements Supplier<ElasticSearchWriteAccess>, Serializable {

        private static final long serialVersionUID = 1L;

        private int bufferCapacity;
        private String host;
        private int port;
        private int retryMillis;
        private String tag;
        private int timeoutMillis;

        private MeSupplier(FluentdWriter me) {
            this.bufferCapacity = me.bufferCapacity;
            this.host = me.host;
            this.port = me.port;
            this.retryMillis = me.retryMillis;
            this.tag = me.tag;
            this.timeoutMillis = me.timeoutMillis;
        }

        @Override
        public ElasticSearchWriteAccess get() {
            try {
                FluentdWriter accessor = new FluentdWriter();
                accessor.setBufferCapacity(bufferCapacity);
                accessor.setHost(host);
                accessor.setPort(port);
                accessor.setRetryMillis(retryMillis);
                accessor.setTag(tag);
                accessor.setTimeoutMillis(timeoutMillis);
                return accessor;
            } catch (Exception e) {
                throw new RuntimeException("Could not create ElasticSearchWriteAccessorFluentd", e);
            }
        }
    }

    @Override
    public Supplier<ElasticSearchWriteAccess> getSupplier() {
        return new MeSupplier(this);
    }

}
