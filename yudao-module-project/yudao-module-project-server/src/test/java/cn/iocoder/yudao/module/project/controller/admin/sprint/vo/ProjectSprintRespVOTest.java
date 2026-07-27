package cn.iocoder.yudao.module.project.controller.admin.sprint.vo;

import cn.iocoder.yudao.module.project.convert.project.ProjectSprintConvert;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectSprintDO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.dromara.core.trans.anno.Trans;
import org.dromara.core.trans.vo.VO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectSprintRespVOTest {

    @Test
    void testConvertSprintResponse() {
        ProjectSprintDO sprint = new ProjectSprintDO();
        sprint.setId(10L);
        sprint.setProjectId(1L);
        sprint.setTitle("第 1 迭代");
        sprint.setStatus("NOT_STARTED");
        sprint.setOwnerUserId(2L);
        sprint.setPlannedStartTime(LocalDate.of(2026, 7, 27).atStartOfDay());
        sprint.setPlannedEndTime(LocalDate.of(2026, 8, 9).atStartOfDay());
        sprint.setCapacityHours(new BigDecimal("7.5"));
        sprint.setDescription("完成核心流程联调");

        ProjectSprintRespVO respVO = ProjectSprintConvert.INSTANCE.convert(sprint);

        assertEquals(10L, respVO.getId());
        assertEquals(1L, respVO.getProjectId());
        assertEquals("第 1 迭代", respVO.getTitle());
        assertEquals("NOT_STARTED", respVO.getStatus());
        assertEquals(2L, respVO.getOwnerUserId());
        assertEquals(LocalDate.of(2026, 7, 27), respVO.getStartDate());
        assertEquals(LocalDate.of(2026, 8, 9), respVO.getEndDate());
        assertEquals(new BigDecimal("7.5"), respVO.getCapacityHours());
        assertEquals("完成核心流程联调", respVO.getDescription());
        assertNull(respVO.getOwnerUserName());
        assertNull(respVO.getOwnerUserAvatar());
        assertNull(respVO.getUsedHours());
        assertNull(respVO.getTotalWorkItemCount());
        assertNull(respVO.getCompletedWorkItemCount());
    }

    @Test
    void testOwnerUserTranslationContract() throws NoSuchFieldException {
        assertTrue(VO.class.isAssignableFrom(ProjectSprintRespVO.class));
        Trans trans = ProjectSprintRespVO.class.getDeclaredField("ownerUserId").getAnnotation(Trans.class);

        assertNotNull(trans);
        assertEquals(AdminUserApi.PREFIX, trans.key());
        assertArrayEquals(new String[]{"nickname", "avatar"}, trans.fields());
        assertArrayEquals(new String[]{"ownerUserName", "ownerUserAvatar"}, trans.refs());
    }

}
