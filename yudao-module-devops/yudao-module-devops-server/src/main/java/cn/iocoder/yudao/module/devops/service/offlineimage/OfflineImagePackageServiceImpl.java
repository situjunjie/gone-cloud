package cn.iocoder.yudao.module.devops.service.offlineimage;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.offlineimage.vo.OfflineImagePackagePageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.offlineimage.vo.OfflineImagePackageRespVO;
import cn.iocoder.yudao.module.devops.convert.offlineimage.OfflineImagePackageConvert;
import cn.iocoder.yudao.module.devops.dal.dataobject.offlineimage.OfflineImagePackageDO;
import cn.iocoder.yudao.module.devops.dal.mysql.offlineimage.OfflineImagePackageMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.OFFLINE_IMAGE_PACKAGE_NOT_EXISTS;

/**
 * DevOps 离线镜像包 Service 实现。
 */
@Service
@Validated
public class OfflineImagePackageServiceImpl implements OfflineImagePackageService {

    @Resource
    private OfflineImagePackageMapper offlineImagePackageMapper;

    @Override
    public PageResult<OfflineImagePackageRespVO> getOfflineImagePackagePage(OfflineImagePackagePageReqVO reqVO) {
        PageResult<OfflineImagePackageDO> pageResult = offlineImagePackageMapper.selectPage(reqVO);
        return new PageResult<>(
                OfflineImagePackageConvert.INSTANCE.convertList(pageResult.getList()),
                pageResult.getTotal()
        );
    }

    @Override
    public OfflineImagePackageRespVO getOfflineImagePackage(Long id) {
        OfflineImagePackageDO packageDO = validateOfflineImagePackageExists(id);
        return OfflineImagePackageConvert.INSTANCE.convert(packageDO);
    }

    @Override
    public String getDownloadUrl(Long id) {
        OfflineImagePackageDO packageDO = validateOfflineImagePackageExists(id);
        if (StrUtil.isBlank(packageDO.getOssUrl())) {
            throw exception(OFFLINE_IMAGE_PACKAGE_NOT_EXISTS);
        }
        // OSS URL 由 Jenkins 上传后直接返回，平台直接返回该 URL 供下载
        return packageDO.getOssUrl();
    }

    private OfflineImagePackageDO validateOfflineImagePackageExists(Long id) {
        OfflineImagePackageDO packageDO = offlineImagePackageMapper.selectById(id);
        if (packageDO == null) {
            throw exception(OFFLINE_IMAGE_PACKAGE_NOT_EXISTS);
        }
        return packageDO;
    }

}
