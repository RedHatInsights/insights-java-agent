"""
insights_jvm.py - A zero-dependency script to scan RHEL systems for
JVM processes and create a JSON report for upload to Insights.
Parses /proc filesystem to gather system and process information.
An alternative would be to look in /tmp/hsperfdata_<userid> for PIDs
that we have read access to. The disadvantages of this route include
needing to parse the hsperfdata format.
"""

# FIXME This needs to be replaced before deploy
import json
import time
import os
import re
import subprocess
import glob
from collections import namedtuple
from datetime import datetime
from typing import Dict, List
from dataclasses import dataclass

# Named tuples for structured data
ProcessInfo = namedtuple('ProcessInfo', ['pid', 'name', 'launch_time', 'cmdline', 'exe', 'processors', 'rhel_version'])

class ProcUtil:
    """Simple class to replace psutil functionality """
    def __init__(self):
        self.rhel_version = self._read_file('/etc/redhat-release')
        self.processors = os.cpu_count()

    def _read_file(self, filepath):
        try:
            with open(filepath, 'r') as f:
                return f.read().strip()
        except (IOError, OSError):
            return None

    def get_pids(self):
        """Get list of all process IDs"""
        _pids = []
        for pid_dir in glob.glob('/proc/[0-9]*'):
            try:
                pid = int(os.path.basename(pid_dir))
                _pids.append(pid)
            except ValueError:
                continue
        return sorted(_pids)

    def pid_exists(self, p_id):
        """Check if a process ID exists"""
        return os.path.isdir(f'/proc/{p_id}')

    def get_process_info(self, p_id) -> ProcessInfo:
        """Get detailed information about a process"""
        if not self.pid_exists(p_id):
            return None

        # Read /proc/pid/stat
        stat_content = self._read_file(f'/proc/{p_id}/stat')
        if not stat_content:
            return None

        stat_fields = stat_content.split()
        if len(stat_fields) < 24:
            return None

        # Extract relevant fields
        name = stat_fields[1].strip('()')

        cmdline = self.get_process_cmdline(p_id)
        exe = self.get_process_exe(p_id)
        launch_time = self.get_process_launch_time(p_id)

        return ProcessInfo(p_id, name, launch_time, cmdline, exe, self.processors, self.rhel_version)

    def get_process_launch_time(self, pid):
        """Retrieve the process start time"""
        try:
            # Read /proc/[pid]/stat
            with open(f'/proc/{pid}/stat', 'r') as f:
                stat_data = f.read().split()

            # Field 22 is starttime (in clock ticks since boot)
            starttime_ticks = int(stat_data[21])

            # Get system boot time
            with open('/proc/stat', 'r') as f:
                for line in f:
                    if line.startswith('btime'):
                        boot_time = int(line.split()[1])
                        break

            # Get clock ticks per second
            clock_ticks = os.sysconf(os.sysconf_names['SC_CLK_TCK'])

            # Calculate process start time
            start_time = boot_time + (starttime_ticks / clock_ticks)
            return str(datetime.fromtimestamp(start_time))

        except (FileNotFoundError, IndexError, ValueError):
            return None

    def get_process_cmdline(self, pid):
        """Get process command line arguments"""
        cmdline_content = self._read_file(f'/proc/{pid}/cmdline')
        if not cmdline_content:
            return []

        # Command line arguments are null-separated
        # Filter out empty strings that can occur with trailing nulls
        args = [arg for arg in cmdline_content.split('\x00') if arg]
        return args

    def get_process_exe(self, pid):
        """Get process executable path"""
        try:
            return os.readlink(f'/proc/{pid}/exe')
        except (OSError, IOError):
            return None

    def get_processes(self):
        """Get information about all processes"""
        _processes = []
        for pid in self.get_pids():
            proc_info = self.get_process_info(pid)
            if proc_info:
                _processes.append(proc_info)
        return _processes

@dataclass
class JVMInfo:
    """Container for parsed JVM information"""
    system_properties: Dict[str, str]
    vm_flags: Dict[str, str]
    vm_arguments: List[str]
    non_default_vm_flags: Dict[str, str]

