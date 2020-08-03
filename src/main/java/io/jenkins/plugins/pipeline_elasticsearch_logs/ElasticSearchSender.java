package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.jenkinsci.remoting.SerializableOnlyOverRemoting;

import hudson.CloseProofOutputStream;
import hudson.console.LineTransformationOutputStream;
import hudson.model.BuildListener;
import hudson.remoting.RemoteOutputStream;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.ElasticSearchWriteAccess;
import net.sf.json.JSONObject;

public class ElasticSearchSender implements BuildListener, Closeable {
    private static final String EVENT_PREFIX_BUILD = "build";

    private static final String EVENT_PREFIX_NODE = "node";

    private static final Logger LOGGER = Logger.getLogger(ElasticSearchSender.class.getName());

    private static final long serialVersionUID = 1;

    private transient @CheckForNull PrintStream logger;
    private final @CheckForNull NodeInfo nodeInfo;

    protected transient ElasticSearchWriteAccess writer;
    protected final ElasticSearchRunConfiguration config;
    protected String eventPrefix;
    private final @CheckForNull OutputStream out;

    public ElasticSearchSender(@CheckForNull NodeInfo nodeInfo, @Nonnull ElasticSearchRunConfiguration config, @CheckForNull OutputStream out)
                    throws IOException {
        this.nodeInfo = nodeInfo;
        this.config = config;
        if (nodeInfo != null) {
            eventPrefix = EVENT_PREFIX_NODE;
        } else {
            eventPrefix = EVENT_PREFIX_BUILD;
        }
        this.out = out;
    }

    public PrintStream getWrappedLogger(@CheckForNull OutputStream logger) {
        try {
            return new PrintStream(new ElasticSearchOutputStream(logger), false, "UTF-8");
        } catch (UnsupportedEncodingException x) {
            throw new AssertionError(x);
        }
    }

    @Override
    public PrintStream getLogger() {
        if (logger == null) {
            logger = getWrappedLogger(out);
        }
        return logger;
    }

    @Override
    public void close() throws IOException {
        if (logger != null) {
          logger.close();
          logger = null;
        }
        if (writer != null) {
            try {
                writer.close();
            } finally {
                writer = null;
            }
        }
    }

    private ElasticSearchWriteAccess getElasticSearchWriter() throws IOException {
        if (writer == null) {
            try {
                writer = config.createWriteAccess();
            } catch (URISyntaxException e) {
                throw new IOException(e);
            }
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
        private final ElasticSearchRunConfiguration config;
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

            ConsoleNotes.parse(b, len, data, config.isSaveAnnotations());
            data.put(ElasticSearchGraphListener.EVENT_TYPE, eventPrefix + EVENT_TYPE_MESSAGE);
            if (nodeInfo != null) {
                nodeInfo.appendNodeInfo(data);
            }

            if (LOGGER.isLoggable(Level.FINEST)) {
                String jsonDataString = JSONObject.fromObject(data).toString();
                LOGGER.log(Level.FINEST, "Sending data: {0}", jsonDataString);
            }
            getElasticSearchWriter().push(data);
        }


        @Override
        public void close() throws IOException {
            super.close();
            if (forwardingLogger != null)
            {
                forwardingLogger.close();
            }
        }

        @Override
        public void flush() throws IOException {
            super.flush();
            if (forwardingLogger != null)
            {
                forwardingLogger.flush();
            }
        }


    }
}
