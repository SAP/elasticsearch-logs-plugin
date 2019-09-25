package io.jenkins.plugins.pipeline_elasticsearch_logs;

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

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.matchers.IdMatcher;

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
import jenkins.model.Jenkins;

public class ElasticSearchConfiguration extends AbstractDescribableImpl<ElasticSearchConfiguration> {
    private static final transient Logger LOGGER = Logger.getLogger(ElasticSearchConfiguration.class.getName());

    private transient String host;

    private transient int port;

    private transient String key;

    private transient boolean ssl;

    private String certificateId;

    @CheckForNull
    private String credentialsId;

    private Boolean saveAnnotations = true;

    private Boolean readLogsFromElasticsearch = false;

    private RunIdProvider runIdProvider;

    private String url;

    @DataBoundConstructor
    public ElasticSearchConfiguration(String url) throws URISyntaxException {
        this.url = url;
        new URI(url);
    }

    public RunIdProvider getRunIdProvider() {
        return runIdProvider;
    }

    @DataBoundSetter
    public void setRunIdProvider(RunIdProvider runIdProvider) {
        this.runIdProvider = runIdProvider;
    }

    protected Object readResolve() {
        if (saveAnnotations == null) {
            saveAnnotations = true;
        }
        if (runIdProvider == null) {
            runIdProvider = new DefaultRunIdProvider("");
        }

        if (url == null) {
            String protocol = "http";
            if (ssl) {
                protocol = "https";
            }
            url = protocol + "://" + host + ":" + port + "/" + key;
        }

        return this;
    }

    public String getUrl() {
        return url;
    }

    public boolean isSaveAnnotations() {
        return saveAnnotations;
    }

    @DataBoundSetter
    public void setSaveAnnotations(boolean saveAnnotations) {
        this.saveAnnotations = saveAnnotations;
    }

    public boolean isReadLogsFromElasticsearch() {
        return readLogsFromElasticsearch;
    }

    @DataBoundSetter
    public void setReadLogsFromElasticsearch(boolean readLogsFromElasticsearch) {
        this.readLogsFromElasticsearch = readLogsFromElasticsearch;
    }

    public String getCertificateId() {
        return certificateId;
    }

    @DataBoundSetter
    public void setCertificateId(String certificateId) {
        this.certificateId = certificateId;
    }

    @CheckForNull
    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    @CheckForNull
    private StandardUsernamePasswordCredentials getCredentials() {
        if (credentialsId == null) {
            return null;
        }
        return getCredentials(credentialsId);
    }

    @CheckForNull
    private static StandardUsernamePasswordCredentials getCredentials(@Nonnull String id) {
        StandardUsernamePasswordCredentials credential = null;
        List<StandardUsernamePasswordCredentials> credentials = CredentialsProvider.lookupCredentials(
                StandardUsernamePasswordCredentials.class, Jenkins.get(), ACL.SYSTEM, Collections.emptyList());
        IdMatcher matcher = new IdMatcher(id);
        for (StandardUsernamePasswordCredentials c : credentials) {
            if (matcher.matches(c)) {
                credential = c;
            }
        }
        return credential;
    }

    @CheckForNull
    private static StandardCertificateCredentials getCertificateCredentials(@Nonnull String id) {
        StandardCertificateCredentials credential = null;
        List<StandardCertificateCredentials> credentials = CredentialsProvider.lookupCredentials(
                StandardCertificateCredentials.class, Jenkins.get(), ACL.SYSTEM, Collections.emptyList());
        IdMatcher matcher = new IdMatcher(id);
        for (StandardCertificateCredentials c : credentials) {
            if (matcher.matches(c)) {
                credential = c;
            }
        }
        return credential;
    }

    private KeyStore getCustomKeyStore() {
        KeyStore customKeyStore = null;

        if (!StringUtils.isBlank(certificateId)) {
            StandardCertificateCredentials certificateCredentials = getCertificateCredentials(certificateId);
            if (certificateCredentials != null) {
                customKeyStore = certificateCredentials.getKeyStore();
            }
        }
        return customKeyStore;
    }

    private boolean isSsl() {
        URI uri = URI.create(url);
        String scheme = uri.getScheme();
        return "https".equals(scheme);
    }

