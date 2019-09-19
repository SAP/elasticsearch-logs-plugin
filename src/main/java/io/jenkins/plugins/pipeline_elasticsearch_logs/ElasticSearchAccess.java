package io.jenkins.plugins.pipeline_elasticsearch_logs;

import static com.google.common.collect.Ranges.closedOpen;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import com.google.common.collect.Range;

/**
 * Post data to Elastic Search.
 *
 */
public class ElasticSearchAccess
{
  private static final Logger LOGGER = Logger.getLogger(ElasticSearchAccess.class.getName());

  private static final Range<Integer> SUCCESS_CODES = closedOpen(200, 300);

  private final URI uri;

  @CheckForNull
  private String username;

  @CheckForNull
  private String password;

  private transient String auth;

  private KeyStore trustKeyStore;

  @CheckForNull
  private transient HttpClientBuilder clientBuilder;

  public ElasticSearchAccess(URI uri, String username, String password)
  {
    this.uri = uri;
    this.password = password;
    this.username = username;
  }

  public static ElasticSearchAccess createElasticSearchAccess(ElasticSearchRunConfiguration config) throws IOException
  {
    ElasticSearchAccess writer = new ElasticSearchAccess(config.getUri(), config.getUsername(), config.getPassword());
    if (config.getTrustKeyStore() != null)
    {
      writer.setTrustKeyStore(config.getTrustKeyStore());
    }
    return writer;
  }

  public void setTrustKeyStore(KeyStore trustKeyStore)
  {
    this.trustKeyStore = trustKeyStore;
  }

  @CheckForNull
  private String getAuth()
  {
    if (auth == null && StringUtils.isNotBlank(username))
    {
      auth = Base64
        .encodeBase64String((username + ":" + StringUtils.defaultString(password)).getBytes(StandardCharsets.UTF_8));
    }
    return auth;
  }

  private HttpPost getHttpPost(String data)
  {
    HttpPost postRequest = new HttpPost(uri);
    // char encoding is set to UTF_8 since this request posts a JSON string
    StringEntity input = new StringEntity(data, StandardCharsets.UTF_8);
    input.setContentType(ContentType.APPLICATION_JSON.toString());
    postRequest.setEntity(input);
    auth = getAuth();
    if (auth != null)
    {
      postRequest.addHeader("Authorization", "Basic " + auth);
    }
    return postRequest;
  }  

  public RestHighLevelClient createNewRestClient() {
      RestClientBuilder builder = RestClient.builder(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()));
      if(getAuth() != null) builder.setDefaultHeaders(new Header[] {new BasicHeader("Authorization", "Basic " + getAuth())});
      return new RestHighLevelClient(builder);
  }

  @Nonnull
  private HttpClientBuilder getClientBuilder()
  {
    if (clientBuilder == null)
    {
      clientBuilder = HttpClientBuilder.create();
      RequestConfig.Builder requestBuilder = RequestConfig.custom();
      requestBuilder.setConnectTimeout(2000);
      requestBuilder.setConnectionRequestTimeout(2000);
      requestBuilder.setSocketTimeout(2000);
      clientBuilder.setDefaultRequestConfig(requestBuilder.build());
      if (trustKeyStore != null)
      {
        try
        {
          SSLHelper.setClientBuilderSSLContext(clientBuilder, trustKeyStore);
        }
        catch (KeyManagementException | CertificateException | NoSuchAlgorithmException | KeyStoreException
              | IOException e)
        {
          LOGGER.log(Level.SEVERE, "Failed to set SSLContext for http client. Will try without.", e);
        }
      }
    }
    return clientBuilder;
  }
  
  @Restricted(NoExternalUse.class)
  String testConnection() throws URISyntaxException, IOException
  {
    URI testUri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), null, null, null);
    HttpGet getRequest = new HttpGet(testUri);
    String auth = getAuth();
    if (auth != null)
    {
      getRequest.addHeader("Authorization", "Basic " + auth);
    }
    
    try (CloseableHttpClient httpClient = getClientBuilder().build())
    {
      try (CloseableHttpResponse response = httpClient.execute(getRequest))
      {
        if (!SUCCESS_CODES.contains(response.getStatusLine().getStatusCode()))
        {
          String errorMessage = this.getErrorMessage(response);
          throw new IOException(errorMessage);
        }
      }
      catch (Exception e)
      {
        throw new IOException(e);
      }
    }
    
    return "";
  }

  /**
   * Posts the given string to elastic search.
   * 
   * @param data
   *          The data to post
   * @throws IOException
   */
  public void push(String data) throws IOException
  {

    HttpPost post = getHttpPost(data);

    try (CloseableHttpClient httpClient = getClientBuilder().build())
    {
      try (CloseableHttpResponse response = httpClient.execute(post))
      {
        if (!SUCCESS_CODES.contains(response.getStatusLine().getStatusCode()))
        {
          String errorMessage = this.getErrorMessage(response);
          throw new IOException(errorMessage);
        }
      }
      catch (Exception e)
      {
        throw new IOException(e);
      }
    }
  }

  private String getErrorMessage(CloseableHttpResponse response)
  {
    ByteArrayOutputStream byteStream = null;
    PrintStream stream = null;
    try
    {
      byteStream = new ByteArrayOutputStream();
      stream = new PrintStream(byteStream, true, StandardCharsets.UTF_8.name());

      try
      {
        stream.print("HTTP error code: ");
        stream.println(response.getStatusLine().getStatusCode());
        stream.print("URI: ");
        stream.println(uri.toString());
        stream.println("RESPONSE: " + response.toString());
        response.getEntity().writeTo(stream);
      }
      catch (IOException e)
      {
        stream.println(ExceptionUtils.getStackTrace(e));
      }
      stream.flush();
      return byteStream.toString(StandardCharsets.UTF_8.name());
    }
    catch (UnsupportedEncodingException e)
    {
      return ExceptionUtils.getStackTrace(e);
    }
    finally
    {
      if (stream != null)
      {
        stream.close();
      }
    }
  }

}
