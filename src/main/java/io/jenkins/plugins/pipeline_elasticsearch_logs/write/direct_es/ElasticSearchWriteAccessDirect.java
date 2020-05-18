package io.jenkins.plugins.pipeline_elasticsearch_logs.write.direct_es;

import static com.google.common.collect.Ranges.closedOpen;
import static io.jenkins.plugins.pipeline_elasticsearch_logs.Utils.logExceptionAndReraiseWithTruncatedDetails;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.matchers.IdMatcher;
import com.google.common.collect.Range;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
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

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.security.ACL;
import io.jenkins.plugins.pipeline_elasticsearch_logs.ElasticSearchConfiguration;
import io.jenkins.plugins.pipeline_elasticsearch_logs.ElasticSearchGlobalConfiguration;
import io.jenkins.plugins.pipeline_elasticsearch_logs.SSLHelper;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.ElasticSearchWriteAccess;
import jenkins.model.Jenkins;

/**
 * Post data to Elastic Search.
 */
public class ElasticSearchWriteAccessDirect extends ElasticSearchWriteAccess {
    private static final Logger LOGGER = Logger.getLogger(ElasticSearchWriteAccessDirect.class.getName());

    private static final Range<Integer> SUCCESS_CODES = closedOpen(200, 300);

    private final URI uri;

    @CheckForNull
    private String username;

    @CheckForNull
    private String password;

    private final int connectionTimeout;

    @CheckForNull
    private byte[] trustStoreBytes;

    private transient String auth;

    private transient KeyStore trustKeyStore;
    

    @CheckForNull
    private transient CloseableHttpClient client;

    @DataBoundConstructor
    public ElasticSearchWriteAccessDirect() throws URISyntaxException {
        ElasticSearchConfiguration config = ElasticSearchGlobalConfiguration.get().getElasticSearch();
        if(config != null) {
            String credentialsId = config.getCredentialsId();
            if(credentialsId != null) {
                StandardUsernamePasswordCredentials credentials = getCredentials(credentialsId);
                if(credentials != null) {
                    this.username = credentials.getUsername();
                    this.password = credentials.getPassword().getPlainText();
                }
            }
            this.uri = new URI(config.getUrl());
            this.connectionTimeout = config.getConnectionTimeoutMillis();
        } else {
            this.username = null;
            this.password = null;
            this.uri = null;
            this.connectionTimeout = -1;
        }
    }
    
    /*
     * For tests only
     */
    public ElasticSearchWriteAccessDirect(URI uri, String user, String password, int connectionTimeout, byte[] trustStoreBytes) {
        this.username = user;
        this.password = password;
        this.uri = uri;
        this.connectionTimeout = connectionTimeout;
        this.trustStoreBytes = trustStoreBytes == null ? null : trustStoreBytes.clone();
    }

