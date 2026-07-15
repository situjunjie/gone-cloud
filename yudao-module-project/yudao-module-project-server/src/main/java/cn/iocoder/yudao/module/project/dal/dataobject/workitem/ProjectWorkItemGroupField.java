package cn.iocoder.yudao.module.project.dal.dataobject.workitem;

import cn.iocoder.yudao.module.project.enums.ProjectWorkItemQueryFieldEnum;
import cn.iocoder.yudao.module.project.enums.WorkItemFieldTypeEnum;
import lombok.Getter;

/**
 * 工作项分组字段的服务端解析结果。
 *
 * <p>物理列只能由固定字段枚举或字段类型枚举生成，不能直接使用客户端入参。</p>
 */
@Getter
public class ProjectWorkItemGroupField {

    private final Source source;
    private final String column;
    private final Long customFieldId;

    private ProjectWorkItemGroupField(Source source, String column, Long customFieldId) {
        this.source = source;
        this.column = column;
        this.customFieldId = customFieldId;
    }

    public static ProjectWorkItemGroupField fixed(ProjectWorkItemQueryFieldEnum field) {
        return new ProjectWorkItemGroupField(Source.FIXED, field.getPhysicalColumn(), null);
    }

    public static ProjectWorkItemGroupField customScalar(WorkItemFieldTypeEnum type, Long customFieldId) {
        String column = switch (type) {
            case TEXT -> "text_value";
            case NUMBER -> "number_value";
            case DATE_TIME -> "datetime_value";
            case BOOLEAN -> "boolean_value";
            case USER -> "user_value";
            case SINGLE_SELECT, MULTI_SELECT -> throw new IllegalArgumentException("select field is not scalar");
        };
        return new ProjectWorkItemGroupField(Source.CUSTOM_SCALAR, column, customFieldId);
    }

    public static ProjectWorkItemGroupField customOption(Long customFieldId) {
        return new ProjectWorkItemGroupField(Source.CUSTOM_OPTION, "option_id", customFieldId);
    }

    public boolean isFixed() {
        return source == Source.FIXED;
    }

    public boolean isCustomScalar() {
        return source == Source.CUSTOM_SCALAR;
    }

    public boolean isCustomOption() {
        return source == Source.CUSTOM_OPTION;
    }

    public enum Source {
        FIXED,
        CUSTOM_SCALAR,
        CUSTOM_OPTION
    }

}
