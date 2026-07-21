package cn.iocoder.yudao.module.project.service.project;

import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectMemberAddReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectMemberRespVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectMemberUpdateRoleReqVO;

import java.util.List;
import java.util.Map;

public interface ProjectMemberService {

    void initializeProjectMembers(Long projectId, Long creatorUserId, Long ownerUserId,
                                  Map<String, Long> builtinRoleIds);

    Long addProjectMember(ProjectMemberAddReqVO reqVO);

    void updateProjectMemberRoles(ProjectMemberUpdateRoleReqVO reqVO);

    void removeProjectMember(Long memberId);

    List<ProjectMemberRespVO> getProjectMemberList(Long projectId);

    Long getProjectMemberCount(Long projectId);

    void ensureOwnerAdmin(Long projectId, Long ownerUserId);

}
