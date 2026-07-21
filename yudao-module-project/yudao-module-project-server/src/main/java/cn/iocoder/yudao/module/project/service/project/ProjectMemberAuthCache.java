package cn.iocoder.yudao.module.project.service.project;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMemberAuthCache implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private boolean member;
    private Long memberId;
    private Set<Long> roleIds = new LinkedHashSet<>();
    private boolean admin;

    public static ProjectMemberAuthCache notMember() {
        return new ProjectMemberAuthCache(false, null, new LinkedHashSet<>(), false);
    }

}
