/* Copyright (C) Red Hat 2023 */
package com.redhat.insights.doubles;

import com.redhat.insights.logging.InsightsLogger;

public class NoopInsightsLogger implements InsightsLogger {

  @Override
  public void debug(String message) {
    // No-op
  }

  @Override
  public void debug(String message, Throwable err) {
    // No-op
  }

  @Override
  public void info(String message) {
    // No-op
  }

  @Override
  public void error(String message) {
    // No-op
  }

  @Override
  public void error(String message, Throwable err) {
    // No-op
  }

  @Override
  public void warning(String message) {
    // No-op
  }

  @Override
  public void warning(String message, Throwable err) {
    // No-op
  }
}
