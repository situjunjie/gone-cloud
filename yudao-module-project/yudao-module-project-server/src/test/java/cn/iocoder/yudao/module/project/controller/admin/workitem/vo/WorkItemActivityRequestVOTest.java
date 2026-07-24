package cn.iocoder.yudao.module.project.controller.admin.workitem.vo;

import cn.iocoder.yudao.module.project.controller.admin.comment.vo.CommentCreateReqVO;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkItemActivityRequestVOTest {

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
    void testActivityPageLookbackBoundary() {
        WorkItemActivityPageReqVO valid = new WorkItemActivityPageReqVO();
        valid.setWorkItemId(1L);
        valid.setPageNo(10);
        valid.setPageSize(20);
        WorkItemActivityPageReqVO tooDeep = new WorkItemActivityPageReqVO();
        tooDeep.setWorkItemId(1L);
        tooDeep.setPageNo(11);
        tooDeep.setPageSize(20);

        assertTrue(validator.validate(valid).isEmpty());
        assertEquals(Set.of("工作项动态最多查询前 200 条"), validator.validate(tooDeep).stream()
                .map(item -> item.getMessage()).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void testWorkItemCommentCreateInheritsContentValidation() {
        WorkItemCommentCreateReqVO reqVO = new WorkItemCommentCreateReqVO();
        assertEquals(Set.of("工作项编号不能为空", "评论正文不能为空"), validator.validate(reqVO).stream()
                .map(item -> item.getMessage()).collect(java.util.stream.Collectors.toSet()));

        reqVO.setWorkItemId(1L);
        reqVO.setContent("x".repeat(10_001));
        assertEquals(Set.of("评论正文不能超过 10000 个字符"), validator.validate(reqVO).stream()
                .map(item -> item.getMessage()).collect(java.util.stream.Collectors.toSet()));

        reqVO.setContent("合法 Markdown");
        assertTrue(validator.validate(reqVO).isEmpty());
    }

    @Test
    void testGenericCommentCreateKeepsInheritedValidation() {
        CommentCreateReqVO reqVO = new CommentCreateReqVO();
        Set<String> missingMessages = validator.validate(reqVO).stream()
                .map(item -> item.getMessage()).collect(java.util.stream.Collectors.toSet());
        assertTrue(missingMessages.contains("评论目标类型不能为空"));
        assertTrue(missingMessages.contains("评论目标编号不能为空"));
        assertTrue(missingMessages.contains("评论正文不能为空"));

        reqVO.setTargetType("UNKNOWN");
        reqVO.setTargetId(1L);
        reqVO.setContent("合法 Markdown");
        assertEquals(Set.of("评论目标类型不正确"), validator.validate(reqVO).stream()
                .map(item -> item.getMessage()).collect(java.util.stream.Collectors.toSet()));

        reqVO.setTargetType("WORK_ITEM");
        reqVO.setContent("x".repeat(10_001));
        assertEquals(Set.of("评论正文不能超过 10000 个字符"), validator.validate(reqVO).stream()
                .map(item -> item.getMessage()).collect(java.util.stream.Collectors.toSet()));
    }

}
