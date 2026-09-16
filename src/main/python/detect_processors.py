#!/usr/bin/env python3
"""
Linux Processor Detection Script

Detects the number of processors available on Linux systems.
Works on both bare metal and containerized environments.
Uses only Python standard library (no external dependencies).
"""

import os
import sys


def read_file_safely(filepath):
    """Safely read a file and return its content, or None if it fails."""
    try:
        with open(filepath, 'r') as f:
            return f.read().strip()
    except (IOError, OSError):
        return None


def get_cpus_from_proc_cpuinfo():
    """Get CPU count from /proc/cpuinfo."""
    #! Change justification: Fix Edge Case 2 (exact whole-word key match for processor field in /proc/cpuinfo).
    #! Test fails if /proc/cpuinfo contains lines with 'processor' prefix like 'processor version' or non-core attributes.
    try:
        with open('/proc/cpuinfo', 'r') as f:
            count = 0
            for line in f:
                if ':' in line:
                    key = line.split(':', 1)[0].strip()
                    if key == 'processor':
                        count += 1
            return count if count > 0 else None
    except (IOError, OSError):
        return None


def get_cpus_from_sys_devices():
    """Get CPU count from /sys/devices/system/cpu/."""
    try:
        cpu_dirs = []
        for item in os.listdir('/sys/devices/system/cpu/'):
            if item.startswith('cpu') and item[3:].isdigit():
                cpu_dirs.append(item)
        return len(cpu_dirs)
    except (IOError, OSError):
        return None


def get_cgroup_cpu_quota():
    """
    Get CPU quota from cgroup (for containers).
    Returns the effective CPU limit based on cgroup settings.
    Supports fractional CPU quotas (e.g. 1.5, 0.5).
    """
    #! Change justification: Allow fractional CPU quota values (float / int) rather than integer division truncation.
    #! Test fails if fractional quotas like 150000/100000 (1.5) or 50000/100000 (0.5) are truncated.
    # Try cgroup v2 first
    quota_file = '/sys/fs/cgroup/cpu.max'
    content = read_file_safely(quota_file)
    if content:
        parts = content.split()
        if len(parts) >= 2 and parts[0] != 'max':
            try:
                quota = int(parts[0])
                period = int(parts[1])
                if quota > 0 and period > 0:
                    val = quota / period
                    return int(val) if val.is_integer() else val
            except ValueError:
                pass
    
    # Try cgroup v1
    quota_file = '/sys/fs/cgroup/cpu/cpu.cfs_quota_us'
    period_file = '/sys/fs/cgroup/cpu/cpu.cfs_period_us'
    
    quota_content = read_file_safely(quota_file)
    period_content = read_file_safely(period_file)
    
    if quota_content and period_content:
        try:
            quota = int(quota_content)
            period = int(period_content)
            if quota > 0 and period > 0:
                val = quota / period
                return int(val) if val.is_integer() else val
        except ValueError:
            pass
    
    return None


def get_cgroup_cpuset():
    """
    Get CPU set from cgroup (for containers).
    Returns the number of CPUs in the cpuset.
    """
    # Try cgroup v2 first
    cpuset_file = '/sys/fs/cgroup/cpuset.cpus.effective'
    content = read_file_safely(cpuset_file)
    if not content:
        # Try cgroup v1
        cpuset_file = '/sys/fs/cgroup/cpuset/cpuset.cpus'
        content = read_file_safely(cpuset_file)
    
    if content:
        try:
            # Parse CPU set format like "0-3" or "0,2,4-7"
            cpu_count = 0
            for part in content.split(','):
                part = part.strip()
                if '-' in part:
                    start, end = map(int, part.split('-'))
                    cpu_count += end - start + 1
                else:
                    cpu_count += 1
            return cpu_count
        except ValueError:
            pass
    
    return None


