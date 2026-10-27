# 部署手册

```text
版权声明：本项目已开源，但版权属于个人所有，禁止转卖。如有问题请联系https://www.oaones.com/contract%20me
```

## 1. 前言

```text
本文介绍如何将泛微OA（E9版本）在不使用em7云桥的情况下，接入企微/钉钉/飞书等第三方平台。一切以泛微OA已有或二开接口为前提，通过接口和第三方平台进行对接。但不保证程序没有任何bug，可以提交Issue进行反馈。（开源版本中仅开放企微功能和接口）
```

## 2. 前后端架构

### 2.1 前端文件

```text
前端文件集中在 `ecology/oaones` 下，使用jsp进行页面渲染。
```

### 2.2 后端文件

```text
后端文件集中在 `ecology/src or classbean/com/customization/oaones/appsyn` 下，使用ssm架构，通过mybatis的xml进行数据库交互。
```

### 2.3 其他文件

```text
其他为配置文件，用于泛微标准安全协议等的通行等。
项目中所有文件理论上应全部为新增文件，需要修改的文件在下方单独列出。如覆盖时提示冲突，说明与原有项目文件冲突，不可使用本开源项目。需要定制二开。
```

## 3. 功能说明

### 3.1 复刻EM中云桥功能

```text
连接第三方平台的组织架构，同时细化绑定功能，增加免绑定，自动绑定等设置。同时增加定时同步功能。
支持分权,支持设置多人分组织维护，解决em只能一人维护问题。
```

### 3.2 统一身份认证

```text
重写登录页面，实现多平台统一身份认证功能，支持多企微/多钉钉/多飞书，以及微信平台。后续将支持小程序消息推送。
```

## 4. 使用文档

### 4.1 文件覆盖

```text
将文件下载后，直接覆盖到 `ecology` 目录，重启resin。
```

### 4.2 Sql执行

```text
请将项目下的data/「database」/oaones/appsyn下文件，按照顺序，进行执行。
```

### 4.3 配置oa

```text
配置白名单地址：
集成中心->统一身份认证->认证接入管理配置->白名单地址配置->统一白名单地址配置->通用白名单
如果没有该模块，请使用以下sql（sqlserver版本，其他版本请自行插入）：
INSERT INTO [Int_WhiteList] ([exclude_],[type_],[desc_],[orderNum],[creater],[modifier],[modifydate],[modifytime],[is_sys]) VALUES ('/oaones/appsyn/login/login.jsp','url','统一登录页','100','null','null,null,null,null');
```

### 4.4 配置 web.xml 和 nginx（统一身份认证）

Web.xml 中注册监听器（WEB-INF/web.xml）

```xml
<listener>
<listener-class>com.customization.oaones.appsyn.job.OaonesSyncPlanSchedulerListener</listener-class>
</listener>
```

nginx配置（单独设置了nginx配置文件）

```text
server {
    listen 443 ssl;
    server_name oatest.weavertest.com;

    ssl_certificate     /etc/nginx/ssl/oatest.weavertest.com.pem;
    ssl_certificate_key /etc/nginx/ssl/oatest.weavertest.com.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 10m; 

    access_log /var/log/nginx/oatest.weavertest.com.access.log;
    error_log  /var/log/nginx/oatest.weavertest.com.error.log;

    # 公共代理头与超时
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Host $host;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_read_timeout 300s;
    proxy_send_timeout 300s;

    # 内部登录态校验：已登录返回 200，未登录/会话失效返回 401
    location = /_oaones_session_check {
        internal;

        proxy_pass http://127.0.0.1:82/oaones/appsyn/auth/session.jsp; //路径一定要正确，务必文件可以访问
        proxy_pass_request_body off;
        proxy_set_header Content-Length "";

        proxy_set_header Host $host;
        proxy_set_header Cookie $http_cookie;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $host;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
    }

    location @oaones_login {
        return 302 /oaones/login?gopage=$uri;
    }

    # E9 前端入口：
    # 未登录 → /oaones/login
    # 已登录 → 正常代理到 E9
    location = /wui/index.html {
        auth_request /_oaones_session_check;
        error_page 401 = @oaones_login;
        proxy_pass http://127.0.0.1:82;
    }

    # E9 后端入口：
    # 未登录 → /oaones/login
    # 已登录 → 正常代理到 E9
    location = /wui/engine.html {
        auth_request /_oaones_session_check;
        error_page 401 = @oaones_login;
        proxy_pass http://127.0.0.1;
    }
    # 其它请求照旧
    location / {
        # if ($oaones_has_session = 0) {
        #     return 302 /oaones/login?gopage=$uri;
        # }
        proxy_pass http://127.0.0.1;
    }
}
```

### 4.5 配置消息订阅

```text
后端应用中心-->消息中心-->消息订阅设置
禁用原有订阅，新建如下：
名称：oaones自有订阅，
推送方式：自定义类推送，
推送类：com.customization.oaones.appsyn.msg.sub.QywxMsgReceiveSub，
数据类型：文本，
通道类型：Direct
```

### 4.6 退出页以及超时登录弹窗

```text
这里需要ecode对标准组件进行拦截和重写，可自行编写ecode或者登录oaones网站进行下载已有代码，部署ecode
或访问：
```
### 4.7 菜单URL设置

```text
1.个人账号中心：/oaones/appsyn/accountCenter/MyAccount.jsp
2.登录页：/oaones/appsyn/login/login.jsp 别名：/oaones/login，如需修改别名，请在prop中修改。
3.租户平台 企微/钉钉/飞书：/oaones/appsyn/index/TenantIndex.jsp?platform=wecom/dingtalk/feishu
4.微信绑定/oaones/appsyn/personalWx/config.jsp?showSetting=true（不加参数默认不显示appid和密钥）
5.绑定预览:/oaones/appsyn/index/BindOverview.jsp
6.微信绑定预览：/oaones/appsyn/personalWx/config.jsp
7.同步中心：/oaones/appsyn/syncCenter/syncCenter.jsp
8.会议同步：/oaones/appsyn/meetingSync/meetingSync.jsp
9.日程同步：/oaones/appsyn/scheduleSync/scheduleSync.jsp
10：考勤同步：/oaones/appsyn/attSync/attSync.jsp
11.消息推送：/oaones/appsyn/msgCenter/msgCenter.jsp
12.登录设置：/oaones/appsyn/loginConfig/config.jsp
13.应用设置：/oaones/appsyn/appCenter/AppList.jsp
14.启动台：/oaones/appsyn/appCenter/Desktop.jsp
15.接口日志中心:/oaones/appsyn/apiLog/apiLog.jsp
16.操作监控平台:/oaones/appsyn/opMonitor/opMonitor.jsp
17.统一认证报表:/oaones/appsyn/authReport/authReport.jsp
18.分部租户映射：/oaones/appsyn/orgMap/orgMap.jsp
19.人员同步日志：/oaones/appsyn/syncCenter/syncResult.jsp
指定平台和租户：/oaones/appsyn/syncCenter/syncResult.jsp?platform=wecom&cfg_id=123&requestid=456
跨平台按流程查询：/oaones/appsyn/syncCenter/syncResult.jsp?requestid=456
按同步批次查询：/oaones/appsyn/syncCenter/syncResult.jsp?platform=wecom&cfg_id=123&log_id=789
hide_filters=1：隐藏全部搜索条件。
hide_summary=1：隐藏统计行和明细截断提示。
20.日志设置（系统设置）：/oaones/appsyn/systemSettings/systemSettings.jsp
```
