/* Copyright (C) Red Hat 2023-2025 */
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
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.net.ssl.SSLContext;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.util.TimeValue;

public final class InsightsAgentHttpClient implements InsightsHttpClient {
  private static final InsightsLogger logger = AgentLogger.getLogger();
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

  public InsightsAgentHttpClient(InsightsConfiguration configuration) {
    this.configuration = configuration;
    this.sslContextSupplier =
        () -> {
          throw new InsightsException(
              ERROR_SSL_CREATING_CONTEXT, "Could not create SSL context in Token Auth mode");
        };
    this.useMTLS = false;
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
    HttpClientBuilder clientBuilder = HttpClients.custom();
    // Do Timeouts first, as we set them in the default request config
    int delay = (int) configuration.getHttpClientTimeout().toMillis();
    RequestConfig requestConfig =
        RequestConfig.custom()
            .setConnectionRequestTimeout(delay, TimeUnit.MILLISECONDS)
            .setConnectTimeout(delay, TimeUnit.MILLISECONDS)
            .setResponseTimeout(delay, TimeUnit.MILLISECONDS)
            .build();
    clientBuilder.setDefaultRequestConfig(requestConfig);
    if (configuration.getProxyConfiguration().isPresent()) {
      InsightsConfiguration.ProxyConfiguration conf = configuration.getProxyConfiguration().get();
      clientBuilder.setRoutePlanner(
          new DefaultProxyRoutePlanner(new HttpHost("http", conf.getHost(), conf.getPort())));
    }
    clientBuilder.setRetryStrategy(
        new DefaultHttpRequestRetryStrategy(
            configuration.getHttpClientRetryMaxAttempts(), TimeValue.ofSeconds(1)));
    if (useMTLS) {
      SSLContext sslContext = sslContextSupplier.get();
      if (sslContext == null) {
        return;
      }
      SSLConnectionSocketFactory sslSocketFactory =
          new SSLConnectionSocketFactory(sslContext, new DefaultHostnameVerifier());
      RegistryBuilder<ConnectionSocketFactory> socketFactoryRegistryBuilder =
          RegistryBuilder.<ConnectionSocketFactory>create().register("https", sslSocketFactory);
      HttpClientConnectionManager connMan =
          new BasicHttpClientConnectionManager(socketFactoryRegistryBuilder.build());
      clientBuilder.setConnectionManager(connMan);
    } else {
      // clientBuilder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
    }
    try (CloseableHttpClient client = clientBuilder.build()) {
      HttpPost post;
      if (useMTLS) {
        post = createPost();
      } else {
        post = createAuthTokenPost();
      }
      post.setHeader("Cache-Control", "no-store");
      MultipartEntityBuilder builder = MultipartEntityBuilder.create();
      builder.setMode(HttpMultipartMode.LEGACY);
      builder.addBinaryBody("file", bytes, GENERAL_CONTENT_TYPE, filename);
      builder.addTextBody("type", GENERAL_MIME_TYPE);
      post.setEntity(builder.build());
      try (CloseableHttpResponse response = client.execute(post)) {
        logger.debug(
            "Red Hat Insights HTTP Client: status="
                + new StatusLine(response)
                + ", body="
                + EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
        switch (response.getCode()) {
          case 201:
            logger.debug(
                "Red Hat Insights - Advisor content type with no metadata accepted for"
                    + " processing");
            break;
          case 202:
            logger.debug("Red Hat Insights - Payload was accepted for processing");
            break;
          case 401:
            throw new InsightsException(ERROR_HTTP_SEND_AUTH_ERROR, response.getReasonPhrase());
          case 413:
            throw new InsightsException(ERROR_HTTP_SEND_PAYLOAD, response.getReasonPhrase());
          case 415:
            throw new InsightsException(
                ERROR_HTTP_SEND_INVALID_CONTENT_TYPE, response.getReasonPhrase());
          case 500:
          case 503:
          default:
            throw new InsightsException(
                ERROR_HTTP_SEND_SERVER_ERROR, new StatusLine(response).toString());
        }
      }
    } catch (IOException | ParseException ioex) {
      logger.debug("Error", ioex);
    }
  }

  HttpPost createAuthTokenPost() {
    String token = configuration.getMaybeAuthToken().get();
    HttpPost post =
        new HttpPost(assembleURI(configuration.getUploadBaseURL(), configuration.getUploadUri()));
    post.setHeader("Authorization", "Bearer " + token);
    return post;
  }

  HttpPost createPost() {
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
