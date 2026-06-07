package cn.iocoder.yudao.module.devops.service.repositoryprovider.dto;

import lombok.Data;

/**
 * 代码源 compare diff 文件。
 */
@Data
public class RepositoryProviderCompareDiffDTO {

    private String oldPath;
    private String newPath;
    private Boolean newFile;
    private Boolean deletedFile;
    private Boolean renamedFile;
    private String diff;

}
