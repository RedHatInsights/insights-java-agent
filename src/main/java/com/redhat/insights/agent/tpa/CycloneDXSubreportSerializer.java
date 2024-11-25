/* Copyright (C) Red Hat 2024 */
package com.redhat.insights.agent.tpa;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.redhat.insights.reports.InsightsSubreport;
import java.io.IOException;

public class CycloneDXSubreportSerializer extends JsonSerializer<InsightsSubreport> {
  @Override
  public void serialize(InsightsSubreport value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {}
}
