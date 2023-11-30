/* Copyright (C) Red Hat 2023 */
package com.redhat.insights.agent.shaded;

import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.LogRecord;

// Maven Shade plugin rewrites java.util.logging.Logger classes to reference this one instead.
// This is part of the encapsulation of the Agent's logging to separate it from the attached
// application's logging.
public class ShadeLogger {

  public static ShadeLogger getLogger(String name) {
    return new ShadeLogger();
  }

  public static ShadeLogger getLogger(String name, String resourceBundleName) {
    return new ShadeLogger();
  }

  public static ShadeLogger getAnonymousLogger() {
    return new ShadeLogger();
  }

  public static ShadeLogger getAnonymousLogger(String resourceBundleName) {
    return new ShadeLogger();
  }

  public String getName() {
    return "";
  }

  public void log(LogRecord record) {}

  public void log(Level level, String msg) {}

  public void log(Level level, String msg, Object arg1) {}

  public void log(Level level, String msg, Object[] args) {}

  public void log(Level level, String msg, Throwable t) {}

  public void logp(Level level, String klazz, String mtd, String msg) {}

  public void logp(Level level, String klazz, String mtd, String msg, Object arg1) {}

  public void logp(Level level, String klazz, String mtd, String msg, Object[] args) {}

  public void logp(Level level, String klazz, String mtd, String msg, Throwable t) {}

  public void logrb(Level level, String klazz, String mtd, String bundle, String msg) {}

  public void logrb(
      Level level, String klazz, String mtd, String bundle, String msg, Object arg1) {}

  public void logrb(
      Level level, String klazz, String mtd, String bundle, String msg, Object[] args) {}

  public void logrb(
      Level level, String klazz, String mtd, ResourceBundle bundle, String msg, Object... args) {}

  public void logrb(
      Level level, String klazz, String mtd, String bundle, String msg, Throwable t) {}

  public void logrb(
      Level level, String klazz, String mtd, ResourceBundle bundle, String msg, Throwable t) {}

  public void severe(String msg) {}

  public void warning(String msg) {}

  public void info(String msg) {}

  public void config(String msg) {}

  public void fine(String msg) {}

  public void finer(String msg) {}

  public void finest(String msg) {}

  public void throwing(String klazz, String mtd, Throwable t) {}

  public void setLevel(Level newLevel) throws SecurityException {}

  public boolean isLoggable(Level level) {
    return false;
  }
}
