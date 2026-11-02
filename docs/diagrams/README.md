# 图表目录

项目已进入设计收尾与开发初期。已有流程图保留为设计输入，当前不再等待访谈结论。

## 已产出

| 文件 | 内容 | 状态 |
| --- | --- | --- |
| `current-flow.drawio` | 现状流程：群消息沉底、逐个询问、服务点纸质登记、无留痕 | 历史基线 |
| `target-swimlane.drawio` | 目标泳道图：学生 / 服务点管理员 / 系统三方协作 | 可继续细化 |

打开方式：装了 draw.io Desktop 直接双击；没装就打开 <https://app.diagrams.net>，`File → Open From → Device` 选择文件。

后续如设计评审发现流程差异，按变更记录修订，不重新启动需求调研。

## 设计收尾清单

| 图 | 工具 | 文件名 | 优先级 |
| --- | --- | --- | --- |
| 用例图 | PlantUML | `use-case.puml` | P0 |
| 业务流程图 / 泳道图 | draw.io | `claim-flow.drawio` | P0 |
| 活动图 | draw.io | `publish-activity.drawio` | P1 |
| 状态图 | PlantUML | `item-state.puml` | P0 |
| ER 图 | draw.io | `er-model.drawio` | P0 |
| 类图 | PlantUML | `domain-class.puml` | P1 |
| 认领时序图 | PlantUML | `claim-sequence.puml` | P0 |
| 部署图 | draw.io | `deployment.drawio` | P1 |
| PAD 图 | draw.io | `claim-pad.drawio` | P1 |

规则：

1. 每张图同时提交源文件（`.puml` / `.drawio`）和导出文件（`.svg`）。
2. 图内文字与需求文档用词一致，例如统一叫“认领申请”，不要混用“申领”“索取”。
3. 图内不写实现细节（表名、类名可以出现在 ER/类图，流程图中不要出现）。

