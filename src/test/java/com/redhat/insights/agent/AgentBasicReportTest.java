/* Copyright (C) Red Hat 2024 */
package com.redhat.insights.agent;

import static com.redhat.insights.agent.AgentMain.parseArgs;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.insights.Filtering;
import com.redhat.insights.reports.InsightsReport;

import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.util.*;

import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.Test;

public class AgentBasicReportTest {

  @Test
  void testOpenshiftParamsMissing() throws JsonProcessingException {
    Optional<AgentConfiguration> oConfig = parseArgs("name=foo");
    final InsightsReport report = AgentBasicReport.of(oConfig.get(), null);

    ObjectMapper oMapper = new ObjectMapper();
    Map<String, Object> r =
        oMapper.readValue(report.serialize(), new TypeReference<Map<String, Object>>() {});
    assertNotNull(r.get("details"));
    Map<String, Object> d = (Map<String, Object>) r.get("details");
    assertEquals(AgentConfiguration.PROPERTY_NOT_GIVEN_DEFAULT, String.valueOf(d.get("pod_name")));
    assertEquals(
        AgentConfiguration.PROPERTY_NOT_GIVEN_DEFAULT, String.valueOf(d.get("pod_namespace")));
    assertFalse(Boolean.valueOf(String.valueOf(d.get("is_ocp"))));
  }

  @Test
  void testOpenshiftParams() throws JsonProcessingException {
    Optional<AgentConfiguration> oConfig =
        parseArgs("name=foo;token=bar;pod_name=XXX;pod_namespace=YYY");
    final InsightsReport report = AgentBasicReport.of(oConfig.get(), null);

    ObjectMapper oMapper = new ObjectMapper();
    Map<String, Object> r =
        oMapper.readValue(report.serialize(), new TypeReference<Map<String, Object>>() {});
    assertNotNull(r.get("details"));
    Map<String, Object> d = (Map<String, Object>) r.get("details");
    assertEquals("XXX", String.valueOf(d.get("pod_name")));
    assertEquals("YYY", String.valueOf(d.get("pod_namespace")));
    assertTrue(Boolean.valueOf(String.valueOf(d.get("is_ocp"))));
  }

  @Test
  void testOpenshiftWithSbom() throws JsonProcessingException {
    // Setup Byte Buddy agent
    Instrumentation instrumentation = ByteBuddyAgent.install();

    Optional<AgentConfiguration> oConfig =
            parseArgs("name=foo;token=bar;pod_name=XXX;pod_namespace=YYY;sbom=true");
    final InsightsReport report = AgentBasicReport.of(oConfig.get(), instrumentation);
    report.generateReport(Filtering.DEFAULT);

    ObjectMapper oMapper = new ObjectMapper();
    Map<String, Object> r =
            oMapper.readValue(report.serialize(), new TypeReference<Map<String, Object>>() {});
    assertNotNull(r.get("details"));
    Map<String, Object> d = (Map<String, Object>) r.get("details");
    assertEquals("XXX", String.valueOf(d.get("pod_name")));
    assertEquals("YYY", String.valueOf(d.get("pod_namespace")));
    assertTrue(Boolean.valueOf(String.valueOf(d.get("is_ocp"))));

    byte[] json = report.serializeRaw();
    System.out.println(new String(json, StandardCharsets.UTF_8));
  }
}
