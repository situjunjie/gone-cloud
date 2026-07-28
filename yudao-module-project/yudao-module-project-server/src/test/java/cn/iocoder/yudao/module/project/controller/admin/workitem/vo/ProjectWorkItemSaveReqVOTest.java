package cn.iocoder.yudao.module.project.controller.admin.workitem.vo;

import cn.iocoder.yudao.module.project.convert.workitem.ProjectWorkItemConvert;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemUpdateFieldEnum;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectWorkItemSaveReqVOTest {

    private static jakarta.validation.ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void testObsoleteWorkItemCategoryIsRejected() {
        ProjectWorkItemSaveReqVO reqVO = validReqVO();
        assertTrue(validator.validate(reqVO).isEmpty());

        reqVO.setCategory("WORK_ITEM");
        assertEquals(java.util.Set.of("category"), validator.validate(reqVO).stream()
                .map(item -> item.getPropertyPath().toString()).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void testWorklogAggregateAndLegacyFieldsAreNotWritable() {
        assertFalse(hasField(ProjectWorkItemSaveReqVO.class, "estimatedWorkload"));
        assertFalse(hasField(ProjectWorkItemSaveReqVO.class, "estimatedHours"));
        assertFalse(hasField(ProjectWorkItemSaveReqVO.class, "actualHours"));
        assertNull(ProjectWorkItemUpdateFieldEnum.of("estimatedWorkload"));
        assertNull(ProjectWorkItemUpdateFieldEnum.of("estimatedHours"));
        assertNull(ProjectWorkItemUpdateFieldEnum.of("actualHours"));

        ProjectWorkItemDO workItem = ProjectWorkItemConvert.INSTANCE.convert(validReqVO());
        assertNull(workItem.getEstimatedHours());
        assertNull(workItem.getActualHours());
        assertTrue(hasField(ProjectWorkItemRespVO.class, "estimatedHours"));
        assertTrue(hasField(ProjectWorkItemRespVO.class, "actualHours"));
        assertFalse(hasField(ProjectWorkItemRespVO.class, "estimatedWorkload"));
    }

    private boolean hasField(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredFields()).anyMatch(field -> field.getName().equals(name));
    }

    private ProjectWorkItemSaveReqVO validReqVO() {
        ProjectWorkItemSaveReqVO reqVO = new ProjectWorkItemSaveReqVO();
        reqVO.setTitle("工作项");
        reqVO.setCategory("TASK");
        reqVO.setPriority("MEDIUM");
        reqVO.setAssigneeUserId(1L);
        reqVO.setProjectId(1L);
        return reqVO;
    }

}
