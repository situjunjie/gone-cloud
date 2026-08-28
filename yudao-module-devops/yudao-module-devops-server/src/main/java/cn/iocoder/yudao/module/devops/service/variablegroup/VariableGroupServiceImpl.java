package cn.iocoder.yudao.module.devops.service.variablegroup;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.variablegroup.vo.VariableGroupPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.variablegroup.vo.VariableGroupSaveReqVO;
import cn.iocoder.yudao.module.devops.convert.variablegroup.VariableGroupConvert;
import cn.iocoder.yudao.module.devops.dal.dataobject.variablegroup.VariableGroupDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationEnvDO;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.variablegroup.VariableGroupMapper;
import cn.iocoder.yudao.module.devops.enums.VariableGroupScopeEnum;
import cn.iocoder.yudao.module.devops.service.application.ApplicationService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import java.util.LinkedHashMap;
import java.util.Map;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.*;

@Service
@Validated
public class VariableGroupServiceImpl implements VariableGroupService {
    @Resource private VariableGroupMapper variableGroupMapper;
    @Resource private ApplicationService applicationService;
    @Resource private ApplicationEnvMapper applicationEnvMapper;

    @Override public Long createVariableGroup(VariableGroupSaveReqVO req) {
        validate(req); validateUnique(req, null);
        VariableGroupDO obj = VariableGroupConvert.INSTANCE.convert(req); normalize(obj); variableGroupMapper.insert(obj); return obj.getId();
    }
    @Override public void updateVariableGroup(VariableGroupSaveReqVO req) {
        getRequired(req.getId()); validate(req); validateUnique(req, req.getId());
        VariableGroupDO obj = VariableGroupConvert.INSTANCE.convert(req); normalize(obj); variableGroupMapper.updateById(obj);
    }
    @Override public void deleteVariableGroup(Long id) { getRequired(id); variableGroupMapper.deleteById(id); }
    @Override public VariableGroupDO getVariableGroup(Long id) { return variableGroupMapper.selectById(id); }
    @Override public PageResult<VariableGroupDO> getVariableGroupPage(VariableGroupPageReqVO req) { return variableGroupMapper.selectPage(req); }
    @Override public Map<String, String> getEffectiveVariables(Long appId, Long applicationEnvId) {
        Map<String,String> out = new LinkedHashMap<>();
        variableGroupMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<VariableGroupDO>().eq(VariableGroupDO::getScope, "GLOBAL").eq(VariableGroupDO::getStatus, CommonStatusEnum.ENABLE.getStatus()).orderByAsc(VariableGroupDO::getId)).forEach(g -> out.putAll(parse(g)));
        if (appId != null) variableGroupMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<VariableGroupDO>().eq(VariableGroupDO::getScope, "APPLICATION").eq(VariableGroupDO::getAppId, appId).eq(VariableGroupDO::getStatus, 0).orderByAsc(VariableGroupDO::getId)).forEach(g -> out.putAll(parse(g)));
        if (applicationEnvId != null) variableGroupMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<VariableGroupDO>().eq(VariableGroupDO::getScope, "APPLICATION_ENV").eq(VariableGroupDO::getApplicationEnvId, applicationEnvId).eq(VariableGroupDO::getStatus, 0).orderByAsc(VariableGroupDO::getId)).forEach(g -> out.putAll(parse(g)));
        return out;
    }
    private Map<String,String> parse(VariableGroupDO g) { try { return VariableGroupParser.parse(g.getVariables()); } catch (RuntimeException e) { return Map.of(); } }
    private void normalize(VariableGroupDO o) { if (o.getAppId() == null) o.setAppId(0L); if (o.getApplicationEnvId() == null) o.setApplicationEnvId(0L); }
    private VariableGroupDO getRequired(Long id) { VariableGroupDO x = variableGroupMapper.selectById(id); if (x == null) throw exception(VARIABLE_GROUP_NOT_EXISTS); return x; }
    private void validateUnique(VariableGroupSaveReqVO r, Long id) { VariableGroupDO x = variableGroupMapper.selectByNameAndScope(r.getName(), r.getScope(), r.getAppId(), r.getApplicationEnvId()); if (x != null && !x.getId().equals(id)) throw exception(VARIABLE_GROUP_NAME_DUPLICATE); }
    private void validate(VariableGroupSaveReqVO r) {
        try { VariableGroupParser.parse(r.getVariables()); } catch (IllegalArgumentException e) { throw exception(VARIABLE_GROUP_VARIABLES_INVALID, e.getMessage()); } catch (IllegalStateException e) { throw exception(VARIABLE_GROUP_KEY_DUPLICATE, e.getMessage()); }
        boolean global = VariableGroupScopeEnum.GLOBAL.getScope().equals(r.getScope());
        boolean app = VariableGroupScopeEnum.APPLICATION.getScope().equals(r.getScope());
        boolean env = VariableGroupScopeEnum.APPLICATION_ENV.getScope().equals(r.getScope());
        if ((!global && !app && !env) || (global && (r.getAppId()!=null || r.getApplicationEnvId()!=null)) || (app && (r.getAppId()==null || r.getApplicationEnvId()!=null)) || (env && (r.getApplicationEnvId()==null || r.getAppId()!=null))) throw exception(VARIABLE_GROUP_SCOPE_INVALID);
        if (app) applicationService.validateApplicationExists(r.getAppId());
        if (env) {
            ApplicationEnvDO relation = applicationEnvMapper.selectById(r.getApplicationEnvId());
            if (relation == null) throw exception(VARIABLE_GROUP_SCOPE_INVALID);
        }
    }
}
