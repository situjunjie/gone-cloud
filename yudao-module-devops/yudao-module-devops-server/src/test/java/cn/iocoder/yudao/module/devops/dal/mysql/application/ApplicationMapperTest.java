package cn.iocoder.yudao.module.devops.dal.mysql.application;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationPageReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * {@link ApplicationMapper} 的分页 Wrapper 单元测试。
 */
class ApplicationMapperTest extends BaseMockitoUnitTest {

    private ApplicationMapper mapper;
    private ArgumentCaptor<Wrapper<ApplicationDO>> wrapperCaptor;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(ApplicationMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, ApplicationDO.class);
        mapper = mock(ApplicationMapper.class, CALLS_REAL_METHODS);
        ArgumentCaptor<IPage<ApplicationDO>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
        wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        lenient().doReturn(null).when(mapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
    }

    @Test
    void testSelectPage_mineFiltersOwnerOrMember() {
        // 准备参数
        ApplicationPageReqVO reqVO = new ApplicationPageReqVO();
        reqVO.setMemberUserId(7L);

        // 调用
        mapper.selectPage(reqVO);

        // 断言：拥有者与成员行取并集，且 userId 作为绑定参数而不是拼接字符串
        Wrapper<ApplicationDO> wrapper = wrapperCaptor.getValue();
        String sqlSegment = wrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("dev_application.owner_user_id"));
        assertTrue(sqlSegment.contains("EXISTS (SELECT 1 FROM dev_application_member am"));
        assertTrue(sqlSegment.contains("am.deleted = 0"));
        assertTrue(((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().containsValue(7L));
    }

    @Test
    void testSelectPage_withoutMineDoesNotFilterMembership() {
        // 调用：不传 memberUserId 时保持既有分页行为
        mapper.selectPage(new ApplicationPageReqVO());

        // 断言
        Wrapper<ApplicationDO> wrapper = wrapperCaptor.getValue();
        assertFalse(wrapper.getSqlSegment().contains("dev_application_member"));
        assertFalse(wrapper.getSqlSegment().contains("owner_user_id"));
    }

    @Test
    void testSelectPage_mineKeepsOtherFilters() {
        // 准备参数
        ApplicationPageReqVO reqVO = new ApplicationPageReqVO();
        reqVO.setMemberUserId(7L);
        reqVO.setName("Gone");
        reqVO.setStatus(0);

        // 调用
        mapper.selectPage(reqVO);

        // 断言：成员过滤不影响既有筛选条件
        Wrapper<ApplicationDO> wrapper = wrapperCaptor.getValue();
        String sqlSegment = wrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("name"));
        assertTrue(sqlSegment.contains("status"));
        assertTrue(sqlSegment.contains("dev_application_member"));
        assertEquals(3, ((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().size());
    }

}
