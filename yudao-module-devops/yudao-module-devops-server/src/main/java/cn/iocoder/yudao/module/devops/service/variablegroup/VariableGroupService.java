package cn.iocoder.yudao.module.devops.service.variablegroup;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.variablegroup.vo.VariableGroupPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.variablegroup.vo.VariableGroupSaveReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.variablegroup.VariableGroupDO;
import java.util.Map;
import java.util.List;
import cn.iocoder.yudao.module.devops.controller.admin.variablegroup.vo.VariableGroupSimpleRespVO;

public interface VariableGroupService {
    Long createVariableGroup(VariableGroupSaveReqVO req);
    void updateVariableGroup(VariableGroupSaveReqVO req);
    void deleteVariableGroup(Long id);
    VariableGroupDO getVariableGroup(Long id);
    PageResult<VariableGroupDO> getVariableGroupPage(VariableGroupPageReqVO req);
    Map<String, String> getEffectiveVariables(Long appId, Long applicationEnvId);
    List<VariableGroupSimpleRespVO> getVariableGroupSimpleList(Long appId);
}
