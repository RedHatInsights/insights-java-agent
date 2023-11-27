/* Copyright (C) Red Hat 2023 */
package com.redhat.insights.agent;

import com.redhat.insights.logging.InsightsLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SLF4JLogger implements InsightsLogger {

  private final Logger delegate;

  public SLF4JLogger(Class<?> clazz) {
    this.delegate = LoggerFactory.getLogger(clazz);
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
    delegate.warn(message);
  }

  @Override
  public void warning(String message, Throwable err) {
    delegate.warn(message, err);
  }
}
