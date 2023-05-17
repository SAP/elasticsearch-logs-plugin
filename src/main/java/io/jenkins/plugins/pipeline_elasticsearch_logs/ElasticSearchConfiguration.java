package io.jenkins.plugins.pipeline_elasticsearch_logs;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.matchers.IdMatcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.uniqueid.IdStore;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.Run;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import io.jenkins.plugins.pipeline_elasticsearch_logs.runid.DefaultRunIdProvider;
import io.jenkins.plugins.pipeline_elasticsearch_logs.runid.RunIdProvider;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.ElasticSearchWriteAccess;
import jenkins.model.Jenkins;

public class ElasticSearchConfiguration extends AbstractDescribableImpl<ElasticSearchConfiguration> {
    private static final transient Logger LOGGER = Logger.getLogger(ElasticSearchConfiguration.class.getName());

    private transient String host;

    private transient int port;

    private transient String key;

    private transient boolean ssl;

    private Boolean saveAnnotations = true;

    private Boolean writeAnnotationsToLogFile = true;

    private RunIdProvider runIdProvider;

    private ElasticSearchWriteAccess elasticsearchWriteAccess;



    @DataBoundConstructor
    public ElasticSearchConfiguration() throws URISyntaxException {
    }

    public RunIdProvider getRunIdProvider() {
        return runIdProvider;
    }

    @DataBoundSetter
    public void setRunIdProvider(RunIdProvider runIdProvider) {
        this.runIdProvider = runIdProvider;
    }

    public ElasticSearchWriteAccess getElasticsearchWriteAccess() {
        return elasticsearchWriteAccess;
    }

    @DataBoundSetter
    public void setElasticsearchWriteAccess(ElasticSearchWriteAccess elasticsearchWriteAccess) {
        this.elasticsearchWriteAccess = elasticsearchWriteAccess;
    }

    public boolean isSaveAnnotations() {
        return saveAnnotations;
    }

    @DataBoundSetter
    public void setSaveAnnotations(boolean saveAnnotations) {
        this.saveAnnotations = saveAnnotations;
    }

    public boolean isWriteAnnotationsToLogFile() {
        return writeAnnotationsToLogFile;
    }

    @DataBoundSetter
    public void setWriteAnnotationsToLogFile(boolean writeAnnotationsToLogFile) {
        this.writeAnnotationsToLogFile = writeAnnotationsToLogFile;
    }

    /**
     * Returns a serializable representation of the plugin configuration with credentials resolved.
     * Reason: on remote side credentials cannot be accessed by credentialsId, same for keystore.
     * That's why the values are transfered to remote.
     *
     * @return the ElasticSearchSerializableConfiguration
     * @throws IOException
     */
    public ElasticSearchRunConfiguration getRunConfiguration(Run<?, ?> run) throws IOException {

        return new ElasticSearchRunConfiguration(isSaveAnnotations(), getUniqueRunId(run),
                getRunIdProvider().getRunId(run), getWriteAccessFactory(), isWriteAnnotationsToLogFile());
    }

    // Can be overwritten in tests
    @CheckForNull
    @Restricted(NoExternalUse.class)
    protected Supplier<ElasticSearchWriteAccess> getWriteAccessFactory() throws IOException {
        return elasticsearchWriteAccess.getSupplier();
    }

    public static String getUniqueRunId(Run<?, ?> run) {
        String runId = IdStore.getId(run);
        if (runId == null) {
            IdStore.makeId(run);
            runId = IdStore.getId(run);
        }

        return runId;
    }

    @Extension
    @Symbol("elasticsearch")
    public static class DescriptorImpl extends Descriptor<ElasticSearchConfiguration> {
    }

}


