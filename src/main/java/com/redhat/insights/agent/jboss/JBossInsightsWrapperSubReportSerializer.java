/* Copyright (C) Red Hat 2023 */
package com.redhat.insights.agent.jboss;

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
    JBossInsightsWrapperSubReport wrapper = (JBossInsightsWrapperSubReport) subReport;
    JBossInsightsBasicSubReport basic = wrapper.getBasic();
    JBossInsightsConfigurationSubReport configuration = wrapper.getConfiguration();
    generator.writeStartObject();
    generator.writeFieldName("eap-installtion");
    basic.getSerializer().serialize(basic, generator, serializerProvider);
    generator.writeFieldName("eap-configuration");
    configuration.getSerializer().serialize(configuration, generator, serializerProvider);
    generator.writeEndObject();
    generator.flush();
  }
}
