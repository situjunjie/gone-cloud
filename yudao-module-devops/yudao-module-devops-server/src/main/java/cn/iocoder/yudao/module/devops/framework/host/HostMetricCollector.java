package cn.iocoder.yudao.module.devops.framework.host;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostCpuRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostDetailRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostDiskRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostLoadRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostMemoryRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostProcessRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostSystemRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.host.EnvironmentHostDO;
import com.jcraft.jsch.Session;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Linux 主机指标采集器。
 */
@Component
public class HostMetricCollector {

    private static final int COMMAND_TIMEOUT_MILLIS = 8_000;
    private static final int CPU_SAMPLE_INTERVAL_MILLIS = 400;

    @Resource
    private HostSshClient hostSshClient;
    @Resource
    private HostCommandExecutor hostCommandExecutor;

    public EnvironmentHostDetailRespVO collect(EnvironmentHostDO host, int processLimit) throws Exception {
        Session session = hostSshClient.openSession(host);
        try {
            EnvironmentHostDetailRespVO respVO = new EnvironmentHostDetailRespVO();
            respVO.setConnected(true);
            respVO.setSystem(collectSystem(session));
            respVO.setCpu(collectCpu(session));
            respVO.setLoad(parseLoad(execute(session, "cat /proc/loadavg")));
            respVO.setMemory(parseMemory(execute(session, "cat /proc/meminfo")));
            respVO.setDisks(parseDisks(execute(session, "df -P")));
            respVO.setProcesses(collectProcesses(session, processLimit));
            respVO.setCollectedAt(LocalDateTime.now());
            return respVO;
        } finally {
            session.disconnect();
        }
    }

    public List<EnvironmentHostProcessRespVO> collectProcesses(EnvironmentHostDO host, int limit) throws Exception {
        Session session = hostSshClient.openSession(host);
        try {
            return collectProcesses(session, limit);
        } finally {
            session.disconnect();
        }
    }

    EnvironmentHostSystemRespVO parseSystem(String hostname, String kernel, String osRelease) {
        EnvironmentHostSystemRespVO respVO = new EnvironmentHostSystemRespVO();
        respVO.setHostname(StrUtil.trim(hostname));
        respVO.setKernel(StrUtil.trim(kernel));
        Map<String, String> values = parseOsRelease(osRelease);
        respVO.setOsName(firstNotBlank(values.get("PRETTY_NAME"), values.get("NAME")));
        respVO.setOsVersion(firstNotBlank(values.get("VERSION"), values.get("VERSION_ID")));
        return respVO;
    }

    EnvironmentHostLoadRespVO parseLoad(String text) {
        String[] parts = splitWhitespace(text);
        EnvironmentHostLoadRespVO respVO = new EnvironmentHostLoadRespVO();
        if (parts.length >= 3) {
            respVO.setLoad1(parseDouble(parts[0]));
            respVO.setLoad5(parseDouble(parts[1]));
            respVO.setLoad15(parseDouble(parts[2]));
        }
        if (parts.length >= 4) {
            respVO.setRunningProcessSummary(parts[3]);
        }
        return respVO;
    }

    EnvironmentHostMemoryRespVO parseMemory(String text) {
        Map<String, Long> values = new LinkedHashMap<>();
        for (String line : lines(text)) {
            String[] parts = splitWhitespace(line.replace(":", ""));
            if (parts.length >= 2) {
                values.put(parts[0], parseLong(parts[1]));
            }
        }
        Long total = values.get("MemTotal");
        Long available = values.get("MemAvailable");
        EnvironmentHostMemoryRespVO respVO = new EnvironmentHostMemoryRespVO();
        respVO.setTotalKb(total);
        respVO.setAvailableKb(available);
        if (total != null && available != null) {
            long used = Math.max(0L, total - available);
            respVO.setUsedKb(used);
            respVO.setUsagePercent(percent(used, total));
        }
        return respVO;
    }

    List<EnvironmentHostDiskRespVO> parseDisks(String text) {
        List<EnvironmentHostDiskRespVO> disks = new ArrayList<>();
        String[] lines = lines(text);
        for (int i = 1; i < lines.length; i++) {
            String[] parts = splitWhitespace(lines[i]);
            if (parts.length < 6) {
                continue;
            }
            EnvironmentHostDiskRespVO disk = new EnvironmentHostDiskRespVO();
            disk.setFilesystem(parts[0]);
            disk.setTotalKb(parseLong(parts[1]));
            disk.setUsedKb(parseLong(parts[2]));
            disk.setAvailableKb(parseLong(parts[3]));
            disk.setUsagePercent(parsePercent(parts[4]));
            disk.setMountPoint(parts[5]);
            disks.add(disk);
        }
        return disks;
    }

