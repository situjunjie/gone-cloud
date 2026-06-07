package cn.iocoder.yudao.module.devops.framework.git;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GitFileResolution {

    private String filePath;
    private String resolvedContent;

}
