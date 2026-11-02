# 数据库脚本

| 项 | 说明 |
| --- | --- |
| 目标库 | MySQL 8.4，`utf8mb4` / `utf8mb4_0900_ai_ci` |
| 当前脚本 | `migration/V1__init_schema.sql`（29 张表） |
| 状态 | 已按设计文档 5.2 编写，并于 2026-11-02 在 MySQL 8.4.3 容器验证通过 |

## 自动迁移

使用 `local` 配置启动后端即可自动执行迁移：

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
$s = "G:\软件工程\campus-lost-found\.local\maven-settings.xml"
mvn -gs $s -s $s spring-boot:run
```

`db/migration` 只放生产结构迁移，`db/dev` 只放本地演示数据。当前版本为 `V2`；空库执行后会建出 29 张业务表，并写入 20 个分类、9 条测试帖子（其中 6 条可公开检索；另 3 条分别验证私有、待审核和过期过滤）。

## 手动执行（排障用）

```powershell
# 本机已有 MySQL80 服务占用 3306，项目容器使用 3307
docker compose -f deploy\docker-compose.dev.yml up -d mysql
docker cp db\migration\V1__init_schema.sql clf-mysql:/tmp/V1__init_schema.sql
docker exec clf-mysql mysql -uroot -pclf_dev_root --default-character-set=utf8mb4 lostfound -e "source /tmp/V1__init_schema.sql"
```

执行完检查表数量与关键索引：

```sql
SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'lostfound';
SHOW INDEX FROM item_post;
```

2026-11-02 实测：29 张表、37 个外键；全部为 InnoDB / `utf8mb4_0900_ai_ci`；`item_post` 上存在 `idx_post_feed`、`ft_post_text`、`idx_post_expire`；`claim.active_flag` 为 STORED GENERATED。

## 与测试的关系

`mvn test` 中的集成测试使用 Testcontainers 启动临时 MySQL 8.4，不依赖本机容器；默认健康检查测试不读取数据库。Testcontainers 固定使用 `1.21.4`，以兼容 Docker Engine 29。Docker 不可用时集成测试会明确失败，不使用 `disabledWithoutDocker` 静默跳过。

## 设计约定

- 主键统一 `BIGINT UNSIGNED AUTO_INCREMENT`；对外暴露用 `public_id`（ULID），不暴露自增 ID。
- 时间统一 `DATETIME(3)`，服务器时区 `Asia/Shanghai`。
- 软删除用 `deleted_at`，查询必须带 `deleted_at IS NULL`。
- 状态字段用 `VARCHAR` + 应用层枚举，不加 MySQL `ENUM`，便于后续扩展。
- `claim.active_flag` 是生成列，配合唯一索引保证「一个帖子同时只有一个进行中的认领」。
- 认领答案用 `VARBINARY` 存 AES-GCM 密文，禁止明文。

## 首次执行后要做的

1. ~~把执行结果、表数量、耗时写进 `docs/test/README.md`。~~ 已于 2026-11-02 完成。
2. 导出逆向 ER 图（DBeaver / Navicat 都可以），存到 `docs/diagrams/er-final.drawio`。
3. 如果有字段与设计文档不一致，先改文档再改脚本，避免两边漂移。
