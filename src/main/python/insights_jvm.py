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
import glob
from collections import namedtuple

# Named tuples for structured data
ProcessInfo = namedtuple('ProcessInfo', ['pid', 'name', 'status', 'memory_rss', 'memory_vms', 'cmdline', 'exe'])

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

        # Memory information from /proc/pid/status
        status_content = self._read_file_lines(f'/proc/{p_id}/status')
        memory_rss = 0
        memory_vms = 0

        for line in status_content:
            if line.startswith('VmRSS:'):
                memory_rss = int(line.split()[1]) * 1024  # Convert kB to bytes
            elif line.startswith('VmSize:'):
                memory_vms = int(line.split()[1]) * 1024  # Convert kB to bytes

        # Get command line
        cmdline = self.get_process_cmdline(p_id)

        # Get executable path
        exe = self.get_process_exe(p_id)

        return ProcessInfo(p_id, name, status, memory_rss, memory_vms, cmdline, exe)

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

    def get_process_by_name(self, name):
        """Find processes by name"""
        matching = []
        for _p in self.get_processes():
            if name.lower() in _p.name.lower():
                matching.append(_p)
        return matching

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
    it_args = iter(cmdline)
    try:
        while True:
            item = next(it_args)
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
    d = {"java_class_path": get_classpath(nt.cmdline), "name": nt.name, \
            "jvm_args": get_java_args(nt.cmdline)}
    (d['heap_min'], d['heap_max']) = get_java_memory(nt.cmdline)
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
#                 any('java' in str(arg).lower() for arg in p.cmdline):
            report = make_report(p)
            print(pretty_json(p))
            print(pretty_json(report))