    private KeyStore getTrustKeyStore() {
      if (trustKeyStore == null && trustStoreBytes != null) {
          try {
              trustKeyStore = KeyStore.getInstance("PKCS12");
              trustKeyStore.load(new ByteArrayInputStream(trustStoreBytes), "".toCharArray());
          } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
              LOGGER.log(Level.WARNING, "Failed to create KeyStore from bytes", e);
          }
      }
      return trustKeyStore;
    }

    @Extension
    @Symbol("esDirectWrite")
    public static class DescriptorImpl extends ElasticSearchWriteAccessDescriptor {
        @Override
        public String getDisplayName() {
            return "Direct Elasticsearch Writer";
        }
    }

    @CheckForNull
    private static StandardUsernamePasswordCredentials getCredentials(@Nonnull String id) {
        StandardUsernamePasswordCredentials credential = null;
        List<StandardUsernamePasswordCredentials> credentials = CredentialsProvider
                .lookupCredentials(StandardUsernamePasswordCredentials.class, Jenkins.get(), ACL.SYSTEM, Collections.emptyList());
        IdMatcher matcher = new IdMatcher(id);
        for (StandardUsernamePasswordCredentials c : credentials) {
            if (matcher.matches(c)) {
                credential = c;
            }
        }
        return credential;
    }

    @CheckForNull
    private String getAuth() {
        if (auth == null && StringUtils.isNotBlank(username)) {
            auth = Base64.encodeBase64String((username + ":" + StringUtils.defaultString(password)).getBytes(StandardCharsets.UTF_8));
        }
        return auth;
    }

    private HttpPost getHttpPost(String data) {
        HttpPost postRequest = new HttpPost(uri);
        // char encoding is set to UTF_8 since this request posts a JSON string
        StringEntity input = new StringEntity(data, StandardCharsets.UTF_8);
        input.setContentType(ContentType.APPLICATION_JSON.toString());
        postRequest.setEntity(input);
        auth = getAuth();
        if (auth != null) {
            postRequest.addHeader("Authorization", "Basic " + auth);
        }
        return postRequest;
    }

    private CloseableHttpClient getClient() {
        if(client == null) {
            HttpClientBuilder clientBuilder = HttpClientBuilder.create();
            RequestConfig.Builder requestBuilder = RequestConfig.custom();
            requestBuilder.setConnectTimeout(connectionTimeout);
            requestBuilder.setConnectionRequestTimeout(connectionTimeout);
            requestBuilder.setSocketTimeout(connectionTimeout);
            clientBuilder.setDefaultRequestConfig(requestBuilder.build());
            if (getTrustKeyStore() != null) {
                try {
                    SSLHelper.setClientBuilderSSLContext(clientBuilder, trustKeyStore);
                } catch (KeyManagementException | CertificateException | NoSuchAlgorithmException | KeyStoreException | IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to set SSLContext for http client. Will try without.", e);
                }
            }
            client = clientBuilder.build();
            if(LOGGER.isLoggable(Level.FINE)) LOGGER.fine("New Http client created");
        }
        return client;
    }
    

    @Restricted(NoExternalUse.class)
    public String testConnection() throws URISyntaxException, IOException {
        URI testUri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), null, null, null);
        HttpGet getRequest = new HttpGet(testUri);
        String auth = getAuth();
        if (auth != null) {
            getRequest.addHeader("Authorization", "Basic " + auth);
        }

        CloseableHttpResponse response = null;
        try {
            CloseableHttpClient httpClient = getClient();
            response = httpClient.execute(getRequest);
            if (!SUCCESS_CODES.contains(response.getStatusLine().getStatusCode())) {
                String errorMessage = this.getErrorMessage(response);
                throw new IOException(errorMessage);
            }
        } catch (Exception e) {
            logExceptionAndReraiseWithTruncatedDetails(LOGGER, Level.SEVERE, "Test GET request to Elasticsearch failed", e);
        } finally {
            if(response != null) EntityUtils.consumeQuietly(response.getEntity());
        }

        return "";
    }

    /**
     * Posts the given string to elastic search.
     *
     * @param data
     *            The data to post
     * @throws IOException
     */
    public void push(String data) throws IOException {
        LOGGER.info(this.getClass().getSimpleName()+".push("+data+")");
        HttpPost post = getHttpPost(data);

        CloseableHttpResponse response = null;
        try {
            CloseableHttpClient httpClient = getClient();
            response = httpClient.execute(post);
            if (!SUCCESS_CODES.contains(response.getStatusLine().getStatusCode())) {
                String errorMessage = this.getErrorMessage(response);
                throw new IOException(errorMessage);
            }
        } catch (Exception e) {
            logExceptionAndReraiseWithTruncatedDetails(LOGGER, Level.SEVERE, "Could not push log to Elasticsearch", e);
        } finally {
            if(response != null) EntityUtils.consumeQuietly(response.getEntity());
        }
    }

    private String getErrorMessage(CloseableHttpResponse response) {
        ByteArrayOutputStream byteStream = null;
        PrintStream stream = null;
        try {
            byteStream = new ByteArrayOutputStream();
            stream = new PrintStream(byteStream, true, StandardCharsets.UTF_8.name());

            try {
                stream.print("HTTP error code: ");
                stream.println(response.getStatusLine().getStatusCode());
                stream.print("URI: ");
                stream.println(uri.toString());
                stream.println("RESPONSE: " + response.toString());
                response.getEntity().writeTo(stream);
            } catch (IOException e) {
                stream.println(ExceptionUtils.getStackTrace(e));
            }
            stream.flush();
            return byteStream.toString(StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return ExceptionUtils.getStackTrace(e);
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
    }


    private static class MeSupplier implements Supplier<ElasticSearchWriteAccess>, Serializable {

        private final URI uri;

        @CheckForNull
        private String username;

        @CheckForNull
        private String password;

        private final int connectionTimeout;

        @CheckForNull
        private byte[] trustStoreBytes;

        private MeSupplier(ElasticSearchWriteAccessDirect me) throws IOException {
            ElasticSearchConfiguration config = ElasticSearchGlobalConfiguration.get().getElasticSearch();
            if (config != null) {
                String credentialsId = config.getCredentialsId();
                if (credentialsId != null) {
                    StandardUsernamePasswordCredentials credentials = getCredentials(credentialsId);
                    if (credentials != null) {
                        this.username = credentials.getUsername();
                        this.password = credentials.getPassword().getPlainText();
                    }
                }
                this.uri = URI.create(config.getUrl());
                this.connectionTimeout = config.getConnectionTimeoutMillis();
                this.trustStoreBytes = config.getKeyStoreBytes();
            } else {
                this.username = null;
                this.password = null;
                this.uri = null;
                this.connectionTimeout = -1;
            }
        }

        @Override
        public ElasticSearchWriteAccess get() {
            try {
                return new ElasticSearchWriteAccessDirect(uri, username, password, connectionTimeout, trustStoreBytes);
            } catch (Exception e) {
                throw new RuntimeException("Could not create ElasticSearchWriteAccessDirect", e);
            }
        }
    }

    @Override
    public Supplier<ElasticSearchWriteAccess> getSupplier() throws IOException {
        return new MeSupplier(this);
    }

}
