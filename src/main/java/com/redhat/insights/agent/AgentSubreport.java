/* Copyright (C) Red Hat 2023 */
package com.redhat.insights.agent;

import com.fasterxml.jackson.databind.JsonSerializer;
import com.redhat.insights.jars.ClasspathJarInfoSubreport;
import com.redhat.insights.reports.AppInsightsReportSerializer;
import com.redhat.insights.reports.InsightsSubreport;

public class AgentSubreport implements InsightsSubreport {

  private AgentSubreport() {}

  public static InsightsSubreport of(ClasspathJarInfoSubreport jarsReport) {
    return new AgentSubreport();
  }

  @Override
  public void generateReport() {}

  @Override
  public String getVersion() {
    return "1.0.0";
  }

  @Override
  public JsonSerializer<InsightsSubreport> getSerializer() {
    return new AppInsightsReportSerializer();
  }
}
