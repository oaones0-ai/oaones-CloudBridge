<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%
    /* 三个平台共用本片段：平台由引入方决定（request 属性 oaonesPBPlatform 优先，其次 platform 参数）；
       平台文案统一用 oaonesPBShort（企微 / 钉钉 / 飞书）与 oaonesPBFull（企业微信 / 钉钉 / 飞书）。 */
    String oaonesPBPlatform = (String) request.getAttribute("oaonesPBPlatform");
    if (oaonesPBPlatform == null || oaonesPBPlatform.trim().length() == 0) {
        oaonesPBPlatform = request.getParameter("platform");
    }
    if (oaonesPBPlatform == null) {
        oaonesPBPlatform = "";
    }
    oaonesPBPlatform = oaonesPBPlatform.trim().toLowerCase();
    boolean oaonesPBDd = "dingtalk".equals(oaonesPBPlatform);
    boolean oaonesPBFs = "feishu".equals(oaonesPBPlatform);
    String oaonesPBFull = oaonesPBFs ? "飞书" : (oaonesPBDd ? "钉钉" : "企业微信");
    String oaonesPBShort = oaonesPBFs ? "飞书" : (oaonesPBDd ? "钉钉" : "企微");
    // 租户标识 / 应用标识在各平台的叫法：企微 corpid + AgentId，钉钉 AppKey + AgentId，飞书 App ID + App ID
    String oaonesPBKeyLabel = oaonesPBFs ? "App ID" : (oaonesPBDd ? "AppKey" : "corpid");
    String oaonesPBAppIdLabel = oaonesPBFs ? "App ID" : "AgentId";
%>

<%--
  统一消息中心 → 推送日志
  每次调用平台接口都留一条日志（分片时一片一条），支持多维检索、详情查看（含报文与异常栈）与一键重推。
--%>
<div class="help-source" id="msgLogHelpText">
    本页是<strong>推送状态</strong>视图：一行一次推送（分片时一片一条），记录业务单号 / 消息类型 / 接收人 / 分片 /
    去重键 / 重试计划与推送结果。<strong>原始入参</strong>随本条推送保留；
    <strong><%= oaonesPBShort %>请求报文、返回码与返回报文、异常堆栈、耗时</strong>统一保存在
    <strong>「接口日志中心」</strong>（同一次接口调用全库只存一份），本页按关联网关取回来展示——同一份数据不再重复保存。
    失败记录<strong>标红高亮</strong>，行内“重推”可对失败消息一键重发（无需重新发起流程，报文从接口日志取）。
    <strong>重推（含定时重试）每推一次单独留一条记录</strong>：原失败记录保留自己的报文与失败原因，
    重推成功的那条状态列显示<strong>“重推后成功”</strong>（重推一次就是两条记录，重推几次就有几条）；
    被后续重推取代的失败记录会在结果列同样标注<strong>重推后成功</strong>，
    并且<strong>这类记录与推送成功 / 已跳过 / 重复拦截的记录都不能再重推</strong>（按钮置灰）。
    分片发送时一片一条日志，用“分片”列看出第几片 / 共几片。
     “跳转地址”列是这条消息<strong>实际发出去的可点链接</strong>（文本卡片 / 模板卡片 / 图文 / 任务卡片的 url；
    文本消息取正文里拼进去的网址），可直接点开核对是否指向流程详情页；
    卡片没带链接（跳转地址取不到）时该列为空，详情里的“<%= oaonesPBShort %>请求报文”能看到具体报文
    （报文来自接口日志中心；若接口日志已按保留天数清理，这里会提示“没有可查看的报文”）。
    “已跳过 / 去重拦截”这类<strong>没有实际调用接口</strong>的记录只在状态表里，结果列显示的是业务原因。
    详情弹框里<strong>每段报文都有“复制”按钮</strong>（复制时带上段落标题），
    底部“复制全部报文”一次复制本条日志的跳转地址 + 原始入参 + 请求/返回报文 + 异常堆栈，
    右上角“全屏”可整页铺开核对报文（再点一次或按 Esc 退出，Esc 在全屏时只退全屏、再按才关弹框）。
    <br>“原始入参”是 OA 标准消息的原文，里面的 <code>~`~`7 中文`~`8 English`~`~</code> 是泛微的
    <strong>多语言标记</strong>（流程名 / 人员姓名等字段）；实际下发的“<%= oaonesPBShort %>请求报文”已按
    接收人的 OA 语言取成纯文本，<%= oaonesPBFull %>不会收到这些标记。
    <span class="rule">一次最多取出 1000 条，超出请缩小时间范围。</span>
