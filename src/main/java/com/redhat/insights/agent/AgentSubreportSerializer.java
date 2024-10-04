/* Copyright (C) Red Hat 2023-2024 */
package com.redhat.insights.agent;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.redhat.insights.reports.InsightsSubreport;
import java.io.IOException;

public class AgentSubreportSerializer extends JsonSerializer<InsightsSubreport> {
  @Override
  public void serialize(
      InsightsSubreport insightsSubreport,
      JsonGenerator generator,
      SerializerProvider serializerProvider)
      throws IOException {
    AgentSubreport subreport = (AgentSubreport) insightsSubreport;
    generator.writeStartObject();
    generator.writeStringField("version", subreport.getVersion());
    generator.writeStringField("workloadType", subreport.getGuessedWorkload());
    generator.writeStringField("is_ocp", subreport.isOCP());
    generator.writeStringField("pod_name", subreport.getPodName());
    generator.writeStringField("pod_namespace", subreport.getPodNamespace());
    generator.writeEndObject();
    generator.flush();
  }
}
