package cn.iocoder.yudao.module.project.dal.mysql.comment;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.project.controller.admin.comment.vo.CommentPageReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.comment.CommentDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CommentMapperTest extends BaseMockitoUnitTest {

    private CommentMapper mapper;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(CommentMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, CommentDO.class);
        mapper = mock(CommentMapper.class, CALLS_REAL_METHODS);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testSelectPage_filtersTargetAndUsesStableDescendingOrder() {
        ArgumentCaptor<IPage<CommentDO>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
        ArgumentCaptor<Wrapper<CommentDO>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        lenient().doReturn(null).when(mapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        CommentPageReqVO reqVO = new CommentPageReqVO();
        reqVO.setTargetType("WORK_ITEM");
        reqVO.setTargetId(300L);

        mapper.selectPage(reqVO, 200L);

        String sql = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("project_id"), sql);
        assertTrue(sql.contains("target_type"), sql);
        assertTrue(sql.contains("target_id"), sql);
        assertTrue(sql.contains("ORDER BY create_time DESC,id DESC"), sql);
        Map<String, Object> parameters = ((AbstractWrapper<?, ?, ?>) wrapperCaptor.getValue()).getParamNameValuePairs();
        assertTrue(parameters.containsValue(200L));
        assertTrue(parameters.containsValue("WORK_ITEM"));
        assertTrue(parameters.containsValue(300L));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testUpdateContent_isConditionalOnNormalStatus() {
        ArgumentCaptor<Wrapper<CommentDO>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        doReturn(1).when(mapper).update(isNull(), wrapperCaptor.capture());

        assertEquals(1, mapper.updateContentByIdAndStatus(1L, "NORMAL", "新正文", LocalDateTime.now()));

        AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) wrapperCaptor.getValue();
        assertTrue(wrapper.getSqlSegment().contains("id"));
        assertTrue(wrapper.getSqlSegment().contains("status"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(1L));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("NORMAL"));
        assertTrue(wrapper.getSqlSet().contains("content"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testMarkDeleted_clearsContentAndChecksCurrentStatus() {
        ArgumentCaptor<Wrapper<CommentDO>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        doReturn(1).when(mapper).update(isNull(), wrapperCaptor.capture());

        mapper.markDeletedByIdAndStatus(1L, "NORMAL", "DELETED", LocalDateTime.now());

        AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) wrapperCaptor.getValue();
        assertTrue(wrapper.getSqlSegment().contains("id"), wrapper.getSqlSegment());
        assertTrue(wrapper.getSqlSegment().contains("status"), wrapper.getSqlSegment());
        assertTrue(wrapper.getParamNameValuePairs().containsValue(1L));
        assertTrue(wrapper.getSqlSet().contains("status"), wrapper.getSqlSet());
        assertTrue(wrapper.getSqlSet().contains("content"), wrapper.getSqlSet());
    }

}
