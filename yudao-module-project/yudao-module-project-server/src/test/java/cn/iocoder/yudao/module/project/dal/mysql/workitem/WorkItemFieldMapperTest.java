package cn.iocoder.yudao.module.project.dal.mysql.workitem;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class WorkItemFieldMapperTest extends BaseMockitoUnitTest {

    private WorkItemFieldMapper mapper;
    private ArgumentCaptor<Wrapper<WorkItemFieldDO>> wrapperCaptor;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(WorkItemFieldMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, WorkItemFieldDO.class);
        mapper = mock(WorkItemFieldMapper.class, CALLS_REAL_METHODS);
        wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        doAnswer(invocation -> List.of()).when(mapper).selectList(wrapperCaptor.capture());
    }

    @Test
    void testSelectListByEffectiveProjectId_globalOrProject() {
        mapper.selectListByEffectiveProjectId(1L);

        String sql = wrapperCaptor.getValue().getCustomSqlSegment();
        assertTrue(sql.contains("scope"));
        assertTrue(sql.contains("project_id"));
        assertTrue(sql.contains(" OR "));
        assertTrue(sql.contains("ORDER BY sort ASC,id ASC"));
    }

}
