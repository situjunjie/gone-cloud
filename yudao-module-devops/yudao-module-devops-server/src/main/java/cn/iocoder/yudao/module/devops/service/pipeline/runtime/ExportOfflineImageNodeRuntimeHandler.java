package cn.iocoder.yudao.module.devops.service.pipeline.runtime;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.module.devops.dal.dataobject.offlineimage.OfflineImagePackageDO;
import cn.iocoder.yudao.module.devops.dal.mysql.offlineimage.OfflineImagePackageMapper;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 导出离线镜像节点处理器。
 */
@Component
public class ExportOfflineImageNodeRuntimeHandler implements PipelineNodeRuntimeHandler {

    @Resource
    private PipelineNodeRuntimeSupport runtimeSupport;
    @Resource
    private OfflineImagePackageMapper offlineImagePackageMapper;

    @Override
    public boolean supports(String nodeType) {
        return PipelineNodeRegistryServiceImpl.TYPE_EXPORT_OFFLINE_IMAGE.equals(nodeType);
    }

    @Override
    public String getNodeType() {
        return PipelineNodeRegistryServiceImpl.TYPE_EXPORT_OFFLINE_IMAGE;
    }

    @Override
    public void onStarted(PipelineNodeCallbackContext context) {
        runtimeSupport.markStarted(context);
    }

    @Override
    public void onCompleted(PipelineNodeCallbackContext context) {
        runtimeSupport.markCompleted(context);
        String packageMetadataJson = context.getCallback().getPackageMetadata();
        if (StrUtil.isBlank(packageMetadataJson)) {
            return;
        }
        Map<String, Object> metadata = JSONUtil.toBean(packageMetadataJson, Map.class);
        String ossFilePath = (String) metadata.get("ossFilePath");
        Long packageSize = metadata.get("packageSize") instanceof Number
                ? ((Number) metadata.get("packageSize")).longValue()
                : Long.parseLong(String.valueOf(metadata.get("packageSize")));
        String imageDigest = (String) metadata.get("imageDigest");
        String imageName = (String) metadata.get("imageName");
        String imageTag = (String) metadata.get("imageTag");

        OfflineImagePackageDO existingPackage = offlineImagePackageMapper.selectByPipelineRunIdAndImage(
                context.getRun().getId(), imageName, imageTag);
        if (existingPackage != null) {
            existingPackage.setImageDigest(imageDigest);
            existingPackage.setPackageSize(packageSize);
            existingPackage.setStatus(1);
            offlineImagePackageMapper.updateById(existingPackage);
            return;
        }

        OfflineImagePackageDO packageDO = new OfflineImagePackageDO();
        packageDO.setPipelineRunId(context.getRun().getId());
        packageDO.setImageName(imageName);
        packageDO.setImageTag(imageTag);
        packageDO.setImageDigest(imageDigest);
        packageDO.setArchitecture("amd64");
        packageDO.setFileId(0L);
        packageDO.setPackageSize(packageSize);
        packageDO.setStatus(1);
        offlineImagePackageMapper.insert(packageDO);
    }

    @Override
    public void onFailed(PipelineNodeCallbackContext context) {
        runtimeSupport.markFailed(context);
    }

}