    private byte[] getKeyStoreBytes() throws IOException {
        KeyStore keyStore = getCustomKeyStore();
        if (isSsl() && keyStore != null) {
            ByteArrayOutputStream b = new ByteArrayOutputStream(2048);
            try {
                keyStore.store(b, "".toCharArray());
                return b.toByteArray();
            } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
                LOGGER.log(Level.WARNING, "Could not read keystore", e);
                if (e instanceof IOException)
                    throw (IOException) e;
                throw new IOException("Could not read keystore", e);
            }
        }
        return null;
    }

    /**
     * Returns a serializable representation of the plugin configuration with
     * credentials resolved. Reason: on remote side credentials cannot be accessed
     * by credentialsId, same for keystore. That's why the values are transfered to
     * remote.
     *
     * @return the ElasticSearchSerializableConfiguration
     * @throws IOException
     */
    public ElasticSearchRunConfiguration getRunConfiguration(Run<?, ?> run) throws IOException {
        String username = null;
        String password = null;
        StandardUsernamePasswordCredentials credentials = getCredentials();
        if (credentials != null) {
            username = credentials.getUsername();
            password = Secret.toString(credentials.getPassword());
        }

        URI uri = null;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }

        return new ElasticSearchRunConfiguration(uri, username, password, getKeyStoreBytes(), isSaveAnnotations(),
                getUniqueRunId(run), getRunIdProvider().getRunId(run), isReadLogsFromElasticsearch(),
                getAccessFactory());
    }

    // Can be overwritten in tests
    @CheckForNull
    @Restricted(NoExternalUse.class)
    protected Supplier<ElasticSearchAccess> getAccessFactory() {
        return null;
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
        public static ListBoxModel doFillCredentialsIdItems(@QueryParameter String credentialsId) {
            StandardUsernameListBoxModel model = new StandardUsernameListBoxModel();

            model.includeEmptyValue().includeAs(ACL.SYSTEM, (Item) null, StandardUsernamePasswordCredentials.class,
                    Collections.emptyList()).includeCurrentValue(credentialsId);

            return model;
        }

        public static ListBoxModel doFillCertificateIdItems(@QueryParameter String certificateId) {
            StandardListBoxModel model = new StandardListBoxModel();
            model.includeEmptyValue().includeAs(ACL.SYSTEM, (Item) null, StandardCertificateCredentials.class)
                    .includeCurrentValue(certificateId);

            return model;
        }

        public FormValidation doCheckUrl(@QueryParameter("value") String value) {
            if (StringUtils.isBlank(value)) {
                return FormValidation.warning("URL must not be empty");
            }
            try {
                URL url = new URL(value);
                if (!url.getProtocol().equals("http") && !url.getProtocol().equals("https")) {
                    return FormValidation.error("Only http/https are allowed protocols");
                }

            } catch (MalformedURLException e) {
                return FormValidation.error("URL is not well formed");
            }
            return FormValidation.ok();
        }

        public FormValidation doValidateConnection(@QueryParameter(fixEmpty = true) String url,
                @QueryParameter(fixEmpty = true) String credentialsId,
                @QueryParameter(fixEmpty = true) String certificateId) {

            String username = null;
            String password = null;
            if (credentialsId != null) {
                StandardUsernamePasswordCredentials credentials = getCredentials(credentialsId);
                if (credentials != null) {
                    username = credentials.getUsername();
                    password = Secret.toString(credentials.getPassword());
                }
            }
            KeyStore trustStore = null;

            if (!StringUtils.isBlank(certificateId)) {
                StandardCertificateCredentials certificateCredentials = getCertificateCredentials(certificateId);
                if (certificateCredentials != null) {
                    trustStore = certificateCredentials.getKeyStore();
                }
            }

            try {
                ElasticSearchAccess writer = new ElasticSearchAccess(new URI(url), username, password);
                writer.setTrustKeyStore(trustStore);
                writer.testConnection();
            } catch (URISyntaxException e) {
                return FormValidation.error(e, "The URL could not be parsed.");
            } catch (IOException e) {
                return FormValidation.error(e, "Connection failed.");
            }

            return FormValidation.ok("Success");
        }
    }

}
