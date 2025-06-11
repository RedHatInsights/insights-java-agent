"""
insights_jvm.py - A zero-dependency script to scan RHEL systems for
JVM processes and create a JSON report for upload to Insights.
Parses /proc filesystem to gather system and process information
"""

import os
import time
import glob
from collections import namedtuple

# Named tuples for structured data
ProcessInfo = namedtuple('ProcessInfo', ['pid', 'name', 'status', 'memory_rss', 'memory_vms', 'cmdline', 'exe'])
MemoryInfo = namedtuple('MemoryInfo', ['total', 'available', 'used', 'free', 'cached', 'buffers'])

class ProcUtil:
    def __init__(self):
        self._last_cpu_times = None
        self._last_cpu_time = None
        self._process_cpu_cache = {}
    
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
    
    def get_memory_info(self):
        """Get system memory information"""
        meminfo = {}
        lines = self._read_file_lines('/proc/meminfo')
        
        for line in lines:
            if ':' in line:
                key, value = line.split(':', 1)
                # Extract numeric value (assuming kB)
                value = int(value.strip().split()[0]) * 1024  # Convert kB to bytes
                meminfo[key] = value
        
        total = meminfo.get('MemTotal', 0)
        free = meminfo.get('MemFree', 0)
        available = meminfo.get('MemAvailable', free)  # Fallback to free if available not present
        buffers = meminfo.get('Buffers', 0)
        cached = meminfo.get('Cached', 0)
        used = total - available
        
        return MemoryInfo(total, available, used, free, cached, buffers)
    
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

    def get_process_cwd(self, pid):
        """Get process current working directory"""
        try:
            return os.readlink(f'/proc/{pid}/cwd')
        except (OSError, IOError):
            return None

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


# Convenience functions for common operations
def virtual_memory():
    """Get virtual memory info"""
    return ProcUtil().get_memory_info()

def pids():
    """Get list of process IDs"""
    return ProcUtil().get_pids()

def process_exists(pid):
    """Check if process exists"""
    return ProcUtil().pid_exists(pid)


# Example usage
if __name__ == '__main__':
    proc = ProcUtil()
    
#    print("=== System Information ===")
    mem = proc.get_memory_info()
    print(f"Memory: {mem.used // (1024**3):.1f}GB / {mem.total // (1024**3):.1f}GB ({(mem.used/mem.total)*100:.1f}%)")
    
#    print(f"Uptime: {proc.get_uptime():.0f} seconds")
    
#    print("\n=== Top 5 Processes by Memory ===")
    processes = proc.get_processes()
#    top_memory = sorted(processes, key=lambda p: p.memory_rss, reverse=True)[:5]
    
    for p in processes:
        if p.cmdline is None:
            continue
        # ProcessInfo = namedtuple('ProcessInfo', ['pid', 'name', 'status', 'memory_rss', 'memory_vms', 'cmdline', 'exe'])
        # Check if 'java' is in the process name or command line
        if 'java' in p.name.lower() or \
                any('java' in str(arg).lower() for arg in p.cmdline):
            print(f"Java process detected: PID={p.pid}, Name={p.name}, Cmdline={p.cmdline}")
#        print(f"PID {p.pid}: {p.name} ({p.memory_rss // (1024**2):.1f}MB)")
