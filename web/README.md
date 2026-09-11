# genealogy-web（P0 家族树只读竖切）

本地 Vue 3 + TypeScript 前端，用 **Vue Flow** 展示家族**网络图**（非单父树）。  
数据默认来自 typed fixture，也可经 env 切换到真实：

```http
GET /api/v1/families/{familyId}/graph?rootPersonId=&depth=
```

## 原则（硬）

| 项 | 说明 |
|----|------|
| **只读投影** | 权威数据 = Person + Relationship + Marriage/Union；树/图画布仅为视图 |
| **对接未来 API** | 经 `src/features/tree/api/graphClient.ts` 切换；默认仍用 fixture |
| **禁整树写回** | 画布拖拽仅本地位移；不提供、不调用整树 `PUT`；写路径仅单笔 person / relationship / marriage |
| **无 parent_id** | 亲子仅来自 `relationships[]` |
| **代数不持久化** | 布局层号 = 相对焦点人物；P0 不读写世代权威字段 |
| **长辈字号** | 树姓名 **18px** / semibold；详情主姓名 **22px**；默认 zoom **100%** |

### depth 换算

默认视图「上 1 / 下 2」且含焦点层 ⇒ 请求 **`depth=3`**  
（`depth = up + down`，焦点层计入窗内：`up1 + down2 + 焦点 ⇒ depth=3`）

## 启动

```bash
cd genealogy-web
npm install
npm run dev
```

浏览器打开终端提示的本地地址（通常 `http://localhost:5173`）。

- 单击人物节点 → 打开详情抽屉（父母 / 配偶 / 子女）
- 双击人物 → 切换布局焦点
- 顶栏截断横幅：fixture 带 `truncated: true` 时显示「已达展开上限」

## 切换 graph 数据源（env）

**默认仍为 mock fixture**（`VITE_USE_GRAPH_API` 未设或 `false`）。

复制 `.env.example` 为 `.env`（或 `.env.local`）后按需修改：

```bash
# false / 未设置 → 本地 fixture（MOCK / 待换真 API）
VITE_USE_GRAPH_API=false

# 真 API 基址；设置非空也会切到真 API。留空 = 同源相对路径
VITE_GRAPH_API_BASE=
```

切到真 API 的两种方式（任一即可）：

1. `VITE_USE_GRAPH_API=true`
2. 设置非空 `VITE_GRAPH_API_BASE`（例如 `http://localhost:8080`）

真请求形如：

```http
GET {VITE_GRAPH_API_BASE}/api/v1/families/{familyId}/graph?rootPersonId=&depth=
```

路径**必须带 familyId**（B1）；不接受无 family 的 person-only 路由。  
P0 无 `asOf`；响应可含 ended unions；禁整树写回。

入口：`fetchFamilyGraph`（`src/features/tree/api/graphClient.ts`）。

## 测试与构建

```bash
npm test          # vitest：unionLayout 单测
npm run build     # vue-tsc + vite build
```

## 关键路径

```
src/features/tree/
  api/types.ts                 # GraphProjection 等类型
  api/fixture.ts               # 陈父离婚再婚演示数据（MOCK）
  api/graphClient.ts           # fetchFamilyGraph：fixture ↔ 真 API 切换点
  layout/unionLayout.ts        # 纯函数布局：Person+Marriage+Relationship → nodes/edges
  layout/unionLayout.test.ts   # 离婚再婚 / 双亲 / 多父 / 单亲
  canvas/TreeCanvas.vue        # Vue Flow 壳
  canvas/PersonNode.vue        # 姓名 18px semibold
  canvas/UnionNode.vue         # 婚姻锚点（结束=虚线）
  panels/PersonDetailDrawer.vue
  state/treeViewStore.ts       # Pinia（loadDemo → fetchFamilyGraph）
src/shared/typography/tokens.css
src/pages/TreePage.vue
.env.example                   # VITE_USE_GRAPH_API / VITE_GRAPH_API_BASE
```

## 明确不做（本竖切）

- 不克隆 / 不推送 `guijs/genealogy`
- 默认不接真实后端；可用 env 切换，仍不写整树 API
- 不持久化代数 / 世代号
- asOf 历史时点视图 → P1
