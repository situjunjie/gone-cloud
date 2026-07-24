package cn.iocoder.yudao.module.system.dal.mysql.logger;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.api.logger.dto.OperateLogPageReqDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.logger.OperateLogDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class OperateLogMapperTest extends BaseMockitoUnitTest {

    private OperateLogMapper mapper;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(OperateLogMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, OperateLogDO.class);
        mapper = mock(OperateLogMapper.class, CALLS_REAL_METHODS);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testSelectPage_defaultOrderRemainsIdDesc() {
        Wrapper<OperateLogDO> wrapper = captureWrapper(new OperateLogPageReqDTO());

        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("ORDER BY id DESC"), sql);
        assertTrue(!sql.contains("create_time"), sql);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testSelectTimelinePage_usesCreateTimeAndIdDesc() {
        Wrapper<OperateLogDO> wrapper = captureTimelineWrapper(new OperateLogPageReqDTO());

        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("ORDER BY create_time DESC,id DESC"), sql);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Wrapper<OperateLogDO> captureWrapper(OperateLogPageReqDTO reqDTO) {
        ArgumentCaptor<IPage<OperateLogDO>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
        ArgumentCaptor<Wrapper<OperateLogDO>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        lenient().doReturn(null).when(mapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());

        mapper.selectPage(reqDTO);

        return wrapperCaptor.getValue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Wrapper<OperateLogDO> captureTimelineWrapper(OperateLogPageReqDTO reqDTO) {
        ArgumentCaptor<IPage<OperateLogDO>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
        ArgumentCaptor<Wrapper<OperateLogDO>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        lenient().doReturn(null).when(mapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());

        mapper.selectTimelinePage(reqDTO);

        return wrapperCaptor.getValue();
    }

}
