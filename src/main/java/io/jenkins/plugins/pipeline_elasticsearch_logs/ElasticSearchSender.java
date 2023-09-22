package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.jenkinsci.remoting.SerializableOnlyOverRemoting;

import com.google.common.base.Splitter;

import hudson.CloseProofOutputStream;
import hudson.console.LineTransformationOutputStream;
import hudson.model.BuildListener;
import hudson.remoting.RemoteOutputStream;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.EventWriter;
import net.sf.json.JSONObject;

public class ElasticSearchSender implements BuildListener, Closeable {
    private static final String EVENT_PREFIX_BUILD = "build";

    private static final String EVENT_PREFIX_NODE = "node";

    private static final Logger LOGGER = Logger.getLogger(ElasticSearchSender.class.getName());

    private static final long serialVersionUID = 1;

    private transient @CheckForNull PrintStream outputStreamLogger;
    private final @CheckForNull NodeInfo nodeInfo;

    protected transient EventWriter writer;
    protected final ElasticsearchRunConfig config;
    protected String eventPrefix;
    private final @CheckForNull OutputStream out;

    public ElasticSearchSender(
        @CheckForNull NodeInfo nodeInfo,
        @Nonnull ElasticsearchRunConfig config,
        @CheckForNull OutputStream out
    ) throws IOException {
        this.nodeInfo = nodeInfo;
        this.config = config;
        if (nodeInfo != null) {
            eventPrefix = EVENT_PREFIX_NODE;
        } else {
            eventPrefix = EVENT_PREFIX_BUILD;
        }
        this.out = out;
    }

    public OutputStream getWrappedLogger(@CheckForNull OutputStream logger) {
        return new ElasticSearchOutputStream(logger);
    }

    @Override
    public PrintStream getLogger() {
        if (outputStreamLogger == null) {
            try {
                outputStreamLogger = new PrintStream(getWrappedLogger(out), false, "UTF-8");
            } catch (UnsupportedEncodingException x) {
                throw new AssertionError(x);
            }
        }
        return outputStreamLogger;
    }

    @Override
    public void close() throws IOException {
        Exception firstException = null;

        if (outputStreamLogger != null) {
            try {
                outputStreamLogger.close();
            }
            catch (Exception ex) {
                if (firstException == null) firstException = ex;
            }
            outputStreamLogger = null;
        }

        if (writer != null) {
            try {
                writer.close();
            }
            catch (Exception ex) {
                if (firstException == null) firstException = ex;
            }
            writer = null;
        }

        if (firstException != null) {
            throw new IOException(firstException);
        }
    }

    private EventWriter getEventWriter() {
        if (writer == null) {
            writer = config.createEventWriter();
        }
        return writer;
    }

    private Object writeReplace() {
        return new Replacement(this);
    }

    // Method partially copied from BufferedBuildListener or workflow-api plugin
    private static final class Replacement implements SerializableOnlyOverRemoting {

        private static final long serialVersionUID = 1;

        private final RemoteOutputStream ros;
        private final DelayBufferedOutputStream.Tuning tuning = DelayBufferedOutputStream.Tuning.DEFAULT; // load defaults on master
        private final ElasticsearchRunConfig config;
        private final @CheckForNull NodeInfo nodeInfo;

        Replacement(ElasticSearchSender ess) {
            LOGGER.log(Level.FINER, "Creating Replacement for the ElasticSearchSender during Serialization");
            this.ros = new RemoteOutputStream(new CloseProofOutputStream(ess.out));
            this.config = ess.config;
            this.nodeInfo = ess.nodeInfo;
        }

        private Object readResolve() throws IOException {
            LOGGER.log(Level.FINER, "Creating new ElasticSearchSender during Deserialization");
            return new ElasticSearchSender(nodeInfo, config, new GCFlushedOutputStream(new DelayBufferedOutputStream(ros, tuning)));
        }

    }

    private class ElasticSearchOutputStream extends LineTransformationOutputStream {
        @Override
        public void write(int b) throws IOException {
            if (forwardingLogger != null) {
                forwardingLogger.write(b);
            }
            super.write(b);
        }

        private static final String EVENT_TYPE_MESSAGE = "Message";
        private @CheckForNull OutputStream forwardingLogger;

        public ElasticSearchOutputStream(@CheckForNull OutputStream logger) {
            this.forwardingLogger = logger;
        }

        @Override
        protected void eol(byte[] b, int len) throws IOException {
            Map<String, Object> data = config.createData();

            data.put(ElasticSearchGraphListener.EVENT_TYPE, eventPrefix + EVENT_TYPE_MESSAGE);
            if (nodeInfo != null) {
                nodeInfo.appendNodeInfo(data);
            }

            ConsoleNotes.parse(b, len, data, config.isSaveAnnotations());

            for (Map<String, Object> chunk: split(data)) {
                if (LOGGER.isLoggable(Level.FINEST)) {
                    String jsonDataString = JSONObject.fromObject(chunk).toString();
                    LOGGER.log(Level.FINEST, "Sending data: {0}", jsonDataString);
                }
                getEventWriter().push(chunk);
            }
        }

        protected List<Map<String, Object>> split(Map<String, Object> data) {
            List<Map<String, Object>> chunks = new ArrayList<>();

            if (data.containsKey(ConsoleNotes.MESSAGE_KEY)) {
                String message = (String) data.get(ConsoleNotes.MESSAGE_KEY);
                int maxLength = config.getSplitMessagesLongerThan();
                if (message.length() > maxLength) {
                    String messageId = UUID.randomUUID().toString();
                    Integer messageCount = 0;
                    for (String part: Splitter.fixedLength(maxLength).split(message)) {
                        Map<String, Object> chunk = new HashMap<>(data);
                        if (messageCount > 0) {
                            chunk.remove(ConsoleNotes.ANNOTATIONS_KEY);
                        }
                        chunk.put("messageId", messageId);
                        chunk.put("messageCount", messageCount);
                        chunk.put(ConsoleNotes.MESSAGE_KEY, part);
                        chunks.add(chunk);
                        messageCount++;
                    }
                }
            }

            if (chunks.size() == 0) {
                chunks.add(data);
            }

            return chunks;
        }

        @Override
        public void close() throws IOException {
            super.close();
            // TODO close forwarding logger also in case of exception
            if (forwardingLogger != null)
            {
                forwardingLogger.close();
            }
        }

        @Override
        public void flush() throws IOException {
            super.flush();
            // TODO flush forwarding logger also in case of exception
            if (forwardingLogger != null)
            {
                forwardingLogger.flush();
            }
        }
    }
}
