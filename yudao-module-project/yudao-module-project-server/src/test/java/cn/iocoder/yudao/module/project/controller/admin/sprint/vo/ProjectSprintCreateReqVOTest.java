package cn.iocoder.yudao.module.project.controller.admin.sprint.vo;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectSprintCreateReqVOTest {

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
    void testRequiredFields() {
        ProjectSprintCreateReqVO reqVO = new ProjectSprintCreateReqVO();

        assertEquals(Set.of("项目编号不能为空", "迭代名称不能为空", "负责人用户编号不能为空"),
                messages(reqVO));
    }

    @Test
    void testCapacityAllowsOneDecimalPlace() {
        ProjectSprintCreateReqVO reqVO = validReqVO();
        reqVO.setCapacityHours(new BigDecimal("7.5"));
        assertTrue(validator.validate(reqVO).isEmpty());

        reqVO.setCapacityHours(new BigDecimal("7.55"));
        assertEquals(Set.of("工时容量最多保留 1 位小数"), messages(reqVO));
    }

    @Test
    void testOptionalFieldsMayBeNull() {
        assertTrue(validator.validate(validReqVO()).isEmpty());
    }

    @Test
    void testTextLengthLimits() {
        ProjectSprintCreateReqVO reqVO = validReqVO();
        reqVO.setTitle("x".repeat(51));
        reqVO.setDescription("x".repeat(501));

        assertEquals(Set.of("迭代名称长度不能超过 50 个字符", "迭代描述长度不能超过 500 个字符"),
                messages(reqVO));
    }

    private ProjectSprintCreateReqVO validReqVO() {
        ProjectSprintCreateReqVO reqVO = new ProjectSprintCreateReqVO();
        reqVO.setProjectId(1L);
        reqVO.setTitle("第 1 迭代");
        reqVO.setOwnerUserId(2L);
        return reqVO;
    }

    private Set<String> messages(ProjectSprintCreateReqVO reqVO) {
        return validator.validate(reqVO).stream().map(item -> item.getMessage())
                .collect(java.util.stream.Collectors.toSet());
    }

}