def is_containerized():
    """
    Detect if we're running in a container.
    """
    #! Change justification: Fix Edge Case 3 (comprehensive container detection for Kubernetes, containerd, cgroup v2, etc.).
    #! Test fails if container environments lacking /.dockerenv or /run/.containerenv (e.g. k8s CRI-O/containerd) are not detected.
    # Check for container-specific files
    container_indicators = [
        '/.dockerenv',
        '/run/.containerenv',  # Podman
    ]
    
    for indicator in container_indicators:
        if os.path.exists(indicator):
            return True
    
    # Check /proc/1/cgroup (cgroup v1 / early cgroup v2)
    cgroup_content = read_file_safely('/proc/1/cgroup')
    if cgroup_content:
        container_runtimes = ['docker', 'containerd', 'lxc', 'systemd/docker', 'kubepods', 'libpod']
        for runtime in container_runtimes:
            if runtime in cgroup_content.lower():
                return True

    # Check /proc/self/cgroup for Kubernetes / containerd slices
    self_cgroup_content = read_file_safely('/proc/self/cgroup')
    if self_cgroup_content:
        container_runtimes = ['docker', 'containerd', 'lxc', 'systemd/docker', 'kubepods', 'libpod']
        for runtime in container_runtimes:
            if runtime in self_cgroup_content.lower():
                return True

    # Check /proc/1/environ for container runtime marker
    environ_content = read_file_safely('/proc/1/environ')
    if environ_content:
        if 'container=' in environ_content or 'KUBERNETES_SERVICE_HOST' in environ_content:
            return True

    return False


def detect_processors():
    """
    Main function to detect the number of processors.
    Returns a tuple: (cpu_count, method_used, is_container)
    """
    container = is_containerized()
    
    # Method 1: Try os.cpu_count() (most reliable for bare metal)
    os_cpu_count = os.cpu_count()
    
    # Method 2: Try cgroup limits (important for containers)
    cgroup_quota = get_cgroup_cpu_quota()
    cgroup_cpuset = get_cgroup_cpuset()
    
    # Method 3: Try /proc/cpuinfo
    proc_cpuinfo_count = get_cpus_from_proc_cpuinfo()
    
    # Method 4: Try /sys/devices/system/cpu/
    sys_devices_count = get_cpus_from_sys_devices()
    
    # Decision logic
    if container:
        # In container, prefer cgroup limits over system CPU count
        if cgroup_quota is not None:
            return cgroup_quota, 'cgroup CPU quota', True
        elif cgroup_cpuset is not None:
            return cgroup_cpuset, 'cgroup CPU set', True
        elif os_cpu_count is not None:
            return os_cpu_count, 'os.cpu_count() (container)', True
    else:
        # On bare metal, prefer os.cpu_count()
        if os_cpu_count is not None:
            return os_cpu_count, 'os.cpu_count()', False
    
    # Fallback methods
    if proc_cpuinfo_count is not None:
        return proc_cpuinfo_count, '/proc/cpuinfo', container
    elif sys_devices_count is not None:
        return sys_devices_count, '/sys/devices/system/cpu/', container
    else:
        return 1, 'fallback (could not detect)', container


def main():
    """Main entry point."""
    if len(sys.argv) > 1 and sys.argv[1] in ['-h', '--help']:
        print(__doc__)
        print("\nUsage: python3 detect_processors.py")
        print("       python3 detect_processors.py --verbose")
        return
    
    verbose = len(sys.argv) > 1 and sys.argv[1] in ['-v', '--verbose']
    
    try:
        cpu_count, method, is_container = detect_processors()
        
        if verbose:
            print(f"Environment: {'Container' if is_container else 'Bare Metal'}")
            print(f"Detection method: {method}")
            print(f"Number of processors: {cpu_count}")
            
            # Show additional information in verbose mode
            print("\nDetailed information:")
            print(f"  os.cpu_count(): {os.cpu_count()}")
            print(f"  /proc/cpuinfo: {get_cpus_from_proc_cpuinfo()}")
            print(f"  /sys/devices/system/cpu/: {get_cpus_from_sys_devices()}")
            print(f"  cgroup CPU quota: {get_cgroup_cpu_quota()}")
            print(f"  cgroup CPU set: {get_cgroup_cpuset()}")
        else:
            print(cpu_count)
            
    except Exception as e:
        print(f"Error detecting processors: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == '__main__':
    main() 