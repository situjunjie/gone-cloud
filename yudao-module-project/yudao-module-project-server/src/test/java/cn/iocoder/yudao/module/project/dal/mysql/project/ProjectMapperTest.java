package cn.iocoder.yudao.module.project.dal.mysql.project;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectPageReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class ProjectMapperTest extends BaseMockitoUnitTest {

    private ProjectMapper mapper;
    private ArgumentCaptor<Wrapper<ProjectDO>> wrapperCaptor;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(ProjectMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, ProjectDO.class);
        mapper = mock(ProjectMapper.class, CALLS_REAL_METHODS);
        ArgumentCaptor<IPage<ProjectDO>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
        wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        lenient().doReturn(null).when(mapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
    }

    @Test
    void testProjectPageAlwaysFiltersCurrentMember() {
        mapper.selectPage(new ProjectPageReqVO(), 7L);

        Wrapper<ProjectDO> wrapper = wrapperCaptor.getValue();
        assertTrue(wrapper.getSqlSegment().contains("EXISTS (SELECT 1 FROM proj_project_member pm"));
        assertTrue(wrapper.getSqlSegment().contains("pm.user_id"));
        assertTrue(((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().containsValue(7L));
    }

}
