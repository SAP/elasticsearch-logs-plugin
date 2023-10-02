package io.jenkins.plugins.pipeline_elasticsearch_logs.write.index_api;

import static com.google.common.collect.Range.closedOpen;
import static io.jenkins.plugins.pipeline_elasticsearch_logs.Utils.logExceptionAndReraiseWithTruncatedDetails;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;

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
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import com.google.common.collect.Range;

import io.jenkins.plugins.pipeline_elasticsearch_logs.SSLHelper;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.EventWriter;
import net.sf.json.JSONObject;

/**
 * An {@link EventWriter} using the Elasticsearch Index API (HTTP POST of
 * single events).
 */
public class IndexAPIEventWriter implements EventWriter {
    private static final Logger LOGGER = Logger.getLogger(IndexAPIEventWriter.class.getName());

    private static final Range<Integer> SUCCESS_CODES = closedOpen(200, 300);

    private final IndexAPIEventWriterRunConfig config;

    @CheckForNull
    private KeyStore trustStore;

    private transient CloseableHttpClient httpClient;

	private transient HttpClientContext httpClientContext;

    // mutex of push(), testConnection() and close(), with concurrent calls to push()
    // and testConnection()
    ReadWriteLock lock = new ReentrantReadWriteLock();

    private boolean isClosed = false;

    IndexAPIEventWriter(IndexAPIEventWriterRunConfig config) {
        this.config = config;
        createHttpClientAndContext();
    }

    @Override
    public void push(Map<String, Object> data) throws IOException {
        try {
            this.lock.readLock().lock();
            failIfClosed();

            String dataString = JSONObject.fromObject(data).toString();
            HttpPost post = createHttpPostRequest(dataString);

            CloseableHttpResponse response = null;
            try {
                response = this.httpClient.execute(post, this.httpClientContext);
                if (!SUCCESS_CODES.contains(response.getStatusLine().getStatusCode())) {
                    String errorMessage = this.getErrorMessage(response);
                    throw new IOException(errorMessage);
                }
            } catch (Exception e) {
                logExceptionAndReraiseWithTruncatedDetails(LOGGER, Level.SEVERE, "Could not send event to Elasticsearch", e);
            } finally {
                if (response != null) EntityUtils.consumeQuietly(response.getEntity());
            }
        }
        finally {
            this.lock.readLock().unlock();
        }
    }

    private HttpPost createHttpPostRequest(String data) {
        HttpPost postRequest = new HttpPost(this.config.getIndexUrl());
        // char encoding is set to UTF_8 since this request posts a JSON string
        StringEntity input = new StringEntity(data, StandardCharsets.UTF_8);
        input.setContentType(ContentType.APPLICATION_JSON.toString());
        postRequest.setEntity(input);
        return postRequest;
    }

    private void createHttpClientAndContext() {
        this.httpClientContext = HttpClientContext.create();

        HttpClientBuilder clientBuilder = HttpClientBuilder.create();

        RequestConfig.Builder requestBuilder = RequestConfig.custom();
        if (this.config.getConnectTimeoutMillis() != null) {
            requestBuilder.setConnectTimeout(this.config.getConnectTimeoutMillis());
        }
        if (this.config.getRequestTimeoutMillis() != null) {
            requestBuilder.setConnectionRequestTimeout(this.config.getRequestTimeoutMillis());
        }
        if (this.config.getSocketTimeoutMillis() != null) {
            requestBuilder.setSocketTimeout(this.config.getSocketTimeoutMillis());
        }
        clientBuilder.setDefaultRequestConfig(requestBuilder.build());

        if (StringUtils.isNotBlank(this.config.getUsername())) {
            URI indexUrl = config.getIndexUrl();
            HttpHost targetHost = new HttpHost(indexUrl.getHost(), indexUrl.getPort(), indexUrl.getScheme());
            org.apache.http.client.CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                new AuthScope(targetHost),
                new UsernamePasswordCredentials(config.getUsername(), StringUtils.defaultString(config.getPassword()))
            );

            AuthCache authCache = new BasicAuthCache();
            authCache.put(targetHost, new BasicScheme());
            httpClientContext.setCredentialsProvider(credentialsProvider);
            httpClientContext.setAuthCache(authCache);
        }

        trustStore = getTrustStore();
        if (trustStore != null) {
            try {
                SSLHelper.setClientBuilderSSLContext(clientBuilder, trustStore);
            } catch (KeyManagementException | CertificateException | NoSuchAlgorithmException | KeyStoreException | IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to set SSLContext for HTTP client. Will try without.", e);
            }
        }

        this.httpClient = clientBuilder.build();
        if (LOGGER.isLoggable(Level.FINE)) LOGGER.fine("New HTTP client created");
    }

    @Restricted(NoExternalUse.class)
    public String testConnection() throws URISyntaxException, IOException {
        try {
            this.lock.readLock().lock();
            failIfClosed();

            // The Elasticsearch base URL is not necessarily on the root path
            // Better: Remove the last two segments from the index URL path
            URI indexUrl = config.getIndexUrl();
            URI testUri = new URI(indexUrl.getScheme(), null, indexUrl.getHost(), indexUrl.getPort(), null, null, null);
            HttpGet request = new HttpGet(testUri);

            CloseableHttpResponse response = null;
            try {
                response = httpClient.execute(request, httpClientContext);
                // With the given credential a GET may not be authorized
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
        finally {
            this.lock.readLock().unlock();
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
                stream.println(this.config.getIndexUrl().toString());
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

    private KeyStore getTrustStore() {
        if (this.trustStore == null) {
            this.trustStore = createTrustStore();
        }
        return this.trustStore;
    }

    private KeyStore createTrustStore() {
        byte[] trustStoreBytes = this.config.getTrustStoreBytes();
        if (trustStoreBytes != null) {
            try {
                KeyStore trustStore = KeyStore.getInstance("PKCS12");
                trustStore.load(new ByteArrayInputStream(trustStoreBytes), "".toCharArray());
                return trustStore;
            } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
                LOGGER.log(Level.WARNING, "Failed to create trust store from bytes", e);
                return null;
            }
        }
        return null;
    }

    @Override
    public synchronized void close() throws IOException {
        try {
            this.lock.writeLock().lock();

            failIfClosed();

            this.httpClient.close();
        }
        finally {
            this.httpClient = null;
            this.httpClientContext = null;
            this.trustStore = null;
            this.isClosed = true;

            this.lock.writeLock().unlock();
        }
    }

    private void failIfClosed() throws IllegalStateException {
        if (this.isClosed) {
            throw new IllegalStateException("object is closed already");
        }
    }
}
