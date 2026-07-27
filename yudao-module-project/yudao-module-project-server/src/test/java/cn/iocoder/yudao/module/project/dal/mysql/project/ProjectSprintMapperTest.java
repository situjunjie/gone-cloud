package cn.iocoder.yudao.module.project.dal.mysql.project;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectSprintDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class ProjectSprintMapperTest extends BaseMockitoUnitTest {

    private ProjectSprintMapper mapper;
    private ArgumentCaptor<Wrapper<ProjectSprintDO>> wrapperCaptor;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(ProjectSprintMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, ProjectSprintDO.class);
        mapper = mock(ProjectSprintMapper.class, CALLS_REAL_METHODS);
        wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        lenient().doReturn(List.of()).when(mapper).selectList(wrapperCaptor.capture());
    }

    @Test
    void testSelectListByProjectIdFiltersAndSorts() {
        mapper.selectListByProjectId(10L);

        Wrapper<ProjectSprintDO> wrapper = wrapperCaptor.getValue();
        assertTrue(wrapper.getSqlSegment().contains("project_id"));
        assertTrue(wrapper.getSqlSegment().contains("ORDER BY id DESC"));
        assertTrue(((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().containsValue(10L));
    }

}
