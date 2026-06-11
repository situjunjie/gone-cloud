package cn.iocoder.yudao.module.devops.service.offlineimage;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.offlineimage.vo.OfflineImagePackagePageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.offlineimage.vo.OfflineImagePackageRespVO;

public interface OfflineImagePackageService {

    /**
     * 获得离线镜像包分页。
     */
    PageResult<OfflineImagePackageRespVO> getOfflineImagePackagePage(OfflineImagePackagePageReqVO reqVO);

    /**
     * 获得离线镜像包详情。
     */
    OfflineImagePackageRespVO getOfflineImagePackage(Long id);

    /**
     * 获得离线镜像包下载 URL。
     */
    String getDownloadUrl(Long id);

}
