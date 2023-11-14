/* Copyright (C) Red Hat 2023 */
package com.redhat.insights.agent;

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
class JBossInsightsWrapperSubReport implements InsightsSubreport {
  private static final Class[] EMPTY_CLASS_ARRAY = new Class[0];
  private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

  private final InsightsLogger logger;
  private String productName = null;
  private String productVersion = null;
  private String productFullName = null;
  private boolean isProduct = false;

  JBossInsightsWrapperSubReport(InsightsLogger logger) {
    this.logger = logger;
  }

  @Override
  public void generateReport() {
    try {
      Class<?> moduleClass = this.getClass().getClassLoader().loadClass("org.jboss.modules.Module");
      Method getBootModuleLoaderMethod =
          moduleClass.getDeclaredMethod("getBootModuleLoader", EMPTY_CLASS_ARRAY);
      Method getClassLoaderMethod =
          moduleClass.getDeclaredMethod("getClassLoader", EMPTY_CLASS_ARRAY);
      Object moduleLoader = getBootModuleLoaderMethod.invoke(null, EMPTY_OBJECT_ARRAY);
      Method loadModuleMethod =
          moduleLoader.getClass().getMethod("loadModule", new Class[] {String.class});
      Object versionModule = loadModuleMethod.invoke(moduleLoader, "org.jboss.as.version");
      ClassLoader versionModuleClassLoader =
          (ClassLoader) getClassLoaderMethod.invoke(versionModule, EMPTY_OBJECT_ARRAY);
      Method getContextModuleLoaderMethod =
          moduleClass.getDeclaredMethod("getContextModuleLoader", EMPTY_CLASS_ARRAY);
      Object versionModuleLoader =
          getContextModuleLoaderMethod.invoke(versionModule, EMPTY_OBJECT_ARRAY);
      String home = getHome();
      if (home != null) {
        Class moduleLoaderClass = getModuleLoaderClass(versionModuleLoader.getClass());
        Class productConfigClass =
            versionModuleClassLoader.loadClass("org.jboss.as.version.ProductConfig");
        Method fromFilesystemSlotMethod =
            productConfigClass.getDeclaredMethod(
                "fromFilesystemSlot", new Class[] {moduleLoaderClass, String.class, Map.class});
        Object productConfig =
            fromFilesystemSlotMethod.invoke(
                null, moduleLoaderClass.cast(versionModuleLoader), home, Collections.emptyMap());
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
      }
    } catch (NoSuchMethodException
        | SecurityException
        | ClassNotFoundException
        | IllegalAccessException
        | IllegalArgumentException
        | InvocationTargetException ex) {
      logger.error(ex.getMessage(), ex);
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
    return new JBossInsightsWrapperSubReportSerializer();
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
