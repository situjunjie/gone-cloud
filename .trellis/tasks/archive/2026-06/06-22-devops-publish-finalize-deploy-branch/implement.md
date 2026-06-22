# Implement

## Checklist

- [ ] 调整 `ChangePublishFinalizeStepHandler`，从“逐个 change finalize”改为“按 run finalize”调用
- [ ] 扩展 `ChangeService` / `ChangeServiceImpl`，实现“merge 部署分支 + 批量发布变更状态 + 删除变更分支和部署分支”
- [ ] 保持错误码、日志、幂等删除行为一致，必要时补充新校验
- [ ] 更新 focused tests：
  - [ ] `ChangePublishFinalizeStepHandlerTest`
  - [ ] `ChangeServiceImplTest`
  - [ ] `PipelineSpecValidationServiceImplTest`（如果断言文案需要变更）
- [ ] 同步更新相关 Trellis spec
- [ ] 运行 devops server focused tests 并确认通过

## Validation Commands

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='ChangeServiceImplTest,ChangePublishFinalizeStepHandlerTest,PipelineSpecValidationServiceImplTest,RepositoryProviderServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

## Risky Files

- `.../service/change/ChangeServiceImpl.java`
- `.../service/pipeline/execution/handler/ChangePublishFinalizeStepHandler.java`
- `.../service/change/ChangeService.java`
- `.../service/pipeline/PipelineSpecValidationServiceImpl.java`
- `.../src/test/java/.../ChangeServiceImplTest.java`

## Review Gate Before Start

- 明确 merge 源分支固定为 `PipelineRunDO.branchName`
- 明确清理范围包含变更分支和部署分支
- 保持平台步骤 `ChangePublishFinalize` 的 YAML 形状不变
