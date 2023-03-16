/* Copyright (C) Red Hat 2023 */
package com.redhat.insights.agent;

import com.sun.tools.attach.VirtualMachine;
import java.net.URL;

public class Attach {
  public static void main(String[] args) {
    if (args.length < 2) {
      System.err.println("Need args: pid, options"); // , path to agent
      System.exit(1);
    }
    URL jarUrl = AgentMain.class.getProtectionDomain().getCodeSource().getLocation();
    System.out.println("Trying to attach: " + jarUrl);
    String agentJar = jarUrl.toExternalForm().replaceFirst("^file:", "");

    String pid = args[0];
    String options = args[1];
    //    String agentJar = args[2];

    try {
      VirtualMachine vm = VirtualMachine.attach(pid);
      vm.loadAgent(agentJar, options);
      vm.detach();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
