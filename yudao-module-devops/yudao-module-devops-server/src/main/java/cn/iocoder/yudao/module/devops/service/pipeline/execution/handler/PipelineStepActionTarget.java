package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import lombok.Builder;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流水线步骤动作目标。
 */
@Data
@Builder
public class PipelineStepActionTarget {

    /**
     * 前端路由名称。
     */
    private String routeName;
    /**
     * 前端路由或下载路径。
     */
    private String path;
    /**
     * 下载地址。
     */
    private String url;
    /**
     * 后端 API 路径。
     */
    private String apiPath;
    /**
     * HTTP 方法。
     */
    private String method;
    /**
     * 文件名。
     */
    private String fileName;
    /**
     * 确认提示。
     */
    private String confirmText;
    /**
     * 路由参数。
     */
    @Builder.Default
    private Map<String, Object> params = new LinkedHashMap<>();
    /**
     * 查询参数。
     */
    @Builder.Default
    private Map<String, Object> query = new LinkedHashMap<>();
    /**
     * API 请求体。
     */
    @Builder.Default
    private Map<String, Object> body = new LinkedHashMap<>();

}
