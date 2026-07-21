package cn.iocoder.yudao.module.project.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProjectRoleBuiltinTypeEnum {

    ADMIN("ADMIN", "管理员"),
    PARTICIPANT("PARTICIPANT", "参与人"),
    NONE("NONE", null);

    private final String value;
    private final String roleName;

    public static boolean isBuiltin(String value) {
        return ADMIN.value.equals(value) || PARTICIPANT.value.equals(value);
    }

}
