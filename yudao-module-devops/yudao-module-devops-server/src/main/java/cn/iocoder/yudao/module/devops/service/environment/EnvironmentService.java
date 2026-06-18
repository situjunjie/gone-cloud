package cn.iocoder.yudao.module.devops.service.environment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentConnectionCheckRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerComposeProjectDetailRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerComposeProjectRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerContainerRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerDashboardRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerImageRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesDashboardRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesDeploymentRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesNamespaceRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesPodRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentSaveReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesServiceRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;

import java.util.List;

public interface EnvironmentService {

    Long createEnvironment(EnvironmentSaveReqVO createReqVO);

    void updateEnvironment(EnvironmentSaveReqVO updateReqVO);

    void deleteEnvironment(Long id);

    EnvironmentDO getEnvironment(Long id);

    PageResult<EnvironmentDO> getEnvironmentPage(EnvironmentPageReqVO pageReqVO);

    EnvironmentDO validateEnvironmentExists(Long id);

    EnvironmentConnectionCheckRespVO checkEnvironmentConnection(Long id);

    List<EnvironmentKubernetesNamespaceRespVO> getKubernetesNamespaces(Long id);

    EnvironmentKubernetesDashboardRespVO getKubernetesDashboard(Long id);

    List<EnvironmentKubernetesPodRespVO> getKubernetesPods(Long id);

    List<EnvironmentKubernetesDeploymentRespVO> getKubernetesDeployments(Long id);

    List<EnvironmentKubernetesServiceRespVO> getKubernetesServices(Long id);

    EnvironmentDockerDashboardRespVO getDockerDashboard(Long id);

    List<EnvironmentDockerContainerRespVO> getDockerContainers(Long id, Boolean all);

    List<EnvironmentDockerImageRespVO> getDockerImages(Long id, String keyword, Boolean dangling, Boolean unused);

    List<EnvironmentDockerComposeProjectRespVO> getDockerComposeProjects(Long id);

    EnvironmentDockerComposeProjectDetailRespVO getDockerComposeProjectDetail(Long id, String projectName);

    void startDockerContainer(Long id, String containerId);

    void stopDockerContainer(Long id, String containerId);

    void restartDockerContainer(Long id, String containerId);

    void startDockerComposeProject(Long id, String projectName);

    void stopDockerComposeProject(Long id, String projectName);

}
