/* Copyright (C) Red Hat 2023 */
package com.redhat.insights.agent.jboss;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.redhat.insights.reports.InsightsSubreport;
import java.io.IOException;

/** @author Emmanuel Hugonnet (c) 2023 Red Hat, Inc. */
public class JBossInsightsConfigurationSubReportSerializer
    extends JsonSerializer<InsightsSubreport> {

  @Override
  public void serialize(
      InsightsSubreport subReport, JsonGenerator generator, SerializerProvider serializerProvider)
      throws IOException {
    JBossInsightsConfigurationSubReport jBossInsightsConfigurationSubReport =
        (JBossInsightsConfigurationSubReport) subReport;
    generator.writeStartObject();
    generator.writeStringField("version", jBossInsightsConfigurationSubReport.getVersion());
    generator.writeEndObject();
    generator.flush();
  }
}
