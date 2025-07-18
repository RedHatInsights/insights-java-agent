"""
insights_jvm.py - A zero-dependency script to scan RHEL systems for
JVM processes and create a JSON report for upload to Insights.
Parses /proc filesystem to gather system and process information.
An alternative would be to look in /tmp/hsperfdata_<userid> for PIDs
that we have read access to. The disadvantages of this route include
needing to parse the hsperfdata format.
"""

import os
import re
import subprocess
import glob
import hashlib
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

    def get_process_info(self, p_id):
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
        # Field 22 is starttime (in clock ticks since boot)
        name = stat_fields[1].strip('()')
        launch_time = self.get_process_launch_time(stat_fields)

        cmdline = self.get_process_cmdline(p_id)
        exe = self.get_process_exe(p_id)

        return ProcessInfo(p_id, name, launch_time, cmdline, exe, self.processors, self.rhel_version)

    def get_process_launch_time(self, stat_fields):
        try:
            starttime_ticks = int(stat_fields[21])

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
                self._parse_vm_flags(line)
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

    def _parse_vm_flags(self, raw_line: str) -> None:
        flags = raw_line.split()
        for flag in flags:
            # VM flags can be in format: -XX:flag=value or -XX:+flag or -XX:-flag
            if flag.startswith('-XX:'):
                if '=' in flag:
                    # Format: -XX:flag=value
                    flag_part = flag[4:]  # Remove -XX:
                    key, value = flag_part.split('=', 1)
                    self.vm_flags[key.strip()] = value.strip()
                elif flag.startswith('-XX:+'):
                    # Format: -XX:+flag (enabled boolean flag)
                    flag = flag[5:]  # Remove -XX:+
                    self.vm_flags[flag.strip()] = 'true'
                elif flag.startswith('-XX:-'):
                    # Format: -XX:-flag (disabled boolean flag)
                    flag = flag[5:]  # Remove -XX:-
                    self.vm_flags[flag.strip()] = 'false'
            elif flag.startswith('-'):
                # Other JVM arguments like -Xms, -Xmx, etc.
                if '=' in flag:
                    key, value = flag.split('=', 1)
                    self.vm_flags[key.strip()] = value.strip()
                else:
                    self.vm_flags[flag.strip()] = 'true'

    def _parse_non_default_flag_line(self, line: str) -> None:
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

def _escape_json_string(s) -> str:
    """Escape special characters in JSON strings."""
    if not isinstance(s, str):
        return str(s)
    
    # Replace backslashes first to avoid double escaping
    s = s.replace('\\', '\\\\')
    s = s.replace('"', '\\"')
    s = s.replace('\n', '\\n')
    s = s.replace('\r', '\\r')
    s = s.replace('\t', '\\t')
    s = s.replace('\b', '\\b')
    s = s.replace('\f', '\\f')
    return s

def _serialize_json(obj, indent=0, sort_keys=True) -> str:
    """Custom JSON serializer without using json module."""
    indent_str = '  ' * indent
    next_indent_str = '  ' * (indent + 1)
    
    if obj is None:
        return 'null'
    elif isinstance(obj, bool):
        return 'true' if obj else 'false'
    elif isinstance(obj, (int, float)):
        return str(obj)
    elif isinstance(obj, str):
        return f'"{_escape_json_string(obj)}"'
    elif isinstance(obj, (list, tuple)):
        if not obj:
            return '[]'
        items = []
        for item in obj:
            serialized_item = _serialize_json(item, indent + 1, sort_keys)
            items.append(f'{next_indent_str}{serialized_item}')
        return '[\n' + ',\n'.join(items) + f'\n{indent_str}]'
    elif isinstance(obj, dict):
        if not obj:
            return '{}'
        items = []
        keys = sorted(obj.keys()) if sort_keys else obj.keys()
        for key in keys:
            serialized_key = _escape_json_string(str(key))
            serialized_value = _serialize_json(obj[key], indent + 1, sort_keys)
            items.append(f'{next_indent_str}"{serialized_key}": {serialized_value}')
        return '{\n' + ',\n'.join(items) + f'\n{indent_str}' + '}'
    else:
        # Fallback for other types
        return f'"{_escape_json_string(str(obj))}"'

def pretty_json(nt) -> str:
    converted = convert_namedtuples(nt)
    return _serialize_json(converted, indent=0, sort_keys=True)

# Misc helper methods

def find_jinfo_binary(java_executable_path: str) -> str:
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

    # vm_flags can be parsed to produce heap_max and heap_min
    # vm_arguments may contain java_class_path

    return {"method": "jinfo",
            "jvm.flags": jvm_info.vm_flags,
            # "jvm.arguments": jvm_info.vm_arguments,
            "java.major.version": jvm_info.system_properties['java.specification.version'],
            "vendor": jvm_info.system_properties['java.vm.vendor'],
            "java.vm.name": jvm_info.system_properties['java.vm.name'],
            "kernel.version": jvm_info.system_properties['os.version'],
            "system.arch": jvm_info.system_properties['os.arch'],
            "version.string": jvm_info.system_properties['java.runtime.version']}

def version_to_dict(output):
    # "raw": output
    info = {"method": "version"}

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
    """Retrieve general flags, sanitizing as we go"""
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
            with open(f'{jboss_home}/version.txt', 'r') as f_version:
                jboss_version = f_version.read().strip()
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
            # FIXME: This is a hack to get the memory flags
            item = next(it_args)
            if '-Xmx' in item:
                max_mem = item[4:]
            elif '-Xms' in item:
                min_mem = item[4:]
    except StopIteration:
        pass
    return (min_mem, max_mem)

def make_report(nt):
    """Convert Named Tuple to Report Dictionary"""
    d = {'java.class.path': get_classpath(nt.cmdline), 'name': nt.exe,
            'launch.time': nt.launch_time, 'rhel.version': nt.rhel_version,
         'processors': nt.processors }
    (d['jvm.heap.min'], d['jvm.heap.max']) = get_java_memory(nt.cmdline)
    (d['jvm.args'], d['jboss.version']) = get_java_args(nt.cmdline)
    d.update(get_extra_info(nt.exe, nt.pid))
    return d

# Main script
if __name__ == '__main__':
    proc = ProcUtil()

    hostname = os.uname()[1]
    processes = proc.get_processes()
    for p in processes:
        if p.exe is None:
            continue
        # Check if 'java' is in the process name or exec'd binary
        if 'java' in p.name.lower() or 'java' in p.exe.lower():
            report = {"version" : "1.0.2", "psdata": make_report(p)}
            report['psdata']['system.hostname'] = hostname
            # Compute SHA256 hash of the report contents
            json_output = pretty_json(report)
            content_hash = hashlib.sha256(json_output.encode('utf-8')).hexdigest()
            
            # Write report to file using SHA256 hash as filename
            output_dir = "/var/tmp/insights-runtimes/uploads"
            try:
                os.makedirs(output_dir, exist_ok=True)
                filename = f"{content_hash}_connect.json"
                filepath = os.path.join(output_dir, filename)
                
                with open(filepath, 'w') as f:
                    f.write(json_output)
                
            except (OSError, IOError) as e:
                print(f"Error writing report to file: {e}")