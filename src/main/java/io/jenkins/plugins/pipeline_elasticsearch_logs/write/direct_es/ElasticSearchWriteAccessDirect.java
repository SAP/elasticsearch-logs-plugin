package io.jenkins.plugins.pipeline_elasticsearch_logs.write.direct_es;

import static com.google.common.collect.Range.closedOpen;
import static io.jenkins.plugins.pipeline_elasticsearch_logs.Utils.logExceptionAndReraiseWithTruncatedDetails;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.common.collect.Range;

import hudson.Extension;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.pipeline_elasticsearch_logs.SSLHelper;
import io.jenkins.plugins.pipeline_elasticsearch_logs.SerializableSupplier;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.ElasticSearchWriteAccess;
import net.sf.json.JSONObject;

/**
 * Post data to Elasticsearch.
 */
public class ElasticSearchWriteAccessDirect extends ElasticSearchWriteAccess {
    private static final Logger LOGGER = Logger.getLogger(ElasticSearchWriteAccessDirect.class.getName());

    private static final Range<Integer> SUCCESS_CODES = closedOpen(200, 300);

    private final URI indexUrl;

    @CheckForNull
    private Integer connectTimeoutMillis;

    @CheckForNull
    private Integer requestTimeoutMillis;

    @CheckForNull
    private Integer socketTimeoutMillis;

    @CheckForNull
    private String authCredentialsId;

    @CheckForNull
    private String username;

    @CheckForNull
    private String password;

    @CheckForNull
    private String trustStoreCredentialsId;

    @CheckForNull
    private byte[] trustStoreBytes;

    @CheckForNull
    private transient KeyStore trustStore;

    @CheckForNull
    private transient CloseableHttpClient httpClient;

	private transient HttpClientContext httpClientContext;

    @DataBoundConstructor
    public ElasticSearchWriteAccessDirect(String indexUrl) throws URISyntaxException {
        // Configuration as Code can return null instead of an empty string
        this.indexUrl = new URI(StringUtils.defaultString(indexUrl));
    }

    private ElasticSearchWriteAccessDirect(
        URI indexUrl,
        Integer connectTimeoutMillis,
        Integer requestTimeoutMillis,
        Integer socketTimeoutMillis,
        String user,
        String password,
        byte[] trustStoreBytes
    ) {
        this.indexUrl = indexUrl;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.requestTimeoutMillis = requestTimeoutMillis;
        this.socketTimeoutMillis = socketTimeoutMillis;
        this.username = user;
        this.password = password;
        this.trustStoreBytes = trustStoreBytes == null ? null : trustStoreBytes.clone();
    }

    @Nonnull
    public String getIndexUrl() {
        return this.indexUrl.toString();
    }

    @Nonnull
    public Integer getConnectTimeoutMillis() {
        return this.connectTimeoutMillis;
    }

    @DataBoundSetter
    public void setConnectTimeoutMillis(@CheckForNull Integer timeoutMillis) {
        this.connectTimeoutMillis = ensureValidTimeoutMillis(timeoutMillis);
    }

    @Nonnull
    public Integer getRequestTimeoutMillis() {
        return this.requestTimeoutMillis;
    }

    @DataBoundSetter
    public void setRequestTimeoutMillis(@CheckForNull Integer timeoutMillis) {
        this.requestTimeoutMillis = ensureValidTimeoutMillis(timeoutMillis);
    }

    @Nonnull
    public Integer getSocketTimeoutMillis() {
        return this.socketTimeoutMillis;
    }

    @DataBoundSetter
    public void setSocketTimeoutMillis(@CheckForNull Integer timeoutMillis) {
        this.socketTimeoutMillis = ensureValidTimeoutMillis(timeoutMillis);
    }

    @Nonnull
    public static Integer ensureValidTimeoutMillis(@CheckForNull Integer value) {
        if (value == null || value < 0) {
            return null;
        }
        return value;
    }

    public String getTrustStoreCredentialsId() {
        return trustStoreCredentialsId;
    }

    @DataBoundSetter
    public void setTrustStoreCredentialsId(String credentialsId) {
        this.trustStoreCredentialsId = credentialsId;
    }

    @CheckForNull
    public String getAuthCredentialsId() {
        return authCredentialsId;
    }

    @DataBoundSetter
    public void setAuthCredentialsId(String credentialsId) {
        this.authCredentialsId = credentialsId;
    }

    @CheckForNull
    private StandardUsernamePasswordCredentials getAuthCredentials() {
        if (this.authCredentialsId == null) {
            return null;
        }
        return CredentialsHelper.findCredentials(
            StandardUsernamePasswordCredentials.class,
            authCredentialsId
        );
    }

    private KeyStore getTrustStore() {
        if (this.trustStore == null) {
            this.trustStore = createTrustStore();
        }
        return this.trustStore;
    }

