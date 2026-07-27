package cn.iocoder.yudao.module.project.dal.mysql.workitem;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemPageReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectSprintWorkItemCountResult;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemGroupCountResult;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemGroupField;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldDO;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemQueryFieldEnum;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemQueryOperatorEnum;
import cn.iocoder.yudao.module.project.enums.WorkItemFieldTypeEnum;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProjectWorkItemGroupCountMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:project_group_count;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("CREATE TABLE proj_work_item ("
                    + "id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL, "
                    + "sprint_id BIGINT, status VARCHAR(32), priority VARCHAR(32), "
                    + "deleted TINYINT NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE proj_work_item_field_value ("
                    + "id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, work_item_id BIGINT NOT NULL, "
                    + "field_id BIGINT NOT NULL, text_value VARCHAR(255), number_value DECIMAL(20, 4), "
                    + "datetime_value TIMESTAMP, boolean_value BOOLEAN, user_value BIGINT, "
                    + "deleted TINYINT NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE proj_work_item_field_value_option ("
                    + "id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, work_item_id BIGINT NOT NULL, "
                    + "field_id BIGINT NOT NULL, option_id BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0)");

            statement.execute("INSERT INTO proj_work_item VALUES "
                    + "(1, 1, 100, 10, 'PENDING', 'HIGH', 0),"
                    + "(2, 1, 100, 10, 'PENDING', 'LOW', 0),"
                    + "(3, 1, 100, 10, 'DONE', 'HIGH', 0),"
                    + "(4, 1, 100, NULL, NULL, 'HIGH', 0),"
                    + "(5, 1, 200, 20, 'PENDING', 'HIGH', 0),"
                    + "(6, 1, 100, 10, 'PENDING', 'HIGH', 1),"
                    + "(7, 1, 300, 30, 'PENDING', 'HIGH', 0),"
                    + "(8, 1, 300, 30, 'COMPLETED', 'LOW', 0),"
                    + "(9, 1, 300, 30, 'COMPLETED', 'HIGH', 1),"
                    + "(10, 1, 300, 31, 'COMPLETED', 'HIGH', 0),"
                    + "(11, 1, 301, 30, 'COMPLETED', 'HIGH', 0)");
            statement.execute("INSERT INTO proj_work_item_field_value VALUES "
                    + "(11, 1, 1, 10, 'A', NULL, NULL, NULL, NULL, 0),"
                    + "(12, 1, 2, 10, 'A', NULL, NULL, NULL, NULL, 0),"
                    + "(13, 1, 3, 10, 'B', NULL, NULL, NULL, NULL, 0),"
                    + "(14, 1, 5, 10, 'A', NULL, NULL, NULL, NULL, 0),"
                    + "(15, 1, 6, 10, 'A', NULL, NULL, NULL, NULL, 0)");
            statement.execute("INSERT INTO proj_work_item_field_value_option VALUES "
                    + "(21, 1, 1, 20, 101, 0),"
                    + "(22, 1, 1, 20, 102, 0),"
                    + "(23, 1, 2, 20, 101, 0),"
                    + "(24, 1, 3, 20, 102, 0),"
                    + "(25, 1, 3, 20, 102, 0),"
                    + "(26, 1, 5, 20, 101, 0),"
                    + "(27, 1, 6, 20, 101, 0)");
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(ProjectWorkItemMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, ProjectWorkItemDO.class);
        try (InputStream input = Resources.getResourceAsStream("mapper/workitem/ProjectWorkItemMapper.xml")) {
            new XMLMapperBuilder(input, configuration, "mapper/workitem/ProjectWorkItemMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    void testGroupCount_executesFixedScalarOptionNullAndComplexFilters() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            ProjectWorkItemMapper mapper = session.getMapper(ProjectWorkItemMapper.class);
            ProjectWorkItemPageReqVO projectQuery = new ProjectWorkItemPageReqVO();
            projectQuery.setProjectId(100L);

            List<ProjectWorkItemGroupCountResult> fixed = mapper.selectGroupCount(
                    mapper.buildQueryWrapper(projectQuery),
                    ProjectWorkItemGroupField.fixed(ProjectWorkItemQueryFieldEnum.STATUS));
            assertGroup(fixed.get(0), "PENDING", 2L, String.class);
            assertGroup(fixed.get(1), "DONE", 1L, String.class);
            assertNull(fixed.get(2).getValue());
            assertEquals(1L, fixed.get(2).getCount());

            List<ProjectWorkItemGroupCountResult> scalar = mapper.selectGroupCount(
                    mapper.buildQueryWrapper(projectQuery),
                    ProjectWorkItemGroupField.customScalar(WorkItemFieldTypeEnum.TEXT, 10L));
            assertGroup(scalar.get(0), "A", 2L, String.class);
            assertGroup(scalar.get(1), "B", 1L, String.class);
            assertNull(scalar.get(2).getValue());
            assertEquals(1L, scalar.get(2).getCount());

            List<ProjectWorkItemGroupCountResult> options = mapper.selectGroupCount(
                    mapper.buildQueryWrapper(projectQuery), ProjectWorkItemGroupField.customOption(20L));
            assertGroup(options.get(0), 101L, 2L, Long.class);
            assertGroup(options.get(1), 102L, 2L, Long.class);
            assertNull(options.get(2).getValue());
            assertEquals(1L, options.get(2).getCount());

            ProjectWorkItemPageReqVO complexQuery = new ProjectWorkItemPageReqVO();
            complexQuery.setProjectId(100L);
            complexQuery.setConditionGroups(List.of(
                    buildGroup(buildCondition(ProjectWorkItemQueryFieldEnum.STATUS, "PENDING")),
                    buildGroup(buildCondition(ProjectWorkItemQueryFieldEnum.STATUS, "DONE"),
                            buildCondition(ProjectWorkItemQueryFieldEnum.PRIORITY, "HIGH"))));
            List<ProjectWorkItemGroupCountResult> complex = mapper.selectGroupCount(
                    mapper.buildQueryWrapper(complexQuery),
                    ProjectWorkItemGroupField.fixed(ProjectWorkItemQueryFieldEnum.PRIORITY));
            assertGroup(complex.get(0), "HIGH", 2L, String.class);
            assertGroup(complex.get(1), "LOW", 1L, String.class);

            ProjectWorkItemPageReqVO customFilterQuery = new ProjectWorkItemPageReqVO();
            customFilterQuery.setProjectId(100L);
            customFilterQuery.setConditions(List.of(buildCustomTextCondition(10L, "A")));
            List<ProjectWorkItemGroupCountResult> customFiltered = mapper.selectGroupCount(
                    mapper.buildQueryWrapper(customFilterQuery),
                    ProjectWorkItemGroupField.fixed(ProjectWorkItemQueryFieldEnum.STATUS));
            assertEquals(1, customFiltered.size());
            assertGroup(customFiltered.get(0), "PENDING", 2L, String.class);
        }
    }

    @Test
    void testSprintWorkItemCounts_filtersProjectAndDeletedRows() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            ProjectWorkItemMapper mapper = session.getMapper(ProjectWorkItemMapper.class);

            List<ProjectSprintWorkItemCountResult> result = mapper.selectSprintWorkItemCounts(300L, "COMPLETED");

            assertEquals(2, result.size());
            Map<Long, ProjectSprintWorkItemCountResult> resultMap = result.stream().collect(
                    Collectors.toMap(ProjectSprintWorkItemCountResult::getSprintId, item -> item));
            assertSprintCount(resultMap.get(30L), 30L, 2L, 1L);
            assertSprintCount(resultMap.get(31L), 31L, 1L, 1L);
        }
    }

    private void assertGroup(ProjectWorkItemGroupCountResult result, Object value, Long count, Class<?> valueType) {
        assertEquals(value, result.getValue());
        assertEquals(count, result.getCount());
        assertInstanceOf(valueType, result.getValue());
    }

    private void assertSprintCount(ProjectSprintWorkItemCountResult result, Long sprintId,
                                   Long totalCount, Long completedCount) {
        assertEquals(sprintId, result.getSprintId());
        assertEquals(totalCount, result.getTotalCount());
        assertEquals(completedCount, result.getCompletedCount());
    }

    private ProjectWorkItemPageReqVO.Condition buildCondition(ProjectWorkItemQueryFieldEnum field, Object value) {
        ProjectWorkItemPageReqVO.Condition condition = new ProjectWorkItemPageReqVO.Condition();
        condition.setField(field.getField());
        condition.setFieldEnum(field);
        condition.setOperatorEnum(ProjectWorkItemQueryOperatorEnum.EQ);
        condition.setParsedValue(value);
        return condition;
    }

    private ProjectWorkItemPageReqVO.Condition buildCustomTextCondition(Long fieldId, String value) {
        WorkItemFieldDO field = new WorkItemFieldDO();
        field.setId(fieldId);
        field.setFieldType(WorkItemFieldTypeEnum.TEXT.getValue());
        ProjectWorkItemPageReqVO.Condition condition = new ProjectWorkItemPageReqVO.Condition();
        condition.setField("custom:" + fieldId);
        condition.setCustomField(field);
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
