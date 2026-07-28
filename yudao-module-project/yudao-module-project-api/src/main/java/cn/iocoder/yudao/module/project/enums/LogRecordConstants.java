package cn.iocoder.yudao.module.project.enums;

/**
 * 项目模块操作日志常量。
 */
public interface LogRecordConstants {

    String PROJECT_WORK_ITEM_TYPE = "项目工作项";
    String PROJECT_WORK_ITEM_UPDATE_SUB_TYPE = "更新工作项";
    String PROJECT_WORK_ITEM_UPDATE_SUCCESS = "{{#workItemChange.summary}}";

    String PROJECT_WORKLOG_TYPE = "项目工时";
    String PROJECT_WORKLOG_CREATE_SUB_TYPE = "创建工时";
    String PROJECT_WORKLOG_CREATE_SUCCESS = "创建工时记录 {{#_ret}}，工作项 {{#reqVO.workItemId}}，"
            + "类型 {{#reqVO.type}}，工时 {{#reqVO.hours}}";
    String PROJECT_WORKLOG_UPDATE_SUB_TYPE = "更新工时";
    String PROJECT_WORKLOG_UPDATE_SUCCESS = "更新工时记录 {{#updateReqVO.id}}，工作项 {{#worklog.workItemId}}，"
            + "工时 {{#updateReqVO.hours}}，类别 {{#updateReqVO.category}}";
    String PROJECT_WORKLOG_DELETE_SUB_TYPE = "删除工时";
    String PROJECT_WORKLOG_DELETE_SUCCESS = "删除工时记录 {{#id}}，工作项 {{#worklog.workItemId}}";

}
