# 图表目录

W1 只建立目录与命名约定，正式图表从 W4 开始产出。

## W2 已产出（待团队核对）

| 文件 | 内容 | 状态 |
| --- | --- | --- |
| `current-flow.drawio` | 现状流程：群消息沉底、逐个询问、服务点纸质登记、无留痕 | 待访谈后修订 |
| `target-swimlane.drawio` | 目标泳道图：学生 / 服务点管理员 / 系统三方协作 | 待评审 |

打开方式：装了 draw.io Desktop 直接双击；没装就打开 <https://app.diagrams.net>，`File → Open From → Device` 选择文件。

这两张图里的流程是按需求文档和常识画的，**必须用 W2 访谈结果核对后再定稿**，尤其是“服务点现在怎么登记”这一段。

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

