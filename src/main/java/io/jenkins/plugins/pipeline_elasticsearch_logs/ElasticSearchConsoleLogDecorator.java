package io.jenkins.plugins.pipeline_elasticsearch_logs;

import hudson.Extension;
import hudson.console.ConsoleLogFilter;
import hudson.model.Run;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

@Extension(ordinal = 1000)
public class ElasticSearchConsoleLogDecorator extends ConsoleLogFilter implements Serializable {
    private static final long serialVersionUID = 1L;

    @Override
    public OutputStream decorateLogger(Run run, OutputStream logger) throws IOException, InterruptedException {
        ElasticSearchRunConfiguration config = ElasticSearchGlobalConfiguration.getRunConfiguration(run);
        if (config != null) {
            ElasticSearchSender sender = new ElasticSearchSender(null, config);
            return sender.getWrappedLogger(logger);
        } else
            return logger;
    }

    protected ElasticSearchGlobalConfiguration getConfiguration() {
        return ElasticSearchGlobalConfiguration.get();
    }

}
