import unittest
import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '../../main/python'))
from insights_jvm import ProcUtil, JInfoParser

class TestMath(unittest.TestCase):
    def setUp(self):
        self.proc = ProcUtil()

    def test_addition(self):
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

