package io.jenkins.plugins.pipeline_elasticsearch_logs.write.fluentd;

import static java.lang.String.format;

import java.io.IOException;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.fluentd.logger.FluentLogger;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.ElasticSearchWriteAccess;

/**
 * Post data to Fluentd.
 *
 */
public class FluentdWriter extends ElasticSearchWriteAccess {

    private static final Logger LOGGER = Logger.getLogger(FluentdWriter.class.getName());

    private transient FluentLogger fluentd;
    private transient FluentdErrorHandler errorHandler;
    private transient boolean configChanged;

    private final static int SYS_LOG_FREQUENCY = 2000; //TODO: 10*1000;

    private final static int DEFAULT_TIMEOUT_MILLIS = 3000;
    private final static int DEFAULT_RETRY_MILLIS = 1000;
    private final static int DEFAULT_BUFFER_CAPACITY = 1048576;

    private String tagPrefix;
    private String tag;

    private String host;
    private int port;
    private int retryMillis = DEFAULT_RETRY_MILLIS;
    private int timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
    private int bufferCapacity = DEFAULT_BUFFER_CAPACITY;

    @DataBoundConstructor
    public FluentdWriter() throws URISyntaxException {
    }

    public String getTagPrefix() {
        return tagPrefix;
    }

    @DataBoundSetter
    public void setTagPrefix(String tagPrefix) {
        this.tagPrefix = tagPrefix;
        this.configChanged = true;
    }

    public String getTag() {
        return tag;
    }

    @DataBoundSetter
    public void setTag(String tag) {
        this.tag = tag;
        this.configChanged = true;
    }

    public String getHost() {
        return host;
    }

    @DataBoundSetter
    public void setHost(String host) {
        this.host = host;
        this.configChanged = true;
    }

    public int getPort() {
        return port;
    }

    @DataBoundSetter
    public void setPort(int port) {
        if(port < 0 || port > 65535) throw new IllegalArgumentException("Port out of range: " + port);
        this.port = port;
        this.configChanged = true;
    }

    public int getRetryMillis() {
        return retryMillis;
    }

    @DataBoundSetter
    public void setRetryMillis(int retryMillis) {
        if (retryMillis < 0)
            retryMillis = 0;
        this.retryMillis = retryMillis;
        this.configChanged = true;
    }

    public int getTimeoutMillis() {
        return timeoutMillis;
    }

    @DataBoundSetter
    public void setTimeoutMillis(int timeoutMillis) {
        if(timeoutMillis < 0) throw new IllegalArgumentException("timeoutMillis less than 0");
        this.timeoutMillis = timeoutMillis;
        this.configChanged = true;
    }

    public int getBufferCapacity() {
        return bufferCapacity;
    }

    @DataBoundSetter
    public void setBufferCapacity(int bufferCapacity) {
        if (bufferCapacity < 0)
            bufferCapacity = DEFAULT_BUFFER_CAPACITY;
        this.bufferCapacity = bufferCapacity;
        this.configChanged = true;
    }


    @Extension
    @Symbol("fluentd")
    public static class DescriptorImpl extends ElasticSearchWriteAccessDescriptor {
        @Override
        public String getDisplayName() {
            return "Fluentd Elasticsearch Writer";
        }
    }

    /**
     * Logs the given object to Fluentd. This method blocks if Fluentd is not reachable and
     * throws an IOException after TIMEOUT_MILLIS.
     *
     * @param data The data to post
     * @throws IOException if something failed permanently
     */
    @Override
    public void push(Map<String, Object> data) throws IOException {
        if(fluentd == null || configChanged) initFluentdLogger();
        try {

            long timeMarker = System.currentTimeMillis();
            boolean sent = fluentd.log(tag, data);
            if(!sent) {
                //Errors are not reported explicitly. But the latest error should be the reason for (sent == false)
                Exception lastError = errorHandler.getLastError(timeMarker);
                throw new IOException("Could not send logs to fluentd", lastError);
            }

            // (sent == true) does not necessarily mean the data has already been sent to fluentd.
            // To ensure data can be sent we flush the library buffer every time.
            flushWithRetry();

        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }


    private void flushWithRetry() throws InterruptedException, IOException {
        long start = System.currentTimeMillis();
        long nextSystemLog = 0;
        boolean failed = true;
        while (failed) {
            long timeMarker = System.currentTimeMillis();
            fluentd.flush();
            // Unfortunately flush() does not fail obviously. That's why we get errors indirectly via our ErrorHandler.
            Exception flushError = errorHandler.getLastError(timeMarker);
            if (flushError == null) {
                failed = false;
            } else {
                if (nextSystemLog <= System.currentTimeMillis()) {
                    LOGGER.warning(format(
                            "Could not flush fluentd library buffer since %s seconds. FluentLogger (%s) %s. Configured host=%s, port=%s.",
                            ((System.currentTimeMillis() - start) / 1000), fluentd.hashCode(),
                            (fluentd.isConnected() ? "connected" : "not connected"), host, port));
                    nextSystemLog = System.currentTimeMillis() + SYS_LOG_FREQUENCY;
                }
                Thread.sleep(retryMillis);
                if (isTimeoutReached(start)) {
                    throw new IOException(format(
                            "Could not flush fluentd library buffer since %s seconds. FluentLogger (%s) %s. Configured host=%s, port=%s.",
                            (timeoutMillis / 1000), fluentd.hashCode(), (fluentd.isConnected() ? "connected" : "not connected"), host,
                            port));
                }

            }
        }
    }

    private boolean isTimeoutReached(long startMillis) {
        return System.currentTimeMillis() - startMillis > timeoutMillis * 5;
    }

    private void initFluentdLogger() {
        if(fluentd == null || errorHandler == null || configChanged) {
            if(fluentd != null) {
                try {
                    fluentd.close();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error closing old FluentLogger", e);
                }
            }
            fluentd = FluentLogger.getLogger(tagPrefix, host, port, timeoutMillis, bufferCapacity);
            LOGGER.finer(format("Created new FluentLogger(tagprefix=%s, host=%s, port=%s, timeoutMillis=%s, bufferCapacity=%s) hash:%s%s%s",
                    tagPrefix, host, port, timeoutMillis, bufferCapacity, fluentd.hashCode(),
                    (configChanged?" (config changed)":""),
                    (fluentd.isConnected()?" (connected)":" (not connected yet)") ));
            configChanged = false;
            errorHandler = new FluentdErrorHandler();
            fluentd.setErrorHandler(errorHandler);
        }
    }

    private static class MeSupplier implements Supplier<ElasticSearchWriteAccess>, Serializable {

        private static final long serialVersionUID = 1L;

        private int bufferCapacity;
        private String host;
        private int port;
        private int retryMillis;
        private String tag;
        private String tagPrefix;
        private int timeoutMillis;

        private MeSupplier(FluentdWriter me) {
            this.bufferCapacity = me.bufferCapacity;
            this.host = me.host;
            this.port = me.port;
            this.retryMillis = me.retryMillis;
            this.tag = me.tag;
            this.tagPrefix = me.tagPrefix;
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
                accessor.setTagPrefix(tagPrefix);
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