class JInfoParser:
    """Parser for jinfo -v output"""

    def __init__(self):
        self.system_properties = {}
        self.vm_flags = {}
        self.vm_arguments = []
        self.non_default_vm_flags = {}

    def parse_output(self, output: str) -> JVMInfo:
        """Parse jinfo -v output text"""
        self._reset()
        lines = output.strip().split('\n')

        current_section = None

        for line in lines:
            line = line.strip()
            if not line:
                continue

            # Identify sections
            if line.startswith('Java System Properties:'):
                current_section = 'properties'
                continue
            if line.startswith('VM Flags:'):
                current_section = 'flags'
                continue
            if line.startswith('VM Arguments:'):
                current_section = 'arguments'
                continue
            if line.startswith('Non-default VM flags:'):
                current_section = 'non_default_flags'
                continue

            # Parse content based on current section
            if current_section == 'properties':
                if '=' in line:
                    key, value = line.split('=', 1)
                    self.system_properties[key.strip()] = value.strip()
            elif current_section == 'flags':
                self._parse_flag_line(line)
            elif current_section == 'arguments':
                if line and not line.startswith('jvm_args:'):
                    self.vm_arguments.append(line.strip())
            elif current_section == 'non_default_flags':
                self._parse_non_default_flag_line(line)

        return JVMInfo(
            system_properties=self.system_properties.copy(),
            vm_flags=self.vm_flags.copy(),
            vm_arguments=self.vm_arguments.copy(),
            non_default_vm_flags=self.non_default_vm_flags.copy()
        )

    def _reset(self):
        """Reset internal state for new parsing"""
        self.system_properties.clear()
        self.vm_flags.clear()
        self.vm_arguments.clear()
        self.non_default_vm_flags.clear()

    def _parse_flag_line(self, line: str):
        """Parse a VM flag line"""
        # VM flags can be in format: -XX:flag=value or -XX:+flag or -XX:-flag
        if line.startswith('-XX:'):
            if '=' in line:
                # Format: -XX:flag=value
                flag_part = line[4:]  # Remove -XX:
                key, value = flag_part.split('=', 1)
                self.vm_flags[key.strip()] = value.strip()
            elif line.startswith('-XX:+'):
                # Format: -XX:+flag (enabled boolean flag)
                flag = line[5:]  # Remove -XX:+
                self.vm_flags[flag.strip()] = 'true'
            elif line.startswith('-XX:-'):
                # Format: -XX:-flag (disabled boolean flag)
                flag = line[5:]  # Remove -XX:-
                self.vm_flags[flag.strip()] = 'false'
        elif line.startswith('-'):
            # Other JVM arguments like -Xms, -Xmx, etc.
            if '=' in line:
                key, value = line.split('=', 1)
                self.vm_flags[key.strip()] = value.strip()
            else:
                self.vm_flags[line.strip()] = 'true'

    def _parse_non_default_flag_line(self, line: str):
        """Parse non-default VM flags"""
        # Usually in format: flag=value or flag
        if '=' in line:
            key, value = line.split('=', 1)
            self.non_default_vm_flags[key.strip()] = value.strip()
        else:
            self.non_default_vm_flags[line.strip()] = 'true'

# JSON helpers

# For nested named tuples, you need a custom converter
def convert_namedtuples(obj):
    """Recursively convert named tuples to dictionaries."""
    if hasattr(obj, '_asdict'):
        return {k: convert_namedtuples(v) for k, v in obj._asdict().items()}
    if isinstance(obj, (list, tuple)):
        return [convert_namedtuples(item) for item in obj]
    return obj

def pretty_json(nt):
    """Convert named tuple (with potential nesting) to pretty JSON."""
    converted = convert_namedtuples(nt)
    return json.dumps(converted, indent=2, sort_keys=True)

# Misc helper methods

def find_jinfo_binary(java_executable_path):
    """
    Find the jinfo binary in the same directory as the Java executable.

    Args:
        java_executable_path (str): Path to the Java executable

    Returns:
        str: Path to jinfo binary or None if not found
    """
    java_dir = os.path.dirname(java_executable_path)
    jps_path = os.path.join(java_dir, 'jinfo')

    if os.path.exists(jps_path) and os.access(jps_path, os.X_OK):
        return jps_path

    return None

