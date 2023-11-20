/* Copyright (C) Red Hat 2023 */
package com.redhat.insights.agent.jboss;

import static com.redhat.insights.agent.jboss.JBossModuleHelper.EMPTY_CLASS_ARRAY;
import static com.redhat.insights.agent.jboss.JBossModuleHelper.EMPTY_OBJECT_ARRAY;

import com.fasterxml.jackson.databind.JsonSerializer;
import com.redhat.insights.logging.InsightsLogger;
import com.redhat.insights.reports.InsightsSubreport;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/** @author Emmanuel Hugonnet (c) 2023 Red Hat, Inc. */
public class JBossInsightsConfigurationSubReport implements InsightsSubreport {

  private final InsightsLogger logger;

  public JBossInsightsConfigurationSubReport(InsightsLogger logger) {
    this.logger = logger;
  }

  @Override
  public void generateReport() {
    try {
      ClassLoader serverClassLoader = JBossModuleHelper.getModuleClassLoader("org.jboss.as.server");
      Class<?> serviceContainerClass =
          serverClassLoader.loadClass("org.jboss.msc.service.ServiceContainer");
      Class<?> serviceControllerClass =
          serverClassLoader.loadClass("org.jboss.msc.service.ServiceController");
      Class<?> modelControllerClientFactoryClass =
          serverClassLoader.loadClass("org.jboss.as.controller.ModelControllerClientFactory");
      Class<?> modelControllerClientClass =
          serverClassLoader.loadClass("org.jboss.as.controller.client.ModelControllerClient");
      Object container =
          serviceContainerClass.cast(
              getServiceContainer(serverClassLoader, serviceContainerClass, 30000));
      Object jbossServerClientFactoryServiceName =
          serverClassLoader
              .loadClass("org.jboss.as.server.ServerService")
              .getDeclaredField("JBOSS_SERVER_CLIENT_FACTORY")
              .get(null);
      Object modelControllerClientFactoryServiceController =
          serviceControllerClass.cast(
              serviceContainerClass
                  .getMethod("getService", jbossServerClientFactoryServiceName.getClass())
                  .invoke(container, new Object[] {jbossServerClientFactoryServiceName}));
      System.out.println("Service " + modelControllerClientFactoryServiceController);
      Object modelControllerClientFactory =
          modelControllerClientFactoryClass.cast(
              serviceControllerClass
                  .getMethod("getValue", EMPTY_CLASS_ARRAY)
                  .invoke(modelControllerClientFactoryServiceController, EMPTY_OBJECT_ARRAY));
      System.out.println("modelControllerClientFactory loaded " + modelControllerClientFactory);
      Object executorCapability =
          serverClassLoader
              .loadClass("org.jboss.as.controller.AbstractControllerService")
              .getDeclaredField("EXECUTOR_CAPABILITY")
              .get(null);
      Object managementExecutorServiceName =
          executorCapability
              .getClass()
              .getMethod("getCapabilityServiceName", EMPTY_CLASS_ARRAY)
              .invoke(executorCapability, EMPTY_OBJECT_ARRAY);
      Object executorServiceController =
          serviceControllerClass.cast(
              serviceContainerClass
                  .getMethod("getService", managementExecutorServiceName.getClass())
                  .invoke(container, new Object[] {managementExecutorServiceName}));
      Executor executor =
          Executor.class.cast(
              serviceControllerClass
                  .getMethod("getValue", EMPTY_CLASS_ARRAY)
                  .invoke(executorServiceController, EMPTY_OBJECT_ARRAY));
      System.out.println("executor loaded " + executor);
      Object modelControllerClient =
          modelControllerClientClass.cast(
              modelControllerClientFactoryClass
                  .getDeclaredMethod(
                      "createSuperUserClient",
                      new Class<?>[] {java.util.concurrent.Executor.class, boolean.class})
                  .invoke(modelControllerClientFactory, new Object[] {executor, false}));
      System.out.println("modelControllerClient loaded " + modelControllerClient);

    } catch (NoSuchMethodException
        | SecurityException
        | ClassNotFoundException
        | IllegalAccessException
        | IllegalArgumentException
        | InvocationTargetException
        | NoSuchFieldException ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  private Object getServiceContainer(
      ClassLoader serverClassLoader, Class<?> serviceContainerClass, long duration)
      throws ClassNotFoundException, IllegalAccessException, IllegalArgumentException,
          InvocationTargetException, NoSuchMethodException {
    Class<?> currentServiceContainerClass =
        serverClassLoader.loadClass("org.jboss.as.server.CurrentServiceContainer");
    Object container = null;
    long timeout = System.currentTimeMillis() + duration;
    while (container == null && System.currentTimeMillis() < timeout) {
      container =
          currentServiceContainerClass
              .getMethod("getServiceContainer", EMPTY_CLASS_ARRAY)
              .invoke(null, EMPTY_OBJECT_ARRAY);
    }
    if (container != null) {
      serviceContainerClass
          .getMethod("awaitStability", new Class<?>[] {long.class, TimeUnit.class})
          .invoke(container, new Object[] {duration, TimeUnit.MILLISECONDS});
    }
    return container;
  }

  @Override
  public String getVersion() {
    return "1.0.0";
  }

  @Override
  public JsonSerializer<InsightsSubreport> getSerializer() {
    return new JBossInsightsConfigurationSubReportSerializer();
  }
}
