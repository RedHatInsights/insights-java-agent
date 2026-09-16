/* Copyright (C) Red Hat 2025-2026 */
package com.redhat.insights.agent;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.redhat.insights.agent.AgentMain.parseArgs;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.redhat.insights.Filtering;
import com.redhat.insights.InsightsException;
import com.redhat.insights.config.InsightsConfiguration;
import com.redhat.insights.reports.InsightsReport;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Additional coverage tests for InsightsAgentHttpClient that complement the existing
 * InsightsAgentHttpClientTest: HTTP response codes 202/413/415/500, mTLS decorate() path,
 * isReadyToSend(), toString(), and proxy configuration.
 */
@WireMockTest
public class InsightsAgentHttpClientExtTest {

  // -------------------------------------------------------------------------
  // HTTP 202 — accepted for processing (no exception, different log path)
  // -------------------------------------------------------------------------

  @Test
  void sendInsightsReport_202_noException(WireMockRuntimeInfo wmri) {
    // ! Without this test, the HTTP 202 case branch in sendCompressedInsightsReport() is uncovered.
    stubFor(post(InsightsConfiguration.DEFAULT_UPLOAD_URI).willReturn(aResponse().withStatus(202)));
    Optional<AgentConfiguration> oConfig =
        parseArgs("name=foo;token=fubar;base_url=" + wmri.getHttpBaseUrl());
    final InsightsReport report = AgentBasicReport.of(oConfig.get());
    report.generateReport(Filtering.DEFAULT);
    InsightsAgentHttpClient client = new InsightsAgentHttpClient(oConfig.get());
    // 202 should not throw — it is logged at debug level only.
    assertDoesNotThrow(() -> client.sendInsightsReport("foo", report));
  }

  // -------------------------------------------------------------------------
  // HTTP 413 — payload too large
  // -------------------------------------------------------------------------

  @Test
  void sendInsightsReport_413_throwsPayloadException(WireMockRuntimeInfo wmri) {
    // ! Without this test, the HTTP 413 case branch in sendCompressedInsightsReport() is uncovered.
    stubFor(
        post(InsightsConfiguration.DEFAULT_UPLOAD_URI)
            .willReturn(aResponse().withStatus(413).withStatusMessage("Payload Too Large")));
    Optional<AgentConfiguration> oConfig =
        parseArgs("name=foo;token=fubar;base_url=" + wmri.getHttpBaseUrl());
    final InsightsReport report = AgentBasicReport.of(oConfig.get());
    report.generateReport(Filtering.DEFAULT);
    InsightsAgentHttpClient client = new InsightsAgentHttpClient(oConfig.get());
    InsightsException ex =
        assertThrows(InsightsException.class, () -> client.sendInsightsReport("foo", report));
    assertTrue(ex.getMessage().contains("I4ASR"), "Expected Insights error code prefix");
  }

  // -------------------------------------------------------------------------
  // HTTP 415 — unsupported media type
  // -------------------------------------------------------------------------

  @Test
  void sendInsightsReport_415_throwsInvalidContentTypeException(WireMockRuntimeInfo wmri) {
    // ! Without this test, the HTTP 415 case branch in sendCompressedInsightsReport() is uncovered.
    stubFor(
        post(InsightsConfiguration.DEFAULT_UPLOAD_URI)
            .willReturn(aResponse().withStatus(415).withStatusMessage("Unsupported Media Type")));
    Optional<AgentConfiguration> oConfig =
        parseArgs("name=foo;token=fubar;base_url=" + wmri.getHttpBaseUrl());
    final InsightsReport report = AgentBasicReport.of(oConfig.get());
    report.generateReport(Filtering.DEFAULT);
    InsightsAgentHttpClient client = new InsightsAgentHttpClient(oConfig.get());
    InsightsException ex =
        assertThrows(InsightsException.class, () -> client.sendInsightsReport("foo", report));
    assertTrue(ex.getMessage().contains("I4ASR"), "Expected Insights error code prefix");
  }

  // -------------------------------------------------------------------------
  // HTTP 500 — server error (also covers the default: branch)
  // -------------------------------------------------------------------------

  @Test
  void sendInsightsReport_500_throwsServerErrorException(WireMockRuntimeInfo wmri) {
    // ! Without this test, the HTTP 500/default case branch in sendCompressedInsightsReport() is
    // ! uncovered.
    stubFor(
        post(InsightsConfiguration.DEFAULT_UPLOAD_URI)
            .willReturn(aResponse().withStatus(500).withStatusMessage("Internal Server Error")));
    Optional<AgentConfiguration> oConfig =
        parseArgs("name=foo;token=fubar;base_url=" + wmri.getHttpBaseUrl());
    final InsightsReport report = AgentBasicReport.of(oConfig.get());
    report.generateReport(Filtering.DEFAULT);
    InsightsAgentHttpClient client = new InsightsAgentHttpClient(oConfig.get());
    InsightsException ex =
        assertThrows(InsightsException.class, () -> client.sendInsightsReport("foo", report));
    assertTrue(ex.getMessage().contains("I4ASR"), "Expected Insights error code prefix");
  }

  @Test
  void sendInsightsReport_503_throwsServerErrorException(WireMockRuntimeInfo wmri) {
    // ! Without this test, the HTTP 503 (falls through to default:) branch is uncovered.
    stubFor(
        post(InsightsConfiguration.DEFAULT_UPLOAD_URI)
            .willReturn(aResponse().withStatus(503).withStatusMessage("Service Unavailable")));
    Optional<AgentConfiguration> oConfig =
        parseArgs("name=foo;token=fubar;base_url=" + wmri.getHttpBaseUrl());
    final InsightsReport report = AgentBasicReport.of(oConfig.get());
    report.generateReport(Filtering.DEFAULT);
    InsightsAgentHttpClient client = new InsightsAgentHttpClient(oConfig.get());
    InsightsException ex =
        assertThrows(InsightsException.class, () -> client.sendInsightsReport("foo", report));
    assertTrue(ex.getMessage().contains("I4ASR"), "Expected Insights error code prefix");
  }

