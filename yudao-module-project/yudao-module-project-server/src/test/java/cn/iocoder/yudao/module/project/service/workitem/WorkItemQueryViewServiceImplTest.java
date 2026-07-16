package cn.iocoder.yudao.module.project.service.workitem;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemQueryViewSaveReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemQueryViewDO;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemQueryViewMapper;
import cn.iocoder.yudao.module.project.enums.WorkItemQueryViewScopeEnum;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkItemQueryViewServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private WorkItemQueryViewServiceImpl service;

    @Mock
    private WorkItemQueryViewMapper queryViewMapper;
    @Mock
    private ProjectWorkItemQueryConditionService queryConditionService;

    @Test
    void testCreateQueryView_conditionGroupsPreserved() {
        WorkItemQueryViewSaveReqVO reqVO = buildReq();
        doAnswer(invocation -> {
            WorkItemQueryViewDO queryView = invocation.getArgument(0);
            queryView.setId(10L);
            return 1;
        }).when(queryViewMapper).insert(any(WorkItemQueryViewDO.class));

        Long id = service.createQueryView(reqVO, 9L);

        assertEquals(10L, id);
        verify(queryConditionService).validateQueryViewCondition(reqVO.getCondition(), 9L);
        ArgumentCaptor<WorkItemQueryViewDO> captor = ArgumentCaptor.forClass(WorkItemQueryViewDO.class);
        verify(queryViewMapper).insert(captor.capture());
        WorkItemQueryViewDO.QueryCondition saved = captor.getValue().getCondition();
        assertEquals("me", saved.getConditionGroups().get(0).getConditions().get(0).getValue());
    }

    @Test
    void testUpdateQueryView_validatesCondition() {
        WorkItemQueryViewDO old = new WorkItemQueryViewDO();
        old.setId(10L);
        old.setScope(WorkItemQueryViewScopeEnum.USER.getValue());
        old.setUserId(9L);
        when(queryViewMapper.selectById(10L)).thenReturn(old);
        WorkItemQueryViewSaveReqVO reqVO = buildReq();
        reqVO.setId(10L);

        service.updateQueryView(reqVO, 9L);

        verify(queryConditionService).validateQueryViewCondition(reqVO.getCondition(), 9L);
        verify(queryViewMapper).updateById(any(WorkItemQueryViewDO.class));
    }

    private WorkItemQueryViewSaveReqVO buildReq() {
        WorkItemQueryViewDO.FieldCondition fieldCondition = new WorkItemQueryViewDO.FieldCondition();
        fieldCondition.setField("assigneeUserId");
        fieldCondition.setOperator("eq");
        fieldCondition.setValue("me");
        WorkItemQueryViewDO.ConditionGroup group = new WorkItemQueryViewDO.ConditionGroup();
        group.setConditions(List.of(fieldCondition));
        WorkItemQueryViewDO.QueryCondition condition = new WorkItemQueryViewDO.QueryCondition();
        condition.setConditionGroups(List.of(group));

        WorkItemQueryViewSaveReqVO reqVO = new WorkItemQueryViewSaveReqVO();
        reqVO.setName("我的待办");
        reqVO.setCode("my-todo");
        reqVO.setScope(WorkItemQueryViewScopeEnum.USER.getValue());
        reqVO.setCondition(condition);
        return reqVO;
    }

}
