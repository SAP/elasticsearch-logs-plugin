package io.jenkins.plugins.pipeline_elasticsearch_logs.utils;

import org.jenkinsci.plugins.uniqueid.IdStore;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import hudson.model.Run;

@Restricted(NoExternalUse.class)
public class RunUtils {

    private RunUtils() {}

    public static String getUniqueRunId(Run<?, ?> run) {
        String runId = IdStore.getId(run);
        if (runId == null) {
            IdStore.makeId(run);
            runId = IdStore.getId(run);
        }
        return runId;
    }
}
