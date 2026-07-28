package cn.iocoder.yudao.module.project.dal.mysql.worklog;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogSummaryReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.worklog.SprintWorklogSummaryResult;
import cn.iocoder.yudao.module.project.dal.dataobject.worklog.WorklogDO;
import cn.iocoder.yudao.module.project.dal.dataobject.worklog.WorklogSummaryResult;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.ProjectWorkItemMapper;
import cn.iocoder.yudao.module.project.enums.WorklogGroupByEnum;
import cn.iocoder.yudao.module.project.enums.WorklogTypeEnum;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
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
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorklogMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:project_worklog;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("CREATE TABLE proj_worklog (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, "
                    + "project_id BIGINT NOT NULL, work_item_id BIGINT NOT NULL, type VARCHAR(32) NOT NULL, "
                    + "hours DECIMAL(10,2) NOT NULL, category VARCHAR(32) NOT NULL, description VARCHAR(1000), "
                    + "owner_user_id BIGINT NOT NULL, work_date DATE, deleted TINYINT NOT NULL DEFAULT 0, "
                    + "creator VARCHAR(64) DEFAULT '', create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "updater VARCHAR(64) DEFAULT '', update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            statement.execute("CREATE TABLE proj_work_item (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, "
                    + "project_id BIGINT NOT NULL, sprint_id BIGINT, parent_work_item_id BIGINT, "
                    + "estimated_hours DECIMAL(20,2) NOT NULL DEFAULT 0, actual_hours DECIMAL(20,2) NOT NULL DEFAULT 0, "
                    + "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0)");
            statement.execute("INSERT INTO proj_work_item (id,tenant_id,project_id,sprint_id,parent_work_item_id,deleted) VALUES "
                    + "(1,1,100,7,NULL,0),(2,1,100,NULL,1,0),(3,1,200,8,NULL,0),(4,1,100,NULL,2,0),"
                    + "(5,1,200,NULL,2,0),(6,1,100,NULL,7,0),(7,1,100,NULL,6,0),(8,1,100,NULL,NULL,0),"
                    + "(9,2,100,NULL,2,0),(10,1,100,NULL,2,1),(11,1,100,NULL,10,0)");
            statement.execute("INSERT INTO proj_worklog "
                    + "(id,tenant_id,project_id,work_item_id,type,hours,category,description,owner_user_id,work_date,deleted) VALUES "
                    + "(1,1,100,1,'ESTIMATED',3.00,'DESIGN',NULL,10,NULL,0),"
                    + "(2,1,100,1,'ACTUAL',2.50,'DEVELOPMENT',NULL,10,DATE '2026-07-01',0),"
                    + "(3,1,100,2,'ACTUAL',4.00,'DEVELOPMENT',NULL,11,DATE '2026-07-02',0),"
                    + "(4,1,100,1,'ACTUAL',9.00,'TESTING',NULL,10,DATE '2026-07-03',1),"
                    + "(5,1,200,3,'ACTUAL',8.00,'DESIGN',NULL,12,DATE '2026-07-01',0),"
                    + "(6,1,100,4,'ACTUAL',1.50,'DESIGN',NULL,12,DATE '2026-07-03',0)");
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        configuration.addInterceptor(interceptor);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(WorklogMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, WorklogDO.class);
        MapperBuilderAssistant workItemAssistant = new MapperBuilderAssistant(configuration, "");
        workItemAssistant.setCurrentNamespace(ProjectWorkItemMapper.class.getName());
        TableInfoHelper.initTableInfo(workItemAssistant, ProjectWorkItemDO.class);
        try (InputStream input = Resources.getResourceAsStream("mapper/worklog/WorklogMapper.xml")) {
            new XMLMapperBuilder(input, configuration, "mapper/worklog/WorklogMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        try (InputStream input = Resources.getResourceAsStream("mapper/workitem/ProjectWorkItemMapper.xml")) {
            new XMLMapperBuilder(input, configuration, "mapper/workitem/ProjectWorkItemMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    void testPageAppliesAllFiltersAndPagination() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            WorklogMapper mapper = session.getMapper(WorklogMapper.class);
            WorklogPageReqVO reqVO = new WorklogPageReqVO();
            reqVO.setProjectId(100L);
            reqVO.setType("ACTUAL");
            reqVO.setCategory("DEVELOPMENT");
            reqVO.setWorkDateStart(LocalDate.of(2026, 7, 1));
            reqVO.setWorkDateEnd(LocalDate.of(2026, 7, 2));
            reqVO.setPageNo(1);
            reqVO.setPageSize(1);

            PageResult<WorklogDO> page = mapper.selectPage(reqVO);
            assertEquals(2L, page.getTotal());
            assertEquals(List.of(3L), page.getList().stream().map(WorklogDO::getId).toList());

            reqVO.setSprintId(7L);
            reqVO.setWorkItemId(1L);
            reqVO.setOwnerUserId(10L);
            PageResult<WorklogDO> filteredPage = mapper.selectPage(reqVO);
            assertEquals(1L, filteredPage.getTotal());
            assertEquals(List.of(2L), filteredPage.getList().stream().map(WorklogDO::getId).toList());
        }
    }

    @Test
    void testTotalsFiltersAndAllGroupDimensions() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            WorklogMapper mapper = session.getMapper(WorklogMapper.class);
            WorklogSummaryReqVO reqVO = new WorklogSummaryReqVO();
            reqVO.setProjectId(100L);
            LambdaQueryWrapperX<WorklogDO> wrapper = mapper.buildQueryWrapper(reqVO);

            WorklogSummaryResult total = mapper.selectSummary(wrapper);
            assertHours(total, "3.00", "8.00");

            for (WorklogGroupByEnum groupBy : WorklogGroupByEnum.values()) {
                List<WorklogSummaryResult> groups = mapper.selectGroupSummary(wrapper, groupBy);
                int expectedGroupCount = switch (groupBy) {
                    case WORK_ITEM, OWNER -> 3;
                    case CATEGORY, SPRINT -> 2;
                };
                assertEquals(expectedGroupCount, groups.size());
            }
            List<WorklogSummaryResult> sprintGroups = mapper.selectGroupSummary(wrapper, WorklogGroupByEnum.SPRINT);
            assertEquals("7", sprintGroups.get(0).getValue());
            assertNull(sprintGroups.get(1).getValue());

            reqVO.setWorkDateStart(LocalDate.of(2026, 7, 2));
            WorklogSummaryResult dateTotal = mapper.selectSummary(mapper.buildQueryWrapper(reqVO));
            assertHours(dateTotal, "0.00", "5.50");
        }
    }

    @Test
    void testSprintActualAggregation() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            WorklogMapper mapper = session.getMapper(WorklogMapper.class);
            List<SprintWorklogSummaryResult> sprints = mapper.selectSprintActualHours(100L);
            assertEquals(1, sprints.size());
            assertEquals(7L, sprints.get(0).getSprintId());
            assertEquals(new BigDecimal("2.50"), sprints.get(0).getActualHours());
        }
    }

    @Test
    void testDynamicAggregatesPreserveLargeTotals() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("INSERT INTO proj_worklog "
                    + "(id,tenant_id,project_id,work_item_id,type,hours,category,owner_user_id,work_date,deleted) VALUES "
                    + "(7,1,100,1,'ACTUAL',99999999.99,'DEVELOPMENT',10,DATE '2026-07-04',0),"
                    + "(8,1,100,1,'ACTUAL',99999999.99,'DEVELOPMENT',10,DATE '2026-07-05',0)");
            WorklogMapper mapper = session.getMapper(WorklogMapper.class);
            WorklogSummaryReqVO reqVO = new WorklogSummaryReqVO();
            reqVO.setProjectId(100L);

            assertHours(mapper.selectSummary(mapper.buildQueryWrapper(reqVO)), "3.00", "200000007.98");
            List<SprintWorklogSummaryResult> sprints = mapper.selectSprintActualHours(100L);
            assertEquals(List.of(7L), sprints.stream().map(SprintWorklogSummaryResult::getSprintId).toList());
            assertEquals(new BigDecimal("200000002.48"), sprints.get(0).getActualHours());
        }
    }

    @Test
    void testMaterializedWorkItemCountersUseAtomicDeltas() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            ProjectWorkItemMapper mapper = session.getMapper(ProjectWorkItemMapper.class);
            assertEquals(1, mapper.addWorklogHours(1L, WorklogTypeEnum.ESTIMATED, new BigDecimal("3.00")));
            assertEquals(1, mapper.addWorklogHours(1L, WorklogTypeEnum.ESTIMATED,
                    new BigDecimal("99999999.99")));
            assertEquals(1, mapper.addWorklogHours(1L, WorklogTypeEnum.ACTUAL, new BigDecimal("2.50")));
            assertEquals(1, mapper.addWorklogHours(1L, WorklogTypeEnum.ACTUAL, new BigDecimal("-0.75")));

            try (Statement statement = session.getConnection().createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT estimated_hours, actual_hours FROM proj_work_item WHERE id = 1")) {
                assertTrue(resultSet.next());
                assertEquals(new BigDecimal("100000002.99"), resultSet.getBigDecimal("estimated_hours"));
                assertEquals(new BigDecimal("1.75"), resultSet.getBigDecimal("actual_hours"));
            }
        }
    }

    @Test
    void testDescendantCteIsRecursiveCycleSafeAndProjectBound() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            ProjectWorkItemMapper mapper = session.getMapper(ProjectWorkItemMapper.class);
            List<Long> descendants = mapper.selectDescendantIds(1L, 100L, 1L);
            assertEquals(List.of(2L, 4L), descendants);
            assertFalse(descendants.contains(5L)); // Broken cross-project parent link must not leak.
            assertFalse(descendants.contains(9L)); // A different tenant must not enter the range.
            assertFalse(descendants.contains(10L)); // Deleted descendants and their subtree stay invisible.
            assertFalse(descendants.contains(11L));

            List<Long> cycleDescendants = mapper.selectDescendantIds(6L, 100L, 1L);
            assertEquals(List.of(7L), cycleDescendants);
            assertFalse(cycleDescendants.contains(6L));
            assertEquals(List.of(), mapper.selectDescendantIds(8L, 100L, 1L));
        }
    }

    @Test
    void testDescendantScopeSharesFiltersPaginationAndAllSummaries() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            WorklogMapper mapper = session.getMapper(WorklogMapper.class);
            ProjectWorkItemMapper workItemMapper = session.getMapper(ProjectWorkItemMapper.class);
            WorklogPageReqVO pageReqVO = new WorklogPageReqVO();
            pageReqVO.setProjectId(100L);
            pageReqVO.setWorkItemId(1L);
            pageReqVO.setWorkItemScope("DESCENDANTS");
            pageReqVO.setType("ACTUAL");
            pageReqVO.setPageNo(1);
            pageReqVO.setPageSize(1);
            List<Long> descendantIds = workItemMapper.selectDescendantIds(1L, 100L, 1L);
            PageResult<WorklogDO> page = mapper.selectPage(pageReqVO,
                    mapper.buildQueryWrapper(pageReqVO, descendantIds));
            assertEquals(2L, page.getTotal());
            assertEquals(List.of(6L), page.getList().stream().map(WorklogDO::getId).toList());

            LambdaQueryWrapperX<WorklogDO> summaryWrapper = mapper.buildQueryWrapper(pageReqVO, descendantIds);
            WorklogSummaryResult summary = mapper.selectSummary(summaryWrapper);
            assertHours(summary, "0.00", "5.50");
            for (WorklogGroupByEnum groupBy : WorklogGroupByEnum.values()) {
                List<WorklogSummaryResult> groups = mapper.selectGroupSummary(summaryWrapper, groupBy);
                assertEquals(groupBy == WorklogGroupByEnum.SPRINT ? 1 : 2, groups.size());
                assertEquals(new BigDecimal("5.50"), groups.stream().map(WorklogSummaryResult::getActualHours)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
            }

            pageReqVO.setCategory("DEVELOPMENT");
            PageResult<WorklogDO> filteredPage = mapper.selectPage(pageReqVO,
                    mapper.buildQueryWrapper(pageReqVO, descendantIds));
            assertEquals(1L, filteredPage.getTotal());
            assertEquals(List.of(3L), filteredPage.getList().stream().map(WorklogDO::getId).toList());
            WorklogSummaryResult filteredSummary = mapper.selectSummary(
                    mapper.buildQueryWrapper(pageReqVO, descendantIds));
            assertHours(filteredSummary, "0.00", "4.00");
        }
    }

    private void assertHours(WorklogSummaryResult result, String estimated, String actual) {
        assertEquals(new BigDecimal(estimated), result.getEstimatedHours());
        assertEquals(new BigDecimal(actual), result.getActualHours());
    }

}