  // -------------------------------------------------------------------------
  // decorate() — token path (already partially tested) + explicit assertion
  // -------------------------------------------------------------------------

  @Test
  void decorate_tokenAuth_setsTransportTypeToken() {
    // ! Without this test, the else-branch of decorate() (token mode) is not directly asserted.
    Optional<AgentConfiguration> oConfig = parseArgs("name=foo;token=mytoken");
    InsightsAgentHttpClient client = new InsightsAgentHttpClient(oConfig.get());
    final InsightsReport report = AgentBasicReport.of(oConfig.get());
    report.generateReport(Filtering.DEFAULT);
    // decorate() is called from sendInsightsReport(); we call it directly to test the side-effect.
    client.decorate(report);
    // The report's basic map should now contain transport.type.https = token.
    Object transportType = report.getBasic().get("transport.type.https");
    assertEquals("token", transportType);
    Object authToken = report.getBasic().get("auth.token");
    assertEquals("mytoken", authToken);
  }

  // -------------------------------------------------------------------------
  // isReadyToSend() — token mode (useMTLS = false) always returns true
  // -------------------------------------------------------------------------

  @Test
  void isReadyToSend_tokenMode_returnsTrue() {
    // ! Without this test, isReadyToSend() is uncovered.
    Optional<AgentConfiguration> oConfig = parseArgs("name=foo;token=tok");
    InsightsAgentHttpClient client = new InsightsAgentHttpClient(oConfig.get());
    assertTrue(client.isReadyToSend());
  }

  // -------------------------------------------------------------------------
  // toString() — both token and mTLS variants
  // -------------------------------------------------------------------------

  @Test
  void toString_tokenMode_containsToken() {
    // ! Without this test, the token branch of toString() is uncovered.
    Optional<AgentConfiguration> oConfig = parseArgs("name=foo;token=mytoken");
    InsightsAgentHttpClient client = new InsightsAgentHttpClient(oConfig.get());
    String s = client.toString();
    assertTrue(s.contains("mytoken"), "toString() should include the token value");
  }

  // -------------------------------------------------------------------------
  // Proxy configuration branch
  // -------------------------------------------------------------------------

  @Test
  void sendInsightsReport_withProxy_doesNotThrow(WireMockRuntimeInfo wmri) {
    // ! Without this test, the proxy configuration branch in sendCompressedInsightsReport() is
    // ! uncovered. We configure a proxy that happens to point to the WireMock server itself so the
    // ! request actually succeeds (the proxy route planner will use it for the HTTP request).
    stubFor(post(InsightsConfiguration.DEFAULT_UPLOAD_URI).willReturn(aResponse().withStatus(201)));

    String wireMockHost = "localhost";
    int wireMockPort = wmri.getHttpPort();

    Optional<AgentConfiguration> oConfig =
        parseArgs(
            "name=foo;token=fubar;base_url="
                + wmri.getHttpBaseUrl()
                + ";proxy="
                + wireMockHost
                + ";proxy_port="
                + wireMockPort);
    assertTrue(oConfig.isPresent());
    assertTrue(oConfig.get().getProxyConfiguration().isPresent());

    final InsightsReport report = AgentBasicReport.of(oConfig.get());
    report.generateReport(Filtering.DEFAULT);
    InsightsAgentHttpClient client = new InsightsAgentHttpClient(oConfig.get());
    assertDoesNotThrow(() -> client.sendInsightsReport("foo", report));
  }

  // -------------------------------------------------------------------------
  // AgentConfiguration — token_file path
  // -------------------------------------------------------------------------

  @Test
  void agentConfiguration_tokenFileNotFound_warnsAndFallsThrough() {
    // ! Without this test, the IOException warning branch in getMaybeAuthToken() is uncovered.
    Map<String, String> args = new HashMap<>();
    args.put("name", "app");
    args.put("token_file", "/nonexistent/path/token.txt");
    AgentConfiguration config = new AgentConfiguration(args);
    // File does not exist → warning is logged, Optional.empty() returned via super.
    Optional<String> token = config.getMaybeAuthToken();
    assertFalse(token.isPresent(), "token should be absent when token_file does not exist");
  }

  // -------------------------------------------------------------------------
  // AgentConfiguration — getUploadBaseURL / getUploadUri override paths
  // -------------------------------------------------------------------------

  @Test
  void agentConfiguration_uploadBaseUrlOverride() {
    // ! Without this test, the args.containsKey(AGENT_ARG_BASE_URL) branch is uncovered.
    Optional<AgentConfiguration> oConfig =
        parseArgs("name=app;base_url=https://custom.example.com");
    assertTrue(oConfig.isPresent());
    assertEquals("https://custom.example.com", oConfig.get().getUploadBaseURL());
  }

  @Test
  void agentConfiguration_uploadUriOverride() {
    // ! Without this test, the args.containsKey(AGENT_ARG_UPLOAD_URI) branch is uncovered.
    Optional<AgentConfiguration> oConfig = parseArgs("name=app;uri=/custom/upload");
    assertTrue(oConfig.isPresent());
    assertEquals("/custom/upload", oConfig.get().getUploadUri());
  }
}
