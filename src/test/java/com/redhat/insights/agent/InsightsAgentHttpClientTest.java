/* Copyright (C) Red Hat 2024-2025 */
package com.redhat.insights.agent;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.redhat.insights.InsightsErrorCode.ERROR_SSL_CREATING_CONTEXT;
import static com.redhat.insights.agent.AgentMain.parseArgs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.redhat.insights.Filtering;
import com.redhat.insights.InsightsException;
import com.redhat.insights.config.InsightsConfiguration;
import com.redhat.insights.reports.InsightsReport;
import java.net.URI;
import java.util.*;
import org.apache.http.Header;
import org.apache.http.client.methods.HttpPost;
import org.junit.jupiter.api.Test;

@WireMockTest
public class InsightsAgentHttpClientTest {

  @Test
  void testBearerToken() {
    Optional<AgentConfiguration> oConfig = parseArgs("name=foo;token=bar;is_ocp=true");
    InsightsAgentHttpClient client =
        new InsightsAgentHttpClient(
            oConfig.get(),
            () -> {
              throw new InsightsException(
                  ERROR_SSL_CREATING_CONTEXT,
                  "Illegal attempt to create SSLContext for token auth");
            });
    HttpPost post = client.createAuthTokenPost();
    List<Header> headers = Arrays.asList(post.getAllHeaders());
    assertTrue(
        headers.stream()
            .anyMatch(
                h -> h.getName().equals("Authorization") && h.getValue().equals("Bearer bar")));
  }

  @Test
  void testBearerToken2() {
    Optional<AgentConfiguration> oConfig = parseArgs("name=foo;token=bar;is_ocp=true");
    InsightsAgentHttpClient client = new InsightsAgentHttpClient(oConfig.get());
    HttpPost post = client.createAuthTokenPost();
    List<Header> headers = Arrays.asList(post.getAllHeaders());
    assertTrue(
        headers.stream()
            .anyMatch(
                h -> h.getName().equals("Authorization") && h.getValue().equals("Bearer bar")));
  }

  @Test
  void sendInsightsReportOk(WireMockRuntimeInfo wmri) {
    stubFor(post(InsightsConfiguration.DEFAULT_UPLOAD_URI).willReturn(aResponse().withStatus(201)));
    Optional<AgentConfiguration> oConfig =
        parseArgs("name=foo;token=fubar;base_url=" + wmri.getHttpBaseUrl());
    final InsightsReport report = AgentBasicReport.of(oConfig.get());
    report.generateReport(Filtering.DEFAULT);
    InsightsAgentHttpClient client = new InsightsAgentHttpClient(oConfig.get());
    client.sendInsightsReport("foo", report);
    verify(1, postRequestedFor(urlPathEqualTo(InsightsConfiguration.DEFAULT_UPLOAD_URI)));
  }

  @Test
  void sendInsightsReportAuthError(WireMockRuntimeInfo wmri) {
    stubFor(post(InsightsConfiguration.DEFAULT_UPLOAD_URI).willReturn(aResponse().withStatus(401)));
    Optional<AgentConfiguration> oConfig =
        parseArgs("name=foo;token=fubar;base_url=" + wmri.getHttpBaseUrl());
    final InsightsReport report = AgentBasicReport.of(oConfig.get());
    report.generateReport(Filtering.DEFAULT);
    InsightsAgentHttpClient client = new InsightsAgentHttpClient(oConfig.get());
    try {
      client.sendInsightsReport("foo", report);
      fail();
    } catch (InsightsException e) {
      assertEquals("I4ASR0011: Unauthorized", e.getMessage());
    }
  }

  @Test
  void assembleURI() {
    Optional<AgentConfiguration> oConfig = parseArgs("name=foo");
    InsightsAgentHttpClient client = new InsightsAgentHttpClient(oConfig.get());
    URI uri = client.assembleURI("https://example.com", "foo");
    assertEquals("https://example.com/foo", uri.toString());
    uri = client.assembleURI("https://example.com/", "/foo");
    assertEquals("https://example.com/foo", uri.toString());
    uri = client.assembleURI("https://example.com", "/foo");
    assertEquals("https://example.com/foo", uri.toString());
    uri = client.assembleURI("https://example.com/", "foo");
    assertEquals("https://example.com/foo", uri.toString());
  }
}
