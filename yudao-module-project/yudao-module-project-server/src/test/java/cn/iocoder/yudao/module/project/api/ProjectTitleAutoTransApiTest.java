package cn.iocoder.yudao.module.project.api;

import cn.iocoder.yudao.module.project.api.sprint.ProjectSprintApi;
import cn.iocoder.yudao.module.project.api.sprint.ProjectSprintApiImpl;
import cn.iocoder.yudao.module.project.api.sprint.dto.ProjectSprintTitleRespDTO;
import cn.iocoder.yudao.module.project.api.workitem.ProjectWorkItemApi;
import cn.iocoder.yudao.module.project.api.workitem.ProjectWorkItemApiImpl;
import cn.iocoder.yudao.module.project.api.workitem.dto.ProjectWorkItemTitleRespDTO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectSprintDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectSprintMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.ProjectWorkItemMapper;
import org.dromara.core.trans.anno.AutoTrans;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectTitleAutoTransApiTest {

    @Test
    void testAutoTransProvidersSupportSingleAndBatchQueries() {
        ProjectSprintMapper projectSprintMapper = mock(ProjectSprintMapper.class);
        ProjectWorkItemMapper projectWorkItemMapper = mock(ProjectWorkItemMapper.class);
        ProjectSprintDO sprint = new ProjectSprintDO();
        sprint.setId(10L);
        sprint.setTitle("第 1 迭代");
        ProjectWorkItemDO workItem = new ProjectWorkItemDO();
        workItem.setId(20L);
        workItem.setTitle("父工作项");
        when(projectSprintMapper.selectByIds(List.of(10L))).thenReturn(List.of(sprint));
        when(projectSprintMapper.selectById(10L)).thenReturn(sprint);
        when(projectWorkItemMapper.selectByIds(List.of(20L))).thenReturn(List.of(workItem));
        when(projectWorkItemMapper.selectById(20L)).thenReturn(workItem);

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("projectSprintMapper", ProjectSprintMapper.class, () -> projectSprintMapper);
            context.registerBean("projectWorkItemMapper", ProjectWorkItemMapper.class, () -> projectWorkItemMapper);
            context.register(ProjectSprintApiImpl.class, ProjectWorkItemApiImpl.class);
            context.refresh();

            Map<String, Object> providers = context.getBeansWithAnnotation(AutoTrans.class);
            assertTrue(providers.containsValue(context.getBean(ProjectSprintApi.class)));
            assertTrue(providers.containsValue(context.getBean(ProjectWorkItemApi.class)));

            ProjectSprintTitleRespDTO sprintBatch = context.getBean(ProjectSprintApi.class)
                    .selectByIds(List.of("10")).get(0);
            ProjectSprintTitleRespDTO sprintSingle = context.getBean(ProjectSprintApi.class).selectById("10");
            assertEquals(10L, sprintBatch.getId());
            assertEquals("第 1 迭代", sprintSingle.getTitle());

            ProjectWorkItemTitleRespDTO workItemBatch = context.getBean(ProjectWorkItemApi.class)
                    .selectByIds(List.of("20")).get(0);
            ProjectWorkItemTitleRespDTO workItemSingle = context.getBean(ProjectWorkItemApi.class).selectById("20");
            assertEquals(20L, workItemBatch.getId());
            assertEquals("父工作项", workItemSingle.getTitle());
        }
    }

}