</div>

<div class="filter-bar">
    <span class="filter-label">时间</span>
    <%-- 日期时间文本框统一走 js/datetime.js（日历 + 时/分/秒三列 + 手工输入），
         “起 / 止”空值初值分别按 00:00:00 / 23:59:59 补齐，正好覆盖整天 --%>
    <span class="dt-field" style="width:206px;">
        <input type="text" id="msgLogFrom" data-oaones-date="datetime" data-oaones-date-time="00:00:00"
               autocomplete="off" data-lpignore="true" placeholder="起 yyyy-MM-dd HH:mm:ss">
    </span>
    <span class="filter-label">~</span>
    <span class="dt-field" style="width:206px;">
        <input type="text" id="msgLogTo" data-oaones-date="datetime" data-oaones-date-time="23:59:59"
               autocomplete="off" data-lpignore="true" placeholder="止 yyyy-MM-dd HH:mm:ss">
    </span>
    <span class="filter-label">业务单号/标题</span>
    <input type="text" id="msgLogBizId" autocomplete="off" data-lpignore="true" style="width:150px;">
    <%-- 日志里的 std_type 是**标准消息来源编号**（typecode），按来源筛；消息类型看列表“消息类型”列 --%>
    <span class="filter-label">消息来源</span>
    <select id="msgLogStdType" name="msgLogStdType" style="width:190px;"></select>
    <span class="filter-label">状态</span>
    <select id="msgLogStatus" name="msgLogStatus" style="width:120px;">
        <option value="">全部</option>
        <option value="1">推送成功（含重推后成功）</option>
        <option value="2">推送失败</option>
        <option value="3">重复拦截</option>
        <option value="4">已跳过</option>
        <option value="0">待推送</option>
    </select>
    <span class="filter-label">接收人/错误</span>
    <input type="text" id="msgLogKeyword" autocomplete="off" data-lpignore="true" style="width:150px;">
    <button class="btn" id="msgLogSearchBtn">查询</button>
    <button class="btn secondary" id="msgLogResetBtn">重置</button>
    <span class="help-tip" data-help="msgLogHelpText" role="button" tabindex="0"
          title="查看日志说明" aria-label="查看日志说明">?</span>
</div>

<div class="table-wrap">
    <table class="grid" id="msgLogTable">
        <thead>
        <tr>
            <th style="width:150px;">推送时间</th>
            <th style="width:120px;">业务单号</th>
            <th style="width:140px;">消息类型</th>
            <th style="width:110px;"><%= oaonesPBShort %>形态</th>
            <th style="width:120px;">接收人</th>
            <th style="width:100px;">跳转地址</th>
            <th style="width:90px;">状态</th>
            <th style="width:70px;">耗时</th>
            <th style="width:110px;">重试/下次</th>
            <th style="width:130px;">结果</th>
            <th style="width:120px;">操作</th>
        </tr>
        </thead>
        <tbody id="msgLogBody"></tbody>
    </table>
</div>
<div class="pager" id="msgLogPager"></div>

