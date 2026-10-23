package cn.iocoder.yudao.module.devops.service.application;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationMemberAddReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationMemberRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationMemberTransferOwnerReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationMemberDO;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMemberMapper;
import cn.iocoder.yudao.module.devops.enums.ApplicationMemberTypeEnum;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.APPLICATION_MEMBER_NOT_EXISTS;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.APPLICATION_MEMBER_OWNER_CANNOT_REMOVE;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.APPLICATION_MEMBER_OWNER_ONLY;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.APPLICATION_MEMBER_TRANSFER_SELF;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.APPLICATION_MEMBER_TRANSFER_TARGET_NOT_MEMBER;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.APPLICATION_NOT_EXISTS;

/**
 * DevOps 应用成员 Service 实现类。
 */
@Slf4j
@Service
@Validated
public class ApplicationMemberServiceImpl implements ApplicationMemberService {

    @Resource
    private ApplicationMapper applicationMapper;
    @Resource
    private ApplicationMemberMapper applicationMemberMapper;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public List<ApplicationMemberRespVO> getApplicationMemberList(Long appId, Long operatorUserId) {
        ApplicationDO application = validateOwner(appId, operatorUserId);
        Long ownerUserId = application.getOwnerUserId();
        List<ApplicationMemberRespVO> participants = new ArrayList<>();
        ApplicationMemberRespVO ownerMember = null;
        // Mapper 已按 createTime 升序返回，参与者天然有序；拥有者单独抽出放到首位
        for (ApplicationMemberDO member : applicationMemberMapper.selectListByAppId(appId)) {
            ApplicationMemberRespVO respVO = convert(member, ownerUserId);
            if (ApplicationMemberTypeEnum.OWNER.getType().equals(respVO.getMemberType())) {
                ownerMember = respVO;
            } else {
                participants.add(respVO);
            }
        }
        // 拥有者未物化进成员表时，实时合成一行，保证列表始终包含拥有者
        if (ownerMember == null) {
            ownerMember = new ApplicationMemberRespVO();
            ownerMember.setId(null);
            ownerMember.setAppId(appId);
            ownerMember.setUserId(ownerUserId);
            ownerMember.setMemberType(ApplicationMemberTypeEnum.OWNER.getType());
            ownerMember.setCreateTime(application.getCreateTime());
        }
        List<ApplicationMemberRespVO> result = new ArrayList<>(participants.size() + 1);
        result.add(ownerMember);
        result.addAll(participants);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addApplicationMembers(ApplicationMemberAddReqVO reqVO, Long operatorUserId) {
        ApplicationDO application = validateOwner(reqVO.getAppId(), operatorUserId);
        if (CollUtil.isEmpty(reqVO.getUserIds())) {
            return;
        }
        // 拥有者不能作为参与者；重复用户去重
        Set<Long> userIds = reqVO.getUserIds().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        userIds.remove(application.getOwnerUserId());
        if (CollUtil.isEmpty(userIds)) {
            return;
        }
        adminUserApi.validateUserList(userIds).checkError();

        Map<Long, ApplicationMemberDO> existingMap = applicationMemberMapper
                .selectListByTenantIdAndAppIdAndUserIdsIncludingDeleted(application.getTenantId(),
                        application.getId(), userIds)
                .stream().collect(Collectors.toMap(ApplicationMemberDO::getUserId, Function.identity()));
        List<ApplicationMemberDO> insertMembers = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Long userId : userIds) {
            ApplicationMemberDO existing = existingMap.get(userId);
            if (existing == null) {
                ApplicationMemberDO member = new ApplicationMemberDO();
                member.setAppId(application.getId());
                member.setUserId(userId);
                insertMembers.add(member);
            } else if (Boolean.TRUE.equals(existing.getDeleted())) {
                // 逻辑删除的历史成员行恢复，避免唯一键冲突
                applicationMemberMapper.restoreById(existing.getId(), application.getTenantId(), now);
            }
            // 已是有效成员：幂等跳过
        }
        if (CollUtil.isNotEmpty(insertMembers)) {
            applicationMemberMapper.insertBatch(insertMembers);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeApplicationMember(Long appId, Long userId, Long operatorUserId) {
        ApplicationDO application = validateOwner(appId, operatorUserId);
        if (Objects.equals(application.getOwnerUserId(), userId)) {
            throw exception(APPLICATION_MEMBER_OWNER_CANNOT_REMOVE);
        }
        ApplicationMemberDO member = applicationMemberMapper.selectByAppIdAndUserId(appId, userId);
        if (member == null) {
            throw exception(APPLICATION_MEMBER_NOT_EXISTS);
        }
        applicationMemberMapper.deleteById(member.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferApplicationOwner(ApplicationMemberTransferOwnerReqVO reqVO, Long operatorUserId) {
        ApplicationDO application = validateOwner(reqVO.getAppId(), operatorUserId);
        Long oldOwnerUserId = application.getOwnerUserId();
        Long newOwnerUserId = reqVO.getUserId();
        if (Objects.equals(oldOwnerUserId, newOwnerUserId)) {
            throw exception(APPLICATION_MEMBER_TRANSFER_SELF);
        }
        // 目标必须是已有成员
        if (applicationMemberMapper.selectByAppIdAndUserId(reqVO.getAppId(), newOwnerUserId) == null) {
            throw exception(APPLICATION_MEMBER_TRANSFER_TARGET_NOT_MEMBER);
        }
        // 原拥有者需要保留为参与者：不存在成员行时补齐（含恢复历史删除行）
        ensureMemberRow(application, oldOwnerUserId);
        // 目标成员行保持不变，转移后由 owner_user_id 派生出 OWNER，行标识与加入时间不丢
        ApplicationDO updateObj = new ApplicationDO();
        updateObj.setId(application.getId());
        updateObj.setOwnerUserId(newOwnerUserId);
        applicationMapper.updateById(updateObj);
    }

    /**
     * 校验应用存在，且请求者就是应用拥有者。
     */
    private ApplicationDO validateOwner(Long appId, Long operatorUserId) {
        ApplicationDO application = applicationMapper.selectById(appId);
        if (application == null) {
            throw exception(APPLICATION_NOT_EXISTS);
        }
        if (!Objects.equals(application.getOwnerUserId(), operatorUserId)) {
            throw exception(APPLICATION_MEMBER_OWNER_ONLY);
        }
        return application;
    }

    /**
     * 确保指定用户在应用下存在一条有效成员行。
     */
    private void ensureMemberRow(ApplicationDO application, Long userId) {
        ApplicationMemberDO existing = applicationMemberMapper.selectByTenantIdAndAppIdAndUserIdIncludingDeleted(
                application.getTenantId(), application.getId(), userId);
        if (existing == null) {
            ApplicationMemberDO member = new ApplicationMemberDO();
            member.setAppId(application.getId());
            member.setUserId(userId);
            applicationMemberMapper.insert(member);
        } else if (Boolean.TRUE.equals(existing.getDeleted())) {
            applicationMemberMapper.restoreById(existing.getId(), application.getTenantId(), LocalDateTime.now());
        }
    }

    private ApplicationMemberRespVO convert(ApplicationMemberDO member, Long ownerUserId) {
        ApplicationMemberRespVO respVO = new ApplicationMemberRespVO();
        respVO.setId(member.getId());
        respVO.setAppId(member.getAppId());
        respVO.setUserId(member.getUserId());
        respVO.setCreateTime(member.getCreateTime());
        respVO.setMemberType(Objects.equals(member.getUserId(), ownerUserId)
                ? ApplicationMemberTypeEnum.OWNER.getType()
                : ApplicationMemberTypeEnum.PARTICIPANT.getType());
        return respVO;
    }

}
