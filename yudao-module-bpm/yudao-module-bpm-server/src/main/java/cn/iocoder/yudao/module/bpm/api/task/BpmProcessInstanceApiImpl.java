package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCancelReqDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceStatusRespDTO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceCancelReqVO;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * Flowable 流程实例 Api 实现类
 *
 * @author 芋道源码
 * @author jason
 */
@RestController
@Validated
public class BpmProcessInstanceApiImpl implements BpmProcessInstanceApi {

    @Resource
    private BpmProcessInstanceService processInstanceService;

    @Override
    public CommonResult<String> createProcessInstance(Long userId, @Valid BpmProcessInstanceCreateReqDTO reqDTO) {
        return success(processInstanceService.createProcessInstance(userId, reqDTO));
    }

    @Override
    public CommonResult<Boolean> cancelProcessInstance(Long userId, @Valid BpmProcessInstanceCancelReqDTO reqDTO) {
        processInstanceService.cancelProcessInstanceByAdmin(userId,
                new BpmProcessInstanceCancelReqVO().setId(reqDTO.getId()).setReason(reqDTO.getReason()));
        return success(true);
    }

    @Override
    public CommonResult<BpmProcessInstanceStatusRespDTO> getProcessInstanceStatus(String id) {
        HistoricProcessInstance processInstance = processInstanceService.getHistoricProcessInstance(id);
        if (processInstance == null) {
            return success(null);
        }
        return success(new BpmProcessInstanceStatusRespDTO()
                .setId(processInstance.getId())
                .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
                .setStatus(FlowableUtils.getProcessInstanceStatus(processInstance))
                .setBusinessKey(processInstance.getBusinessKey()));
    }

}
