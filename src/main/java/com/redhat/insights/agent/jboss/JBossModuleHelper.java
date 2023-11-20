/* Copyright (C) Red Hat 2023 */
package com.redhat.insights.agent.jboss;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** @author ehugonne */
public class JBossModuleHelper {

  static final Class[] EMPTY_CLASS_ARRAY = new Class[0];
  static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
  private static Class<?> moduleClass = null;

  public static ClassLoader getModuleClassLoader(String moduleName)
      throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException,
          IllegalArgumentException, InvocationTargetException {
    Method getClassLoaderMethod =
        getModuleClass().getDeclaredMethod("getClassLoader", EMPTY_CLASS_ARRAY);
    return (ClassLoader) getClassLoaderMethod.invoke(loadModule(moduleName), EMPTY_OBJECT_ARRAY);
  }

  public static Object loadModule(String moduleName)
      throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException,
          IllegalArgumentException, InvocationTargetException {
    Object moduleLoader = getBootModuleLoader();
    Method loadModuleMethod =
        moduleLoader.getClass().getMethod("loadModule", new Class[] {String.class});
    return loadModuleMethod.invoke(moduleLoader, moduleName);
  }

  public static Object getBootModuleLoader()
      throws IllegalAccessException, IllegalArgumentException, InvocationTargetException,
          ClassNotFoundException, NoSuchMethodException {
    Method getBootModuleLoaderMethod =
        getModuleClass().getDeclaredMethod("getBootModuleLoader", EMPTY_CLASS_ARRAY);
    return getBootModuleLoaderMethod.invoke(null, EMPTY_OBJECT_ARRAY);
  }

  public static Class<?> getModuleClass() throws ClassNotFoundException {
    if (moduleClass == null) {
      moduleClass = JBossModuleHelper.class.getClassLoader().loadClass("org.jboss.modules.Module");
    }
    return moduleClass;
  }
}
