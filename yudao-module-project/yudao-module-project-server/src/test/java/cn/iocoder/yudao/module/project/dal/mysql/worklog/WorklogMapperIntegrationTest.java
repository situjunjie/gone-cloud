package cn.iocoder.yudao.module.project.dal.mysql.worklog;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogSummaryReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.worklog.SprintWorklogSummaryResult;
import cn.iocoder.yudao.module.project.dal.dataobject.worklog.WorkItemWorklogSummaryResult;
import cn.iocoder.yudao.module.project.dal.dataobject.worklog.WorklogDO;
import cn.iocoder.yudao.module.project.dal.dataobject.worklog.WorklogSummaryResult;
import cn.iocoder.yudao.module.project.enums.WorklogGroupByEnum;
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
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
                    + "project_id BIGINT NOT NULL, sprint_id BIGINT, deleted TINYINT NOT NULL DEFAULT 0)");
            statement.execute("INSERT INTO proj_work_item VALUES (1,1,100,7,0),(2,1,100,NULL,0),(3,1,200,8,0)");
            statement.execute("INSERT INTO proj_worklog "
                    + "(id,tenant_id,project_id,work_item_id,type,hours,category,description,owner_user_id,work_date,deleted) VALUES "
                    + "(1,1,100,1,'ESTIMATED',3.00,'DESIGN',NULL,10,NULL,0),"
                    + "(2,1,100,1,'ACTUAL',2.50,'DEVELOPMENT',NULL,10,DATE '2026-07-01',0),"
                    + "(3,1,100,2,'ACTUAL',4.00,'DEVELOPMENT',NULL,11,DATE '2026-07-02',0),"
                    + "(4,1,100,1,'ACTUAL',9.00,'TESTING',NULL,10,DATE '2026-07-03',1),"
                    + "(5,1,200,3,'ACTUAL',8.00,'DESIGN',NULL,12,DATE '2026-07-01',0)");
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
        try (InputStream input = Resources.getResourceAsStream("mapper/worklog/WorklogMapper.xml")) {
            new XMLMapperBuilder(input, configuration, "mapper/worklog/WorklogMapper.xml",
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
            assertHours(total, "3.00", "6.50");

            for (WorklogGroupByEnum groupBy : WorklogGroupByEnum.values()) {
                List<WorklogSummaryResult> groups = mapper.selectGroupSummary(wrapper, groupBy);
                assertEquals(2, groups.size());
            }
            List<WorklogSummaryResult> sprintGroups = mapper.selectGroupSummary(wrapper, WorklogGroupByEnum.SPRINT);
            assertEquals("7", sprintGroups.get(0).getValue());
            assertNull(sprintGroups.get(1).getValue());

            reqVO.setWorkDateStart(LocalDate.of(2026, 7, 2));
            WorklogSummaryResult dateTotal = mapper.selectSummary(mapper.buildQueryWrapper(reqVO));
            assertHours(dateTotal, "0.00", "4.00");
        }
    }

    @Test
    void testBatchWorkItemAndSprintActualAggregation() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            WorklogMapper mapper = session.getMapper(WorklogMapper.class);
            Map<Long, WorkItemWorklogSummaryResult> workItems = mapper.selectWorkItemSummaries(List.of(1L, 2L))
                    .stream().collect(Collectors.toMap(WorkItemWorklogSummaryResult::getWorkItemId, item -> item));
            assertEquals(new BigDecimal("3.00"), workItems.get(1L).getEstimatedHours());
            assertEquals(new BigDecimal("2.50"), workItems.get(1L).getActualHours());
            assertEquals(new BigDecimal("4.00"), workItems.get(2L).getActualHours());

            List<SprintWorklogSummaryResult> sprints = mapper.selectSprintActualHours(100L);
            assertEquals(1, sprints.size());
            assertEquals(7L, sprints.get(0).getSprintId());
            assertEquals(new BigDecimal("2.50"), sprints.get(0).getActualHours());
        }
    }

    private void assertHours(WorklogSummaryResult result, String estimated, String actual) {
        assertEquals(new BigDecimal(estimated), result.getEstimatedHours());
        assertEquals(new BigDecimal(actual), result.getActualHours());
    }

}
