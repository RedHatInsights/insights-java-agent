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
ProcessInfo = namedtuple('ProcessInfo', ['pid', 'name', 'status', 'ppid', 'cpu_percent', 'memory_rss', 'memory_vms'])
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
        pids = []
        for pid_dir in glob.glob('/proc/[0-9]*'):
            try:
                pid = int(os.path.basename(pid_dir))
                pids.append(pid)
            except ValueError:
                continue
        return sorted(pids)
    
    def pid_exists(self, pid):
        """Check if a process ID exists"""
        return os.path.isdir(f'/proc/{pid}')
    
    def get_process_info(self, pid):
        """Get detailed information about a process"""
        if not self.pid_exists(pid):
            return None
        
        # Read /proc/pid/stat
        stat_content = self._read_file(f'/proc/{pid}/stat')
        if not stat_content:
            return None
        
        stat_fields = stat_content.split()
        if len(stat_fields) < 24:
            return None
        
        # Extract relevant fields
        name = stat_fields[1].strip('()')
        status = stat_fields[2]
        ppid = 1 # int(stat_fields[3])
        
        # Memory information from /proc/pid/status
        status_content = self._read_file_lines(f'/proc/{pid}/status')
        memory_rss = 0
        memory_vms = 0
        
        for line in status_content:
            if line.startswith('VmRSS:'):
                memory_rss = int(line.split()[1]) * 1024  # Convert kB to bytes
            elif line.startswith('VmSize:'):
                memory_vms = int(line.split()[1]) * 1024  # Convert kB to bytes
        
        # Calculate CPU percentage (simplified)
        cpu_percent = self._get_process_cpu_percent(pid)
        
        return ProcessInfo(pid, name, status, ppid, cpu_percent, memory_rss, memory_vms)
    
    def _get_process_cpu_percent(self, pid):
        """Calculate CPU percentage for a process (simplified)"""
        stat_content = self._read_file(f'/proc/{pid}/stat')
        if not stat_content:
            return 0.0
        
        stat_fields = stat_content.split()
        if len(stat_fields) < 24:
            return 0.0
        
        try:
            utime = int(stat_fields[13])  # User time
            stime = int(stat_fields[14])  # System time
            total_time = utime + stime
            
            # Simple approach: compare with cached values
            current_time = time.time()
            
            if pid in self._process_cpu_cache:
                last_total, last_time = self._process_cpu_cache[pid]
                time_delta = current_time - last_time
                cpu_delta = total_time - last_total
                
                if time_delta > 0:
                    # CPU percentage approximation
                    cpu_percent = (cpu_delta / (time_delta * 100.0)) * 100.0
                    cpu_percent = min(cpu_percent, 100.0)  # Cap at 100%
                else:
                    cpu_percent = 0.0
            else:
                cpu_percent = 0.0
            
            self._process_cpu_cache[pid] = (total_time, current_time)
            return cpu_percent
            
        except (ValueError, IndexError):
            return 0.0
    
    def get_processes(self):
        """Get information about all processes"""
        processes = []
        for pid in self.get_pids():
            proc_info = self.get_process_info(pid)
            if proc_info:
                processes.append(proc_info)
        return processes
    
    def get_process_by_name(self, name):
        """Find processes by name"""
        matching = []
        for proc in self.get_processes():
            if name.lower() in proc.name.lower():
                matching.append(proc)
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
    
    print("=== System Information ===")
    mem = proc.get_memory_info()
    print(f"Memory: {mem.used // (1024**3):.1f}GB / {mem.total // (1024**3):.1f}GB ({(mem.used/mem.total)*100:.1f}%)")
    
    print(f"Uptime: {proc.get_uptime():.0f} seconds")
    
    print("\n=== Top 5 Processes by Memory ===")
    processes = proc.get_processes()
    top_memory = sorted(processes, key=lambda p: p.memory_rss, reverse=True)[:5]
    
    for p in top_memory:
        print(f"PID {p.pid}: {p.name} ({p.memory_rss // (1024**2):.1f}MB)")
