package cn.iocoder.yudao.module.project.service.workitem;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemPageReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemQueryViewDO;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldOptionMapper;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemQueryFieldEnum;
import cn.iocoder.yudao.module.project.enums.WorkItemFieldScopeEnum;
import cn.iocoder.yudao.module.project.enums.WorkItemFieldTypeEnum;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_WORK_ITEM_QUERY_FIELD_NOT_SUPPORTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

class ProjectWorkItemQueryConditionServiceTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ProjectWorkItemQueryConditionService service;

    @Mock
    private WorkItemFieldMapper customFieldMapper;
    @Mock
    private WorkItemFieldOptionMapper customFieldOptionMapper;

    @Test
    void testNormalize_conditionGroups() {
        ProjectWorkItemPageReqVO reqVO = new ProjectWorkItemPageReqVO();
        reqVO.setConditionGroups(List.of(
                buildGroup(buildCondition("title", "eq", "a"), buildCondition("status", "eq", "c")),
                buildGroup(buildCondition("title", "eq", "b"), buildCondition("priority", "eq", "a"))));

        service.normalize(reqVO, 1L);

        ProjectWorkItemPageReqVO.Condition first = reqVO.getConditionGroups().get(0).getConditions().get(0);
        assertEquals(ProjectWorkItemQueryFieldEnum.TITLE, first.getFieldEnum());
        assertEquals("a", first.getParsedValue());
        assertNotNull(reqVO.getConditionGroups().get(1).getConditions().get(1).getOperatorEnum());
    }

    @Test
    void testNormalize_flatAndGroupsConflict() {
        ProjectWorkItemPageReqVO reqVO = new ProjectWorkItemPageReqVO();
        reqVO.setConditions(List.of(buildCondition("title", "eq", "a")));
        reqVO.setConditionGroups(List.of(buildGroup(buildCondition("title", "eq", "b"))));

        assertServiceException(() -> service.normalize(reqVO, 1L),
                PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID, "conditions,conditionGroups");
    }

    @Test
    void testNormalize_emptyGroupRejected() {
        ProjectWorkItemPageReqVO.ConditionGroup group = new ProjectWorkItemPageReqVO.ConditionGroup();
        group.setConditions(List.of());
        ProjectWorkItemPageReqVO reqVO = new ProjectWorkItemPageReqVO();
        reqVO.setConditionGroups(List.of(group));

        assertServiceException(() -> service.normalize(reqVO, 1L),
                PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID, "conditionGroups");
    }

    @Test
    void testNormalize_conditionLimitRejected() {
        List<ProjectWorkItemPageReqVO.Condition> conditions = new ArrayList<>();
        for (int i = 0; i <= ProjectWorkItemQueryConditionService.MAX_CONDITION_COUNT; i++) {
            conditions.add(buildCondition("title", "eq", "value" + i));
        }
        ProjectWorkItemPageReqVO reqVO = new ProjectWorkItemPageReqVO();
        reqVO.setConditionGroups(List.of(buildGroup(conditions.toArray(ProjectWorkItemPageReqVO.Condition[]::new))));

        assertServiceException(() -> service.normalize(reqVO, 1L),
                PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID, "conditionGroups");
    }

    @Test
    void testNormalize_globalCustomFieldAccepted() {
        WorkItemFieldDO field = buildCustomField(10L, WorkItemFieldScopeEnum.GLOBAL.getValue(),
                WorkItemFieldScopeEnum.GLOBAL_PROJECT_ID);
        when(customFieldMapper.selectById(10L)).thenReturn(field);
        ProjectWorkItemPageReqVO.Condition condition = buildCondition("custom:10", "eq", "global-value");
        ProjectWorkItemPageReqVO reqVO = new ProjectWorkItemPageReqVO();
        reqVO.setProjectId(1L);
        reqVO.setConditionGroups(List.of(buildGroup(condition)));

        service.normalize(reqVO, 1L);

        assertEquals(field, condition.getCustomField());
        assertEquals("global-value", condition.getParsedValue());
    }

    @Test
    void testNormalize_foreignProjectCustomFieldRejected() {
        WorkItemFieldDO field = buildCustomField(10L, WorkItemFieldScopeEnum.PROJECT.getValue(), 2L);
        when(customFieldMapper.selectById(10L)).thenReturn(field);
        ProjectWorkItemPageReqVO reqVO = new ProjectWorkItemPageReqVO();
        reqVO.setProjectId(1L);
        reqVO.setConditionGroups(List.of(buildGroup(buildCondition("custom:10", "eq", "value"))));

        assertServiceException(() -> service.normalize(reqVO, 1L),
                PROJECT_WORK_ITEM_QUERY_FIELD_NOT_SUPPORTED, "custom:10");
    }

    @Test
    void testValidateQueryViewCondition_preservesCurrentUserToken() {
        WorkItemQueryViewDO.FieldCondition fieldCondition = new WorkItemQueryViewDO.FieldCondition();
        fieldCondition.setField("assigneeUserId");
        fieldCondition.setOperator("eq");
        fieldCondition.setValue("me");
        WorkItemQueryViewDO.ConditionGroup group = new WorkItemQueryViewDO.ConditionGroup();
        group.setConditions(List.of(fieldCondition));
        WorkItemQueryViewDO.QueryCondition queryCondition = new WorkItemQueryViewDO.QueryCondition();
        queryCondition.setConditionGroups(List.of(group));

        service.validateQueryViewCondition(queryCondition, 99L);

        assertEquals("me", fieldCondition.getValue());
    }

    @Test
    void testNormalize_resolvesCurrentUserTokenAtExecution() {
        ProjectWorkItemPageReqVO.Condition condition = buildCondition("assigneeUserId", "eq", "me");
        ProjectWorkItemPageReqVO reqVO = new ProjectWorkItemPageReqVO();
        reqVO.setConditionGroups(List.of(buildGroup(condition)));

        service.normalize(reqVO, 99L);

        assertEquals(99L, condition.getParsedValue());
    }

    @Test
    void testValidateQueryViewCondition_legacyFlatConditions() {
        WorkItemQueryViewDO.FieldCondition fieldCondition = new WorkItemQueryViewDO.FieldCondition();
        fieldCondition.setField("title");
        fieldCondition.setOperator("contains");
        fieldCondition.setValue("legacy");
        WorkItemQueryViewDO.QueryCondition queryCondition = new WorkItemQueryViewDO.QueryCondition();
        queryCondition.setConditions(List.of(fieldCondition));

        service.validateQueryViewCondition(queryCondition, 99L);

        assertEquals("legacy", fieldCondition.getValue());
    }

    @Test
    void testQueryViewCondition_conditionGroupsJsonRoundTrip() {
        WorkItemQueryViewDO.FieldCondition fieldCondition = new WorkItemQueryViewDO.FieldCondition();
        fieldCondition.setField("title");
        fieldCondition.setOperator("eq");
        fieldCondition.setValue("round-trip");
        WorkItemQueryViewDO.ConditionGroup group = new WorkItemQueryViewDO.ConditionGroup();
        group.setConditions(List.of(fieldCondition));
        WorkItemQueryViewDO.QueryCondition source = new WorkItemQueryViewDO.QueryCondition();
        source.setConditionGroups(List.of(group));

        WorkItemQueryViewDO.QueryCondition result = JsonUtils.parseObject(
                JsonUtils.toJsonString(source), WorkItemQueryViewDO.QueryCondition.class);

        assertEquals("round-trip", result.getConditionGroups().get(0).getConditions().get(0).getValue());
    }

    private ProjectWorkItemPageReqVO.ConditionGroup buildGroup(ProjectWorkItemPageReqVO.Condition... conditions) {
        ProjectWorkItemPageReqVO.ConditionGroup group = new ProjectWorkItemPageReqVO.ConditionGroup();
        group.setConditions(List.of(conditions));
        return group;
    }

    private ProjectWorkItemPageReqVO.Condition buildCondition(String field, String operator, String value) {
        ProjectWorkItemPageReqVO.Condition condition = new ProjectWorkItemPageReqVO.Condition();
        condition.setField(field);
        condition.setOperator(operator);
        condition.setValue(value);
        return condition;
    }

    private WorkItemFieldDO buildCustomField(Long id, String scope, Long projectId) {
        WorkItemFieldDO field = new WorkItemFieldDO();
        field.setId(id);
        field.setScope(scope);
        field.setProjectId(projectId);
        field.setCode("custom_text");
        field.setName("自定义文本");
        field.setFieldType(WorkItemFieldTypeEnum.TEXT.getValue());
        field.setEnabled(true);
        return field;
    }

}
