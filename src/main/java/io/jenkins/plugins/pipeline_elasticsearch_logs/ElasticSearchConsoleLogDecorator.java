package io.jenkins.plugins.pipeline_elasticsearch_logs;

import hudson.Extension;
import hudson.console.ConsoleLogFilter;
import hudson.model.Run;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.logging.Logger;

@Extension(ordinal = 1000)
public class ElasticSearchConsoleLogDecorator extends ConsoleLogFilter implements Serializable {
    private static final Logger LOGGER = Logger.getLogger(ElasticSearchConsoleLogDecorator.class.getName());
    private static final long serialVersionUID = 1L;

    @Override
    public OutputStream decorateLogger(Run run, OutputStream logger) throws IOException, InterruptedException {
        ElasticSearchRunConfiguration config = ElasticSearchGlobalConfiguration.getRunConfiguration(run);
        ElasticSearchSender sender = new ElasticSearchSender(null, config);
        return sender.getWrappedLogger(logger);
    }

    protected ElasticSearchGlobalConfiguration getConfiguration() {
        return ElasticSearchGlobalConfiguration.get();
    }

}