    private KeyStore createTrustStore() {
        if (this.trustStoreBytes != null) {
            try {
                KeyStore trustStore = KeyStore.getInstance("PKCS12");
                trustStore.load(new ByteArrayInputStream(this.trustStoreBytes), "".toCharArray());
                return trustStore;
            } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
                LOGGER.log(Level.WARNING, "Failed to create trust store from bytes", e);
            }
        }
        return getCustomTrustStore();
    }

    private byte[] getTrustStoreBytes() throws IOException {
        if (!isSsl()) return null;

        KeyStore trustStore = getCustomTrustStore();
        if (trustStore == null) return null;

        ByteArrayOutputStream b = new ByteArrayOutputStream(2048);
        try {
            trustStore.store(b, "".toCharArray());
            return b.toByteArray();
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
            LOGGER.log(Level.WARNING, "Could not serialize trust store", e);
            throw new IOException("Could not serialize trust store", e);
        }
    }

    private boolean isSsl() {
        String scheme = this.indexUrl.getScheme();
        return "https".equals(scheme);
    }

    private KeyStore getCustomTrustStore() {
        KeyStore trustStore = null;
        if (!StringUtils.isBlank(this.trustStoreCredentialsId)) {
            StandardCertificateCredentials certificateCredentials =
                    CredentialsHelper.findCredentials(
                        StandardCertificateCredentials.class,
                        this.trustStoreCredentialsId
                    );
            if (certificateCredentials != null) {
                trustStore = certificateCredentials.getKeyStore();
            }
        }
        return trustStore;
    }

    @Extension
    @Symbol("esDirectWrite")
    public static class DescriptorImpl extends ElasticSearchWriteAccessDescriptor {
        @Override
        public String getDisplayName() {
            return "Direct Elasticsearch Writer";
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
            return FormValidation.ok("Success");
        }
    }

    private HttpPost createHttpPostRequest(String data) {
        HttpPost postRequest = new HttpPost(indexUrl);
        // char encoding is set to UTF_8 since this request posts a JSON string
        StringEntity input = new StringEntity(data, StandardCharsets.UTF_8);
        input.setContentType(ContentType.APPLICATION_JSON.toString());
        postRequest.setEntity(input);
        return postRequest;
    }

    private CloseableHttpClient getHttpClient() {
        if (httpClient == null) {
            createHttpClientAndContext();
        }
        return httpClient;
    }

    private void createHttpClientAndContext() {
        if (httpClientContext == null) {
            httpClientContext = HttpClientContext.create();
        }

        HttpClientBuilder clientBuilder = HttpClientBuilder.create();

        RequestConfig.Builder requestBuilder = RequestConfig.custom();
        if (this.connectTimeoutMillis != null) {
            requestBuilder.setConnectTimeout(this.connectTimeoutMillis);
        }
        if (this.requestTimeoutMillis != null) {
            requestBuilder.setConnectionRequestTimeout(this.requestTimeoutMillis);
        }
        if (this.socketTimeoutMillis != null) {
            requestBuilder.setSocketTimeout(this.socketTimeoutMillis);
        }
        clientBuilder.setDefaultRequestConfig(requestBuilder.build());

        {
            String username = this.username;
            String password = this.password;
            if (StringUtils.isBlank(username)) {
                StandardUsernamePasswordCredentials credentials = getAuthCredentials();
                if (credentials != null) {
                    username = credentials.getUsername();
                    password = credentials.getPassword().getPlainText();
                }
            }
            if (StringUtils.isNotBlank(username)) {
                HttpHost targetHost = new HttpHost(indexUrl.getHost(), indexUrl.getPort(), indexUrl.getScheme());
                org.apache.http.client.CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(
                    new AuthScope(targetHost),
                    new UsernamePasswordCredentials(username, StringUtils.defaultString(password))
                );

                AuthCache authCache = new BasicAuthCache();
                authCache.put(targetHost, new BasicScheme());
                httpClientContext.setCredentialsProvider(credentialsProvider);
                httpClientContext.setAuthCache(authCache);
            }
        }

        {
            KeyStore trustStore = getTrustStore();
            if (trustStore != null) {
                try {
                    SSLHelper.setClientBuilderSSLContext(clientBuilder, trustStore);
                } catch (KeyManagementException | CertificateException | NoSuchAlgorithmException | KeyStoreException | IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to set SSLContext for HTTP client. Will try without.", e);
                }
            }
        }

        httpClient = clientBuilder.build();
        if (LOGGER.isLoggable(Level.FINE)) LOGGER.fine("New HTTP client created");
    }

    @Restricted(NoExternalUse.class)
    public String testConnection() throws URISyntaxException, IOException {
        URI testUri = new URI(indexUrl.getScheme(), indexUrl.getUserInfo(), indexUrl.getHost(), indexUrl.getPort(), null, null, null);
        HttpGet request = new HttpGet(testUri);

        CloseableHttpResponse response = null;
        try {
            CloseableHttpClient httpClient = getHttpClient();
            response = httpClient.execute(request, httpClientContext);
            if (!SUCCESS_CODES.contains(response.getStatusLine().getStatusCode())) {
                String errorMessage = this.getErrorMessage(response);
                throw new IOException(errorMessage);
            }
        } catch (Exception e) {
            logExceptionAndReraiseWithTruncatedDetails(LOGGER, Level.SEVERE, "Test GET request to Elasticsearch failed", e);
        } finally {
            if (response != null) EntityUtils.consumeQuietly(response.getEntity());
        }

        return "";
    }

    /**
     * Posts the given Map to elastic search.
     *
     * @param data
     *            The data to post
     * @throws IOException
     */
    @Override
    public void push(Map<String, Object> data) throws IOException {
        String dataString = JSONObject.fromObject(data).toString();
        HttpPost post = createHttpPostRequest(dataString);

        CloseableHttpResponse response = null;
        try {
            CloseableHttpClient httpClient = getHttpClient();
            response = httpClient.execute(post, httpClientContext);
            if (!SUCCESS_CODES.contains(response.getStatusLine().getStatusCode())) {
                String errorMessage = this.getErrorMessage(response);
                throw new IOException(errorMessage);
            }
        } catch (Exception e) {
            logExceptionAndReraiseWithTruncatedDetails(LOGGER, Level.SEVERE, "Could not send event to Elasticsearch", e);
        } finally {
            if(response != null) EntityUtils.consumeQuietly(response.getEntity());
        }
    }

    private String getErrorMessage(CloseableHttpResponse response) {
        try (
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            PrintStream stream = new PrintStream(byteStream, true, StandardCharsets.UTF_8.name())
        ) {
            try {
                stream.print("HTTP error code: ");
                stream.println(response.getStatusLine().getStatusCode());
                stream.print("URI: ");
                stream.println(indexUrl.toString());
                stream.println("RESPONSE: " + response.toString());
                response.getEntity().writeTo(stream);
            } catch (IOException e) {
                stream.println(ExceptionUtils.getStackTrace(e));
            }
            stream.flush();
            return byteStream.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            return ExceptionUtils.getStackTrace(e);
        }
    }

    private static class MeSupplier implements SerializableSupplier<ElasticSearchWriteAccess> {

        private final URI indexUrl;

        @CheckForNull
        private final Integer connectTimeoutMillis;

        @CheckForNull
        private final Integer requestTimeoutMillis;

        @CheckForNull
        private final Integer socketTimeoutMillis;

        @CheckForNull
        private final String username;

        @CheckForNull
        private final String password;

        @CheckForNull
        private final byte[] trustStoreBytes;

        private MeSupplier(ElasticSearchWriteAccessDirect me) throws IOException {
            this.indexUrl = me.indexUrl;
            this.connectTimeoutMillis = me.connectTimeoutMillis;
            this.requestTimeoutMillis = me.requestTimeoutMillis;
            this.socketTimeoutMillis = me.socketTimeoutMillis;

            // credentials must be loaded here because they are not accessible on agents
            if (StringUtils.isNotBlank(me.username)) {
                this.username = me.username;
                this.password = me.password;
            } else {
                StandardUsernamePasswordCredentials credentials = me.getAuthCredentials();
                if (credentials != null) {
                    this.username = credentials.getUsername();
                    this.password = credentials.getPassword().getPlainText();
                } else {
                    this.username = null;
                    this.password = null;
                }
            }

            // trust store must be loaded here to have it available on agents
            if (me.trustStoreBytes != null) {
                this.trustStoreBytes = me.trustStoreBytes;
            } else {
                this.trustStoreBytes = me.getTrustStoreBytes();
            }
        }

        @Override
        public ElasticSearchWriteAccess get() {
            return new ElasticSearchWriteAccessDirect(
                indexUrl,
                connectTimeoutMillis,
                requestTimeoutMillis,
                socketTimeoutMillis,
                username,
                password,
                trustStoreBytes
            );
        }
    }

    @Override
    public SerializableSupplier<ElasticSearchWriteAccess> getSupplier() throws IOException {
        return new MeSupplier(this);
    }

    @Override
    public void close() throws IOException {
        IOException firstException = null;

        if (this.httpClient != null) {
            try {
                this.httpClient.close();
            }
            catch (IOException ex) {
                if (firstException == null) {
                    firstException = ex;
                }
            }
        }

        if (firstException != null) {
            throw firstException;
        }
    }
}
