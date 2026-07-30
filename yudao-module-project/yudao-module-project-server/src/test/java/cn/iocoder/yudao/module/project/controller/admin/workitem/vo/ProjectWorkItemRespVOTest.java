package cn.iocoder.yudao.module.project.controller.admin.workitem.vo;

import cn.iocoder.yudao.module.project.api.sprint.ProjectSprintApi;
import cn.iocoder.yudao.module.project.api.workitem.ProjectWorkItemApi;
import org.dromara.core.trans.anno.AutoTrans;
import org.dromara.core.trans.anno.Trans;
import org.dromara.core.trans.vo.VO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectWorkItemRespVOTest {

    @Test
    void testRelatedTitleTranslationContract() throws NoSuchFieldException {
        assertTrue(VO.class.isAssignableFrom(ProjectWorkItemRespVO.class));

        Trans sprintTrans = ProjectWorkItemRespVO.class.getDeclaredField("sprintId").getAnnotation(Trans.class);
        assertNotNull(sprintTrans);
        assertEquals(ProjectSprintApi.PREFIX, sprintTrans.key());
        assertArrayEquals(new String[]{"title"}, sprintTrans.fields());
        assertEquals("sprintTitle", sprintTrans.ref());

        Trans parentWorkItemTrans = ProjectWorkItemRespVO.class.getDeclaredField("parentWorkItemId")
                .getAnnotation(Trans.class);
        assertNotNull(parentWorkItemTrans);
        assertEquals(ProjectWorkItemApi.PREFIX, parentWorkItemTrans.key());
        assertArrayEquals(new String[]{"title"}, parentWorkItemTrans.fields());
        assertEquals("parentWorkItemTitle", parentWorkItemTrans.ref());

        AutoTrans sprintAutoTrans = ProjectSprintApi.class.getAnnotation(AutoTrans.class);
        assertNotNull(sprintAutoTrans);
        assertArrayEquals(new String[]{"title"}, sprintAutoTrans.fields());

        AutoTrans workItemAutoTrans = ProjectWorkItemApi.class.getAnnotation(AutoTrans.class);
        assertNotNull(workItemAutoTrans);
        assertArrayEquals(new String[]{"title"}, workItemAutoTrans.fields());
    }

}
