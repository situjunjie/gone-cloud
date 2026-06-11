package cn.iocoder.yudao.module.devops.convert.offlineimage;

import cn.iocoder.yudao.module.devops.controller.admin.offlineimage.vo.OfflineImagePackageRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.offlineimage.OfflineImagePackageDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface OfflineImagePackageConvert {

    OfflineImagePackageConvert INSTANCE = Mappers.getMapper(OfflineImagePackageConvert.class);

    OfflineImagePackageRespVO convert(OfflineImagePackageDO bean);

    List<OfflineImagePackageRespVO> convertList(List<OfflineImagePackageDO> list);

}