def run_jinfo(jinfo_path, pid):
    """
    Execute jinfo command to get Java process information.

    Args:
        jinfo_path (str): Path to jinfo binary
        pid (int): Process ID

    Returns:
        tuple: (success, output)
    """
    try:
        # Run jinfo with verbose flag to get more information
        result = subprocess.run([jinfo_path, '-v', str(pid)],
                              capture_output=True,
                              text=True,
                              timeout=10)

        if result.returncode == 0:
            return True, result.stdout

        return False, "{result.stderr}"

    except subprocess.TimeoutExpired:
        return False, "JPS execution timed out"
    except Exception as e:
        return False, f"Error running JPS: {e}"

def run_java_version(java_executable_path):
    """
    Execute java -version command to get basic Java information.

    Args:
        java_executable_path (str): Path to java executable

    Returns:
        tuple: (success, output)
    """
    try:
        result = subprocess.run([java_executable_path, '-version'],
                              capture_output=True,
                              text=True,
                              timeout=10)

        # java -version outputs to stderr by default
        output = result.stderr if result.stderr else result.stdout

        if result.returncode == 0 or output:
            return True, output

        return False, f"Java version command failed with return code {result.returncode}"

    except subprocess.TimeoutExpired:
        return False, "Java version command timed out"
    except Exception as e:
        return False, f"Error running java -version: {e}"


def jinfo_to_dict(jinfo_txt):
    """Processes output from jinfo into a dict"""
    parser = JInfoParser()
    jvm_info = parser.parse_output(jinfo_txt)

## "system_properties": jvm_info.system_properties, \
# "non_default_vm_flags": jvm_info.non_default_vm_flags, \

    # FIXME
    # vm_flags can be parsed to produce heap_max and heap_min
    # vm_arguments may contain java_class_path
    # vm_flags should be persisted?

    return {"method": "jinfo", \
            "vm_flags": jvm_info.vm_flags, "vm_arguments": jvm_info.vm_arguments, \
            "major_version": jvm_info.system_properties['java.specification.version'], \
            "vendor": jvm_info.system_properties['java.vm.vendor'], \
            "java_vm_name": jvm_info.system_properties['java.vm.name'], \
            "kernel_version": jvm_info.system_properties['os.version'], \
            "os_arch": jvm_info.system_properties['os.arch'], \
            "version_string": jvm_info.system_properties['java.runtime.version']}

def version_to_dict(output):
    """
    Parse java -version output and extract version information.

    Args:
        output: Raw output from 'java -version' command

    Returns:
        Dictionary containing parsed version information
    """
    info = {"method": "version", "raw": output}

    if not output:
        return info

    lines = output.strip().split('\n')

    # Parse first line for version number
    if lines:
        first_line = lines[0]

        # Extract version number (handles both old and new format)
        # Old format: java version "1.8.0_291"
        # New format: openjdk version "11.0.12"
        version_match = re.search(r'version "([^"]+)"', first_line)
        if version_match:
            full_version = version_match.group(1)
            info['full_version'] = full_version

            # Parse major version
            if full_version.startswith('1.'):
                # Old versioning scheme (Java 8 and below)
                major_match = re.search(r'1\.(\d+)', full_version)
                if major_match:
                    info['major_version'] = major_match.group(1)
            else:
                # New versioning scheme (Java 9+)
                major_match = re.search(r'^(\d+)', full_version)
                if major_match:
                    info['major_version'] = major_match.group(1)

            # Extract minor and patch versions
            version_parts = full_version.split('.')
            if len(version_parts) >= 2:
                if full_version.startswith('1.'):
                    # Old format: 1.8.0_291
                    if len(version_parts) >= 3:
                        patch_part = version_parts[2]
                        patch_match = re.search(r'^(\d+)', patch_part)
                        if patch_match:
                            info['patch_version'] = patch_match.group(1)

                        # Extract update number
                        update_match = re.search(r'_(\d+)', patch_part)
                        if update_match:
                            info['update_version'] = update_match.group(1)
                else:
                    # New format: 11.0.12
                    if len(version_parts) >= 2:
                        info['minor_version'] = version_parts[1]
                    if len(version_parts) >= 3:
                        info['patch_version'] = version_parts[2]

        # Extract implementation (java, openjdk, etc.)
        impl_match = re.search(r'^(\w+)', first_line)
        if impl_match:
            info['implementation'] = impl_match.group(1)

    # Parse runtime environment info (second line)
    if len(lines) >= 2:
        runtime_line = lines[1]

        # Extract runtime name
        runtime_match = re.search(r'^([^(]+)', runtime_line)
        if runtime_match:
            info['runtime_name'] = runtime_match.group(1).strip()

        # Extract build info
        build_match = re.search(r'\(build ([^)]+)\)', runtime_line)
        if build_match:
            info['build_info'] = build_match.group(1)

    # Parse VM info (third line)
    if len(lines) >= 3:
        vm_line = lines[2]

        # Extract VM name
        vm_match = re.search(r'^([^(]+)', vm_line)
        if vm_match:
            info['vm_name'] = vm_match.group(1).strip()

        # Extract VM build info
        vm_build_match = re.search(r'\(([^)]+)\)', vm_line)
        if vm_build_match:
            info['vm_build_info'] = vm_build_match.group(1)

        # Extract VM mode
        if 'mixed mode' in vm_line:
            info['vm_mode'] = 'mixed mode'
        elif 'interpreted mode' in vm_line:
            info['vm_mode'] = 'interpreted mode'
        elif 'compiled mode' in vm_line:
            info['vm_mode'] = 'compiled mode'

    return info

