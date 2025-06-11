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
import os
import time
import subprocess
import glob
from collections import namedtuple
from datetime import datetime

# Named tuples for structured data
ProcessInfo = namedtuple('ProcessInfo', ['pid', 'name', 'status', 'launch_time', 'cmdline', 'exe'])

class ProcUtil:
    """Simple class to replace psutil functionality """
    def _read_file(self, filepath):
        """Safely read a file and return its contents"""
        try:
            with open(filepath, 'r') as f:
                return f.read().strip()
        except (IOError, OSError):
            return None

    def _read_file_lines(self, filepath):
        """Safely read a file and return lines as a list"""
        content = self._read_file(filepath)
        return content.split('\n') if content else []

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
        name = stat_fields[1].strip('()')
        status = stat_fields[2]

        cmdline = self.get_process_cmdline(p_id)
        exe = self.get_process_exe(p_id)
        launch_time = self.get_process_launch_time(p_id)

        return ProcessInfo(p_id, name, status, launch_time, cmdline, exe)

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

    def get_uptime(self):
        """Get system uptime in seconds"""
        uptime_content = self._read_file('/proc/uptime')
        if uptime_content:
            return float(uptime_content.split()[0])
        return 0.0

    def get_boot_time(self):
        """Get system boot time as timestamp"""
        stat_content = self._read_file_lines('/proc/stat')
        for line in stat_content:
            if line.startswith('btime'):
                return int(line.split()[1])
        return 0

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
            # Filter output for the specific PID
            lines = result.stdout.strip().split('\n')
            for line in lines:
                if line.startswith(str(pid)):
                    return True, f"Jinfo Output for PID {pid}:\n{line}"

            # If PID not found in jps output, return all output
            return True, f"Jinfo Output (PID {pid} not found in current Java processes):\n{result.stdout}"

        return False, f"JPS execution failed: {result.stderr}"

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
            return True, f"Java Version Information:\n{output}"

        return False, f"Java version command failed with return code {result.returncode}"

    except subprocess.TimeoutExpired:
        return False, "Java version command timed out"
    except Exception as e:
        return False, f"Error running java -version: {e}"

def get_extra_info(exe, pid):
    jinfo_path = find_jinfo_binary(exe)

    if jinfo_path:
        print(f"Found jinfo binary at: {jinfo_path}")
        success, output = run_jinfo(jinfo_path, pid)

        if success:
            return output
        else:
            print(f"JPS failed: {output}")
            print("\nFalling back to java -version...")
            success, output = run_java_version(exe)
            if success:
                return output
            else:
                print(f"Java version also failed: {output}")
    else:
        print("jps binary not found in the same directory.")
        print("Executing java -version instead...")

        success, output = run_java_version(exe)
        if success:
            return output
#         else:
#             print(f"Java version failed: {output}")

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

def get_java_args(cmdline):
    """Retrieve Java args"""
    out = ""
    it_args = iter(cmdline[1:-1])
    try:
        while True:
            item = next(it_args)
            if '-Xmx' in item or '-Xmx' in item:
                continue
            if item in ['-classpath', '-cp']:
                next(it_args)
            elif '-D' in item:
                out += " -D=ZZZZZZZZZ"
            else:
                out += ' '+ item
    except StopIteration:
        pass
    return out

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
            "jvm_args": get_java_args(nt.cmdline), "launch_time": nt.launch_time}
    (d['heap_min'], d['heap_max']) = get_java_memory(nt.cmdline)
    d['extra_info'] = get_extra_info(nt.exe, nt.pid)
    return d

# Main script
#
if __name__ == '__main__':
    proc = ProcUtil()

    processes = proc.get_processes()
    for p in processes:
        if p.exe is None:
            continue
        # Check if 'java' is in the process name or exec'd binary
        if 'java' in p.name.lower() or 'java' in p.exe.lower():
            report = make_report(p)
            print(pretty_json(p))
            print(pretty_json(report))
