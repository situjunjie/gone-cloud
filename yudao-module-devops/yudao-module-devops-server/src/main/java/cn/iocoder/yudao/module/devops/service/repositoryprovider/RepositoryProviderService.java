package cn.iocoder.yudao.module.devops.service.repositoryprovider;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.repositoryprovider.vo.RepositoryProviderPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.repositoryprovider.vo.RepositoryProviderProjectRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.repositoryprovider.vo.RepositoryProviderSaveReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.repositoryprovider.RepositoryProviderDO;

import java.util.List;

/**
 * DevOps 代码源 Service 接口。
 */
public interface RepositoryProviderService {

    Long createRepositoryProvider(RepositoryProviderSaveReqVO createReqVO);

    void updateRepositoryProvider(RepositoryProviderSaveReqVO updateReqVO);

    void deleteRepositoryProvider(Long id);

    RepositoryProviderDO getRepositoryProvider(Long id);

    PageResult<RepositoryProviderDO> getRepositoryProviderPage(RepositoryProviderPageReqVO pageReqVO);

    RepositoryProviderDO validateRepositoryProviderExists(Long id);

    void checkRepositoryProvider(Long id);

    List<RepositoryProviderProjectRespVO> getRepositoryProviderProjects(Long id);

}
