package cn.iocoder.yudao.module.project.service.worklog;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogCreateReqVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogRespVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogSummaryReqVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogSummaryRespVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogUpdateReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import cn.iocoder.yudao.module.project.dal.dataobject.worklog.WorklogDO;
import cn.iocoder.yudao.module.project.dal.dataobject.worklog.WorklogSummaryResult;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.ProjectWorkItemMapper;
import cn.iocoder.yudao.module.project.dal.mysql.worklog.WorklogMapper;
import cn.iocoder.yudao.module.project.enums.WorklogGroupByEnum;
import cn.iocoder.yudao.module.project.enums.WorklogTypeEnum;
import cn.iocoder.yudao.module.project.enums.WorklogWorkItemScopeEnum;
import cn.iocoder.yudao.module.project.service.project.ProjectMemberService;
import cn.iocoder.yudao.module.project.service.project.ProjectPermissionService;
import cn.iocoder.yudao.module.project.service.project.ProjectService;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import com.mzt.logapi.context.LogRecordContext;
import com.mzt.logapi.starter.annotation.LogRecord;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.project.enums.LogRecordConstants.*;

@Service
@Validated
public class WorklogServiceImpl implements WorklogService {

    private static final String WORKLOG_CATEGORY_DICT_TYPE = "project_worklog_category";
    private static final BigDecimal ZERO_HOURS = new BigDecimal("0.00");
    private static final BigDecimal MAX_HOURS = new BigDecimal("99999999.99");

