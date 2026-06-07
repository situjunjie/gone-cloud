package cn.iocoder.yudao.module.devops.framework.git;

import lombok.Data;

@Data
public class GitConflictContent {

    private String baseContent;
    private String oursContent;
    private String theirsContent;
    private String workingContent;
    private String resultContent;
    private Boolean contentTooLarge;

}
