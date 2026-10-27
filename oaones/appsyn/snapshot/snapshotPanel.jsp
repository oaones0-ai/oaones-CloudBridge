<%@ page pageEncoding="UTF-8" %>
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
%>

<%-- 租户二级菜单：快照列表（平台组织架构 + 平台人员快照，只读展示） --%>
<%-- 页面说明正文：由页壳工具栏右侧的感叹号（#cfgHelpTip）悬浮/点击弹出 --%>
<%-- 本片段只放标记：取数、渲染与事件注册都在页壳 snapshot.jsp 的脚本里（见 skill.md 第一节第 7 条） --%>
        <div id="snapPanel">
            <div class="bind-stats" id="snapStats" style="display:none;"></div>
            <div class="filter-bar" style="justify-content:space-between;">
                <div style="display:flex; gap:10px; align-items:center; flex-wrap:wrap;">
                    <span class="filter-label">快照筛选：</span>
                    <input type="text" id="snapKeyword" placeholder="部门/成员：名称、ID、职位、手机号、邮箱"
                           autocomplete="off" data-lpignore="true" style="min-width:240px;max-width:340px;"
                           title="按<%= oaonesPBShort %>部门名称/部门ID、成员姓名/成员ID/职位/手机号/邮箱过滤（部门命中时保留该部门全部直属成员）">
                    <button type="button" class="btn small" id="snapSearchBtn">搜索</button>
                    <button type="button" class="btn small secondary" id="snapResetBtn">重置筛选</button>
                </div>
                <div style="display:flex; gap:10px; align-items:center; flex-wrap:wrap;">
                    <span class="view-switch" title="切换展示方式：默认树状（<%= oaonesPBShort %>组织架构 + 本部门直属成员），也可以按成员列表查看">
                        <button type="button" class="view-btn active" id="snapViewTreeBtn">树状</button>
                        <button type="button" class="view-btn" id="snapViewListBtn">列表</button>
                    </span>
                    <button type="button" class="btn small secondary" id="snapExpandBtn"
                            title="展开全部部门，显示下级部门与成员（只影响树的收起状态，不影响筛选与导出）">展开全部</button>
                    <button type="button" class="btn small secondary" id="snapCollapseBtn"
                            title="收起全部部门，只保留顶层部门行（只影响树的收起状态，不影响筛选与导出）">收起全部</button>
                </div>
            </div>
            <div class="muted" id="snapTip" style="margin-bottom:10px;"></div>
            <div id="snapTreeView">
                <div class="map-head">
                    <div class="map-col"><%= oaonesPBShort %>组织架构（部门 / 成员）</div>
                    <div class="map-col"><%= oaonesPBShort %>部门 / 成员信息</div>
                </div>
                <div class="map-tree" id="snapTree"></div>
                <div class="empty" id="snapTreeEmpty" style="display:none;"></div>
            </div>
            <div id="snapListView" style="display:none;">
                <div class="table-wrap">
                    <table class="grid" id="snapTable">
                        <thead>
                        <tr>
                            <th style="width:200px;"><%= oaonesPBShort %>成员</th>
                            <th style="width:130px;">成员ID</th>
                            <th>主部门</th>
                            <th style="width:130px;">职位</th>
                            <th style="width:130px;">手机号</th>
                            <th style="width:180px;">邮箱</th>
                            <th style="width:90px;">状态</th>
                            <th style="width:160px;">快照时间</th>
                        </tr>
                        </thead>
                        <tbody id="snapBody"></tbody>
                    </table>
                    <div class="empty" id="snapListEmpty" style="display:none;"></div>
                </div>
                <div class="pager" id="snapPager"></div>
            </div>
        </div>
