/* Copyright (C) Red Hat 2023 */
package com.redhat.insights.agent.jboss;

import static com.redhat.insights.agent.jboss.JBossModuleHelper.EMPTY_CLASS_ARRAY;
import static com.redhat.insights.agent.jboss.JBossModuleHelper.EMPTY_OBJECT_ARRAY;

import com.fasterxml.jackson.databind.JsonSerializer;
import com.redhat.insights.logging.InsightsLogger;
import com.redhat.insights.reports.InsightsSubreport;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Map;

/** @author Emmanuel Hugonnet (c) 2023 Red Hat, Inc. */
public class JBossInsightsBasicSubReport implements InsightsSubreport {

  private final InsightsLogger logger;
  private String productName = null;
  private String productVersion = null;
  private String productFullName = null;
  private boolean isProduct = false;

  public JBossInsightsBasicSubReport(InsightsLogger logger) {
    this.logger = logger;
  }

  @Override
  public void generateReport() {
    try {
      System.out.println("Getting the server version " + productFullName);
      ClassLoader versionModuleClassLoader =
          JBossModuleHelper.getModuleClassLoader("org.jboss.as.version");
      String home = getHome();
      Object moduleLoader = JBossModuleHelper.getBootModuleLoader();
      if (home != null) {
        Class moduleLoaderClass = getModuleLoaderClass(moduleLoader.getClass());
        Class productConfigClass =
            versionModuleClassLoader.loadClass("org.jboss.as.version.ProductConfig");
        Method fromFilesystemSlotMethod =
            productConfigClass.getDeclaredMethod(
                "fromFilesystemSlot", new Class[] {moduleLoaderClass, String.class, Map.class});
        Object productConfig =
            fromFilesystemSlotMethod.invoke(
                null, moduleLoaderClass.cast(moduleLoader), home, Collections.emptyMap());
        this.productName =
            (String)
                productConfigClass
                    .getDeclaredMethod("getProductName", EMPTY_CLASS_ARRAY)
                    .invoke(productConfig, EMPTY_OBJECT_ARRAY);
        this.productVersion =
            (String)
                productConfigClass
                    .getDeclaredMethod("getProductVersion", EMPTY_CLASS_ARRAY)
                    .invoke(productConfig, EMPTY_OBJECT_ARRAY);
        this.isProduct =
            (Boolean)
                productConfigClass
                    .getDeclaredMethod("isProduct", EMPTY_CLASS_ARRAY)
                    .invoke(productConfig, EMPTY_OBJECT_ARRAY);
        this.productFullName =
            (String)
                productConfigClass
                    .getDeclaredMethod("getPrettyVersionString", EMPTY_CLASS_ARRAY)
                    .invoke(productConfig, EMPTY_OBJECT_ARRAY);
        System.out.println("We have found the product version " + productFullName);
      }
    } catch (NoSuchMethodException
        | SecurityException
        | ClassNotFoundException
        | IllegalAccessException
        | IllegalArgumentException
        | InvocationTargetException ex) {
      logger.error(ex.getMessage(), ex);
      System.out.println("Error " + ex.getMessage());
      ex.printStackTrace(System.out);
    }
  }

  private Class getModuleLoaderClass(Class subModuleLoaderClass) {
    if ("org.jboss.modules.ModuleLoader".equals(subModuleLoaderClass.getName())) {
      return subModuleLoaderClass;
    }
    if ("java.lang.Object".equals(subModuleLoaderClass.getName())) {
      throw new IllegalArgumentException(
          subModuleLoaderClass + " is not a subclass of org.jboss.modules.ModuleLoader");
    }
    return getModuleLoaderClass(subModuleLoaderClass.getSuperclass());
  }

  private String getHome() {
    if (System.getSecurityManager() != null) {
      return AccessController.doPrivileged(
          (PrivilegedAction<String>)
              () -> {
                return System.getProperty("jboss.home.dir", null);
              });
    }
    return System.getProperty("jboss.home.dir", null);
  }

  @Override
  public String getVersion() {
    return "1.0.0";
  }

  @Override
  public JsonSerializer<InsightsSubreport> getSerializer() {
    return new JBossInsightsBasicSubReportSerializer();
  }

  String getProductVersion() {
    return productVersion;
  }

  String getProductFullName() {
    return productFullName;
  }

  String getProduct() {
    return productName;
  }
}