    @Resource
    private WorklogMapper worklogMapper;
    @Resource
    private ProjectWorkItemMapper workItemMapper;
    @Resource
    private ProjectService projectService;
    @Resource
    private ProjectMemberService projectMemberService;
    @Resource
    private ProjectPermissionService projectPermissionService;
    @Resource
    private DictDataApi dictDataApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = PROJECT_WORKLOG_TYPE, subType = PROJECT_WORKLOG_CREATE_SUB_TYPE,
            bizNo = "{{#_ret}}", success = PROJECT_WORKLOG_CREATE_SUCCESS)
    public Long createWorklog(WorklogCreateReqVO reqVO) {
        ProjectWorkItemDO workItem = lockWorkItem(reqVO.getWorkItemId());
        Long loginUserId = getLoginUserId();
        projectService.validateProjectUsable(workItem.getProjectId());
        boolean canManage = projectPermissionService.hasPermission(workItem.getProjectId(), loginUserId,
                "WORKLOG_MANAGE");
        if (!canManage) {
            projectPermissionService.validatePermission(workItem.getProjectId(), loginUserId, "WORKLOG_CREATE");
        }
        Long ownerUserId = reqVO.getOwnerUserId() == null ? loginUserId : reqVO.getOwnerUserId();
        if (!canManage && !Objects.equals(loginUserId, ownerUserId)) {
            throw exception(PROJECT_WORKLOG_OWNER_DENIED);
        }
        validateMutableFields(reqVO.getType(), reqVO.getCategory(), ownerUserId, reqVO.getWorkDate(),
                workItem.getProjectId());
        validateContent(reqVO.getHours(), reqVO.getDescription());
        WorklogDO worklog = new WorklogDO();
        worklog.setProjectId(workItem.getProjectId());
        worklog.setWorkItemId(workItem.getId());
        worklog.setType(reqVO.getType());
        worklog.setHours(reqVO.getHours());
        worklog.setCategory(reqVO.getCategory());
        worklog.setDescription(reqVO.getDescription());
        worklog.setOwnerUserId(ownerUserId);
        worklog.setWorkDate(reqVO.getWorkDate());
        worklogMapper.insert(worklog);
        addWorkItemHours(workItem.getId(), reqVO.getType(), reqVO.getHours());
        return worklog.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = PROJECT_WORKLOG_TYPE, subType = PROJECT_WORKLOG_UPDATE_SUB_TYPE,
            bizNo = "{{#updateReqVO.id}}", success = PROJECT_WORKLOG_UPDATE_SUCCESS)
    public void updateWorklog(WorklogUpdateReqVO updateReqVO) {
        WorklogDO worklog = lockWorklogWithWorkItem(updateReqVO.getId());
        LogRecordContext.putVariable("worklog", worklog);
        Long loginUserId = getLoginUserId();
        boolean canManage = validateWorklogOperation(worklog, loginUserId);
        Long ownerUserId = updateReqVO.getOwnerUserId() == null ? worklog.getOwnerUserId()
                : updateReqVO.getOwnerUserId();
        if (!canManage && !Objects.equals(ownerUserId, worklog.getOwnerUserId())) {
            throw exception(PROJECT_WORKLOG_OPERATION_DENIED);
        }
        validateMutableFields(worklog.getType(), updateReqVO.getCategory(), ownerUserId,
                updateReqVO.getWorkDate(), worklog.getProjectId());
        validateContent(updateReqVO.getHours(), updateReqVO.getDescription());
        WorklogDO update = new WorklogDO();
        update.setId(worklog.getId());
        update.setHours(updateReqVO.getHours());
        update.setCategory(updateReqVO.getCategory());
        update.setDescription(updateReqVO.getDescription());
        update.setOwnerUserId(ownerUserId);
        update.setWorkDate(updateReqVO.getWorkDate());
        worklogMapper.updateById(update);
        addWorkItemHours(worklog.getWorkItemId(), worklog.getType(), updateReqVO.getHours().subtract(worklog.getHours()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = PROJECT_WORKLOG_TYPE, subType = PROJECT_WORKLOG_DELETE_SUB_TYPE,
            bizNo = "{{#id}}", success = PROJECT_WORKLOG_DELETE_SUCCESS)
    public void deleteWorklog(Long id) {
        WorklogDO worklog = lockWorklogWithWorkItem(id);
        LogRecordContext.putVariable("worklog", worklog);
        Long loginUserId = getLoginUserId();
        validateWorklogOperation(worklog, loginUserId);
        worklogMapper.deleteById(id);
        addWorkItemHours(worklog.getWorkItemId(), worklog.getType(), worklog.getHours().negate());
    }

    @Override
    public WorklogRespVO getWorklog(Long id) {
        WorklogDO worklog = validateWorklog(id);
        projectPermissionService.validatePermission(worklog.getProjectId(), getLoginUserId(), "WORKLOG_QUERY");
        ProjectWorkItemDO workItem = workItemMapper.selectById(worklog.getWorkItemId());
        return convert(worklog, workItem);
    }

    @Override
    public PageResult<WorklogRespVO> getWorklogPage(WorklogPageReqVO reqVO) {
        validateDateRange(reqVO.getWorkDateStart(), reqVO.getWorkDateEnd());
        validateQuery(reqVO.getProjectId());
        WorkItemRange range = resolveWorkItemRange(reqVO.getProjectId(), reqVO.getWorkItemId(),
                reqVO.getWorkItemScope());
        if (range.emptyResult()) {
            return new PageResult<>(Collections.emptyList(), 0L);
        }
        PageResult<WorklogDO> page = worklogMapper.selectPage(reqVO,
                worklogMapper.buildQueryWrapper(reqVO, range.workItemIds()));
        Map<Long, ProjectWorkItemDO> workItemMap = getWorkItemMap(page.getList());
        return new PageResult<>(page.getList().stream().map(item -> convert(item,
                workItemMap.get(item.getWorkItemId()))).toList(), page.getTotal());
    }

    @Override
    public WorklogSummaryRespVO getWorklogSummary(WorklogSummaryReqVO reqVO) {
        validateDateRange(reqVO.getWorkDateStart(), reqVO.getWorkDateEnd());
        validateQuery(reqVO.getProjectId());
        if (reqVO.getGroupBy() != null && WorklogGroupByEnum.of(reqVO.getGroupBy()) == null) {
            throw exception(PROJECT_WORKLOG_FIELD_INVALID, "groupBy");
        }
        WorkItemRange range = resolveWorkItemRange(reqVO.getProjectId(), reqVO.getWorkItemId(),
                reqVO.getWorkItemScope());
        if (range.emptyResult()) {
            return emptySummary(reqVO.getGroupBy());
        }
        var wrapper = worklogMapper.buildQueryWrapper(reqVO, range.workItemIds());
        WorklogSummaryResult total = worklogMapper.selectSummary(wrapper);
        List<WorklogSummaryResult> groups = reqVO.getGroupBy() == null ? Collections.emptyList()
                : worklogMapper.selectGroupSummary(wrapper, WorklogGroupByEnum.of(reqVO.getGroupBy()));
        WorklogSummaryRespVO result = new WorklogSummaryRespVO();
        result.setEstimatedHours(hours(total == null ? null : total.getEstimatedHours()));
        result.setActualHours(hours(total == null ? null : total.getActualHours()));
        result.setGroupBy(reqVO.getGroupBy());
        result.setGroups(groups.stream().map(item -> new WorklogSummaryRespVO.Group(item.getValue(), null,
                hours(item.getEstimatedHours()), hours(item.getActualHours()))).toList());
        return result;
    }

    @Override
    public boolean hasWorklog(Long workItemId) {
        return workItemId != null && worklogMapper.existsByWorkItemId(workItemId);
    }

    @Override
    public void deleteByWorkItemId(Long workItemId) {
        if (workItemId != null) {
            worklogMapper.deleteByWorkItemId(workItemId);
        }
    }

    private void validateQuery(Long projectId) {
        projectService.validateProjectExists(projectId);
        projectPermissionService.validatePermission(projectId, getLoginUserId(), "WORKLOG_QUERY");
    }

    private void validateContent(BigDecimal hours, String description) {
        if (hours == null || hours.compareTo(BigDecimal.ZERO) < 0 || hours.compareTo(MAX_HOURS) > 0
                || hours.scale() > 2) {
            throw exception(PROJECT_WORKLOG_FIELD_INVALID, "hours");
        }
        if (description != null && description.length() > 1000) {
            throw exception(PROJECT_WORKLOG_FIELD_INVALID, "description");
        }
    }

    private void validateDateRange(java.time.LocalDate start, java.time.LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw exception(PROJECT_WORKLOG_FIELD_INVALID, "workDateRange");
        }
    }

    private void validateMutableFields(String type, String category, Long ownerUserId,
                                       java.time.LocalDate workDate, Long projectId) {
        WorklogTypeEnum typeEnum = WorklogTypeEnum.of(type);
        if (typeEnum == null) {
            throw exception(PROJECT_WORKLOG_TYPE_INVALID);
        }
        if (typeEnum == WorklogTypeEnum.ACTUAL && workDate == null) {
            throw exception(PROJECT_WORKLOG_ACTUAL_DATE_REQUIRED);
        }
        if (StrUtil.isBlank(category)) {
            throw exception(PROJECT_WORKLOG_FIELD_INVALID, "category");
        }
        dictDataApi.validateDictDataList(WORKLOG_CATEGORY_DICT_TYPE, List.of(category)).checkError();
        if (!projectMemberService.isProjectMember(projectId, ownerUserId)) {
            throw exception(PROJECT_WORKLOG_OWNER_NOT_MEMBER);
        }
    }

    private void validateCanOperate(WorklogDO worklog, Long loginUserId, boolean canManage) {
        if (!canManage && !Objects.equals(loginUserId, worklog.getOwnerUserId())) {
            throw exception(PROJECT_WORKLOG_OPERATION_DENIED);
        }
    }

    private ProjectWorkItemDO lockWorkItem(Long id) {
        ProjectWorkItemDO workItem = id == null ? null : workItemMapper.selectByIdForUpdate(id);
        if (workItem == null) {
            throw exception(PROJECT_WORK_ITEM_NOT_EXISTS);
        }
        return workItem;
    }

    private WorklogDO validateWorklog(Long id) {
        WorklogDO worklog = id == null ? null : worklogMapper.selectById(id);
        if (worklog == null) {
            throw exception(PROJECT_WORKLOG_NOT_EXISTS);
        }
        return worklog;
    }

    private WorklogDO validateLockedWorklog(Long id) {
        WorklogDO worklog = id == null ? null : worklogMapper.selectByIdForUpdate(id);
        if (worklog == null) {
            throw exception(PROJECT_WORKLOG_NOT_EXISTS);
        }
        return worklog;
    }

    private WorklogDO lockWorklogWithWorkItem(Long id) {
        WorklogDO snapshot = validateWorklog(id);
        ProjectWorkItemDO workItem = lockWorkItem(snapshot.getWorkItemId());
        WorklogDO worklog = validateLockedWorklog(id);
        if (!Objects.equals(workItem.getId(), worklog.getWorkItemId())
                || !Objects.equals(workItem.getProjectId(), worklog.getProjectId())) {
            throw exception(PROJECT_WORKLOG_NOT_EXISTS);
        }
        return worklog;
    }

    private boolean validateWorklogOperation(WorklogDO worklog, Long loginUserId) {
        projectService.validateProjectUsable(worklog.getProjectId());
        boolean canManage = projectPermissionService.hasPermission(worklog.getProjectId(), loginUserId,
                "WORKLOG_MANAGE");
        if (!canManage) {
            projectPermissionService.validatePermission(worklog.getProjectId(), loginUserId, "WORKLOG_CREATE");
        }
        validateCanOperate(worklog, loginUserId, canManage);
        return canManage;
    }

    private void addWorkItemHours(Long workItemId, String type, BigDecimal delta) {
        WorklogTypeEnum worklogType = WorklogTypeEnum.of(type);
        if (worklogType == null) {
            throw exception(PROJECT_WORKLOG_TYPE_INVALID);
        }
        if (workItemMapper.addWorklogHours(workItemId, worklogType, delta) == 0) {
            throw exception(PROJECT_WORK_ITEM_NOT_EXISTS);
        }
    }

    private WorkItemRange resolveWorkItemRange(Long projectId, Long workItemId, String workItemScope) {
        if (workItemId == null) {
            if (workItemScope != null) {
                throw exception(PROJECT_WORKLOG_FIELD_INVALID, "workItemScope");
            }
            return WorkItemRange.unscoped();
        }
        WorklogWorkItemScopeEnum scope = workItemScope == null ? WorklogWorkItemScopeEnum.SELF
                : WorklogWorkItemScopeEnum.of(workItemScope);
        if (scope == null) {
            throw exception(PROJECT_WORKLOG_FIELD_INVALID, "workItemScope");
        }
        if (scope == WorklogWorkItemScopeEnum.SELF) {
            return WorkItemRange.scoped(List.of(workItemId));
        }
        List<Long> descendants = workItemMapper.selectDescendantIds(workItemId, projectId,
                TenantContextHolder.getRequiredTenantId());
        return descendants.isEmpty() ? WorkItemRange.emptyRange() : WorkItemRange.scoped(descendants);
    }

    private WorklogSummaryRespVO emptySummary(String groupBy) {
        WorklogSummaryRespVO result = new WorklogSummaryRespVO();
        result.setEstimatedHours(ZERO_HOURS);
        result.setActualHours(ZERO_HOURS);
        result.setGroupBy(groupBy);
        result.setGroups(Collections.emptyList());
        return result;
    }

    private record WorkItemRange(List<Long> workItemIds, boolean emptyResult) {

        static WorkItemRange unscoped() {
            return new WorkItemRange(null, false);
        }

        static WorkItemRange scoped(List<Long> workItemIds) {
            return new WorkItemRange(workItemIds, false);
        }

        static WorkItemRange emptyRange() {
            return new WorkItemRange(Collections.emptyList(), true);
        }
    }

    private Map<Long, ProjectWorkItemDO> getWorkItemMap(List<WorklogDO> worklogs) {
        if (worklogs.isEmpty()) {
            return Collections.emptyMap();
        }
        return workItemMapper.selectBatchIds(worklogs.stream().map(WorklogDO::getWorkItemId).distinct().toList())
                .stream().collect(Collectors.toMap(ProjectWorkItemDO::getId, Function.identity()));
    }

    private WorklogRespVO convert(WorklogDO worklog, ProjectWorkItemDO workItem) {
        WorklogRespVO result = new WorklogRespVO();
        result.setId(worklog.getId());
        result.setProjectId(worklog.getProjectId());
        result.setWorkItemId(worklog.getWorkItemId());
        if (workItem != null) {
            result.setWorkItemNo(workItem.getWorkItemNo());
            result.setWorkItemTitle(workItem.getTitle());
        }
        result.setType(worklog.getType());
        result.setHours(worklog.getHours());
        result.setCategory(worklog.getCategory());
        result.setDescription(worklog.getDescription());
        result.setOwnerUserId(worklog.getOwnerUserId());
        result.setWorkDate(worklog.getWorkDate());
        result.setCreator(worklog.getCreator());
        result.setCreateTime(worklog.getCreateTime());
        result.setUpdateTime(worklog.getUpdateTime());
        return result;
    }

    private BigDecimal hours(BigDecimal value) {
        return value == null ? ZERO_HOURS : value.setScale(2);
    }

}
