/* Copyright (C) Red Hat 2023 */
package com.redhat.insights.doubles;

import com.redhat.insights.config.DefaultInsightsConfiguration;
import com.redhat.insights.config.InsightsConfiguration;
import java.time.Duration;
import java.util.Optional;

public final class MockInsightsConfiguration extends DefaultInsightsConfiguration {
  private final String identificationName;
  private final String certFilePath;
  private final String keyFilePath;
  private final String uploadURL;
  private final String uploadPath;
  private final Optional<ProxyConfiguration> proxyConfiguration;
  private final boolean optingOut;
  private final Duration connectPeriod;
  private final Duration updatePeriod;
  private final long httpClientRetryInitialDelay;
  private final long httpClientRetryBackoffFactor;
  private final int httpClientRetryMaxAttemps;

  private MockInsightsConfiguration(
      String identificationName,
      String certFilePath,
      String keyFilePath,
      String uploadURL,
      String uploadPath,
      Optional<ProxyConfiguration> proxyConfiguration,
      boolean optingOut,
      Duration connectPeriod,
      Duration updatePeriod,
      long httpClientRetryInitialDelay,
      long httpClientRetryBackoffFactor,
      int httpClientRetryMaxAttemps) {
    this.identificationName = identificationName;
    this.certFilePath = certFilePath;
    this.keyFilePath = keyFilePath;
    this.uploadURL = uploadURL;
    this.uploadPath = uploadPath;
    this.proxyConfiguration = proxyConfiguration;
    this.optingOut = optingOut;
    this.connectPeriod = connectPeriod;
    this.updatePeriod = updatePeriod;
    this.httpClientRetryInitialDelay = httpClientRetryInitialDelay;
    this.httpClientRetryBackoffFactor = httpClientRetryBackoffFactor;
    this.httpClientRetryMaxAttemps = httpClientRetryMaxAttemps;
  }

  public static InsightsConfiguration ofOptedOut(String name) {
    return of(name, true);
  }

  public static InsightsConfiguration of(String name, boolean optedOut) {
    return of(name, optedOut, Duration.ofDays(1), Duration.ofSeconds(5));
  }

  public static InsightsConfiguration ofRetries(
      String name,
      long httpClientRetryInitialDelay,
      long httpClientRetryBackoffFactor,
      int httpClientRetryMaxAttempts) {
    return of(
        name,
        false,
        Duration.ofDays(1),
        Duration.ofSeconds(5),
        httpClientRetryInitialDelay,
        httpClientRetryBackoffFactor,
        httpClientRetryMaxAttempts);
  }

  public static InsightsConfiguration of(
      String name, boolean optedOut, Duration connectPeriod, Duration updatePeriod) {
    return new MockInsightsConfiguration(
        name,
        "/dummy",
        "/fake",
        "https://127.0.0.1:999999",
        "/fake",
        Optional.empty(),
        optedOut,
        connectPeriod,
        updatePeriod,
        DEFAULT_HTTP_CLIENT_RETRY_INITIAL_DELAY,
        DEFAULT_HTTP_CLIENT_RETRY_BACKOFF_FACTOR,
        DEFAULT_HTTP_CLIENT_RETRY_MAX_ATTEMPTS);
  }

  public static InsightsConfiguration of(
      String name,
      boolean optedOut,
      Duration connectPeriod,
      Duration updatePeriod,
      long httpClientRetryInitialDelay,
      long httpClientRetryBackoffFactor,
      int httpClientRetryMaxAttempts) {
    return new MockInsightsConfiguration(
        name,
        "/dummy",
        "/fake",
        "https://127.0.0.1:999999",
        "/fake",
        Optional.empty(),
        optedOut,
        connectPeriod,
        updatePeriod,
        httpClientRetryInitialDelay,
        httpClientRetryBackoffFactor,
        httpClientRetryMaxAttempts);
  }

  @Override
  public String getIdentificationName() {
    return identificationName;
  }

  @Override
  public String getCertFilePath() {
    return certFilePath;
  }

  @Override
  public String getKeyFilePath() {
    return keyFilePath;
  }

  @Override
  public String getUploadBaseURL() {
    return uploadURL;
  }

  @Override
  public String getUploadUri() {
    return uploadPath;
  }

  @Override
  public Optional<ProxyConfiguration> getProxyConfiguration() {
    return proxyConfiguration;
  }

  @Override
  public boolean isOptingOut() {
    return optingOut;
  }

  @Override
  public Duration getConnectPeriod() {
    return connectPeriod;
  }

  @Override
  public Duration getUpdatePeriod() {
    return updatePeriod;
  }

  @Override
  public long getHttpClientRetryInitialDelay() {
    return httpClientRetryInitialDelay;
  }

  @Override
  public long getHttpClientRetryBackoffFactor() {
    return httpClientRetryBackoffFactor;
  }

  @Override
  public int getHttpClientRetryMaxAttempts() {
    return httpClientRetryMaxAttemps;
  }
}
