/* Copyright (C) Red Hat 2023 */
package com.redhat.insights.agent;

import static com.redhat.insights.InsightsErrorCode.*;

import com.redhat.insights.InsightsException;
import com.redhat.insights.config.InsightsConfiguration;
import com.redhat.insights.http.InsightsHttpClient;
import com.redhat.insights.logging.InsightsLogger;
import com.redhat.insights.reports.InsightsReport;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import javax.net.ssl.SSLContext;
import org.apache.http.HttpHost;
import org.apache.http.ParseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.util.EntityUtils;

public final class InsightsAgentHttpClient implements InsightsHttpClient {
  private static final InsightsLogger logger = new SLF4JLogger(InsightsAgentHttpClient.class);
  private static final ContentType GENERAL_CONTENT_TYPE = ContentType.create(GENERAL_MIME_TYPE);
  private final Supplier<SSLContext> sslContextSupplier;
  private final InsightsConfiguration configuration;
  private final boolean useMTLS;

  public InsightsAgentHttpClient(
      InsightsConfiguration configuration, Supplier<SSLContext> sslContextSupplier) {
    this.configuration = configuration;
    this.sslContextSupplier = sslContextSupplier;
    this.useMTLS = !configuration.getMaybeAuthToken().isPresent();
  }

  @Override
  public void decorate(InsightsReport report) {
    if (useMTLS) {
      report.decorate("transport.type.https", "mtls");
      // We can't send anything more useful (e.g. SHA hash of cert file) as until
      // we try to send, we don't know if we can read the file at this path
      report.decorate("transport.cert.https", configuration.getCertFilePath());
    } else {
      final String authToken = configuration.getMaybeAuthToken().get();
      report.decorate("transport.type.https", "token");
      report.decorate("auth.token", authToken);
    }
  }

  @Override
  public void sendInsightsReport(String filename, InsightsReport report) {
    decorate(report);
    byte[] json = report.serializeRaw();
    logger.debug("Red Hat Insights Report:\n" + new String(json, StandardCharsets.UTF_8));
    sendCompressedInsightsReport(filename, InsightsHttpClient.gzipReport(json));
  }

  void sendCompressedInsightsReport(String filename, byte[] bytes) {
    HttpClientBuilder clientBuilder = HttpClientBuilder.create();
    if (configuration.getProxyConfiguration().isPresent()) {
      InsightsConfiguration.ProxyConfiguration conf = configuration.getProxyConfiguration().get();
      clientBuilder.setRoutePlanner(
          new DefaultProxyRoutePlanner(new HttpHost(conf.getHost(), conf.getPort(), "http")));
    }
    clientBuilder.setRetryHandler(
        new DefaultHttpRequestRetryHandler(configuration.getHttpClientRetryMaxAttempts(), true));
    if (useMTLS) {
      if (sslContextSupplier.get() == null) {
        return;
      }
      clientBuilder.setSSLContext(sslContextSupplier.get());
    } else {
      clientBuilder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
    }
    CloseableHttpClient client = clientBuilder.build();
    try {
      HttpPost post;
      if (useMTLS) {
        post = createPost();
      } else {
        post = createAuthTokenPost(configuration.getMaybeAuthToken().get());
      }
      post.setHeader("Cache-Control", "no-store");
      MultipartEntityBuilder builder = MultipartEntityBuilder.create();
      builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
      builder.addBinaryBody("file", bytes, GENERAL_CONTENT_TYPE, filename);
      builder.addTextBody("type", GENERAL_MIME_TYPE);
      post.setEntity(builder.build());
      try (CloseableHttpResponse response = client.execute(post)) {
        logger.debug(
            "Red Hat Insights HTTP Client: status="
                + response.getStatusLine()
                + ", body="
                + EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
        switch (response.getStatusLine().getStatusCode()) {
          case 201:
            logger.debug(
                "Red Hat Insights - Advisor content type with no metadata accepted for"
                    + " processing");
            break;
          case 202:
            logger.debug("Red Hat Insights - Payload was accepted for processing");
            break;
          case 401:
            throw new InsightsException(
                ERROR_HTTP_SEND_AUTH_ERROR, response.getStatusLine().getReasonPhrase());
          case 413:
            throw new InsightsException(
                ERROR_HTTP_SEND_PAYLOAD, response.getStatusLine().getReasonPhrase());
          case 415:
            throw new InsightsException(
                ERROR_HTTP_SEND_INVALID_CONTENT_TYPE, response.getStatusLine().getReasonPhrase());
          case 500:
          case 503:
          default:
            throw new InsightsException(
                ERROR_HTTP_SEND_SERVER_ERROR, response.getStatusLine().toString());
        }
      }
    } catch (IOException | ParseException ioex) {
      logger.debug("Error", ioex);
    } finally {
      try {
        client.close();
      } catch (IOException ex) {
        logger.debug("Error", ex);
      }
    }
  }

  private HttpPost createAuthTokenPost(String token) {
    HttpPost post =
        new HttpPost(assembleURI(configuration.getUploadBaseURL(), configuration.getUploadUri()));
    post.setHeader("Authorization", "Basic " + token);
    return post;
  }

  private HttpPost createPost() {
    return new HttpPost(
        assembleURI(configuration.getUploadBaseURL(), configuration.getUploadUri()));
  }

  URI assembleURI(String url, String path) {
    String fullURL;
    if (!url.endsWith("/") && !path.startsWith("/")) {
      fullURL = url + "/" + path;
    } else if (url.endsWith("/") && path.startsWith("/")) {
      fullURL = url + path.substring(1);
    } else {
      fullURL = url + path;
    }
    return URI.create(fullURL);
  }

  @Override
  public boolean isReadyToSend() {
    return !useMTLS || sslContextSupplier.get() != null;
  }

  @Override
  public String toString() {
    if (useMTLS) {
      return "InsightsApacheHttpClient{"
          + "keyFile= "
          + configuration.getKeyFilePath()
          + ", certFile= "
          + configuration.getCertFilePath()
          + ", url= "
          + assembleURI(configuration.getUploadBaseURL(), configuration.getUploadUri())
          + '}';
    }
    return "InsightsApacheHttpClient{"
        + "token= "
        + configuration.getMaybeAuthToken().get()
        + ", url= "
        + assembleURI(configuration.getUploadBaseURL(), configuration.getUploadUri())
        + '}';
  }
}
