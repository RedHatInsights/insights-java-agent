import unittest
import sys
import os

from unittest import skip
from datetime import datetime
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '../../main/python'))
from insights_jvm import ProcessInfo, ProcUtil, JInfoParser, get_java_args, make_report, pretty_json


# Run with "pytest -v --capture=tee-sys ."
class TestMath(unittest.TestCase):
    def setUp(self):
        self.proc = ProcUtil()

    def test_jinfo1(self):
        # nt = ProcessInfo(1234, "java", "S", "", "-cp. scratch.HelloWorld", "/usr/bin/java")

        jinfo_txt = ""
        try:
            with open('../resources/jinfo1.txt', 'r') as f:
                jinfo_txt = f.read().strip()
        except (IOError, OSError):
            self.fail("Error reading jinfo1.txt")
            return None

        self.assertNotEqual(jinfo_txt, "")
        if jinfo_txt:
            parser = JInfoParser()
            jvm_info = parser.parse_output(jinfo_txt)
            self.assertEqual(jvm_info.system_properties['java.specification.version'], '17')

    @skip("Needs live process")
    def test_java_args1(self):
        proc_text = "/home/mnovak/Downloads/jdk-17/bin/java -D[Standalone] -Xlog:gc*:file=/home/mnovak/tmp/jboss-eap-8.0/standalone/log/gc.log:time,uptimemillis:filecount=5,filesize=3M -Djdk.serialFilter=maxbytes=10485760;maxdepth=128;maxarray=100000;maxrefs=300000 -Xms1303m -Xmx1303m -XX:MetaspaceSize=96M -XX:MaxMetaspaceSize=256m -Djava.net.preferIPv4Stack=true -Djboss.modules.system.pkgs=org.jboss.byteman -Djava.awt.headless=true --add-exports=java.desktop/sun.awt=ALL-UNNAMED --add-exports=java.naming/com.sun.jndi.ldap=ALL-UNNAMED --add-exports=java.naming/com.sun.jndi.url.ldap=ALL-UNNAMED --add-exports=java.naming/com.sun.jndi.url.ldaps=ALL-UNNAMED --add-exports=jdk.naming.dns/com.sun.jndi.dns=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.security=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED --add-opens=java.management/javax.management=ALL-UNNAMED --add-opens=java.naming/javax.naming=ALL-UNNAMED -Djava.security.manager=allow -Dorg.jboss.boot.log.file=/home/mnovak/tmp/jboss-eap-8.0/standalone/log/server.log -Dlogging.configuration=file:/home/mnovak/tmp/jboss-eap-8.0/standalone/configuration/logging.properties -jar /home/mnovak/tmp/jboss-eap-8.0/jboss-modules.jar -mp /home/mnovak/tmp/jboss-eap-8.0/modules org.jboss.as.standalone -Djboss.home.dir=/home/mnovak/tmp/jboss-eap-8.0 -Djboss.server.base.dir=/home/mnovak/tmp/jboss-eap-8.0/standalone -c standalone-full-ha.xml"
        lines = [arg for arg in proc_text.split(' ') if arg]
        (args, jboss_home) = get_java_args(lines)
        self.assertEqual(jboss_home, '/home/mnovak/tmp/jboss-eap-8.0')

    def test_jvm_flags(self):
        flags_text = "-XX:CICompilerCount=12 -XX:ConcGCThreads=4 -XX:G1ConcRefinementThreads=16 -XX:G1EagerReclaimRemSetThreshold=64 -XX:G1HeapRegionSize=8388608 -XX:G1RemSetArrayOfCardsEntries=64 -XX:G1RemSetHowlMaxNumBuckets=8 -XX:G1RemSetHowlNumBuckets=8 -XX:GCDrainStackTargetSize=64 -XX:InitialHeapSize=1048576000 -XX:MarkStackSize=4194304 -XX:MaxHeapSize=16710107136 -XX:MaxNewSize=10024386560 -XX:MinHeapDeltaBytes=8388608 -XX:MinHeapSize=8388608 -XX:NonNMethodCodeHeapSize=7602480 -XX:NonProfiledCodeHeapSize=122027880 -XX:ProfiledCodeHeapSize=122027880 -XX:ReservedCodeCacheSize=251658240 -XX:+SegmentedCodeCache -XX:SoftMaxHeapSize=16710107136 -XX:-THPStackMitigation -XX:+UseCompressedOops -XX:+UseG1GC"
        parser = JInfoParser()
        parser._parse_vm_flags(flags_text)
        self.assertEqual(parser.vm_flags["CICompilerCount"], "12")
        self.assertEqual(parser.vm_flags["ConcGCThreads"], "4")

    def test_full(self):
        # Get system boot time
        with open('/proc/stat', 'r') as f:
            for line in f:
                if line.startswith('btime'):
                    launch_time = int(line.split()[1])
                    break

        cmdline = "/home/mnovak/Downloads/jdk-17/bin/java -D[Standalone] -Xlog:gc*:file=/home/mnovak/tmp/jboss-eap-8.0/standalone/log/gc.log:time,uptimemillis:filecount=5,filesize=3M -Djdk.serialFilter=maxbytes=10485760;maxdepth=128;maxarray=100000;maxrefs=300000 -Xms1303m -Xmx2048m -XX:MetaspaceSize=96M -XX:MaxMetaspaceSize=256m -Djava.net.preferIPv4Stack=true -Djboss.modules.system.pkgs=org.jboss.byteman -Djava.awt.headless=true --add-exports=java.desktop/sun.awt=ALL-UNNAMED --add-exports=java.naming/com.sun.jndi.ldap=ALL-UNNAMED --add-exports=java.naming/com.sun.jndi.url.ldap=ALL-UNNAMED --add-exports=java.naming/com.sun.jndi.url.ldaps=ALL-UNNAMED --add-exports=jdk.naming.dns/com.sun.jndi.dns=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.security=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED --add-opens=java.management/javax.management=ALL-UNNAMED --add-opens=java.naming/javax.naming=ALL-UNNAMED -Djava.security.manager=allow -Dorg.jboss.boot.log.file=/home/mnovak/tmp/jboss-eap-8.0/standalone/log/server.log -Dlogging.configuration=file:/home/mnovak/tmp/jboss-eap-8.0/standalone/configuration/logging.properties -jar /home/mnovak/tmp/jboss-eap-8.0/jboss-modules.jar -mp /home/mnovak/tmp/jboss-eap-8.0/modules org.jboss.as.standalone -Djboss.home.dir=/home/mnovak/tmp/jboss-eap-8.0 -Djboss.server.base.dir=/home/mnovak/tmp/jboss-eap-8.0/standalone -c standalone-full-ha.xml"
        exe = "flibble"
        nt = ProcessInfo(13457, "java", str(datetime.fromtimestamp(launch_time)), cmdline.split(), exe, 2, "9.5")
        report = make_report(nt)
        self.assertEqual(report["jvm.heap.max"], "2048m")