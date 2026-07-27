package cn.iocoder.yudao.module.project.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 项目内权限点。权限目录由系统固定定义。
 */
@Getter
@AllArgsConstructor
public enum ProjectPermissionEnum {

    PROJECT_QUERY("查看项目资料", true, true),
    PROJECT_UPDATE("修改项目资料", false, false),
    PROJECT_ARCHIVE("归档项目", false, false),
    MEMBER_QUERY("查看项目成员", true, false),
    SPRINT_CREATE("创建迭代", false, false),
    WORK_ITEM_QUERY("查询工作项", true, true),
    WORK_ITEM_CREATE("创建工作项", true, false),
    WORK_ITEM_UPDATE("修改工作项", true, false),
    WORK_ITEM_DELETE("删除工作项", false, false),
    WORK_ITEM_FIELD_QUERY("查询项目字段", true, false),
    WORK_ITEM_FIELD_MANAGE("管理项目字段", false, false),
    COMMENT_QUERY("查询评论", true, false),
    COMMENT_CREATE("创建评论", true, false),
    COMMENT_UPDATE("修改评论", true, false),
    COMMENT_DELETE("删除评论", true, false);

    private final String name;
    private final boolean participantDefault;
    private final boolean participantRequired;

    public static final Set<String> ALL_CODES;
    public static final Set<String> PARTICIPANT_DEFAULT_CODES;
    public static final Set<String> PARTICIPANT_REQUIRED_CODES;

    static {
        Set<String> allCodes = new LinkedHashSet<>();
        Set<String> participantDefaultCodes = new LinkedHashSet<>();
        Set<String> participantRequiredCodes = new LinkedHashSet<>();
        for (ProjectPermissionEnum permission : values()) {
            allCodes.add(permission.name());
            if (permission.isParticipantDefault()) {
                participantDefaultCodes.add(permission.name());
            }
            if (permission.isParticipantRequired()) {
                participantRequiredCodes.add(permission.name());
            }
        }
        ALL_CODES = Collections.unmodifiableSet(allCodes);
        PARTICIPANT_DEFAULT_CODES = Collections.unmodifiableSet(participantDefaultCodes);
        PARTICIPANT_REQUIRED_CODES = Collections.unmodifiableSet(participantRequiredCodes);
    }

    public static boolean contains(String code) {
        return code != null && ALL_CODES.contains(code);
    }

}
