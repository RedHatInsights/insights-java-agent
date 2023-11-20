/* Copyright (C) Red Hat 2023 */
package com.redhat.insights.agent.jboss;

import com.fasterxml.jackson.databind.JsonSerializer;
import com.redhat.insights.logging.InsightsLogger;
import com.redhat.insights.reports.InsightsSubreport;

/** @author Emmanuel Hugonnet (c) 2023 Red Hat, Inc. */
public class JBossInsightsWrapperSubReport implements InsightsSubreport {

  private final InsightsLogger logger;
  private final JBossInsightsBasicSubReport basic;
  private final JBossInsightsConfigurationSubReport configuration;

  public JBossInsightsWrapperSubReport(InsightsLogger logger) {
    this.logger = logger;
    this.basic = new JBossInsightsBasicSubReport(logger);
    this.configuration = new JBossInsightsConfigurationSubReport(logger);
  }

  @Override
  public void generateReport() {
    basic.generateReport();
    configuration.generateReport();
  }

  @Override
  public String getVersion() {
    return "1.0.0";
  }

  @Override
  public JsonSerializer<InsightsSubreport> getSerializer() {
    return new JBossInsightsWrapperSubReportSerializer();
  }

  public JBossInsightsBasicSubReport getBasic() {
    return basic;
  }

  public JBossInsightsConfigurationSubReport getConfiguration() {
    return configuration;
  }
}
