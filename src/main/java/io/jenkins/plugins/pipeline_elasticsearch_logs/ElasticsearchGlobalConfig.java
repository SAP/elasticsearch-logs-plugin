package io.jenkins.plugins.pipeline_elasticsearch_logs;

import javax.annotation.CheckForNull;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;

@Symbol("elasticsearchLogs")
@Extension
public class ElasticsearchGlobalConfig extends GlobalConfiguration {
    @CheckForNull
    private ElasticsearchConfig elasticsearch;

    public ElasticsearchGlobalConfig() {
        load();
    }

    @CheckForNull
    public ElasticsearchConfig getElasticsearch() {
        return elasticsearch;
    }

    public void setElasticsearch(ElasticsearchConfig elasticsearch) {
        this.elasticsearch = elasticsearch;
        save();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        elasticsearch = null;
        req.bindJSON(this, json);
        save();
        return true;
    }

    public static ElasticsearchGlobalConfig get() {
        return GlobalConfiguration.all().getInstance(ElasticsearchGlobalConfig.class);
    }
}
