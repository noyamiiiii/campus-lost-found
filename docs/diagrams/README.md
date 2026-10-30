# 图表目录

W1 只建立目录与命名约定，正式图表从 W4 开始产出。

| 图 | 工具 | 文件名 | 计划周次 |
| --- | --- | --- | --- |
| 用例图 | PlantUML | `use-case.puml` | W3 |
| 业务流程图 / 泳道图 | draw.io | `claim-flow.drawio` | W4 |
| 活动图 | draw.io | `publish-activity.drawio` | W4 |
| 状态图 | PlantUML | `item-state.puml` | W6 |
| ER 图 | draw.io | `er-model.drawio` | W6 |
| 类图 | PlantUML | `domain-class.puml` | W7 |
| 时序图 | PlantUML | `claim-sequence.puml` | W7 |
| 部署图 | draw.io | `deployment.drawio` | W8 |
| PAD 图 | draw.io | `claim-pad.drawio` | W7 |

规则：

1. 每张图同时提交源文件（`.puml` / `.drawio`）和导出文件（`.svg`）。
2. 图内文字与需求文档用词一致，例如统一叫“认领申请”，不要混用“申领”“索取”。
3. 图内不写实现细节（表名、类名可以出现在 ER/类图，流程图中不要出现）。

