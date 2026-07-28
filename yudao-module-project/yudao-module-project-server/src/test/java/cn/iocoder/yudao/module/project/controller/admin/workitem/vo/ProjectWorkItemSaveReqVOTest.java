package cn.iocoder.yudao.module.project.controller.admin.workitem.vo;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
