package cn.iocoder.yudao.module.project.dal.mysql.workitem;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemPageReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldDO;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemQueryOperatorEnum;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemQueryFieldEnum;
import cn.iocoder.yudao.module.project.enums.WorkItemFieldTypeEnum;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class ProjectWorkItemMapperTest extends BaseMockitoUnitTest {

    private ProjectWorkItemMapper mapper;
    private ArgumentCaptor<Wrapper<ProjectWorkItemDO>> wrapperCaptor;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(ProjectWorkItemMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, ProjectWorkItemDO.class);
        mapper = mock(ProjectWorkItemMapper.class, CALLS_REAL_METHODS);
        wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        BaseMapperX<ProjectWorkItemDO> baseMapper = mapper;
        doAnswer(invocation -> new PageResult<ProjectWorkItemDO>(List.of(), 0L))
                .when(baseMapper).selectPage(any(PageParam.class), wrapperCaptor.capture());
    }

    @Test
    void testSelectPage_multiSelectUsesExistsWithoutJoin() {
        ProjectWorkItemPageReqVO reqVO = buildReq(ProjectWorkItemQueryOperatorEnum.IN, List.of(101L, 102L));

        mapper.selectPage(reqVO);

        String sql = wrapperCaptor.getValue().getCustomSqlSegment();
        assertTrue(sql.contains("EXISTS (SELECT 1 FROM proj_work_item_field_value_option"));
        assertTrue(sql.contains("option_id IN"));
        assertFalse(sql.contains(" JOIN "));
    }

    @Test
    void testSelectPage_notInRequiresValueAndExcludesMatchingOption() {
        ProjectWorkItemPageReqVO reqVO = buildReq(ProjectWorkItemQueryOperatorEnum.NOT_IN, List.of(101L));

        mapper.selectPage(reqVO);

        String sql = wrapperCaptor.getValue().getCustomSqlSegment();
        assertTrue(sql.contains("EXISTS (SELECT 1 FROM proj_work_item_field_value cfv"));
        assertTrue(sql.contains("AND NOT EXISTS (SELECT 1 FROM proj_work_item_field_value_option"));
    }

    @Test
    void testSelectPage_conditionGroupsAndWithinOrBetween() {
        ProjectWorkItemPageReqVO.Condition firstTitle = buildScalarCondition(
                ProjectWorkItemQueryFieldEnum.TITLE, "a");
        ProjectWorkItemPageReqVO.Condition custom = buildCustomCondition(
                ProjectWorkItemQueryOperatorEnum.IN, List.of(101L, 102L));
        ProjectWorkItemPageReqVO.Condition secondTitle = buildScalarCondition(
                ProjectWorkItemQueryFieldEnum.TITLE, "b");
        ProjectWorkItemPageReqVO.Condition status = buildScalarCondition(
                ProjectWorkItemQueryFieldEnum.STATUS, "PENDING");
        ProjectWorkItemPageReqVO reqVO = new ProjectWorkItemPageReqVO();
        reqVO.setProjectId(1L);
        reqVO.setConditionGroups(List.of(buildGroup(firstTitle, custom), buildGroup(secondTitle, status)));

        mapper.selectPage(reqVO);

        String sql = wrapperCaptor.getValue().getCustomSqlSegment();
        assertTrue(sql.contains("project_id"), sql);
        assertTrue(sql.contains("AND (("), sql);
        assertTrue(sql.contains(") OR ("), sql);
        assertTrue(sql.contains(" OR "), sql);
        assertTrue(sql.contains("title"), sql);
        assertTrue(sql.contains("status"), sql);
        assertTrue(sql.contains("EXISTS (SELECT 1 FROM proj_work_item_field_value_option"), sql);
    }

    private ProjectWorkItemPageReqVO buildReq(ProjectWorkItemQueryOperatorEnum operator, List<Object> values) {
        ProjectWorkItemPageReqVO.Condition condition = buildCustomCondition(operator, values);
        ProjectWorkItemPageReqVO reqVO = new ProjectWorkItemPageReqVO();
        reqVO.setProjectId(1L);
        reqVO.setConditions(List.of(condition));
        return reqVO;
    }

    private ProjectWorkItemPageReqVO.Condition buildCustomCondition(ProjectWorkItemQueryOperatorEnum operator,
                                                                    List<Object> values) {
        WorkItemFieldDO field = new WorkItemFieldDO();
        field.setId(10L);
        field.setFieldType(WorkItemFieldTypeEnum.MULTI_SELECT.getValue());
        ProjectWorkItemPageReqVO.Condition condition = new ProjectWorkItemPageReqVO.Condition();
        condition.setField("custom:10");
        condition.setCustomField(field);
        condition.setOperatorEnum(operator);
        condition.setParsedValues(values);
        return condition;
    }

    private ProjectWorkItemPageReqVO.Condition buildScalarCondition(ProjectWorkItemQueryFieldEnum field,
                                                                    Object value) {
        ProjectWorkItemPageReqVO.Condition condition = new ProjectWorkItemPageReqVO.Condition();
        condition.setField(field.getField());
        condition.setFieldEnum(field);
        condition.setOperatorEnum(ProjectWorkItemQueryOperatorEnum.EQ);
        condition.setParsedValue(value);
        return condition;
    }

    private ProjectWorkItemPageReqVO.ConditionGroup buildGroup(ProjectWorkItemPageReqVO.Condition... conditions) {
        ProjectWorkItemPageReqVO.ConditionGroup group = new ProjectWorkItemPageReqVO.ConditionGroup();
        group.setConditions(List.of(conditions));
        return group;
    }

}
