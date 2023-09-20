package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

import hudson.Extension;
import hudson.console.ConsoleLogFilter;
import hudson.model.Run;

@Extension(ordinal = 1000)
public class ElasticSearchConsoleLogDecorator extends ConsoleLogFilter implements Serializable {
    private static final long serialVersionUID = 1L;

    @Override
    public OutputStream decorateLogger(Run run, OutputStream logger) throws IOException, InterruptedException {
        ElasticSearchRunConfiguration config = ElasticSearchRunConfiguration.get(run);
        if (config != null) {
            ElasticSearchSender sender = new ElasticSearchSender(null, config, null);
            // sender must be closed once the returned output stream gets closed
            return new CloserOutputStream(
                sender.getWrappedLogger(logger),
                sender
            );
        } else {
            return logger;
        }
    }

    private static class CloserOutputStream extends FilterOutputStream {

        private AutoCloseable closeable;

        public CloserOutputStream(OutputStream out, AutoCloseable closeable) {
            super(out);
            this.closeable = closeable;
        }

        @Override
        public void close() throws IOException {
            Exception firstException = null;

            try {
                super.close();
            }
            catch (Exception ex) {
                if (firstException == null) firstException = ex;
            }

            try {
                this.closeable.close();
            }
            catch (Exception ex) {
                if (firstException == null) firstException = ex;
            }

            if (firstException != null) {
                throw new IOException(firstException);
            }
        }
    }
}
