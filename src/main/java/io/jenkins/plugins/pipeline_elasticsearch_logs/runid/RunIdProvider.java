package io.jenkins.plugins.pipeline_elasticsearch_logs.runid;

import java.nio.charset.StandardCharsets;

import org.apache.commons.codec.binary.Base64;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Run;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

/**
 * Each run needs to be uniquely identifiable in Elastic search. While in a
 * classical Jenkins with UI it this can be achieved with full project name,
 * build number and instance id, there might be use cases when working with
 * JenkinsFileRunner, where additional information should be added or a simple
 * guid is sufficient. This extension point allows to provide different
 * implementations how the runid looks like.
 *
 */
public abstract class RunIdProvider extends AbstractDescribableImpl<RunIdProvider> implements ExtensionPoint {

    public abstract JSONObject getRunId(Run<?, ?> run);

    public static ExtensionList<RunIdProvider> all() {
        return Jenkins.get().getExtensionList(RunIdProvider.class);
    }

    public static abstract class RunIdProviderDescriptor extends Descriptor<RunIdProvider> {
        protected RunIdProviderDescriptor() {

        }
    }

    protected static String getEffectInstanceId(String instanceId) {
        if (Util.fixEmptyAndTrim(instanceId) != null) {
            return instanceId;
        }
        InstanceIdentity id = InstanceIdentity.get();
        return new String(Base64.encodeBase64(id.getPublic().getEncoded()), StandardCharsets.UTF_8);
    }

}