<div class="modal-mask" id="msgLogDetailMask"></div>
<div class="modal" id="msgLogDetailModal" style="width:900px;display:none;">
    <div class="modal-head">
        <span id="msgLogDetailTitle">推送日志详情</span>
        <span class="modal-head-actions">
            <button type="button" class="btn small secondary" id="msgLogFullBtn"
                    title="整页全屏查看报文（再点一次或按 Esc 退出）">全屏</button>
            <button type="button" class="modal-close" id="msgLogDetailClose">×</button>
        </span>
    </div>
    <div class="modal-body" style="max-height:70vh;overflow:auto;">
        <%-- 摘要按普通文本大小排版（.detail-summary）：单号 / 类型 / 时间都是文本，
             不用数据看板那套大数字样式；文本消息的链接提示单独一行放在下面 --%>
        <div class="summary detail-summary" id="msgLogDetailSummary"></div>
        <div class="detail-tip" id="msgLogTextTip" style="display:none;margin-top:10px;"></div>
        <div class="form-row" id="msgLogJumpUrlRow" style="margin-top:14px;">
            <label>跳转地址</label>
            <div class="field">
                <div class="detail-bar">
                    <button type="button" class="btn small secondary" data-copy="msgLogJumpUrl"
                            title="复制这条消息实际下发的跳转地址">复制</button>
                </div>
                <div class="detail" id="msgLogJumpUrl"></div>
            </div>
        </div>
        <div class="form-row" style="margin-top:14px;">
            <label>原始入参</label>
            <div class="field">
                <div class="detail-bar">
                    <button type="button" class="btn small secondary" data-copy="msgLogSrc"
                            title="复制 OA 标准消息的原始入参">复制</button>
                </div>
                <div class="detail-tip" id="msgLogSrcTip" style="display:none;"></div>
                <div class="detail" id="msgLogSrc"></div>
            </div>
        </div>
        <div class="form-row">
            <label><%= oaonesPBShort %>请求报文</label>
            <div class="field">
                <div class="detail-bar">
                    <button type="button" class="btn small secondary" data-copy="msgLogRequest"
                            title="复制实际发给<%= oaonesPBFull %>的请求报文">复制</button>
                </div>
                <div class="detail" id="msgLogRequest"></div>
            </div>
        </div>
        <div class="form-row">
            <label><%= oaonesPBShort %>返回报文</label>
            <div class="field">
                <div class="detail-bar">
                    <button type="button" class="btn small secondary" data-copy="msgLogResponse"
                            title="复制<%= oaonesPBFull %>返回的报文">复制</button>
                </div>
                <div class="detail" id="msgLogResponse"></div>
            </div>
        </div>
        <div class="form-row" id="msgLogStackRow">
            <label>异常堆栈</label>
            <div class="field">
                <div class="detail-bar">
                    <button type="button" class="btn small secondary" data-copy="msgLogStack"
                            title="复制异常堆栈">复制</button>
                </div>
                <div class="detail" id="msgLogStack"></div>
            </div>
        </div>
    </div>
    <div class="modal-foot">
        <button class="btn secondary" id="msgLogCopyAllBtn"
                title="把本条的跳转地址、原始入参、请求/返回报文与异常堆栈按段落一起复制">复制全部报文</button>
        <button class="btn secondary" id="msgLogRetryBtn">重推该条</button>
        <button class="btn" id="msgLogDetailOkBtn">关闭</button>
    </div>
</div>

