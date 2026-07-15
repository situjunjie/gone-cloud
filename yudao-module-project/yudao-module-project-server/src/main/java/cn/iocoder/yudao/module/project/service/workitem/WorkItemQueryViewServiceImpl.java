package cn.iocoder.yudao.module.project.service.workitem;

import cn.hutool.core.util.ObjUtil;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemQueryViewSaveReqVO;
import cn.iocoder.yudao.module.project.convert.workitem.ProjectWorkItemConvert;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemQueryViewDO;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemQueryViewMapper;
import cn.iocoder.yudao.module.project.enums.WorkItemQueryViewScopeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.WORK_ITEM_QUERY_VIEW_ACCESS_DENIED;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.WORK_ITEM_QUERY_VIEW_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.WORK_ITEM_QUERY_VIEW_NOT_EXISTS;

@Service
@Validated
public class WorkItemQueryViewServiceImpl implements WorkItemQueryViewService {

    @Resource
    private WorkItemQueryViewMapper queryViewMapper;
    @Resource
    private ProjectWorkItemQueryConditionService queryConditionService;

    @Override
    public Long createQueryView(WorkItemQueryViewSaveReqVO createReqVO, Long loginUserId) {
        queryConditionService.validateQueryViewCondition(createReqVO.getCondition(), loginUserId);
        Long userId = buildUserId(createReqVO.getScope(), loginUserId);
        validateQueryViewCodeUnique(null, createReqVO.getScope(), userId, createReqVO.getCode());

        WorkItemQueryViewDO queryView = ProjectWorkItemConvert.INSTANCE.convert(createReqVO);
        queryView.setUserId(userId);
        if (queryView.getSort() == null) {
            queryView.setSort(0);
        }
        queryViewMapper.insert(queryView);
        return queryView.getId();
    }

    @Override
    public void updateQueryView(WorkItemQueryViewSaveReqVO updateReqVO, Long loginUserId) {
        WorkItemQueryViewDO oldQueryView = validateQueryViewExists(updateReqVO.getId());
        validateQueryViewAccess(oldQueryView, loginUserId);
        queryConditionService.validateQueryViewCondition(updateReqVO.getCondition(), loginUserId);

        Long userId = buildUserId(updateReqVO.getScope(), loginUserId);
        validateQueryViewCodeUnique(updateReqVO.getId(), updateReqVO.getScope(), userId, updateReqVO.getCode());

        WorkItemQueryViewDO updateObj = ProjectWorkItemConvert.INSTANCE.convert(updateReqVO);
        updateObj.setUserId(userId);
        if (updateObj.getSort() == null) {
            updateObj.setSort(0);
        }
        queryViewMapper.updateById(updateObj);
    }

    @Override
    public void deleteQueryView(Long id, Long loginUserId) {
        WorkItemQueryViewDO queryView = validateQueryViewExists(id);
        validateQueryViewAccess(queryView, loginUserId);
        queryViewMapper.deleteById(id);
    }

    @Override
    public WorkItemQueryViewDO getQueryView(Long id, Long loginUserId) {
        WorkItemQueryViewDO queryView = validateQueryViewExists(id);
        validateQueryViewAccess(queryView, loginUserId);
        return queryView;
    }

    @Override
    public WorkItemQueryViewDO getQueryViewByCode(String scope, String code, Long loginUserId) {
        Long userId = buildUserId(scope, loginUserId);
        WorkItemQueryViewDO queryView = queryViewMapper.selectByScopeUserIdAndCode(scope, userId, code);
        if (queryView == null) {
            throw exception(WORK_ITEM_QUERY_VIEW_NOT_EXISTS);
        }
        return queryView;
    }

    @Override
    public List<WorkItemQueryViewDO> getQueryViewList(Long loginUserId) {
        List<WorkItemQueryViewDO> list = new ArrayList<>();
        list.addAll(queryViewMapper.selectSystemList());
        list.addAll(queryViewMapper.selectUserList(loginUserId));
        return list;
    }

    private WorkItemQueryViewDO validateQueryViewExists(Long id) {
        if (id == null) {
            throw exception(WORK_ITEM_QUERY_VIEW_NOT_EXISTS);
        }
        WorkItemQueryViewDO queryView = queryViewMapper.selectById(id);
        if (queryView == null) {
            throw exception(WORK_ITEM_QUERY_VIEW_NOT_EXISTS);
        }
        return queryView;
    }

    private void validateQueryViewAccess(WorkItemQueryViewDO queryView, Long loginUserId) {
        if (Objects.equals(queryView.getScope(), WorkItemQueryViewScopeEnum.SYSTEM.getValue())) {
            return;
        }
        if (ObjUtil.notEqual(queryView.getUserId(), loginUserId)) {
            throw exception(WORK_ITEM_QUERY_VIEW_ACCESS_DENIED);
        }
    }

    private void validateQueryViewCodeUnique(Long id, String scope, Long userId, String code) {
        WorkItemQueryViewDO queryView = queryViewMapper.selectByScopeUserIdAndCode(scope, userId, code);
        if (queryView == null || Objects.equals(queryView.getId(), id)) {
            return;
        }
        throw exception(WORK_ITEM_QUERY_VIEW_CODE_DUPLICATE);
    }

    private Long buildUserId(String scope, Long loginUserId) {
        return Objects.equals(scope, WorkItemQueryViewScopeEnum.SYSTEM.getValue())
                ? WorkItemQueryViewMapper.SYSTEM_USER_ID : loginUserId;
    }

}
