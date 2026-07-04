package cn.iocoder.yudao.module.project.service.workitem;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemAssignReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemSaveReqVO;
import cn.iocoder.yudao.module.project.convert.workitem.ProjectWorkItemConvert;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.ProjectWorkItemMapper;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemStatusEnum;
import cn.iocoder.yudao.module.project.service.project.ProjectService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.*;

@Service
@Validated
public class ProjectWorkItemServiceImpl implements ProjectWorkItemService {

    private static final String WORK_ITEM_NO_PREFIX = "WI-";
    private static final String WORK_ITEM_NO_TEMP_PREFIX = "WI-TMP-";

    @Resource
    private ProjectWorkItemMapper workItemMapper;
    @Resource
    private ProjectService projectService;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createWorkItem(ProjectWorkItemSaveReqVO createReqVO) {
        validateWorkItemForCreateOrUpdate(null, createReqVO);

        ProjectWorkItemDO workItem = ProjectWorkItemConvert.INSTANCE.convert(createReqVO);
        workItem.setStatus(ProjectWorkItemStatusEnum.PENDING.getValue());
        workItem.setWorkItemNo(WORK_ITEM_NO_TEMP_PREFIX + IdUtil.fastSimpleUUID());
        workItemMapper.insert(workItem);

        String workItemNo = generateWorkItemNo(workItem.getId());
        workItemMapper.updateWorkItemNoById(workItem.getId(), workItemNo, LocalDateTime.now());
        return workItem.getId();
    }

    @Override
    public void updateWorkItem(ProjectWorkItemSaveReqVO updateReqVO) {
        ProjectWorkItemDO oldWorkItem = validateWorkItemExists(updateReqVO.getId());
        validateWorkItemForCreateOrUpdate(oldWorkItem, updateReqVO);

        ProjectWorkItemDO updateObj = ProjectWorkItemConvert.INSTANCE.convert(updateReqVO);
        updateObj.setWorkItemNo(null);
        updateObj.setStatus(null);
        workItemMapper.updateById(updateObj);
    }

    @Override
    public void deleteWorkItem(Long id) {
        validateWorkItemExists(id);
        validateNoChildWorkItems(id);
        workItemMapper.deleteById(id);
    }

    @Override
    public ProjectWorkItemDO getWorkItem(Long id) {
        return workItemMapper.selectById(id);
    }

    @Override
    public PageResult<ProjectWorkItemDO> getWorkItemPage(ProjectWorkItemPageReqVO pageReqVO) {
        return workItemMapper.selectPage(pageReqVO);
    }

    @Override
    public void assignWorkItem(ProjectWorkItemAssignReqVO assignReqVO) {
        validateWorkItemExists(assignReqVO.getId());
        adminUserApi.validateUser(assignReqVO.getAssigneeUserId());

        ProjectWorkItemDO updateObj = new ProjectWorkItemDO();
        updateObj.setId(assignReqVO.getId());
        updateObj.setAssigneeUserId(assignReqVO.getAssigneeUserId());
        updateObj.setRemark(assignReqVO.getRemark());
        workItemMapper.updateById(updateObj);
    }

    @Override
    public ProjectWorkItemDO validateWorkItemExists(Long id) {
        if (id == null) {
            throw exception(PROJECT_WORK_ITEM_NOT_EXISTS);
        }
        ProjectWorkItemDO workItem = workItemMapper.selectById(id);
        if (workItem == null) {
            throw exception(PROJECT_WORK_ITEM_NOT_EXISTS);
        }
        return workItem;
    }

    private void validateWorkItemForCreateOrUpdate(ProjectWorkItemDO oldWorkItem, ProjectWorkItemSaveReqVO reqVO) {
        projectService.validateProjectUsable(reqVO.getProjectId());
        adminUserApi.validateUser(reqVO.getAssigneeUserId());
        validatePlannedTime(reqVO);
        validateParentWorkItem(oldWorkItem == null ? null : oldWorkItem.getId(), reqVO);
        if (oldWorkItem != null && ObjUtil.notEqual(oldWorkItem.getProjectId(), reqVO.getProjectId())) {
            validateNoChildWorkItems(oldWorkItem.getId());
        }
    }

    private void validatePlannedTime(ProjectWorkItemSaveReqVO reqVO) {
        if (reqVO.getPlannedStartTime() == null || reqVO.getPlannedEndTime() == null) {
            return;
        }
        if (reqVO.getPlannedEndTime().isBefore(reqVO.getPlannedStartTime())) {
            throw exception(PROJECT_WORK_ITEM_PLANNED_TIME_INVALID);
        }
    }

    private void validateParentWorkItem(Long currentWorkItemId, ProjectWorkItemSaveReqVO reqVO) {
        if (reqVO.getParentWorkItemId() == null) {
            return;
        }
        if (Objects.equals(currentWorkItemId, reqVO.getParentWorkItemId())) {
            throw exception(PROJECT_WORK_ITEM_PARENT_INVALID);
        }
        ProjectWorkItemDO parentWorkItem = validateWorkItemExists(reqVO.getParentWorkItemId());
        if (!Objects.equals(parentWorkItem.getProjectId(), reqVO.getProjectId())) {
            throw exception(PROJECT_WORK_ITEM_PARENT_INVALID);
        }
        validateParentChain(currentWorkItemId, parentWorkItem);
    }

    private void validateNoChildWorkItems(Long id) {
        if (workItemMapper.selectCountByParentWorkItemId(id) > 0) {
            throw exception(PROJECT_WORK_ITEM_HAS_CHILDREN);
        }
    }

    private void validateParentChain(Long currentWorkItemId, ProjectWorkItemDO parentWorkItem) {
        if (currentWorkItemId == null) {
            return;
        }
        ProjectWorkItemDO cursor = parentWorkItem;
        while (cursor != null && cursor.getParentWorkItemId() != null) {
            if (Objects.equals(cursor.getParentWorkItemId(), currentWorkItemId)) {
                throw exception(PROJECT_WORK_ITEM_PARENT_INVALID);
            }
            cursor = workItemMapper.selectById(cursor.getParentWorkItemId());
        }
    }

    private String generateWorkItemNo(Long id) {
        return WORK_ITEM_NO_PREFIX + String.format("%06d", id);
    }

}
