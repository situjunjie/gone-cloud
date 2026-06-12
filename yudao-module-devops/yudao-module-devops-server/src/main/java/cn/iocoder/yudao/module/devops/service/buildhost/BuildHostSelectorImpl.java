package cn.iocoder.yudao.module.devops.service.buildhost;

import cn.iocoder.yudao.module.devops.dal.dataobject.buildhost.BuildHostDO;
import cn.iocoder.yudao.module.devops.dal.mysql.buildhost.BuildHostMapper;
import cn.iocoder.yudao.module.devops.enums.BuildHostTypeEnum;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.BUILD_HOST_NO_AVAILABLE;

/**
 * 构建主机选择器默认实现。
 *
 * <p>v1 调度策略：在启用主机中按条件过滤后，优先返回 SSH 主机（主路径），其次返回第一台可用主机。
 * label / 容量维度已在接口预留并在此做基础过滤；远程多机的容量计数调度后置。
 */
@Service
public class BuildHostSelectorImpl implements BuildHostSelector {

    @Resource
    private BuildHostMapper buildHostMapper;

    @Override
    public BuildHostDO select(BuildHostSelectCriteria criteria) {
        BuildHostSelectCriteria effective = criteria != null ? criteria : BuildHostSelectCriteria.none();

        List<BuildHostDO> candidates = buildHostMapper.selectEnabledList();
        if (CollectionUtils.isEmpty(candidates)) {
            throw exception(BUILD_HOST_NO_AVAILABLE);
        }

        // 按类型过滤
        if (StringUtils.hasText(effective.getPreferredType())) {
            candidates = candidates.stream()
                    .filter(host -> effective.getPreferredType().equals(host.getType()))
                    .collect(Collectors.toList());
        }
        // 按标签过滤：要求主机标签包含全部所需标签
        if (!CollectionUtils.isEmpty(effective.getRequiredLabels())) {
            candidates = candidates.stream()
                    .filter(host -> parseLabels(host.getLabels()).containsAll(effective.getRequiredLabels()))
                    .collect(Collectors.toList());
        }
        if (CollectionUtils.isEmpty(candidates)) {
            throw exception(BUILD_HOST_NO_AVAILABLE);
        }

        // v1：优先 SSH 主路径，否则取第一台
        return candidates.stream()
                .filter(host -> BuildHostTypeEnum.isSsh(host.getType()))
                .findFirst()
                .orElse(candidates.get(0));
    }

    private Set<String> parseLabels(String labels) {
        if (!StringUtils.hasText(labels)) {
            return Set.of();
        }
        return Arrays.stream(labels.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

}
