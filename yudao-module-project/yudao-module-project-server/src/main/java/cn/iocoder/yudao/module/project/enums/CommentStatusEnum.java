package cn.iocoder.yudao.module.project.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CommentStatusEnum {

    NORMAL("NORMAL", "正常"),
    DELETED("DELETED", "已删除");

    private final String value;
    private final String name;

}
