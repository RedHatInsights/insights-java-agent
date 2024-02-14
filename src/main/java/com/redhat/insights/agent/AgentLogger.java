/* Copyright (C) Red Hat 2023-2024 */
package com.redhat.insights.agent;

import com.redhat.insights.logging.InsightsLogger;
import com.redhat.insights.logging.JulLogger;

public class AgentLogger implements InsightsLogger {

  private InsightsLogger delegate;

  public AgentLogger(Class<?> clazz) {
    this.delegate = new JulLogger(clazz.getName());
  }

  public void setDebugDelegate() {
    JulLogger newDelegate = new JulLogger("AgentMain");
    newDelegate.setDebug();
    this.delegate = newDelegate;
  }

  @Override
  public void debug(String message) {
    delegate.debug(message);
  }

  @Override
  public void debug(String message, Throwable err) {
    delegate.debug(message, err);
  }

  @Override
  public void error(String message) {
    delegate.error(message);
  }

  @Override
  public void error(String message, Throwable err) {
    delegate.error(message, err);
  }

  @Override
  public void info(String message) {
    delegate.info(message);
  }

  @Override
  public void warning(String message) {
    delegate.warning(message);
  }

  @Override
  public void warning(String message, Throwable err) {
    delegate.warning(message, err);
  }
}
