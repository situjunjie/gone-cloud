package cn.iocoder.yudao.module.project.controller.admin.worklog.vo;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorklogRequestVOTest {

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
    void testCreateValidatesPrecisionRangeAndActualDate() {
        WorklogCreateReqVO reqVO = validCreateReqVO();
        reqVO.setHours(new BigDecimal("1.234"));
        reqVO.setWorkDate(null);
        assertEquals(Set.of("工时数最多 8 位整数和 2 位小数", "实际工时的工作日期不能为空"),
                messages(reqVO));

        reqVO.setHours(new BigDecimal("100000000.00"));
        assertTrue(messages(reqVO).contains("工时数不能超过 99999999.99"));
    }

    @Test
    void testEstimatedDateMayBeNull() {
        WorklogCreateReqVO reqVO = validCreateReqVO();
        reqVO.setType("ESTIMATED");
        reqVO.setWorkDate(null);
        assertTrue(validator.validate(reqVO).isEmpty());
    }

    @Test
    void testFilterRejectsReversedDateRangeAndInvalidEnums() {
        WorklogSummaryReqVO reqVO = new WorklogSummaryReqVO();
        reqVO.setProjectId(1L);
        reqVO.setWorkDateStart(LocalDate.of(2026, 7, 2));
        reqVO.setWorkDateEnd(LocalDate.of(2026, 7, 1));
        reqVO.setType("UNKNOWN");
        reqVO.setGroupBy("UNKNOWN");

        assertEquals(Set.of("工作日期结束不能早于开始日期", "工时类型不正确", "汇总分组维度不正确"),
                messages(reqVO));
    }

    @Test
    void testSummaryDoesNotExposePaginationFields() {
        assertTrue(Arrays.stream(WorklogSummaryReqVO.class.getMethods())
                .noneMatch(method -> method.getName().equals("getPageNo") || method.getName().equals("getPageSize")));
    }

    private WorklogCreateReqVO validCreateReqVO() {
        WorklogCreateReqVO reqVO = new WorklogCreateReqVO();
        reqVO.setWorkItemId(1L);
        reqVO.setType("ACTUAL");
        reqVO.setHours(new BigDecimal("8.25"));
        reqVO.setCategory("DEVELOPMENT");
        reqVO.setWorkDate(LocalDate.of(2026, 7, 28));
        return reqVO;
    }

    private Set<String> messages(Object bean) {
        return validator.validate(bean).stream().map(item -> item.getMessage()).collect(Collectors.toSet());
    }

}
