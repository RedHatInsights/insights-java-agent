/* Copyright (C) Red Hat 2023 */
package com.redhat.insights.agent;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.redhat.insights.reports.InsightsSubreport;
import java.io.IOException;

/** @author Emmanuel Hugonnet (c) 2023 Red Hat, Inc. */
public class JBossInsightsWrapperSubReportSerializer extends JsonSerializer<InsightsSubreport> {

  @Override
  public void serialize(
      InsightsSubreport subReport, JsonGenerator generator, SerializerProvider serializerProvider)
      throws IOException {
    JBossInsightsWrapperSubReport jBossInsightsWrapperSubReport =
        (JBossInsightsWrapperSubReport) subReport;
    generator.writeStartObject();
    generator.writeStringField("name", jBossInsightsWrapperSubReport.getProduct());
    generator.writeStringField("version", jBossInsightsWrapperSubReport.getVersion());
    generator.writeStringField("eap-version", jBossInsightsWrapperSubReport.getProductVersion());
    generator.writeStringField("full-name", jBossInsightsWrapperSubReport.getProductFullName());
    generator.writeEndObject();
    generator.flush();
  }
}
