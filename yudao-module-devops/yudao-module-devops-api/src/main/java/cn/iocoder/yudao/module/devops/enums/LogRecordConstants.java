package cn.iocoder.yudao.module.devops.enums;

/**
 * DevOps 操作日志常量。
 */
public interface LogRecordConstants {

    String DEVOPS_ENVIRONMENT_TYPE = "DEVOPS 环境";

    String DEVOPS_DOCKER_CONTAINER_START_SUB_TYPE = "启动 Docker 容器";
    String DEVOPS_DOCKER_CONTAINER_STOP_SUB_TYPE = "停止 Docker 容器";
    String DEVOPS_DOCKER_CONTAINER_RESTART_SUB_TYPE = "重启 Docker 容器";
    String DEVOPS_DOCKER_COMPOSE_START_SUB_TYPE = "启动 Docker Compose 项目";
    String DEVOPS_DOCKER_COMPOSE_STOP_SUB_TYPE = "停止 Docker Compose 项目";

    String DEVOPS_DOCKER_CONTAINER_START_SUCCESS = "启动 Docker 容器：{{#containerId}}";
    String DEVOPS_DOCKER_CONTAINER_STOP_SUCCESS = "停止 Docker 容器：{{#containerId}}";
    String DEVOPS_DOCKER_CONTAINER_RESTART_SUCCESS = "重启 Docker 容器：{{#containerId}}";
    String DEVOPS_DOCKER_COMPOSE_START_SUCCESS = "启动 Docker Compose 项目：{{#projectName}}";
    String DEVOPS_DOCKER_COMPOSE_STOP_SUCCESS = "停止 Docker Compose 项目：{{#projectName}}";

}