<script>
"use strict";
(function () {
    function byId(id) { return document.getElementById(id); }
    function esc(v) {
        return String(v == null ? "" : v)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }
    function toast(msg, mode) {
        if (window.OaonesToast) { window.OaonesToast.show(msg, { mode: mode }); }
    }
    function value(id) { var el = byId(id); return el ? String(el.value == null ? "" : el.value).trim() : ""; }

    var rows = [];
    var currentId = "";
    var currentLog = null;
    var bound = false;
    var pager = null;
    /** 进入全屏前弹框的位置（拖动过的话退出全屏要还原），null = 未进入全屏。 */
    var fullSavedBox = null;

    /** 各报文块的标题：复制时带上，粘到聊天 / 工单里能一眼看出是哪一段。 */
    var COPY_BLOCKS = {
        msgLogJumpUrl: { title: "【跳转地址】", name: "跳转地址" },
        msgLogSrc: { title: "【原始入参】", name: "原始入参" },
        msgLogRequest: { title: "【企微请求报文】", name: "企微请求报文" },
        msgLogResponse: { title: "【企微返回报文】", name: "企微返回报文" },
        msgLogStack: { title: "【异常堆栈】", name: "异常堆栈" }
    };

    function statusHtml(row) {
        var status = String(row.status);
        var cls = status === "1" ? "bound"
            : (status === "2" ? "fail"
                : (status === "3" ? "nobind" : (status === "4" ? "off" : "unbound")));
        return "<span class=\"tag " + cls + "\">" + statusLabel(row) + "</span>";
    }

    /** 状态文案（列表与详情共用）：重推（含定时重试）成功的那条是单独一条记录，标成“重推后成功”。 */
    function statusLabel(row) {
        var status = String(row.status);
        if (status === "1") {
            return Number(row.retryCount == null ? 0 : row.retryCount) > 0 ? "重推后成功" : "推送成功";
        }
        if (status === "2") { return "推送失败"; }
        if (status === "3") { return "重复拦截"; }
        if (status === "4") { return "已跳过"; }
        return "待推送";
    }

    /**
     * 推送链的键：同一条消息的各次尝试（首发 + 每次重推）共用它。
     * 优先用去重键（标准消息 / 带业务单号的推送都有）；页面测试推送这类没有业务单号、
     * 也就没有去重键的记录，用“来源类型 + 消息类型 + 形态 + 接收人 + 标题”兜底。
     */
    function chainKeyOf(row) {
        if (!row) { return ""; }
        var key = String(row.dedupKey == null ? "" : row.dedupKey).trim();
        if (key) { return "d:" + key; }
        return "b:" + [row.bizType, row.bizId, row.typeName, row.wxMsgtype, row.receiverWx,
            String(row.bizTitle == null ? "" : row.bizTitle).trim()].join("|");
    }

    /* 每条推送链上的三个位置：最新一次尝试 / 最新一次重推（retry_count > 0）/ 最新一次成功，
       用来判断一条失败记录是不是“已经被后续重推取代（甚至已经重推成功）”。 */
    var chainLatest = {};
    var chainRetry = {};
    var chainSuccess = {};

    function refreshChains() {
        chainLatest = {};
        chainRetry = {};
        chainSuccess = {};
        rows.forEach(function (row) {
            var key = chainKeyOf(row);
            if (!key) { return; }
            var id = Number(row.id) || 0;
            if (!chainLatest[key] || id > chainLatest[key]) { chainLatest[key] = id; }
            if (Number(row.retryCount == null ? 0 : row.retryCount) > 0
                    && (!chainRetry[key] || id > chainRetry[key])) {
                chainRetry[key] = id;
            }
            if (String(row.status) === "1" && (!chainSuccess[key] || id > chainSuccess[key])) {
                chainSuccess[key] = id;
            }
        });
    }

    /**
     * 这条失败记录是否已被后续重推取代（链里之后确实发生过重推）。
     * 取代后它只是历史：结果（含失败原因）照常展示，但不再提供重推，避免同一条消息被重复发出去。
     */
    function supersededByRetry(row) {
        if (!row || String(row.status) !== "2") { return false; }
        var key = chainKeyOf(row);
        return !!key && chainRetry[key] > (Number(row.id) || 0);
    }

    /** 这条失败记录之后的重推是否已经成功（是则在结果列标注“重推后成功”）。 */
    function retriedSuccessLater(row) {
        if (!supersededByRetry(row)) { return false; }
        var key = chainKeyOf(row);
        return chainSuccess[key] > (Number(row.id) || 0);
    }

    // 只放行 http/https，避免把报文里的内容当脚本地址塞进 href
    function safeUrl(u) {
        var v = String(u == null ? "" : u).trim();
        return /^https?:\/\//i.test(v) ? v : "";
    }

    /* ------------------------------------------------------------------
     * 一键复制报文：优先用剪贴板 API，非安全上下文（http）退化为临时文本框 + execCommand
     * ------------------------------------------------------------------ */
    function copyText(text, okMsg) {
        var payload = String(text == null ? "" : text);
        if (!payload) { toast("没有可复制的内容", 1); return; }
        function fallback() {
            var ta = document.createElement("textarea");
            ta.value = payload;
            ta.setAttribute("readonly", "readonly");
            ta.style.position = "fixed";
            ta.style.left = "-9999px";
            document.body.appendChild(ta);
            ta.select();
            var ok = false;
            try { ok = document.execCommand("copy"); } catch (e) { ok = false; }
            document.body.removeChild(ta);
            toast(ok ? okMsg : "复制失败，请手动选中报文后复制", ok ? 0 : 1);
        }
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(payload).then(function () { toast(okMsg); }, fallback);
            return;
        }
        fallback();
    }

    /** 取某个报文块的纯文本（跳转地址块取日志里实际下发的地址，不取页面上的提示文字）。 */
    function blockText(id) {
        if (id === "msgLogJumpUrl" && currentLog && currentLog.jumpUrl) {
            return String(currentLog.jumpUrl);
        }
        var el = byId(id);
        return el ? String(el.textContent == null ? "" : el.textContent) : "";
    }

    function copyBlock(id) {
        var block = COPY_BLOCKS[id];
        if (!block) { return; }
        copyText(block.title + "\n" + (blockText(id) || "-"), block.name + "已复制");
    }

    /** 一键复制整条日志的报文：摘要 + 跳转地址 + 三类报文 + 异常堆栈。 */
    function copyAllPayload() {
        var log = currentLog || {};
        var parts = [];
        parts.push("推送日志 #" + (log.id == null ? "" : log.id));
        parts.push("业务单号：" + (log.bizId || "-"));
        parts.push("消息类型：" + (log.typeName || "-") + "（" + (log.wxMsgtype || "-") + "）");
        parts.push("状态：" + (log.id == null ? "-" : statusLabel(log))
                + "，企微返回码：" + (log.errcode == null ? "-" : log.errcode));
        parts.push("推送时间：" + ((log.createdDate || "") + " " + (log.createdTime || "")).trim());
        parts.push("接收人：" + (log.receiverWx || "-") + "（" + (log.receiverCount == null ? "-" : log.receiverCount) + " 人）");
        parts.push("");
        parts.push(COPY_BLOCKS.msgLogJumpUrl.title + "\n" + (blockText("msgLogJumpUrl") || "-"));
        parts.push("");
        parts.push(COPY_BLOCKS.msgLogSrc.title + "\n" + (blockText("msgLogSrc") || "-"));
        parts.push("");
        parts.push(COPY_BLOCKS.msgLogRequest.title + "\n" + (blockText("msgLogRequest") || "-"));
        parts.push("");
        parts.push(COPY_BLOCKS.msgLogResponse.title + "\n" + (blockText("msgLogResponse") || "-"));
        var stackRow = byId("msgLogStackRow");
        if (stackRow && stackRow.style.display !== "none" && blockText("msgLogStack")) {
            parts.push("");
            parts.push(COPY_BLOCKS.msgLogStack.title + "\n" + blockText("msgLogStack"));
        }
        copyText(parts.join("\n"), "本条报文已复制（含跳转地址与全部报文）");
    }

    function jumpUrlCell(row) {
        var url = safeUrl(row.jumpUrl);
        if (!url) { return "<span class=\"notice\">-</span>"; }
        return "<a class=\"hrm-link\" href=\"" + esc(url) + "\" target=\"_blank\" rel=\"noopener noreferrer\""
            + " title=\"" + esc(url) + "\">打开链接</a>";
    }

    /**
     * 免登录入口地址 -> 里面的业务页地址。
     * " + P_FULL + "里应该点入口地址（先静默写 OA 会话）；管理员在浏览器里核对时点业务页更直接。
     */
    function entryBusinessUrl(url) {
        var v = String(url == null ? "" : url);
        if (v.indexOf("appEntry.jsp") < 0) { return ""; }
        var matched = /[?&]state=([^&#]+)/.exec(v);
        if (!matched) { return ""; }
        var state = decodeURIComponent(matched[1]);
        var path = state.charAt(0) === "/" ? state : "";
        if (!path) {
            try {
                var b64 = state.replace(/-/g, "+").replace(/_/g, "/");
                while (b64.length % 4) { b64 += "="; }
                path = window.atob(b64);
                if (window.TextDecoder) {
                    path = new TextDecoder("utf-8").decode(Uint8Array.from(path, function (c) {
                        return c.charCodeAt(0);
                    }));
                } else {
                    path = decodeURIComponent(escape(path));
                }
            } catch (e) {
                path = "";
            }
        }
        if (!path || path.charAt(0) !== "/") { return ""; }
        var sep = v.indexOf("://");
        var slash = sep < 0 ? -1 : v.indexOf("/", sep + 3);
        return slash > 0 ? v.substring(0, slash) + path : path;
    }

    function render() {
        var body = byId("msgLogBody");
        if (!body) { return; }
        if (!pager) {
            pager = OaonesTable.pager({
                key: "oaonesMsgLog",
                el: "msgLogPager",
                onChange: function () { render(); }
            });
        }
        pager.resetIfChanged([value("msgLogFrom"), value("msgLogTo"), value("msgLogBizId"),
            value("msgLogStdType"), value("msgLogStatus"), value("msgLogKeyword")].join("|"));
        var pageRows = pager.apply(rows);
        if (!rows.length) {
            body.innerHTML = "<tr><td colspan=\"11\"><div class=\"empty\">没有符合条件的推送日志</div></td></tr>";
            pager.render();
            return;
        }
        // 先算出每条推送链的最新尝试 / 最新重推 / 最新成功：
        // 被后续重推取代的失败记录只作历史展示（结果列标注“重推后成功”，不再给重推按钮）
        refreshChains();
        var html = "";
        pageRows.forEach(function (row) {
            var fail = String(row.status) === "2";
            var stale = supersededByRetry(row);
            html += "<tr" + (fail ? " style=\"background:var(--oa-danger-soft);\"" : "") + ">"
                + "<td>" + esc(row.createdDate) + " " + esc(row.createdTime) + "</td>"
                + "<td>" + esc(row.bizId || "-") + "</td>"
                + "<td>" + esc(row.typeName || "-") + "</td>"
                + "<td>" + esc(row.wxMsgtype || "-") + (row.taskId ? " <span class=\"tag nobind\">卡片</span>" : "") + "</td>"
                + "<td>" + esc(row.receiverCount) + " 人<span class=\"notice\">（"
                + esc((row.receiverWx || "").length > 40 ? (row.receiverWx || "").substring(0, 40) + "…" : (row.receiverWx || "")) + "）</span></td>"
                + "<td>" + jumpUrlCell(row) + "</td>"
                + "<td>" + statusHtml(row) + "</td>"
                + "<td>" + esc(row.costMs == null ? "-" : row.costMs + "ms") + "</td>"
                + "<td>" + esc(row.retryCount == null ? 0 : row.retryCount)
                + (row.nextRetryTime ? "<div class=\"notice\">下次 " + esc(row.nextRetryTime) + "</div>" : "") + "</td>"
                + "<td>" + esc(row.errmsg || "-")
                + (retriedSuccessLater(row) ? " <span class=\"tag bound\">重推后成功</span>" : "") + "</td>"
                + "<td>"
                + "<button class=\"btn small secondary\" data-act=\"detail\" data-id=\"" + esc(row.id) + "\">详情</button> "
                + (fail && !stale
                        ? "<button class=\"btn small\" data-act=\"retry\" data-id=\"" + esc(row.id) + "\">重推</button>"
                        : "<button class=\"btn small secondary\" disabled title=\""
                          + (stale ? "该条已被后续重推取代，结果见更新的记录，不再重推"
                                   : "只有推送失败的记录可以重推") + "\">重推</button>")
                + "</td></tr>";
        });
        body.innerHTML = html;
        pager.render();
    }

    function load() {
        Oaones.api("msg.log.list", {
            from_time: value("msgLogFrom"),
            to_time: value("msgLogTo"),
            biz_id: value("msgLogBizId"),
            std_type: value("msgLogStdType"),
            status: value("msgLogStatus"),
            keyword: value("msgLogKeyword"),
            page_index: 1,
            page_size: 1000
        }, function (res) {
            if (res.code !== 0) { toast(res.msg || "加载日志失败", 2); return; }
            var data = res.data || {};
            rows = data.list || [];
            var stdOptions = data.stdOptions || [];
            if (stdOptions.length) {
                var html = "<option value=\"\">全部</option>";
                stdOptions.forEach(function (item) {
                    var v = String(item.value);
                    html += "<option value=\"" + esc(v) + "\""
                        + (value("msgLogStdType") === v ? " selected" : "") + ">"
                        + esc(item.label) + "</option>";
                });
                byId("msgLogStdType").innerHTML = html;
            }
            if (data.total > rows.length) {
                toast("本次只取回最近 1000 条（共 " + data.total + " 条），请缩小时间范围", 1);
            }
            render();
        });
    }

    function openDetail(id) {
        currentId = id;
        Oaones.api("msg.log.detail", { id: id }, function (res) {
            if (res.code !== 0) { toast(res.msg || "加载详情失败", 2); return; }
            var log = res.data || {};
            currentLog = log;
            var jumpUrl = safeUrl(log.jumpUrl);
            byId("msgLogDetailTitle").textContent = "推送日志详情 #" + log.id;
            byId("msgLogDetailSummary").innerHTML =
                "<div class=\"num\"><span>业务单号</span><b class=\"mono\">" + esc(log.bizId || "-") + "</b></div>"
                + "<div class=\"num\"><span>消息类型</span><b>" + esc(log.typeName || "-") + "</b></div>"
                + "<div class=\"num\"><span>" + P_SHORT + "形态</span><b>" + esc(log.wxMsgtype || "-") + "</b></div>"
                + "<div class=\"num\"><span>状态</span><b>" + esc(statusLabel(log)) + "</b></div>"
                + "<div class=\"num\"><span>" + P_SHORT + "返回码</span><b class=\"mono\">" + esc(log.errcode == null ? "-" : log.errcode) + "</b></div>"
                + "<div class=\"num\"><span>耗时</span><b>" + esc(log.costMs == null ? "-" : log.costMs + " ms") + "</b></div>"
                + "<div class=\"num\"><span>推送时间</span><b class=\"mono\">" + esc((log.createdDate || "") + " " + (log.createdTime || "")) + "</b></div>"
                + "<div class=\"num\"><span>最近更新</span><b class=\"mono\">" + esc(log.modifyDatetime || "-") + "</b></div>";
            // 文本消息没有链接字段：提示单独一行（12px 说明条，不再挤在摘要的大数字卡片里）
            var textTip = byId("msgLogTextTip");
            if (String(log.wxMsgtype) === "text" && jumpUrl) {
                textTip.innerHTML = "文本消息的链接由" + P_FULL + "客户端识别正文里的网址，"
                    + "想要可点按钮请把该消息类型的" + P_SHORT + "形态改成“文本卡片消息”或“模板卡片消息”。";
                textTip.style.display = "";
            } else {
                textTip.style.display = "none";
            }
            if (jumpUrl) {
                byId("msgLogJumpUrl").innerHTML = "<a class=\"hrm-link\" href=\"" + esc(jumpUrl)
                    + "\" target=\"_blank\" rel=\"noopener noreferrer\">" + esc(jumpUrl) + "</a>"
                    + (entryBusinessUrl(jumpUrl)
                            ? "<div class=\"notice\">" + P_FULL + "里点的是上面的免登录入口地址（先静默登录再跳业务页）；"
                              + "在浏览器里核对业务页可点 <a class=\"hrm-link\" href=\""
                              + esc(entryBusinessUrl(jumpUrl)) + "\" target=\"_blank\" rel=\"noopener noreferrer\">业务页</a></div>"
                            : "");
            } else {
                byId("msgLogJumpUrl").innerHTML = "<span class=\"notice\">报文里没有跳转地址："
                    + "跳转地址模板留空且标准消息没带链接；或链接是站内相对路径而没取到 OA 访问地址"
                    + "（相对地址会被" + P_SHORT + "以 40127 拒收，任务卡片会直接去掉链接）——"
                    + "去「推送配置」页看当前生效的 OA 访问地址与来源，"
                    + "建议在 后台 系统设置 → 微信设置 → 集成设置 里维护「OA访问地址」</span>";
            }
            byId("msgLogJumpUrlRow").style.display = "";
            byId("msgLogSrc").textContent = log.srcJson || "-";
            byId("msgLogRequest").textContent = log.requestJson || "-";
            byId("msgLogResponse").textContent = log.responseJson || "-";
            byId("msgLogStack").textContent = log.errorStack || "-";
            byId("msgLogStackRow").style.display = log.errorStack ? "" : "none";
            // 原始入参是 OA 标准消息的原文：里面的多语言标记要解释清楚，
            // 否则容易以为“报文里有多语言”，实际下发的请求报文已按语言取成纯文本
            var srcTip = byId("msgLogSrcTip");
            if (String(log.srcJson || "").indexOf("~`~`") >= 0) {
                srcTip.innerHTML = "原始入参里出现的 <code>~`~`7 中文`~`8 English`~`9 繁體`~`~</code> "
                    + "是泛微的<strong>多语言标记</strong>（流程名 / 人员姓名等字段存了多语言值，属正常现象）；"
                    + "「" + P_SHORT + "请求报文」里已经按接收人的 OA 语言取成纯文本，" + P_FULL + "不会收到这些标记。";
                srcTip.style.display = "";
            } else {
                srcTip.style.display = "none";
            }
            // 详情里的“重推该条”与列表口径一致：只有尚未被后续重推取代的失败记录才给
            byId("msgLogRetryBtn").style.display =
                String(log.status) === "2" && !supersededByRetry(log) ? "" : "none";
            byId("msgLogDetailMask").style.display = "block";
            byId("msgLogDetailModal").style.display = "block";
        });
    }

    /** 详情弹框全屏 / 退出全屏：整页铺满，方便逐段核对报文。 */
    function toggleFullscreen() {
        var modal = byId("msgLogDetailModal");
        var btn = byId("msgLogFullBtn");
        if (!modal || !btn) { return; }
        var full = !modal.classList.contains("modal-fullscreen");
        if (full) {
            // 拖动过的弹框是行内 left/top 定位，先记下来，退出全屏时还原
            fullSavedBox = {
                left: modal.style.left, top: modal.style.top,
                transform: modal.style.transform, margin: modal.style.margin
            };
            modal.classList.add("modal-fullscreen");
            modal.style.left = "";
            modal.style.top = "";
            modal.style.transform = "";
            modal.style.margin = "";
            modal.style.display = "flex";
            btn.textContent = "退出全屏";
            btn.setAttribute("title", "退出全屏（Esc）");
        } else {
            modal.classList.remove("modal-fullscreen");
            modal.style.display = "block";
            if (fullSavedBox) {
                modal.style.left = fullSavedBox.left;
                modal.style.top = fullSavedBox.top;
                modal.style.transform = fullSavedBox.transform;
                modal.style.margin = fullSavedBox.margin;
            }
            fullSavedBox = null;
            btn.textContent = "全屏";
            btn.setAttribute("title", "整页全屏查看报文（再点一次或按 Esc 退出）");
        }
    }

    function closeDetail() {
        // 关闭时一并退出全屏，下次打开还是常规弹框
        if (byId("msgLogDetailModal").classList.contains("modal-fullscreen")) {
            toggleFullscreen();
        }
        byId("msgLogDetailMask").style.display = "none";
        byId("msgLogDetailModal").style.display = "none";
    }

    function retry(id) {
        Oaones.api("msg.log.retry", { id: id }, function (res) {
            if (res.code !== 0) { toast(res.msg || "重推失败", 2); return; }
            var data = res.data || {};
            toast(data.msg || "已重推", data.code === 0 ? 0 : 2);
            closeDetail();
            load();
        });
    }

    function bindEvents() {
        if (bound) { return; }
        bound = true;
        // 日期时间文本框统一接入选择框组件（日历 + 时/分/秒三列 + 手工输入）
        if (window.OaonesDate) { window.OaonesDate.init(); }
        byId("msgLogSearchBtn").addEventListener("click", function () { load(); });
        byId("msgLogResetBtn").addEventListener("click", function () {
            // 日期时间框走组件清空：同时复位文本框、“清空”按钮与已展开的面板
            if (window.OaonesDate) {
                window.OaonesDate.clear("msgLogFrom");
                window.OaonesDate.clear("msgLogTo");
            } else {
                byId("msgLogFrom").value = "";
                byId("msgLogTo").value = "";
            }
            byId("msgLogBizId").value = "";
            byId("msgLogStdType").value = "";
            byId("msgLogStatus").value = "";
            byId("msgLogKeyword").value = "";
            load();
        });
        byId("msgLogDetailClose").addEventListener("click", closeDetail);
        byId("msgLogDetailOkBtn").addEventListener("click", closeDetail);
        byId("msgLogDetailMask").addEventListener("click", closeDetail);
        byId("msgLogFullBtn").addEventListener("click", toggleFullscreen);
        byId("msgLogCopyAllBtn").addEventListener("click", copyAllPayload);
        byId("msgLogRetryBtn").addEventListener("click", function () { retry(currentId); });
        // 报文块复制：块是整块重绘的，按钮用事件委托
        byId("msgLogDetailModal").addEventListener("click", function (e) {
            var btn = e.target && e.target.closest ? e.target.closest("button[data-copy]") : null;
            if (!btn) { return; }
            copyBlock(btn.getAttribute("data-copy"));
        });
        // Esc：先退全屏，再关弹框
        document.addEventListener("keydown", function (e) {
            var key = e.key || e.keyCode;
            if (key !== "Escape" && key !== "Esc" && key !== 27) { return; }
            var modal = byId("msgLogDetailModal");
            if (!modal || modal.style.display === "none" || modal.style.display === "") { return; }
            if (modal.classList.contains("modal-fullscreen")) {
                toggleFullscreen();
                return;
            }
            closeDetail();
        });
        byId("msgLogBody").addEventListener("click", function (e) {
            var btn = e.target && e.target.closest ? e.target.closest("button[data-act]") : null;
            if (!btn) { return; }
            var id = btn.getAttribute("data-id");
            var act = btn.getAttribute("data-act");
            if (act === "detail") {
                openDetail(id);
            } else if (act === "retry") {
                OaonesToast.confirm("确定重推这条失败消息？", function () { retry(id); });
            }
        });
    }

    window.MsgLogPanel = {
        load: function () {
            bindEvents();
            load();
        }
    };
})();
</script>