def get_extra_info(exe, pid):
    jinfo_path = find_jinfo_binary(exe)

    if jinfo_path:
        success, output = run_jinfo(jinfo_path, pid)

        if success:
            return jinfo_to_dict(output)

    success, output = run_java_version(exe)
    if success:
        return version_to_dict(output)
    return {}

def get_classpath(cmdline):
    """Retrieve classpath from list of Java args"""
    it_args = iter(cmdline)
    try:
        while True:
            item = next(it_args)
            if item in ['-classpath', '-cp']:
                return next(it_args)
    except StopIteration:
        pass
    return ""

def get_java_args(args):
    jboss_home = ""
    out = ""
    it_args = iter(args[1:-1])
    try:
        while True:
            item = next(it_args)
            if '-Xmx' in item or '-Xmx' in item:
                continue
            if item in ['-classpath', '-cp']:
                next(it_args)
            elif item.startswith('-D'):
                if not '=' in item:
                    continue
                (d_key, value, *foo) = item.split('=')
                # print(len(foo))
                # print(d_key)
                if d_key.startswith('-Djboss.home.dir'):
                    jboss_home = value
                else:
                    out += f' {d_key}=ZZZZZZZZZ'
            else:
                out += ' '+ item
    except StopIteration:
        pass

    if jboss_home:
        try:
            with open(f'{jboss_home}/version.txt', 'r') as f:
                jboss_version = f.read().strip()
        except (IOError, OSError):
            return out, ""
        return out, jboss_version
    return out, "Unknown"

def get_java_memory(cmdline):
    """Retrieve Java memory flags"""
    min_mem = None
    max_mem = None
    it_args = iter(cmdline)
    try:
        while True:
            item = next(it_args)
            if '-Xmx' in item:
                max_mem = "8192"
            elif '-Xms' in item:
                min_mem = "8192"
    except StopIteration:
        pass
    return (min_mem, max_mem)

def make_report(nt):
    """Convert Named Tuple to Report Dictionary"""
    d = {"java_class_path": get_classpath(nt.cmdline), "name": nt.exe, \
            "launch_time": nt.launch_time}
    # Read explicit Xmx and Xms (if any) from command line in case jinfo is unavailable
    (d['heap_min'], d['heap_max']) = get_java_memory(nt.cmdline)
    (d['jvm_args'], d['jboss_version']) = get_java_args(nt.cmdline)
    (d['rhel_version'], d['processors']) = (nt.rhel_version, nt.processors)
    d.update(get_extra_info(nt.exe, nt.pid))
    return d

# Main script
if __name__ == '__main__':
    proc = ProcUtil()

    processes = proc.get_processes()
    for p in processes:
        if p.exe is None:
            continue
        # Check if 'java' is in the process name or exec'd binary
        if 'java' in p.name.lower() or 'java' in p.exe.lower():
            report = make_report(p)
            print(pretty_json(report))
