/* Copyright (C) Red Hat 2024 */
package com.redhat.insights.agent.tpa;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.redhat.insights.reports.InsightsSubreport;
import org.cyclonedx.BomGeneratorFactory;
import org.cyclonedx.CycloneDxSchema;
import org.cyclonedx.generators.json.BomJsonGenerator;

import java.io.IOException;

public class CycloneDXSubreportSerializer extends JsonSerializer<InsightsSubreport> {
  @Override
  public void serialize(
      InsightsSubreport insightsSubreport, JsonGenerator generator, SerializerProvider serializers)
      throws IOException {
    CycloneDXSubreport subreport = (CycloneDXSubreport) insightsSubreport;
    generator.writeStartObject();
    BomJsonGenerator bomGenerator = BomGeneratorFactory.createJson(CycloneDxSchema.VERSION_LATEST, subreport.getSbom());
    JsonNode bomString = bomGenerator.toJsonNode();
    generator.writeObjectField("cyclonedx", bomString);
    generator.writeEndObject();
    generator.flush();
  }
}
