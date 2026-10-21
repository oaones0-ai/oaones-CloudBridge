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
  统一消息中心 → 数据看板
  今日 / 昨日 / 累计推送总数、成功数、失败数、成功率，今日各消息类型推送占比与失败原因 TOP。
--%>
<div class="help-source" id="msgBoardHelpText">
    统计口径与“推送日志”明细完全一致（按推送状态落库时间汇总）：
    成功、失败、重复拦截、跳过分别计数，成功率 = 成功 / 总数。
    失败原因 TOP 与平均耗时按<%= oaonesPBShort %>返回信息 / 耗时聚合——这两项来自<strong>接口日志中心</strong>
    （同一次接口调用只存一份，推送状态表不再重复保存报文与结果），便于定位配置或权限问题。
</div>

<div class="toolbar">
    <label style="font-size:15px;font-weight:600;">推送数据看板</label>
    <span class="help-tip" data-help="msgBoardHelpText" role="button" tabindex="0"
          title="查看统计口径" aria-label="查看统计口径">?</span>
    <span style="flex:1;"></span>
    <span class="notice" id="msgBoardQueue"></span>
    <button class="btn secondary" id="msgBoardReloadBtn">刷新</button>
</div>

<div class="summary" id="msgBoardSummary"></div>

<div class="toolbar" style="margin-top:20px;">
    <label style="font-size:15px;font-weight:600;">今日按消息类型统计</label>
</div>
<div class="table-wrap">
    <table class="grid" id="msgBoardTypeTable">
        <thead>
        <tr>
            <th>消息类型</th>
            <th style="width:150px;"><%= oaonesPBShort %>形态</th>
            <th style="width:110px;">总数</th>
            <th style="width:110px;">成功</th>
            <th style="width:110px;">失败</th>
        </tr>
        </thead>
        <tbody id="msgBoardTypeBody"></tbody>
    </table>
</div>

<div class="toolbar" style="margin-top:20px;">
    <label style="font-size:15px;font-weight:600;">今日失败原因 TOP</label>
</div>
<div class="table-wrap">
    <table class="grid" id="msgBoardFailTable">
        <thead>
        <tr>
            <th>失败原因</th>
            <th style="width:110px;">条数</th>
        </tr>
        </thead>
        <tbody id="msgBoardFailBody"></tbody>
    </table>
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

    function block(label, item) {
        item = item || {};
        return "<div class=\"num\"><span>" + esc(label) + " 推送总数</span><b>" + esc(item.total || 0) + "</b>"
            + "<span>成功 " + esc(item.success || 0) + " / 失败 " + esc(item.fail || 0)
            + " / 重复拦截 " + esc(item.dedup || 0) + " / 跳过 " + esc(item.skip || 0) + "</span>"
            + "<span>成功率 " + esc(item.rate || "-") + "，平均耗时 " + esc(item.avgCost || 0) + " ms</span></div>";
    }

    function render(data) {
        data = data || {};
        byId("msgBoardSummary").innerHTML = block("今日", data.today) + block("昨日", data.yesterday)
            + block("累计", data.total);
        byId("msgBoardQueue").textContent = "推送队列：" + (data.queue || "-");

        var types = data.types || [];
        var typeBody = byId("msgBoardTypeBody");
        if (!types.length) {
            typeBody.innerHTML = "<tr><td colspan=\"5\"><div class=\"empty\">今日暂无推送数据</div></td></tr>";
        } else {
            var html = "";
            types.forEach(function (row) {
                html += "<tr><td>" + esc(row.typeName) + "</td><td>" + esc(row.wxMsgtype) + "</td>"
                    + "<td>" + esc(row.total) + "</td><td>" + esc(row.success) + "</td>"
                    + "<td>" + esc(row.fail) + "</td></tr>";
            });
            typeBody.innerHTML = html;
        }

        var fails = data.failTop || [];
        var failBody = byId("msgBoardFailBody");
        if (!fails.length) {
            failBody.innerHTML = "<tr><td colspan=\"2\"><div class=\"empty\">今日没有失败记录</div></td></tr>";
        } else {
            var fhtml = "";
            fails.forEach(function (row) {
                fhtml += "<tr><td>" + esc(row.errmsg) + "</td><td>" + esc(row.total) + "</td></tr>";
            });
            failBody.innerHTML = fhtml;
        }
    }

    function load() {
        Oaones.api("msg.board.get", {}, function (res) {
            if (res.code !== 0) { toast(res.msg || "加载看板失败", 2); return; }
            render(res.data || {});
        });
    }

    var bound = false;
    window.MsgBoardPanel = {
        load: function () {
            if (!bound) {
                bound = true;
                byId("msgBoardReloadBtn").addEventListener("click", load);
            }
            load();
        }
    };
})();
</script>
