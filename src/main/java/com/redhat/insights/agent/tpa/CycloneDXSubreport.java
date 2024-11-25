/* Copyright (C) Red Hat 2024 */
package com.redhat.insights.agent.tpa;

import com.fasterxml.jackson.databind.JsonSerializer;
import com.redhat.insights.logging.InsightsLogger;
import com.redhat.insights.reports.InsightsSubreport;

public class CycloneDXSubreport implements InsightsSubreport {

  private final InsightsLogger logger;
  private CycloneDXModel sbom = null;

  public CycloneDXSubreport(InsightsLogger logger) {
    this.logger = logger;
  }

  @Override
  public synchronized void generateReport() {
    if (sbom == null) {
      sbom = new CycloneDXModel(logger);
    }
    //        sbom.setComponents( libs.getLibraries() );
    //        sbom.setDependencies( libs.getDependencies() );
    //        sbom.save( filename );
  }

  @Override
  public String getVersion() {
    return "" + sbom.getVersion();
  }

  @Override
  public JsonSerializer<InsightsSubreport> getSerializer() {
    return null;
  }
}
