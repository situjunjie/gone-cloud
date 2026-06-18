package cn.iocoder.yudao.module.devops.framework.host;

import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostCpuRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostDiskRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostLoadRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostMemoryRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostProcessRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostSystemRespVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link HostMetricCollector} 的单元测试。
 */
public class HostMetricCollectorTest {

    private final HostMetricCollector collector = new HostMetricCollector();

    @Test
    public void testParseSystem() {
        // 调用
        EnvironmentHostSystemRespVO system = collector.parseSystem("app-01\n", "Linux 6.1 x86_64\n",
                "NAME=\"Ubuntu\"\nVERSION=\"22.04.4 LTS\"\nPRETTY_NAME=\"Ubuntu 22.04.4 LTS\"\n");

        // 断言
        assertEquals("app-01", system.getHostname());
        assertEquals("Linux 6.1 x86_64", system.getKernel());
        assertEquals("Ubuntu 22.04.4 LTS", system.getOsName());
        assertEquals("22.04.4 LTS", system.getOsVersion());
    }

    @Test
    public void testParseCpu() {
        // 调用
        EnvironmentHostCpuRespVO cpu = collector.parseCpu(
                "cpu  100 0 50 850 0 0 0 0 0 0",
                "cpu  130 0 70 900 0 0 0 0 0 0");

        // 断言
        assertEquals(50D, cpu.getUsagePercent());
    }

    @Test
    public void testParseLoad() {
        // 调用
        EnvironmentHostLoadRespVO load = collector.parseLoad("0.15 0.10 0.08 1/128 12345");

        // 断言
        assertEquals(0.15D, load.getLoad1());
        assertEquals(0.10D, load.getLoad5());
        assertEquals(0.08D, load.getLoad15());
        assertEquals("1/128", load.getRunningProcessSummary());
    }

    @Test
    public void testParseMemory() {
        // 调用
        EnvironmentHostMemoryRespVO memory = collector.parseMemory("""
                MemTotal:        1000 kB
                MemAvailable:     400 kB
                """);

        // 断言
        assertEquals(1000L, memory.getTotalKb());
        assertEquals(400L, memory.getAvailableKb());
        assertEquals(600L, memory.getUsedKb());
        assertEquals(60D, memory.getUsagePercent());
    }

    @Test
    public void testParseDisks() {
        // 调用
        List<EnvironmentHostDiskRespVO> disks = collector.parseDisks("""
                Filesystem 1024-blocks Used Available Capacity Mounted on
                /dev/vda1 52403200 20480000 31923200 39% /
                tmpfs 1024 0 1024 0% /run
                """);

        // 断言
        assertEquals(2, disks.size());
        assertEquals("/dev/vda1", disks.get(0).getFilesystem());
        assertEquals(52403200L, disks.get(0).getTotalKb());
        assertEquals(39D, disks.get(0).getUsagePercent());
        assertEquals("/", disks.get(0).getMountPoint());
    }

    @Test
    public void testParseProcesses() {
        // 调用
        List<EnvironmentHostProcessRespVO> processes = collector.parseProcesses("""
                PID PPID USER STAT %CPU %MEM ELAPSED COMMAND COMMAND
                1234 1 root Sl 12.3 4.5 01:23:45 java java -jar app.jar
                2222 1 nginx S 1.1 0.8 00:10:00 nginx nginx: worker process
                """, 1);

        // 断言
        assertEquals(1, processes.size());
        EnvironmentHostProcessRespVO process = processes.get(0);
        assertEquals(1234L, process.getPid());
        assertEquals(1L, process.getPpid());
        assertEquals("root", process.getUser());
        assertEquals(12.3D, process.getCpuPercent());
        assertEquals("java", process.getCommand());
        assertEquals("java -jar app.jar", process.getArgs());
    }

}
