package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineDefinitionRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineDefinitionVersionRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineCacheClearReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelinePublishReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineRollbackReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineSaveDraftReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineValidateReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineValidationRespVO;

import java.util.List;

/**
 * DevOps 流水线定义 Service。
 */
public interface PipelineDefinitionService {

    /**
     * 获得应用环境当前流水线定义及草稿/发布版本。
     *
     * @param applicationEnvId 应用环境编号
     * @return 流水线定义；不存在时返回 null
     */
    PipelineDefinitionRespVO getByApplicationEnvId(Long applicationEnvId);

    /**
     * 保存流水线草稿版本。
     *
     * @param reqVO 保存请求
     * @return 草稿版本编号
     */
    Long saveDraft(PipelineSaveDraftReqVO reqVO);

    /**
     * 校验流水线 YAML 和缓存目录配置。
     *
     * @param reqVO 校验请求
     * @return 校验结果
     */
    PipelineValidationRespVO validate(PipelineValidateReqVO reqVO);

    /**
     * 发布草稿为新版本。
     *
     * @param reqVO 发布请求
     * @param userId 发布人编号
     * @return 已发布版本编号
     */
    Long publish(PipelinePublishReqVO reqVO, Long userId);

    /**
     * 基于历史发布版本生成新的回退发布版本。
     *
     * @param reqVO 回退请求
     * @param userId 发布人编号
     * @return 新发布版本编号
     */
    Long rollback(PipelineRollbackReqVO reqVO, Long userId);

    /**
     * 清理流水线定义下的依赖缓存目录。
     *
     * @param reqVO 清理请求
     * @param userId 操作人编号
     * @return 是否清理成功
     */
    Boolean clearCache(PipelineCacheClearReqVO reqVO, Long userId);

    /**
     * 获得流水线定义的版本列表。
     *
     * @param definitionId 流水线定义编号
     * @return 版本列表
     */
    List<PipelineDefinitionVersionRespVO> getVersionList(Long definitionId);

}
