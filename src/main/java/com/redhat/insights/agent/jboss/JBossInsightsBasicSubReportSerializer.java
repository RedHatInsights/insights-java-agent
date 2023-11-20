/* Copyright (C) Red Hat 2023 */
package com.redhat.insights.agent.jboss;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.redhat.insights.reports.InsightsSubreport;
import java.io.IOException;

/** @author Emmanuel Hugonnet (c) 2023 Red Hat, Inc. */
public class JBossInsightsBasicSubReportSerializer extends JsonSerializer<InsightsSubreport> {

  @Override
  public void serialize(
      InsightsSubreport subReport, JsonGenerator generator, SerializerProvider serializerProvider)
      throws IOException {
    JBossInsightsBasicSubReport jBossInsightsBasicSubReport =
        (JBossInsightsBasicSubReport) subReport;
    generator.writeStartObject();
    generator.writeStringField("name", jBossInsightsBasicSubReport.getProduct());
    generator.writeStringField("version", jBossInsightsBasicSubReport.getVersion());
    generator.writeStringField("eap-version", jBossInsightsBasicSubReport.getProductVersion());
    generator.writeStringField("full-name", jBossInsightsBasicSubReport.getProductFullName());
    generator.writeEndObject();
    generator.flush();
  }
}
