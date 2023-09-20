package io.jenkins.plugins.pipeline_elasticsearch_logs.write.index_api;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.Run;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.EventWriterGlobalConfig;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.EventWriterRunConfig;

/**
 * The global config for {@link IndexAPIEventWriter}.
 */
public class IndexAPIEventWriterGlobalConfig extends EventWriterGlobalConfig {

    private final URI indexUrl;

    @CheckForNull
    private final Integer connectTimeoutMillis;

    @CheckForNull
    private final Integer requestTimeoutMillis;

    @CheckForNull
    private final Integer socketTimeoutMillis;

    @CheckForNull
    private final String authCredentialsId;

    @CheckForNull
    private final String trustStoreCredentialsId;

    @DataBoundConstructor
    public IndexAPIEventWriterGlobalConfig(
        String indexUrl,
        Integer connectTimeoutMillis,
        Integer requestTimeoutMillis,
        Integer socketTimeoutMillis,
        String authCredentialsId,
        String trustStoreCredentialsId
    ) throws URISyntaxException {
        // Configuration as Code can return null instead of an empty string
        this.indexUrl = new URI(StringUtils.defaultString(indexUrl));
        this.connectTimeoutMillis = ensureValidTimeoutMillis(connectTimeoutMillis);
        this.requestTimeoutMillis = ensureValidTimeoutMillis(requestTimeoutMillis);
        this.socketTimeoutMillis = ensureValidTimeoutMillis(socketTimeoutMillis);
        this.authCredentialsId = authCredentialsId;
        this.trustStoreCredentialsId = trustStoreCredentialsId;
    }

    @Nonnull
    public URI getIndexUrl() {
        return this.indexUrl;
    }

    @Nonnull
    public Integer getConnectTimeoutMillis() {
        return this.connectTimeoutMillis;
    }

    @Nonnull
    public Integer getRequestTimeoutMillis() {
        return this.requestTimeoutMillis;
    }

    @Nonnull
    public Integer getSocketTimeoutMillis() {
        return this.socketTimeoutMillis;
    }

    public String getTrustStoreCredentialsId() {
        return trustStoreCredentialsId;
    }

    @CheckForNull
    public String getAuthCredentialsId() {
        return authCredentialsId;
    }

    @CheckForNull
    StandardUsernamePasswordCredentials getAuthCredentials() {
        if (StringUtils.isNotBlank(this.authCredentialsId)) {
            return CredentialsHelper.findCredentials(
                StandardUsernamePasswordCredentials.class,
                this.authCredentialsId
            );
        }
        return null;
    }

    @CheckForNull
    StandardCertificateCredentials getTrustStoreCredentials() {
        if (StringUtils.isNotBlank(this.trustStoreCredentialsId)) {
            return CredentialsHelper.findCredentials(
                StandardCertificateCredentials.class,
                this.trustStoreCredentialsId
            );
        }
        return null;
    }

    @Nonnull
    public static Integer ensureValidTimeoutMillis(@CheckForNull Integer value) {
        if (value == null || value < 0) {
            return null;
        }
        return value;
    }

    @Extension
    @Symbol("indexAPIEventWriter")
    public static class DescriptorImpl extends EventWriterGlobalConfigDescriptor {
        @Override
        public String getDisplayName() {
            return "Index API";
        }

        public static ListBoxModel doFillAuthCredentialsIdItems(
            @QueryParameter String authCredentialsId
        ) {
            StandardUsernameListBoxModel model = new StandardUsernameListBoxModel();
            model
                .includeEmptyValue()
                .includeAs(
                    ACL.SYSTEM,
                    (Item) null,
                    StandardUsernamePasswordCredentials.class,
                    Collections.emptyList()
                )
                .includeCurrentValue(authCredentialsId);
            return model;
        }

        public static ListBoxModel doFillTrustStoreCredentialsIdItems(
            @QueryParameter String trustStoreCredentialsId
        ) {
            StandardListBoxModel model = new StandardListBoxModel();
            model
                .includeEmptyValue()
                .includeAs(
                    ACL.SYSTEM,
                    (Item) null,
                    StandardCertificateCredentials.class
                )
                .includeCurrentValue(trustStoreCredentialsId);

            return model;
        }

        public FormValidation doCheckConnectTimeoutMillis(
            @QueryParameter("value") Integer value
        ) {
            return doCheckTimeoutMillis(value);
        }

        public FormValidation doCheckRequestTimeoutMillis(
            @QueryParameter("value") Integer value
        ) {
            return doCheckTimeoutMillis(value);
        }

        public FormValidation doCheckSocketTimeoutMillis(
            @QueryParameter("value") Integer value
        ) {
            return doCheckTimeoutMillis(value);
        }

        private FormValidation doCheckTimeoutMillis(Integer value)
        {
            if (value != null) {
                Integer newValue = ensureValidTimeoutMillis(value);
                if (newValue != value) {
                    return FormValidation.warning("Illegal value - default will used instead.");
                }
                if (value == 0) {
                    return FormValidation.warning("Do you really want no timeout? Connection attempts may hang infinitely.");
                }
                if (value > 0 && value < 100) {
                    return FormValidation.warning("Do you really want to use such a short timeout?");
                }
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckUrl(
            @QueryParameter("value") String value
        ) {
            if (StringUtils.isBlank(value)) {
                return FormValidation.warning("URL must not be empty");
            }
            try {
                URL url = new URL(value);
                if (!url.getProtocol().equals("http") && !url.getProtocol().equals("https")) {
                    return FormValidation.error("Protocol must be one of 'http', 'https'.");
                }
            } catch (MalformedURLException e) {
                return FormValidation.error("URL is not well-formed.");
            }
            return FormValidation.ok();
        }

        public FormValidation doValidateConnection(
            @QueryParameter(fixEmpty = true) String url,
            @QueryParameter(fixEmpty = true) String authCredentialsId,
            @QueryParameter(fixEmpty = true) String trustStoreCredentialsId
        ) {
            // TODO Implement a validation
            return FormValidation.ok("Success");
        }
    }

    @Override
    public EventWriterRunConfig createRunConfig(Run<?, ?> run) {
        return new IndexAPIEventWriterRunConfig(this);
    }
}
