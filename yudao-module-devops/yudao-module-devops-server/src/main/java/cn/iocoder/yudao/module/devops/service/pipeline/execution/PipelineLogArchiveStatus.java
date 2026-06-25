package cn.iocoder.yudao.module.devops.service.pipeline.execution;

/**
 * 流水线日志归档状态。
 */
public interface PipelineLogArchiveStatus {

    String PENDING = "PENDING";
    String LOCAL_ONLY = "LOCAL_ONLY";
    String UPLOADED = "UPLOADED";
    String FAILED = "FAILED";
    String SKIPPED_TOO_LARGE = "SKIPPED_TOO_LARGE";

}
