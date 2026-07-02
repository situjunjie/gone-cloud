package cn.iocoder.yudao.module.devops.service.pipeline.approval;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationEnvDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.change.ChangeDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.change.ChangeMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.environment.EnvironmentMapper;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.PipelineRunChangeSnapshotContext;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link PipelineApprovalDeployDetailBuilder} 的单元测试。
 */
public class PipelineApprovalDeployDetailBuilderTest extends BaseMockitoUnitTest {

    @InjectMocks
    private PipelineApprovalDeployDetailBuilder builder;

    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private ApplicationEnvMapper applicationEnvMapper;
    @Mock
    private EnvironmentMapper environmentMapper;
    @Mock
    private ChangeMapper changeMapper;
    @Mock
    private AdminUserApi adminUserApi;

    @Test
    public void testBuildDeployHtml_multiChangesWithUserNamesAndEscaping() {
        // 准备参数
        PipelineRunDO run = buildRun();
        run.setChangeSnapshotJson(JsonUtils.toJsonString(List.of(
                new PipelineRunChangeSnapshotContext(10L, "commit-10"),
                new PipelineRunChangeSnapshotContext(20L, "commit-20"))));
        when(applicationMapper.selectById(1L)).thenReturn(application("<应用>"));
        when(applicationEnvMapper.selectById(100L)).thenReturn(applicationEnv());
        when(environmentMapper.selectById(200L)).thenReturn(environment("生产<script>"));
        doReturn(List.of(change(20L, "CHG-20", "第二个变更", 31L, 41L),
                change(10L, "CHG-10", "第一个<script>变更", 30L, 40L)))
                .when(changeMapper).selectListByIds(anyCollection());
        when(adminUserApi.getUserMap(anyCollection())).thenReturn(Map.of(
                7L, user(7L, "发起人"),
                30L, user(30L, "测试人A"),
                40L, user(40L, "审核人A"),
                31L, user(31L, "测试人B"),
                41L, user(41L, "审核人B")));

        // 调用
        String html = builder.buildDeployHtml(run);

        // 断言
        assertTrue(html.contains("&lt;应用&gt;"));
        assertTrue(html.contains("生产&lt;script&gt;"));
        assertTrue(html.contains("第一个&lt;script&gt;变更"));
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("测试人A"));
        assertTrue(html.contains("审核人A"));
        assertTrue(html.contains("测试人B"));
        assertTrue(html.contains("审核人B"));
        assertTrue(html.indexOf("CHG-10") < html.indexOf("CHG-20"));
        assertTrue(html.indexOf("commit-10") < html.indexOf("commit-20"));
    }

    @Test
    public void testBuildDeployHtml_emptyChanges() {
        // 准备参数
        PipelineRunDO run = buildRun();
        run.setChangeSnapshotJson(JsonUtils.toJsonString(List.of()));
        run.setChangeId(null);
        when(applicationMapper.selectById(1L)).thenReturn(application("应用"));
        when(applicationEnvMapper.selectById(100L)).thenReturn(applicationEnv());
        when(environmentMapper.selectById(200L)).thenReturn(environment("生产"));
        when(adminUserApi.getUserMap(anyCollection())).thenReturn(Map.of(7L, user(7L, "发起人")));

        // 调用
        String html = builder.buildDeployHtml(run);

        // 断言
        assertTrue(html.contains("本次发布无关联变更"));
        verifyNoInteractions(changeMapper);
    }

    @Test
    public void testBuildDeployHtml_userQueryFailsFallbackToId() {
        // 准备参数
        PipelineRunDO run = buildRun();
        run.setChangeSnapshotJson(JsonUtils.toJsonString(List.of(
                new PipelineRunChangeSnapshotContext(10L, "commit-10"))));
        when(applicationMapper.selectById(1L)).thenReturn(application("应用"));
        when(applicationEnvMapper.selectById(100L)).thenReturn(applicationEnv());
        when(environmentMapper.selectById(200L)).thenReturn(environment("生产"));
        doReturn(List.of(change(10L, "CHG-10", "变更", 30L, 40L)))
                .when(changeMapper).selectListByIds(anyCollection());
        when(adminUserApi.getUserMap(anyCollection())).thenThrow(new RuntimeException("rpc error"));

        // 调用
        String html = builder.buildDeployHtml(run);

        // 断言
        assertTrue(html.contains(">30<"));
        assertTrue(html.contains(">40<"));
    }

    private PipelineRunDO buildRun() {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(800L);
        run.setAppId(1L);
        run.setApplicationEnvId(100L);
        run.setBranchName("release/prod");
        run.setCommitSha("run-commit");
        run.setTriggerUserId(7L);
        run.setTriggeredAt(LocalDateTime.of(2026, 7, 2, 12, 0));
        return run;
    }

    private ApplicationDO application(String name) {
        ApplicationDO application = new ApplicationDO();
        application.setId(1L);
        application.setName(name);
        application.setAppKey("gone-cloud");
        return application;
    }

    private ApplicationEnvDO applicationEnv() {
        ApplicationEnvDO applicationEnv = new ApplicationEnvDO();
        applicationEnv.setId(100L);
        applicationEnv.setAppId(1L);
        applicationEnv.setEnvId(200L);
        return applicationEnv;
    }

    private EnvironmentDO environment(String name) {
        EnvironmentDO environment = new EnvironmentDO();
        environment.setId(200L);
        environment.setEnvName(name);
        environment.setEnvKey("prod");
        environment.setEnvStage("PROD");
        return environment;
    }

    private ChangeDO change(Long id, String changeKey, String title, Long testerUserId, Long codeReviewerUserId) {
        ChangeDO change = new ChangeDO();
        change.setId(id);
        change.setChangeKey(changeKey);
        change.setTitle(title);
        change.setBranchName("feat/" + changeKey);
        change.setLatestCommitSha("latest-" + id);
        change.setLatestCommitMessage("message-" + id);
        change.setTesterUserId(testerUserId);
        change.setCodeReviewerUserId(codeReviewerUserId);
        return change;
    }

    private AdminUserRespDTO user(Long id, String nickname) {
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(id);
        user.setNickname(nickname);
        return user;
    }

}
