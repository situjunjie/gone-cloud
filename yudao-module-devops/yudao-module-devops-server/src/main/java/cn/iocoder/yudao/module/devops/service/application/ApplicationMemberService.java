package cn.iocoder.yudao.module.devops.service.application;

import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationMemberAddReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationMemberRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationMemberTransferOwnerReqVO;

import java.util.List;

/**
 * DevOps 应用成员 Service 接口。
 *
 * <p>所有操作均要求请求者是应用拥有者（{@code dev_application.owner_user_id}）。
 */
public interface ApplicationMemberService {

    /**
     * 获得应用成员列表（包含拥有者，拥有者优先，其余按加入时间升序）。
     *
     * @param appId          应用编号
     * @param operatorUserId 请求者用户编号
     * @return 成员列表
     */
    List<ApplicationMemberRespVO> getApplicationMemberList(Long appId, Long operatorUserId);

    /**
     * 批量添加参与者。已存在的成员直接跳过（幂等），拥有者自身也会被跳过。
     *
     * @param reqVO          新增请求
     * @param operatorUserId 请求者用户编号
     */
    void addApplicationMembers(ApplicationMemberAddReqVO reqVO, Long operatorUserId);

    /**
     * 移除参与者。按应用编号 + 用户编号定位，不能移除拥有者。
     *
     * @param appId          应用编号
     * @param userId         被移除的用户编号
     * @param operatorUserId 请求者用户编号
     */
    void removeApplicationMember(Long appId, Long userId, Long operatorUserId);

    /**
     * 转移应用拥有者。目标成为拥有者，原拥有者成为参与者，并同步 {@code dev_application.owner_user_id}。
     *
     * @param reqVO          转移请求
     * @param operatorUserId 请求者用户编号
     */
    void transferApplicationOwner(ApplicationMemberTransferOwnerReqVO reqVO, Long operatorUserId);

}
