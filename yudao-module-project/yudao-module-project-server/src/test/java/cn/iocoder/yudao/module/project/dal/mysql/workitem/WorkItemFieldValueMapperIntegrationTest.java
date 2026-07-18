package cn.iocoder.yudao.module.project.dal.mysql.workitem;

import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldValueDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldValueOptionDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkItemFieldValueMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:work_item_field_value;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("CREATE TABLE proj_work_item_field_value ("
                    + "id BIGINT PRIMARY KEY, work_item_id BIGINT NOT NULL, field_id BIGINT NOT NULL)");
            statement.execute("CREATE TABLE proj_work_item_field_value_option ("
                    + "id BIGINT PRIMARY KEY, field_value_id BIGINT NOT NULL, work_item_id BIGINT NOT NULL, "
                    + "field_id BIGINT NOT NULL, option_id BIGINT NOT NULL)");
            statement.execute("INSERT INTO proj_work_item_field_value VALUES "
                    + "(1, 100, 10), (2, 100, 11), (3, 100, 99), (4, 200, 10)");
            statement.execute("INSERT INTO proj_work_item_field_value_option VALUES "
                    + "(1, 1, 100, 10, 1001), (2, 2, 100, 11, 1002), "
                    + "(3, 3, 100, 99, 1099), (4, 4, 200, 10, 2001)");
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        initTableInfo(configuration, WorkItemFieldValueMapper.class, WorkItemFieldValueDO.class);
        initTableInfo(configuration, WorkItemFieldValueOptionMapper.class, WorkItemFieldValueOptionDO.class);
        configuration.addMapper(WorkItemFieldValueMapper.class);
        configuration.addMapper(WorkItemFieldValueOptionMapper.class);
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    void testPhysicalDeleteByWorkItemIdAndFieldIds_targetsOnly() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            session.getMapper(WorkItemFieldValueOptionMapper.class)
                    .physicalDeleteByWorkItemIdAndFieldIds(100L, List.of(10L, 11L));
            session.getMapper(WorkItemFieldValueMapper.class)
                    .physicalDeleteByWorkItemIdAndFieldIds(100L, List.of(10L, 11L));
            session.commit();

            assertEquals(1L, count(session, "proj_work_item_field_value", 100L));
            assertEquals(1L, count(session, "proj_work_item_field_value_option", 100L));
            assertEquals(1L, count(session, "proj_work_item_field_value", 200L));
            assertEquals(1L, count(session, "proj_work_item_field_value_option", 200L));
        }
    }

    private void initTableInfo(MybatisConfiguration configuration, Class<?> mapperType, Class<?> entityType) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(mapperType.getName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }

    private long count(SqlSession session, String tableName, Long workItemId) throws Exception {
        try (Statement statement = session.getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM " + tableName + " WHERE work_item_id = " + workItemId)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

}