    List<EnvironmentHostProcessRespVO> parseProcesses(String text, int limit) {
        List<EnvironmentHostProcessRespVO> processes = new ArrayList<>();
        String[] lines = lines(text);
        for (int i = 1; i < lines.length && processes.size() < limit; i++) {
            String[] parts = splitWhitespace(lines[i], 9);
            if (parts.length < 8) {
                continue;
            }
            EnvironmentHostProcessRespVO process = new EnvironmentHostProcessRespVO();
            process.setPid(parseLong(parts[0]));
            process.setPpid(parseLong(parts[1]));
            process.setUser(parts[2]);
            process.setStat(parts[3]);
            process.setCpuPercent(parseDouble(parts[4]));
            process.setMemoryPercent(parseDouble(parts[5]));
            process.setElapsed(parts[6]);
            process.setCommand(parts[7]);
            process.setArgs(parts.length >= 9 ? parts[8] : null);
            processes.add(process);
        }
        return processes;
    }

    EnvironmentHostCpuRespVO parseCpu(String firstStat, String secondStat) {
        long[] first = parseCpuStat(firstStat);
        long[] second = parseCpuStat(secondStat);
        long totalDelta = second[0] - first[0];
        long idleDelta = second[1] - first[1];
        EnvironmentHostCpuRespVO respVO = new EnvironmentHostCpuRespVO();
        if (totalDelta > 0) {
            respVO.setUsagePercent(round2((totalDelta - idleDelta) * 100D / totalDelta));
        }
        return respVO;
    }

    private EnvironmentHostSystemRespVO collectSystem(Session session) throws Exception {
        String hostname = execute(session, "hostname");
        String kernel = execute(session, "uname -srm");
        String osRelease = execute(session, "cat /etc/os-release 2>/dev/null || true");
        return parseSystem(hostname, kernel, osRelease);
    }

    private EnvironmentHostCpuRespVO collectCpu(Session session) throws Exception {
        String first = execute(session, "grep '^cpu ' /proc/stat");
        Thread.sleep(CPU_SAMPLE_INTERVAL_MILLIS);
        String second = execute(session, "grep '^cpu ' /proc/stat");
        return parseCpu(first, second);
    }

    private List<EnvironmentHostProcessRespVO> collectProcesses(Session session, int limit) throws Exception {
        int safeLimit = normalizeLimit(limit);
        String command = "ps -eo pid,ppid,user,stat,pcpu,pmem,etime,comm,args --sort=-pcpu | head -n "
                + (safeLimit + 1);
        return parseProcesses(execute(session, command), safeLimit);
    }

    private String execute(Session session, String command) throws Exception {
        return hostCommandExecutor.execute(session, command, COMMAND_TIMEOUT_MILLIS);
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 20;
        }
        return Math.min(limit, 100);
    }

    private Map<String, String> parseOsRelease(String text) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : lines(text)) {
            int index = line.indexOf('=');
            if (index <= 0) {
                continue;
            }
            String key = line.substring(0, index);
            String value = line.substring(index + 1);
            values.put(key, trimQuotes(value));
        }
        return values;
    }

    private String trimQuotes(String value) {
        String trimmed = StrUtil.trim(value);
        if (trimmed != null && trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private long[] parseCpuStat(String text) {
        String[] parts = splitWhitespace(text);
        long user = valueAt(parts, 1);
        long nice = valueAt(parts, 2);
        long system = valueAt(parts, 3);
        long idle = valueAt(parts, 4);
        long iowait = valueAt(parts, 5);
        long irq = valueAt(parts, 6);
        long softirq = valueAt(parts, 7);
        long steal = valueAt(parts, 8);
        long total = user + nice + system + idle + iowait + irq + softirq + steal;
        return new long[]{total, idle + iowait};
    }

    private long valueAt(String[] parts, int index) {
        Long value = parts.length > index ? parseLong(parts[index]) : null;
        return value == null ? 0L : value;
    }

    private String firstNotBlank(String first, String second) {
        return StrUtil.isNotBlank(first) ? first : second;
    }

    private String[] lines(String text) {
        if (StrUtil.isBlank(text)) {
            return new String[0];
        }
        return text.strip().split("\\R");
    }

    private String[] splitWhitespace(String text) {
        return splitWhitespace(text, 0);
    }

    private String[] splitWhitespace(String text, int limit) {
        String trimmed = StrUtil.trim(text);
        if (StrUtil.isBlank(trimmed)) {
            return new String[0];
        }
        return limit > 0 ? trimmed.split("\\s+", limit) : trimmed.split("\\s+");
    }

    private Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Double parseDouble(String value) {
        try {
            return Double.valueOf(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Double parsePercent(String value) {
        return parseDouble(StrUtil.removeSuffix(value, "%"));
    }

    private Double percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return null;
        }
        return round2(numerator * 100D / denominator);
    }

    private Double round2(double value) {
        return Math.round(value * 100D) / 100D;
    }

}
