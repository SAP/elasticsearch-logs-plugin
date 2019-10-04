package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.io.IOException;

import javax.annotation.CheckForNull;

import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.model.Run;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;

@Symbol("elasticSearchLogs")
@Extension
public class ElasticSearchGlobalConfiguration extends GlobalConfiguration {
    @CheckForNull
    private ElasticSearchConfiguration elasticSearch;

    public ElasticSearchGlobalConfiguration() {
        load();
    }

    @CheckForNull
    public ElasticSearchConfiguration getElasticSearch() {
        return elasticSearch;
    }

    public void setElasticSearch(ElasticSearchConfiguration elasticSearch) {
        this.elasticSearch = elasticSearch;
        save();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        elasticSearch = null;
        req.bindJSON(this, json);
        save();
        return true;
    }

    public static ElasticSearchGlobalConfiguration get() {
        return GlobalConfiguration.all().getInstance(ElasticSearchGlobalConfiguration.class);
    }

    @CheckForNull
    @Restricted(NoExternalUse.class)
    public static ElasticSearchRunConfiguration getRunConfiguration(Run<?, ?> run) throws IOException {
        ElasticSearchConfiguration config = ElasticSearchGlobalConfiguration.get().getElasticSearch();
        if (config == null) {
            return null;
        }
        return config.getRunConfiguration(run);
    }
}
