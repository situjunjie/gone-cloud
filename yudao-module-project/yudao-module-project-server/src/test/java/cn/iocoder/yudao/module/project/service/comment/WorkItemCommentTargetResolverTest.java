package cn.iocoder.yudao.module.project.service.comment;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.ProjectWorkItemMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_COMMENT_TARGET_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class WorkItemCommentTargetResolverTest extends BaseMockitoUnitTest {

    @InjectMocks
    private WorkItemCommentTargetResolver resolver;

    @Mock
    private ProjectWorkItemMapper workItemMapper;
    @Mock
    private ProjectMapper projectMapper;

    @Test
    void testResolve_returnsPersistedProjectAndDisplayFields() {
        ProjectWorkItemDO workItem = new ProjectWorkItemDO();
        workItem.setId(300L);
        workItem.setProjectId(200L);
        workItem.setWorkItemNo("WI-000300");
        workItem.setTitle("评论目标");
        ProjectDO project = new ProjectDO();
        project.setId(200L);
        when(workItemMapper.selectById(300L)).thenReturn(workItem);
        when(projectMapper.selectById(200L)).thenReturn(project);

        CommentTarget target = resolver.resolve(300L);

        assertEquals(300L, target.targetId());
        assertEquals(200L, target.projectId());
        assertEquals("WI-000300", target.displayNo());
        assertEquals("评论目标", target.title());
    }

    @Test
    void testResolve_rejectsMissingWorkItemOrProject() {
        assertServiceException(() -> resolver.resolve(300L), PROJECT_COMMENT_TARGET_NOT_EXISTS);

        ProjectWorkItemDO workItem = new ProjectWorkItemDO();
        workItem.setId(300L);
        workItem.setProjectId(200L);
        when(workItemMapper.selectById(300L)).thenReturn(workItem);
        assertServiceException(() -> resolver.resolve(300L), PROJECT_COMMENT_TARGET_NOT_EXISTS);
    }

}
