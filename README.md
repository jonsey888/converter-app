# ⚡ 文件格式转换器

一个基于浏览器的文件格式转换工具，支持多种文本格式互转及图片格式转换，数据不上传至服务器。

## 功能特性

### 文本格式转换
| 转换类型 | 说明 |
|---|---|
| CSV → JSON | 将表格数据转为 JSON 数组 |
| JSON → CSV | 将 JSON 数组转为 CSV 表格 |
| Markdown → HTML | 将 Markdown 渲染为 HTML（含实时预览） |
| JSON ↔ YAML | JSON 与 YAML 互转 |
| JSON ↔ XML | JSON 与 XML 互转 |
| JSON ↔ TOML | JSON 与 TOML 互转 |

### 图片格式转换
- 支持 PNG、JPG、WebP 之间的互转
- 可调节输出质量（0.1 ~ 1.0）
- 支持拖拽上传图片

### 其他特性
- 支持拖拽文件直接导入
- 转换结果可一键下载
- 数据仅在本地处理，不存储于服务器

## 技术栈

| 层级 | 技术 |
|---|---|
| 后端 | Java 17 + Spring Boot 3.2 |
| 前端 | Vue 3 + Vite 5 |
| 图片编解码 | Java ImageIO + TwelveMonkeys WebP |
| Markdown 解析 | commonmark-java |

## 项目结构

```
converter-app/
├── backend/                  # Spring Boot 后端
│   ├── src/main/java/com/converter/
│   │   ├── controller/       # REST 接口（/api/convert/text、/api/convert/image）
│   │   ├── service/          # 转换逻辑（文本、图片）
│   │   └── dto/              # 请求/响应数据对象
│   └── pom.xml
└── frontend/                 # Vue 3 前端
    ├── src/
    │   ├── App.vue            # 单页应用主组件
    │   └── main.js
    ├── index.html
    └── package.json
```

## 本地运行

### 前置条件
- Java 17+
- Node.js 18+
- Maven 3.6+

### 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端默认监听 `http://localhost:8080`，文件上传限制为 50MB。

### 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`，开发模式下自动将 `/api` 请求代理到后端。

## API 接口

### 文本转换

```
POST /api/convert/text
Content-Type: application/json

{
  "type": "csv-json",   // 转换类型，见上方表格
  "input": "..."        // 输入内容
}
```

### 图片转换

```
POST /api/convert/image
Content-Type: multipart/form-data

file=<图片文件>
format=jpeg|png|webp   （默认 jpeg）
quality=0.92           （0.1~1.0，默认 0.92）
```
