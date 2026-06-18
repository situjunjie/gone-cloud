package cn.iocoder.yudao.module.devops.controller.admin.environment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentConnectionCheckRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerComposeProjectDetailRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerComposeProjectRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerContainerRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerDashboardRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerImagePageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerImageRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesDashboardRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesDeploymentRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesNamespaceRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesPodRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentSaveReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesServiceRespVO;
import cn.iocoder.yudao.module.devops.convert.environment.EnvironmentConvert;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.service.environment.EnvironmentService;
import cn.iocoder.yudao.module.devops.service.docker.log.DockerContainerLogService;
import cn.iocoder.yudao.module.devops.service.kubernetes.log.KubernetesPodLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - DevOps 环境")
@RestController
@RequestMapping("/devops/environment")
@Validated
public class EnvironmentController {

    @Resource
    private EnvironmentService environmentService;
    @Resource
    private KubernetesPodLogService kubernetesPodLogService;
    @Resource
    private DockerContainerLogService dockerContainerLogService;

    @PostMapping("/create")
    @Operation(summary = "创建环境")
    @PreAuthorize("@ss.hasPermission('devops:environment:create')")
    public CommonResult<Long> createEnvironment(@Valid @RequestBody EnvironmentSaveReqVO createReqVO) {
        return success(environmentService.createEnvironment(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新环境")
    @PreAuthorize("@ss.hasPermission('devops:environment:update')")
    public CommonResult<Boolean> updateEnvironment(@Valid @RequestBody EnvironmentSaveReqVO updateReqVO) {
        environmentService.updateEnvironment(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除环境")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:environment:delete')")
    public CommonResult<Boolean> deleteEnvironment(@RequestParam("id") Long id) {
        environmentService.deleteEnvironment(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得环境")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:environment:query')")
    public CommonResult<EnvironmentRespVO> getEnvironment(@RequestParam("id") Long id) {
        return success(EnvironmentConvert.INSTANCE.convert(environmentService.getEnvironment(id)));
    }

    @GetMapping("/page")
    @Operation(summary = "获得环境分页")
    @PreAuthorize("@ss.hasPermission('devops:environment:query')")
    public CommonResult<PageResult<EnvironmentRespVO>> getEnvironmentPage(@Valid EnvironmentPageReqVO pageReqVO) {
        PageResult<EnvironmentDO> pageResult = environmentService.getEnvironmentPage(pageReqVO);
        return success(EnvironmentConvert.INSTANCE.convertPage(pageResult));
    }

    @PostMapping({"/check", "/check-connection"})
    @Operation(summary = "检测环境连接")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:environment:update')")
    public CommonResult<EnvironmentConnectionCheckRespVO> checkEnvironmentConnection(@RequestParam("id") Long id) {
        return success(environmentService.checkEnvironmentConnection(id));
    }

    @GetMapping("/kubernetes/namespaces")
    @Operation(summary = "获得 Kubernetes Namespace 列表")
    @Parameter(name = "id", description = "环境编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:environment:query')")
    public CommonResult<List<EnvironmentKubernetesNamespaceRespVO>> getKubernetesNamespaces(@RequestParam("id") Long id) {
        return success(environmentService.getKubernetesNamespaces(id));
    }

    @GetMapping("/kubernetes/dashboard")
    @Operation(summary = "获得 Kubernetes 环境大盘")
    @Parameter(name = "id", description = "环境编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:environment:query')")
    public CommonResult<EnvironmentKubernetesDashboardRespVO> getKubernetesDashboard(@RequestParam("id") Long id) {
        return success(environmentService.getKubernetesDashboard(id));
    }

    @GetMapping("/kubernetes/pods")
    @Operation(summary = "获得 Kubernetes Pod 列表")
    @Parameter(name = "id", description = "环境编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:environment:query')")
    public CommonResult<List<EnvironmentKubernetesPodRespVO>> getKubernetesPods(@RequestParam("id") Long id) {
        return success(environmentService.getKubernetesPods(id));
    }

    @GetMapping(value = "/kubernetes/pod-logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式查看 Kubernetes Pod 日志")
    @Parameter(name = "id", description = "环境编号", required = true, example = "1024")
    @Parameter(name = "namespace", description = "Kubernetes Namespace，可空，默认使用环境配置 Namespace", example = "prod")
    @Parameter(name = "podName", description = "Pod 名称", required = true, example = "gone-api-7d98f")
    @Parameter(name = "containerName", description = "容器名称，单容器 Pod 可空", example = "app")
    @Parameter(name = "tailLines", description = "首次返回的尾部日志行数，默认 200，最大 2000", example = "200")
    @PreAuthorize("@ss.hasPermission('devops:environment:query')")
    public SseEmitter streamKubernetesPodLogs(@RequestParam("id") Long id,
                                              @RequestParam(value = "namespace", required = false) String namespace,
                                              @RequestParam("podName") String podName,
                                              @RequestParam(value = "containerName", required = false)
                                              String containerName,
                                              @RequestParam(value = "tailLines", required = false)
                                              Integer tailLines) {
        return kubernetesPodLogService.streamPodLogs(id, namespace, podName, containerName, tailLines);
    }

    @GetMapping("/kubernetes/deployments")
    @Operation(summary = "获得 Kubernetes Deployment 列表")
    @Parameter(name = "id", description = "环境编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:environment:query')")
    public CommonResult<List<EnvironmentKubernetesDeploymentRespVO>> getKubernetesDeployments(@RequestParam("id") Long id) {
        return success(environmentService.getKubernetesDeployments(id));
    }

    @GetMapping("/kubernetes/services")
    @Operation(summary = "获得 Kubernetes Service 列表")
    @Parameter(name = "id", description = "环境编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:environment:query')")
    public CommonResult<List<EnvironmentKubernetesServiceRespVO>> getKubernetesServices(@RequestParam("id") Long id) {
        return success(environmentService.getKubernetesServices(id));
    }

    @GetMapping("/docker/dashboard")
    @Operation(summary = "获得 Docker 环境大盘")
    @Parameter(name = "id", description = "环境编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:environment:query')")
    public CommonResult<EnvironmentDockerDashboardRespVO> getDockerDashboard(@RequestParam("id") Long id) {
        return success(environmentService.getDockerDashboard(id));
    }

    @GetMapping("/docker/containers")
    @Operation(summary = "获得 Docker 容器列表")
    @Parameter(name = "id", description = "环境编号", required = true, example = "1024")
    @Parameter(name = "all", description = "是否包含已停止容器", example = "false")
    @PreAuthorize("@ss.hasPermission('devops:environment:query')")
    public CommonResult<List<EnvironmentDockerContainerRespVO>> getDockerContainers(
            @RequestParam("id") Long id,
            @RequestParam(value = "all", required = false) Boolean all) {
        return success(environmentService.getDockerContainers(id, all));
    }

    @GetMapping("/docker/images")
    @Operation(summary = "获得 Docker 镜像列表")
    @PreAuthorize("@ss.hasPermission('devops:environment:query')")
    public CommonResult<PageResult<EnvironmentDockerImageRespVO>> getDockerImages(
            @Valid EnvironmentDockerImagePageReqVO pageReqVO) {
        return success(environmentService.getDockerImages(pageReqVO));
    }

    @GetMapping("/docker/compose-projects")
    @Operation(summary = "获得 Docker Compose 项目总览")
    @Parameter(name = "id", description = "环境编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:environment:query')")
    public CommonResult<List<EnvironmentDockerComposeProjectRespVO>> getDockerComposeProjects(@RequestParam("id") Long id) {
        return success(environmentService.getDockerComposeProjects(id));
    }

    @GetMapping("/docker/compose-project-detail")
    @Operation(summary = "获得 Docker Compose 项目详情")
    @Parameter(name = "id", description = "环境编号", required = true, example = "1024")
    @Parameter(name = "projectName", description = "Compose 项目名称", required = true, example = "gone-cloud")
    @PreAuthorize("@ss.hasPermission('devops:environment:query')")
    public CommonResult<EnvironmentDockerComposeProjectDetailRespVO> getDockerComposeProjectDetail(
            @RequestParam("id") Long id,
            @RequestParam("projectName") String projectName) {
        return success(environmentService.getDockerComposeProjectDetail(id, projectName));
    }

    @PostMapping("/docker/container/start")
    @Operation(summary = "启动 Docker 容器")
    @Parameter(name = "id", description = "环境编号", required = true, example = "1024")
    @Parameter(name = "containerId", description = "容器 ID 或名称", required = true, example = "abc123")
    @PreAuthorize("@ss.hasPermission('devops:environment:update')")
    public CommonResult<Boolean> startDockerContainer(@RequestParam("id") Long id,
                                                      @RequestParam("containerId") String containerId) {
        environmentService.startDockerContainer(id, containerId);
        return success(true);
    }

    @PostMapping("/docker/container/stop")
    @Operation(summary = "停止 Docker 容器")
    @Parameter(name = "id", description = "环境编号", required = true, example = "1024")
    @Parameter(name = "containerId", description = "容器 ID 或名称", required = true, example = "abc123")
    @PreAuthorize("@ss.hasPermission('devops:environment:update')")
    public CommonResult<Boolean> stopDockerContainer(@RequestParam("id") Long id,
                                                     @RequestParam("containerId") String containerId) {
        environmentService.stopDockerContainer(id, containerId);
        return success(true);
    }

    @PostMapping("/docker/container/restart")
    @Operation(summary = "重启 Docker 容器")
    @Parameter(name = "id", description = "环境编号", required = true, example = "1024")
    @Parameter(name = "containerId", description = "容器 ID 或名称", required = true, example = "abc123")
    @PreAuthorize("@ss.hasPermission('devops:environment:update')")
    public CommonResult<Boolean> restartDockerContainer(@RequestParam("id") Long id,
                                                        @RequestParam("containerId") String containerId) {
        environmentService.restartDockerContainer(id, containerId);
        return success(true);
    }

    @PostMapping("/docker/compose-project/start")
    @Operation(summary = "启动 Docker Compose 项目")
    @Parameter(name = "id", description = "环境编号", required = true, example = "1024")
    @Parameter(name = "projectName", description = "Compose 项目名称", required = true, example = "gone-cloud")
    @PreAuthorize("@ss.hasPermission('devops:environment:update')")
    public CommonResult<Boolean> startDockerComposeProject(@RequestParam("id") Long id,
                                                           @RequestParam("projectName") String projectName) {
        environmentService.startDockerComposeProject(id, projectName);
        return success(true);
    }

    @PostMapping("/docker/compose-project/stop")
    @Operation(summary = "停止 Docker Compose 项目")
    @Parameter(name = "id", description = "环境编号", required = true, example = "1024")
    @Parameter(name = "projectName", description = "Compose 项目名称", required = true, example = "gone-cloud")
    @PreAuthorize("@ss.hasPermission('devops:environment:update')")
    public CommonResult<Boolean> stopDockerComposeProject(@RequestParam("id") Long id,
                                                          @RequestParam("projectName") String projectName) {
        environmentService.stopDockerComposeProject(id, projectName);
        return success(true);
    }

    @GetMapping(value = "/docker/container-logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式查看 Docker 容器日志")
    @Parameter(name = "id", description = "环境编号", required = true, example = "1024")
    @Parameter(name = "containerId", description = "容器 ID 或名称", required = true, example = "abc123")
    @Parameter(name = "tailLines", description = "首次返回的尾部日志行数，默认 200，最大 2000", example = "200")
    @PreAuthorize("@ss.hasPermission('devops:environment:query')")
    public SseEmitter streamDockerContainerLogs(@RequestParam("id") Long id,
                                                @RequestParam("containerId") String containerId,
                                                @RequestParam(value = "tailLines", required = false)
                                                Integer tailLines) {
        return dockerContainerLogService.streamContainerLogs(id, containerId, tailLines);
    }

}
