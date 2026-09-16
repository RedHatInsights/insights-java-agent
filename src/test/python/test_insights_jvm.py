import json
import unittest
import sys
import os
import subprocess
from unittest import skip
from unittest.mock import patch, MagicMock
from datetime import datetime
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '../../main/python'))
from insights_jvm import (
    ProcessInfo,
    ProcUtil,
    JInfoParser,
    get_java_args,
    make_report,
    pretty_json,
    run_jinfo,
    jinfo_to_dict,
    find_jinfo_binary,
    scan_and_write_reports,
)
import tempfile


# Run with "pytest -v --capture=tee-sys ."
class TestMath(unittest.TestCase):
    def setUp(self):
        self.proc = ProcUtil()

    def test_jinfo1(self):
        # nt = ProcessInfo(1234, "java", "S", "", "-cp. scratch.HelloWorld", "/usr/bin/java")

        jinfo_txt = ""
        jinfo_file_path = os.path.join(os.path.dirname(__file__), '../resources/jinfo1.txt')
        try:
            with open(jinfo_file_path, 'r') as f:
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
        # System boot time (use fallback if /proc/stat not present on non-Linux systems)
        launch_time = 1700000000
        if os.path.exists('/proc/stat'):
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

    def test_run_jinfo_error_formatting_and_messages(self):
        #! Change justification: Verify Bug 1 & Bug 5 fixes (stderr interpolation and jinfo error messages on failure/timeout).
        #! Test fails if run_jinfo returns literal "{result.stderr}" or mentions "JPS".
        mock_res = MagicMock()
        mock_res.returncode = 1
        mock_res.stderr = "Error: process not found"
        mock_res.stdout = ""

        with patch('subprocess.run', return_value=mock_res):
            success, output = run_jinfo("/usr/bin/jinfo", 1234)
            self.assertFalse(success)
            self.assertEqual(output, "Error: process not found")

        with patch('subprocess.run', side_effect=subprocess.TimeoutExpired(cmd="jinfo", timeout=10)):
            success, output = run_jinfo("/usr/bin/jinfo", 1234)
            self.assertFalse(success)
            self.assertEqual(output, "jinfo execution timed out")

        with patch('subprocess.run', side_effect=Exception("permission denied")):
            success, output = run_jinfo("/usr/bin/jinfo", 1234)
            self.assertFalse(success)
            self.assertEqual(output, "Error running jinfo: permission denied")

    def test_find_jinfo_binary_path_resolution(self):
        #! Change justification: Verify Bug 5 fix (renamed variable and resolution of jinfo binary next to java executable).
        #! No automated test covered find_jinfo_binary previously.
        with patch('os.path.exists', return_value=True), patch('os.access', return_value=True):
            resolved = find_jinfo_binary('/usr/lib/jvm/java-17/bin/java')
            self.assertEqual(resolved, '/usr/lib/jvm/java-17/bin/jinfo')

    def test_get_java_args_skips_xms_and_xmx(self):
        #! Change justification: Verify Bug 2 fix (both -Xms and -Xmx are skipped from sanitized jvm.args output).
        #! Test fails if -Xms is not filtered out and leaks into jvm.args.
        args = ["java", "-Xms512m", "-Xmx1024m", "-Dcustom.prop=secret", "-server", "dummy_end"]
        out, jboss_home = get_java_args(args)
        self.assertNotIn("-Xms512m", out)
        self.assertNotIn("-Xmx1024m", out)
        self.assertIn("-Dcustom.prop=ZZZZZZZZZ", out)
        self.assertIn("-server", out)
        self.assertEqual(jboss_home, "Unknown")

    def test_get_java_args_with_jar_and_subsequent_args(self):
        #! Change justification: Verify -jar handling in get_java_args (includes -jar and jar path, excludes subsequent application args).
        #! Test fails if -jar / jar path is omitted or if subsequent application args leak into jvm args.
        args = ["java", "-Dcustom.prop=secret", "-jar", "/path/to/app.jar", "server", "--config", "conf.yml"]
        out, jboss_home = get_java_args(args)
        self.assertIn("-Dcustom.prop=ZZZZZZZZZ", out)
        self.assertIn("-jar", out)
        self.assertIn("/path/to/app.jar", out)
        self.assertNotIn("server", out)
        self.assertNotIn("--config", out)
        self.assertNotIn("conf.yml", out)
        self.assertEqual(jboss_home, "Unknown")

    def test_jinfo_to_dict_missing_properties(self):
        #! Change justification: Verify Bug 4 fix (jinfo_to_dict safely handles missing system properties without KeyError).
        #! Test fails if jinfo_to_dict attempts direct indexing on missing keys.
        partial_output = """
Java System Properties:
    java.specification.version = 17
    os.arch = x86_64

VM Flags:
    -XX:+UseG1GC
"""
        result = jinfo_to_dict(partial_output)
        self.assertEqual(result["method"], "jinfo")
        self.assertEqual(result["java.major.version"], "17")
        self.assertEqual(result["system.arch"], "x86_64")
        self.assertEqual(result["vendor"], "")
        self.assertEqual(result["java.vm.name"], "")
        self.assertEqual(result["kernel.version"], "")
        self.assertEqual(result["version.string"], "")

    def test_pretty_json_standard_serialization(self):
        #! Change justification: Verify Performance & Robustness issue 1 fix (use json module for serializing namedtuples/structures).
        #! Test fails if pretty_json produces invalid JSON or fails on types.
        proc = ProcessInfo(1234, "java", "2026-09-16 12:00:00", ["-jar", "app.jar"], "/usr/bin/java", 4, "RHEL 9.2")
        payload = {"version": "1.0.2", "psdata": make_report(proc)}
        json_str = pretty_json(payload)
        parsed = json.loads(json_str)
        self.assertEqual(parsed["version"], "1.0.2")
        self.assertEqual(parsed["psdata"]["name"], "/usr/bin/java")
        self.assertEqual(parsed["psdata"]["processors"], 4)

    def test_get_process_info_with_spaces_in_comm_name(self):
        #! Change justification: Verify Performance & Robustness issue 2 fix (/proc/pid/stat parsing with spaces/parentheses in comm).
        #! Test fails if stat parsing splits naively on whitespace and misaligns fields.
        proc_util = ProcUtil()
        # stat format: pid (comm with spaces and parens (1)) state ppid ... starttime(field 22) ...
        # Fields after ')':
        # 3:S 4:1 5:1 6:0 7:0 8:0 9:0 10:0 11:0 12:0 13:0 14:0 15:0 16:0 17:0 18:0 19:0 20:0 21:0 22:50000 23:0 24:0
        dummy_stat = "1234 (java worker (test)) S 1 1 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 50000 0 0"

        with patch.object(proc_util, 'pid_exists', return_value=True), \
             patch.object(proc_util, '_read_file', return_value=dummy_stat), \
             patch.object(proc_util, 'get_process_cmdline', return_value=['java', '-jar', 'test.jar']), \
             patch.object(proc_util, 'get_process_exe', return_value='/usr/bin/java'), \
             patch.object(proc_util, 'get_process_launch_time', return_value='2026-09-16 10:00:00'):
            info = proc_util.get_process_info(1234)
            self.assertIsNotNone(info)
            self.assertEqual(info.name, "java worker (test)")
            self.assertEqual(info.pid, 1234)
            self.assertEqual(info.exe, "/usr/bin/java")
            self.assertEqual(info.launch_time, "2026-09-16 10:00:00")

    def test_scan_and_write_reports_error_handling(self):
        #! Change justification: Verify Error Logging / Exit Codes fix (scan_and_write_reports reports error count and writes to stderr on failure).
        #! Test fails if write errors are suppressed or error_count is not reported.
        proc = ProcessInfo(1234, "java", "2026-09-16 12:00:00", ["-jar", "app.jar"], "/usr/bin/java", 4, "RHEL 9.2")
        with tempfile.TemporaryDirectory() as temp_dir:
            # Point to a path inside non-writable directory to trigger OSError
            read_only_dir = os.path.join(temp_dir, "readonly_uploads")
            os.makedirs(read_only_dir, mode=0o400)
            target_dir = os.path.join(read_only_dir, "nested")

            with patch('insights_jvm.ProcUtil.get_processes', return_value=[proc]), \
                 patch('sys.stderr.write') as mock_stderr:
                written, errors = scan_and_write_reports(output_dir=target_dir)
                self.assertEqual(written, 0)
                self.assertEqual(errors, 1)
                self.assertTrue(mock_stderr.called)