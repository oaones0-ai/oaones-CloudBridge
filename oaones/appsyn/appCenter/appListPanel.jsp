<%@ page pageEncoding="UTF-8" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesConst" %>
<%--
  应用列表页主体（页壳 = appCenter/AppList.jsp）。
  一行一个应用：应用名称 + 默认访问地址 + 单点登录实现类（指向实现对方身份认证的具体类）。
  图标走**页面上传**（文件落在 oaones/appsyn/img/ 下）：页面上的长宽建议与大小上限直接取 OaonesConst，
  避免“页面文案”与“后端校验”两处各写一份数字。
  本页与「桌面启动台」不设任何菜单关联（互不链接）：页面上不出现桌面页的入口。
  markdown 与脚本同文件（与 appBindManagePanel.jsp 的口径一致），跨页不复用第二份实现。
--%>
<%
    // 静态说明按登录人角色分别输出（系统管理员 / oaones.appsyn.properties#appc.admin.ids 里的维护人）
    boolean oaonesAppcSysAdmin = "true".equals(request.getParameter("sysAdmin"));
    // 启动台自动跳转是全局登录落点设置：只给系统管理员 / appc.admin.ids，名下仅有应用的维护人不显示
    boolean oaonesAppcGopageCanMaintain = "true".equals(request.getParameter("gopageCanMaintain"));
%>
<div class="help-source" id="appListHelpText">本页维护<span class="rule">应用清单</span>：
    一条记录 = 桌面启动台（macOS 风格桌面，另一个单独打开的页面）上的一个图标。
    <span class="rule">应用名称</span>必填且不能重复（图标下方显示的名字）；
    <span class="rule">默认访问地址</span>必填（没有配置单点登录实现类时，点击图标直接打开它；
    可以填 <span class="rule">http(s)://…</span> 的完整地址，也可以填以 <span class="rule">/</span> 开头的站内地址）。
    <span class="rule">单点登录实现类</span>选填：填一个类全限定名，该类必须实现
    <span class="rule">com.customization.oaones.appsyn.appsso.AppSsoHandler</span> 接口，
    由类里的 <span class="rule">loginUrl(AppSsoContext)</span> 方法完成<span class="rule">对对方系统的身份认证</span>
    （把 OA 登录人换成对方认可的身份凭据，并返回最终访问地址）——怎么实现由对方系统决定，
    本模块只负责在员工点击图标时调用这个类；类名留空表示不做单点登录，直接打开默认地址。
    保存时会先校验该类（是否存在、是否实现接口、能否实例化），校验不通过不允许保存；
    列表里也会显示该类的自述说明，类不可用时给出明确提示（避免“配了却打不开”）。
    <span class="rule">图标</span>选填：只能<span class="rule">页面上传</span>——上传后图片保存在
    <span class="rule">/oaones/appsyn/img/</span> 下，记录里存这个站内地址；
    建议 <span class="rule"><%=OaonesConst.APP_ICON_SUGGEST_SIZE%>×<%=OaonesConst.APP_ICON_SUGGEST_SIZE%>
    像素的正方形 PNG（透明底）</span>，最小 <%=OaonesConst.APP_ICON_MIN_SIZE%>×<%=OaonesConst.APP_ICON_MIN_SIZE%>、
    最大 <%=OaonesConst.APP_ICON_MAX_SIZE%>×<%=OaonesConst.APP_ICON_MAX_SIZE%>，
    单个文件不超过 <%=OaonesConst.APP_ICON_MAX_BYTES / 1024%>KB（建议 200KB 以内）；
    不上传时用<span class="rule">系统默认文字图标</span>（按应用名首字生成的渐变色块）——
    该图标在<span class="rule">保存时按当时的应用名固定</span>，之后<span class="rule">改名不会自动变化</span>；
    想让默认图标跟上现在这个名字，点弹框里的<span class="rule">清除图标</span>按当前名称重新生成。
    <span class="rule">打开方式</span>决定桌面启动台上点这个图标时怎么打开：
    <span class="rule">默认</span>——只在<span class="rule">当前页面</span>里打开（外层 OA 框架不动，不是整个页面 reload）；
    <span class="rule">新标签页</span>——浏览器<span class="rule">新开一个标签页</span>打开；
    <span class="rule">原标签页</span>——<span class="rule">整个页面 reload</span>，当前这个浏览器标签页整体换成该应用。
    <span class="rule">维护人</span>（选填，可多选 OA 员工）：应用由<span class="rule">创建人 + 维护人</span>维护——
    创建人在新建时自动记录、天然可维护（不用重复选），维护人用来把维护权追加给别人；
    本页只列出<span class="rule">自己创建的 + 自己是维护人的</span>应用，编辑 / 启停 / 删除也只能动这些应用；
    <b>系统管理员</b>（user.id=1）可查看与维护<span class="rule">全部</span>应用。
    <span class="rule">可见范围</span>（选填）：最上面是<span class="rule">全员可见</span>开关——
    勾选 = <b>不控制权限</b>，<span class="rule">工作台（桌面启动台）</span>上所有登录人都能看到这个应用
    （下面的角色 / 分部 / 部门 / 人员四类不再生效，选择框会变灰）；
    不勾选时按<span class="rule">角色 / 分部 / 部门 / 人员</span>四类多选判定，登录人只要中<span class="rule">其中一类</span>
    （所属角色命中 / 主分部命中 / 主部门命中 / 本人命中）就能在工作台看到这个应用；<b>四类都不选 = 无权限</b>
    （工作台上除创建人 / 维护人 / 系统管理员外谁都看不到）。
    <b>只按“当前分部 / 当前部门”判定，不含下级组织</b>（选了「财务部」时，财务部下级的部门看不到；
    要让下级也看到就把那些下级一并选上）。可见范围只影响工作台显示，不影响本页的维护。
    <span class="rule">排序号</span>决定桌面与列表的顺序（小的在前）。
    <b>停用</b>的应用不会出现在桌面上，直接访问也会被拒绝。
    <% if (oaonesAppcSysAdmin) { %>你是<b>系统管理员</b>（user.id=1），本页可查看与维护<b>全部</b>应用；
    也可以把维护人加到 oaones.appsyn.properties#appc.admin.ids 里让更多人新建应用。
    <% } else { %>能进本页的是：被加入 oaones.appsyn.properties#appc.admin.ids 的人员，以及被指定为某个应用
    创建人 / 维护人的人员（可新增 / 编辑 / 停用 / 删除自己的应用）。
    <% } %></div>

<% if (oaonesAppcGopageCanMaintain) { %>
<%-- 启动台自动跳转：从「登录设置」移到这里（应用中心管启动台，登录设置只留登录页本身） --%>
<div class="appc-gopage" id="appcGopageBox">
    <div class="appc-gopage-head">
        <b>启动台自动跳转</b>
        <span class="help-tip inline" id="appcGopageHelpTip" data-help="appcGopageHelpText"
              role="button" tabindex="0" title="查看启动台自动跳转说明"
              aria-label="查看启动台自动跳转说明">?</span>
        <span class="muted">登录页带 gopage 进入桌面启动台后，按这里的秒数自动跳到目标页</span>
    </div>
    <div class="appc-gopage-row">
        <input type="range" id="appcGopageRange" min="0"
               max="<%=OaonesConst.LOGIN_GOPAGE_MAX_SECONDS%>" step="1" value="5"
               aria-label="启动台自动跳转秒数">
        <input type="number" id="appcGopageSeconds" min="0"
               max="<%=OaonesConst.LOGIN_GOPAGE_MAX_SECONDS%>" step="1"
               autocomplete="off" data-lpignore="true" placeholder="默认">
        <span class="muted" style="font-size:12px;">秒</span>
        <button type="button" class="btn small secondary" id="appcGopageResetBtn">恢复默认</button>
        <button type="button" class="btn small" id="appcGopageSaveBtn">保存跳转设置</button>
        <span class="appc-gopage-result" id="appcGopageResult"></span>
    </div>
    <div class="appc-gopage-ticks">
        <span>0 秒（不跳）</span><span id="appcGopageDefault">留空 = 系统默认值</span>
    </div>
</div>
<div class="help-source" id="appcGopageHelpText">
    登录页带原来的 gopage 进入桌面启动台后，按这里设置的秒数自动跳到目标页。
    用滑块或秒数输入设置：<b>0 = 不自动跳转</b>（停留在启动台），留空 = 使用系统默认值。
    用户点了应用图标或“返回 OA”会立即取消自动跳转。
    这一项只影响启动台的跳转节奏，不是登录页外观，所以放在「应用中心 → 应用列表」维护。
    保存后立即对下一次进入桌面启动台生效，不会改动登录方式、Logo、背景图或左侧文字。
</div>
<% } %>

<div class="toolbar">
    <label>应用筛选</label>
    <input type="text" id="appcKeyword" name="oaonesAppcKeyword"
            placeholder="应用名称 / 访问地址 / 单点登录实现类 / 维护人 / 可见角色 / 备注"
           autocomplete="off" data-lpignore="true" style="min-width:260px;max-width:360px;">
    <button type="button" class="btn small" id="appcSearchBtn">搜索</button>
    <button type="button" class="btn small secondary" id="appcResetBtn">重置筛选</button>
    <span class="help-tip" id="appListHelpTip" data-help="appListHelpText" role="button" tabindex="0"
          title="查看页面说明" aria-label="查看页面说明">?</span>
    <span style="flex:1;"></span>
    <button class="btn" id="appcNewBtn" title="新增一个应用（应用名称 + 默认访问地址 + 单点登录实现类）">新建应用</button>
    <button class="btn secondary" id="appcRefreshBtn">刷新</button>
</div>

<div class="bind-stats" id="appcStats"></div>
<div class="table-wrap">
    <table class="grid" id="appcTable">
        <thead>
        <tr>
            <th style="width:22%;">应用</th>
            <th style="width:24%;">默认访问地址</th>
            <th style="width:30%;">单点登录实现类（对方身份认证）</th>
            <th style="width:8%;">状态</th>
            <th style="width:11%;">更新时间</th>
            <th>操作</th>
        </tr>
        </thead>
        <tbody id="appcBody"></tbody>
    </table>
    <div class="empty" id="appcEmpty" style="display:none;">暂无应用</div>
</div>
<div class="pager" id="appcPagerEl"></div>

<%-- 新建 / 编辑应用弹框 --%>
<div class="modal-mask" id="appcEditMask"></div>
<div class="modal" id="appcEditModal" style="width:700px;">
    <div class="modal-head"><span id="appcEditTitle">新建应用</span>
        <button type="button" class="modal-x" id="appcEditCloseBtn" title="关闭" aria-label="关闭"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"></path></svg></button></div>
    <div class="modal-body">
        <input type="hidden" id="appcId">
        <div class="form-row"><label>应用名称 *</label><div class="field">
            <input id="appcName" placeholder="桌面图标下方的名字，例如：简道云 / 费控系统 / 官网"></div></div>
        <div class="form-row"><label>默认访问地址 *</label><div class="field">
            <input id="appcUrl" placeholder="https://example.com/portal 或 /wui/index.html#/main"></div></div>
        <div class="form-row"><label>打开方式</label><div class="field">
            <%-- 打开方式（表列 open_mode）：0 默认 = 只在当前页面/框架里打开（外层 OA 框架不动）；
                 1 = 新标签页；2 = 原标签页（整个页面 reload）。桌面脚本 js/appDesktop.js 按它分派。 --%>
            <select id="appcOpenMode">
                <option value="0">默认（在当前页面打开，外层 OA 框架不动）</option>
                <option value="1">新标签页（新开一个浏览器标签页打开）</option>
                <option value="2">原标签页（整个页面 reload，当前标签页整体换成该应用）</option>
            </select></div></div>
        <div class="form-row">
            <label>单点登录实现类<span class="help-tip inline" id="appcSsoHelpTip" data-help="appcSsoHelpText"
                    role="button" tabindex="0" title="查看单点登录实现类填写说明"
                    aria-label="查看单点登录实现类填写说明">?</span></label>
            <div class="field">
                <input id="appcSsoClass" placeholder="留空 = 不做单点登录（直接打开默认地址）">
                <div class="row-ops">
                    <button type="button" class="btn small secondary" id="appcCheckClassBtn">校验实现类</button>
                    <span class="muted" id="appcClassResult"></span>
                </div>
            </div>
        </div>
        <div class="help-source" id="appcSsoHelpText">这里填的是<span class="rule">类的全限定名</span>（例如
            <span class="rule">com.xxx.sso.JdySsoHandler</span>），该类必须实现接口
            <span class="rule">com.customization.oaones.appsyn.appsso.AppSsoHandler</span>，
            并有<span class="rule">无参构造函数</span>：
            员工在桌面点击该应用时，系统把当前 OA 登录人交给这个类，
            由它的 <span class="rule">loginUrl(AppSsoContext)</span> 方法完成<b>对对方系统的身份认证</b>
            （生成签名票据 / 一次性令牌 / 免登录地址等），并返回最终要跳转的地址；
            上下文里可用 <span class="rule">getOaUserId()</span> / <span class="rule">getOaLoginId()</span>
            / <span class="rule">getOaUserName()</span> / <span class="rule">getAppUrl()</span> 等取值。
            <span class="rule">留空</span>表示该应用不做单点登录，点击时直接打开默认访问地址。
            类编译好后放进 ecology 的 <span class="rule">classbean</span>（不存在的类保存会被拒绝），
            新类需要<span class="rule">重启 E9</span> 才会被加载；本模块自带的
            <span class="rule">com.customization.oaones.appsyn.appsso.DefaultAppSsoHandler</span>
            是“直连”的默认实现，可先填它验证链路，之后再换成客户自己的实现类。</div>
        <div class="form-row"><label>身份认证方法说明</label><div class="field">
            <input id="appcSsoMethod" placeholder="选填，纯展示：例如 loginUrl —— 调对方 /api/sso/ticket 换一次性票据"></div></div>
        <div class="form-row"><label>图标</label><div class="field">
            <%-- 图标只能页面上传：文件落到 oaones/appsyn/img/ 下，appcIcon（隐藏域）里存的是 /oaones/appsyn/img/xxx.png；
                 appcIconSeed（隐藏域）= 系统默认文字图标的取字/取色基准（保存时按当时的应用名固定，
                 改名不跟随；点“清除图标”时刷成当前应用名，即按现在这个名字重新生成默认图标）。 --%>
            <input type="hidden" id="appcIcon">
            <input type="hidden" id="appcIconSeed">
            <input type="file" id="appcIconFile" style="display:none;"
                   accept="image/png,image/jpeg,image/gif,image/bmp,image/webp">
            <div class="appc-icon-upload">
                <span class="appc-icon-preview" id="appcIconPreview"></span>
                <div class="appc-icon-upload-main">
                    <div class="appc-icon-upload-ops">
                        <button type="button" class="btn small" id="appcIconPickBtn"
                                title="选择本机图片上传，上传后保存在 /oaones/appsyn/img/ 下">选择图片上传</button>
                        <button type="button" class="btn small secondary" id="appcIconClearBtn"
                                title="清空图标，并按当前应用名称重新生成系统默认文字图标（渐变底色）">清除图标</button>
                        <span class="appc-icon-result" id="appcIconResult">未上传</span>
                    </div>
                    <div class="appc-icon-hint" id="appcIconHint">
                        建议 <b><%=OaonesConst.APP_ICON_SUGGEST_SIZE%>×<%=OaonesConst.APP_ICON_SUGGEST_SIZE%></b>
                        像素的正方形 PNG（透明底），最小 <%=OaonesConst.APP_ICON_MIN_SIZE%>×<%=OaonesConst.APP_ICON_MIN_SIZE%>、
                        最大 <%=OaonesConst.APP_ICON_MAX_SIZE%>×<%=OaonesConst.APP_ICON_MAX_SIZE%>；
                        单个文件不超过 <%=OaonesConst.APP_ICON_MAX_BYTES / 1024%>KB。
                        支持 <%=OaonesConst.APP_ICON_EXTENSIONS%>；。
                    </div>
                </div>
            </div>
        </div></div>
        <div class="form-row">
            <label>维护人<span class="help-tip inline" id="appcMtHelpTip" data-help="appcMtHelpText"
                    role="button" tabindex="0" title="查看维护人说明" aria-label="查看维护人说明">?</span></label>
            <div class="field">
                <%-- 维护人（表列 maintainer_ids，多选 OA 员工）：
                     应用由“创建人 + 维护人”维护；创建人记在 modedatacreater（新建时自动写入），
                     天然可维护、不用重复选；维护人用于把维护权追加给别人（页面上的选择弹框见下面的 appcPick） --%>
                <div class="mt-picker" id="appcMtBox" title="点击选择 OA 员工（可多选，点击标签上的 × 可移除）">
                    <span class="mt-chips" id="appcMtChips"></span>
                    <span class="mt-picker-arrow">▾</span>
                </div>
            </div>
        </div>
        <%-- 维护人说明按角色：系统管理员看“可维护全部应用”，其他维护人看“把自己移除后就看不到该应用” --%>
        <div class="help-source" id="appcMtHelpText">应用由<span class="rule">创建人 + 维护人</span>维护：
            这里选的维护人（可多选 OA 员工）与创建人都能在这页编辑 / 启停 / 删除该应用；
            创建人在记录里自动保存（<b>新建应用的人就是创建人</b>），不用在这里重复选。
            <% if (oaonesAppcSysAdmin) { %>你是<b>系统管理员</b>（user.id=1），可查看与维护<b>全部</b>应用，
            本字段用来把某个应用交给别人（们）维护；列表里能看到每条应用的创建人与维护人。
            <% } else { %>把别人选成维护人后，他也能在这页维护这个应用（无需再进 oaones.appsyn.properties#appc.admin.ids）；
            <b>把自己移除后</b>（且你不是创建人时）你就不再看到这个应用。
            <% } %></div>
        <div class="form-row">
            <label>可见范围<span class="help-tip inline" id="appcVisHelpTip" data-help="appcVisHelpText"
                    role="button" tabindex="0" title="查看可见范围说明" aria-label="查看可见范围说明">?</span></label>
            <div class="field">
                <%-- 可见范围（表列 vis_all + vis_role_ids / vis_sub_ids / vis_dept_ids / vis_user_ids）：
                     勾选“全员可见”（vis_all=1）= **不控制权限**，工作台上所有登录人都能看到；
                     不勾选时按下面四类多选判定：四类**满足其一即可见**，四类都为空 = **无权限**（工作台上谁都看不到）；
                     勾选“全员可见”时四类选择框禁用（已选内容保留，取消勾选后恢复生效）；
                     分部 / 部门只按“当前分部 / 当前部门”判定，**不含下级组织**；角色按登录人的角色成员归属判定；
                     只作用于工作台（桌面启动台），管理页里维护人始终能看到自己维护的应用。 --%>
                <div class="appc-vis-row appc-vis-all-row">
                    <%-- 与下面四类选择框同高（34px）、内容垂直居中；勾选框与左侧「角色 / 分部 / 部门 / 人员」标签同列，
                         右侧只写“全员可见”。详细口径放在本行的 tooltip 与「可见范围」的 ! 说明里，页面上不再铺开 --%>
                    <span class="appc-vis-label">
                        <input type="checkbox" id="appcVisAll"
                               title="全员可见：勾选后不控制权限，工作台上所有登录人都能看到这个应用；下面四类不再生效">
                    </span>
                    <label class="appc-vis-all" for="appcVisAll">全员可见</label>
                </div>
                <div class="appc-vis-row">
                    <span class="appc-vis-label">角色</span>
                    <div class="mt-picker" id="appcVisRoleBox"
                         title="点击选择 OA 角色（可多选，点击标签上的 × 可移除；角色成员按当前组织归属判定）">
                        <span class="mt-chips" id="appcVisRoleChips"></span>
                        <span class="mt-picker-arrow">▾</span>
                    </div>
                </div>
                <div class="appc-vis-row">
                    <span class="appc-vis-label">分部</span>
                    <div class="mt-picker" id="appcVisSubBox"
                         title="点击选择 OA 分部（可多选，点击标签上的 × 可移除；只对所选分部本身生效）">
                        <span class="mt-chips" id="appcVisSubChips"></span>
                        <span class="mt-picker-arrow">▾</span>
                    </div>
                </div>
                <div class="appc-vis-row">
                    <span class="appc-vis-label">部门</span>
                    <div class="mt-picker" id="appcVisDeptBox"
                         title="点击选择 OA 部门（可多选，点击标签上的 × 可移除；只对所选部门本身生效）">
                        <span class="mt-chips" id="appcVisDeptChips"></span>
                        <span class="mt-picker-arrow">▾</span>
                    </div>
                </div>
                <div class="appc-vis-row">
                    <span class="appc-vis-label">人员</span>
                    <div class="mt-picker" id="appcVisUserBox"
                         title="点击选择 OA 人员（可多选，点击标签上的 × 可移除）">
                        <span class="mt-chips" id="appcVisUserChips"></span>
                        <span class="mt-picker-arrow">▾</span>
                    </div>
                </div>
                <%-- 未勾选“全员可见”且四类都没选时的醒目提示（等于无权限），由脚本按选中情况显隐 --%>
                <div class="row-note" id="appcVisWarn" style="display:none;">
                    四类都没设置 = <b>无权限</b>：工作台上除创建人 / 维护人 / 系统管理员外，谁都看不到这个应用。
                </div>
            </div>
        </div>
        <%-- 可见范围说明：勾选全员可见 = 不控制权限；否则四类满足其一即可见（与页面顶部说明同口径） --%>
        <div class="help-source" id="appcVisHelpText">最上面的
            <span class="rule">全员可见</span>勾选 = <b>不控制权限</b>：工作台上<b>所有登录人</b>都能看到这个应用，
            下面的角色 / 分部 / 部门 / 人员四类不再参与判定（选择框会变灰，已选内容保留，取消勾选后立即恢复生效）。
            不勾选时<span class="rule">四类满足其一即可见</span>：
            登录人的<span class="rule">主部门</span>命中「部门」、<span class="rule">主分部</span>命中「分部」、
            <span class="rule">所属角色</span>命中「角色」、或本人命中「人员」，
            只要中一条就会在<span class="rule">工作台（桌面启动台）</span>看到这个应用；
            <b>四类都为空 = 无权限</b>（不是“所有人可见”）：工作台上除创建人 / 维护人 /
            系统管理员（要能验证自己维护的应用）外，谁都看不到这个应用。
            <span class="rule">角色</span>可多选：命中的是<span class="rule">角色成员</span>，
            按 OA 标准角色成员规则判定，包括角色里直接指定的人员，以及按当前部门 / 分部 / 岗位指定的成员；
            选了多个角色时，命中其中任意一个即可见。
            <b>只按“当前分部 / 当前部门”判定，不含下级组织</b>——例如只选「财务部」时，
            财务部下级部门的员工看不到；要让下级也看到，请把那些下级部门一并选上（分部同理）。
            可见范围只影响工作台显示：管理页里创建人 / 维护人 / 系统管理员始终能看到自己维护的应用。
        </div>
        <div class="form-row"><label>排序号</label><div class="field">
            <input id="appcSortNo" type="number" step="1" value="0" style="max-width:160px;"
                   placeholder="小的排前面，默认 0"></div></div>
        <div class="form-row"><label>状态</label><div class="field">
            <select id="appcEnable">
                <option value="1">启用（桌面显示该图标，点击可跳转）</option>
                <option value="0">停用（桌面不显示，直接访问会提示已停用）</option>
            </select></div></div>
        <div class="form-row"><label>备注</label><div class="field"><textarea id="appcRemark" rows="2"></textarea></div></div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="appcEditCancelBtn">取消</button>
        <button type="button" class="btn" id="appcSaveBtn">保存</button>
    </div>
</div>

<%-- 多选选择弹框（维护人 + 可见范围的分部/部门/人员共用）：
     打开哪个选择框就把 key 切到它（各选择框分别记忆列表式/树状），骨架与拖动由 js/picker.js 提供 --%>
<div class="modal-mask" id="appcPickMask"></div>
<div class="modal wx-pick-modal" id="appcPickModal" style="width:700px;">
    <div class="modal-head"><span id="appcPickTitle">选择</span>
        <button type="button" class="modal-x" id="appcPickCloseBtn" title="关闭" aria-label="关闭"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"></path></svg></button></div>
    <div class="modal-body">
        <div class="pick-view-bar">
            <input type="text" id="appcPickSearch" placeholder="输入名称 / 登录名 / 工号 / 编号 / ID 搜索"
                   autocomplete="off">
            <div class="view-switch mini" id="appcPickViewSwitch" data-view-key="appcPick:maintainer">
                <button type="button" class="view-btn" data-view="list">列表式</button>
                <button type="button" class="view-btn active" data-view="tree">树状</button>
            </div>
            <button type="button" class="btn small secondary pick-all-btn" id="appcPickAllBtn"
                    data-pick-all="appcPick:maintainer"
                    title="全选/全不选当前列表（搜索后为命中的前 200 个）">全选</button>
        </div>
        <div class="wx-pick-list" id="appcPickList"></div>
        <div class="muted wx-pick-note" id="appcPickNote"></div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="appcPickClearBtn">清空已选</button>
        <button type="button" class="btn secondary" id="appcPickCancelBtn">取消</button>
        <button type="button" class="btn" id="appcPickOkBtn">确定</button>
    </div>
</div>

<script>
"use strict";
/* 片段内自带 byId（片段的脚本比页壳脚本先执行，见 skill.md 第一节第 7 条） */
function appcById(id) { return document.getElementById(id); }
function appcEsc(v) { return Oaones.esc(v); }
function appcToast(msg, mode, duration) {
    if (msg === null || msg === undefined || String(msg) === "") { return; }
    OaonesToast.show(msg, { mode: mode, duration: duration });
}

var appcState = { apps: [], saving: false };

/*
 * 图标上传的前端口径：数值全部取后端常量（OaonesConst），与 saveIcon 的校验一致，
 * 页面上显示的“建议长宽”就是后端认的那一套，避免两处各写一份数字。
 */
var APPC_ICON = {
    url: "/oaones/appsyn/appCenter/iconUpload.jsp",
    prefix: "<%=OaonesConst.APP_ICON_URL_PREFIX%>",
    suggest: <%=OaonesConst.APP_ICON_SUGGEST_SIZE%>,
    min: <%=OaonesConst.APP_ICON_MIN_SIZE%>,
    max: <%=OaonesConst.APP_ICON_MAX_SIZE%>,
    maxBytes: <%=OaonesConst.APP_ICON_MAX_BYTES%>,
    uploader: false
};

/* 列表分页器（每页 10/20/50/100/1000，见 js/table.js） */
var appcPager = OaonesTable.pager({
    key: "appCenter.list",
    el: appcById("appcPagerEl"),
    onChange: function () { renderAppcList(); }
});

/** 图标底色（与桌面页 js/appDesktop.js 同一套算法与配色：两边看到的颜色一致） */
var APPC_PALETTE = [
    "linear-gradient(160deg,#ff9a44,#fc6076)",
    "linear-gradient(160deg,#42e695,#3bb2b8)",
    "linear-gradient(160deg,#5b86e5,#36d1dc)",
    "linear-gradient(160deg,#f7971e,#ffd200)",
    "linear-gradient(160deg,#a18cd1,#fbc2eb)",
    "linear-gradient(160deg,#ff6a88,#ff99ac)",
    "linear-gradient(160deg,#0ba360,#3cba92)",
    "linear-gradient(160deg,#4facfe,#00f2fe)",
    "linear-gradient(160deg,#f093fb,#f5576c)",
    "linear-gradient(160deg,#667eea,#764ba2)"
];

function appcHash(str) {
    var s = String(str == null ? "" : str);
    var h = 0;
    for (var i = 0; i < s.length; i++) { h = (h * 31 + s.charCodeAt(i)) % 1000000007; }
    return h;
}

function appcTileBg(name) { return APPC_PALETTE[appcHash(name) % APPC_PALETTE.length]; }

/**
 * 默认文字图标的取字 / 取色基准：优先用保存时固化的 icon_seed
 * （改名不跟随——维护人改的是名字，图标不该跟着变），老数据（未固化）才退回当前应用名。
 * 与桌面页 js/appDesktop.js 的 tileSeed 是同一口径。
 */
function appcTileSeed(app) {
    var seed = String(app && app.icon_seed ? app.icon_seed : "").trim();
    return seed.length > 0 ? seed : String((app && app.app_name) || "");
}

/** 列表 / 弹框里的小图标（有图标地址用图片，否则用固化下来的默认文字图标） */
function appcTileHtml(app, size) {
    var px = size || 34;
    var icon = String(app.icon || "").trim();
    var seed = appcTileSeed(app);
    var inner = icon.length > 0
        ? "<img src='" + appcEsc(icon) + "' alt='' onerror=\"this.style.display='none'\">"
        : appcEsc(seed.length > 0 ? seed.charAt(0).toUpperCase() : "?");
    return "<span class='appc-icon' style='width:" + px + "px;height:" + px + "px;background:"
        + appcTileBg(seed) + "'>" + inner + "</span>";
}

/* ------------------------------------------------------------------
 * 列表
 * ------------------------------------------------------------------ */

function appcKeywordValue() { return (appcById("appcKeyword").value || "").trim(); }

function appcRowText(a) {
    // 创建人 / 维护人姓名、可见范围角色名也参与筛选：超管按“谁维护 / 谁能看”检索时直接打名称即可
    var roles = (a.vis_roles || []).map(function (r) { return r && r.name ? r.name : ""; }).join(" ");
    return [a.app_name, a.app_url, a.sso_class, a.sso_method, a.remark,
        a.creator_name, a.maintainer_names, roles]
        .map(function (v) { return String(v == null ? "" : v); }).join(" ").toLowerCase();
}

function filterAppcRows() {
    var rows = appcState.apps || [];
    var k = appcKeywordValue().toLowerCase();
    if (!k) { return rows; }
    return rows.filter(function (a) { return appcRowText(a).indexOf(k) >= 0; });
}

/**
 * 应用一列里的图标说明：用的是默认文字图标、且固化下来的首字与当前应用名首字不一致时，
 * 给一行提示（改名后图标不跟着变是既定口径，这里点明“图标上的字为什么和名字对不上”）。
 */
function appcIconNote(a) {
    if (String(a.icon || "").trim().length > 0) { return ""; }
    var name = String(a.app_name || "").trim();
    var seed = appcTileSeed(a);
    if (name.length === 0 || seed.length === 0) { return ""; }
    var seedChar = seed.charAt(0).toUpperCase();
    if (seedChar === name.charAt(0).toUpperCase()) { return ""; }
    return "<div class='user-mini'>默认图标首字：" + appcEsc(seedChar) + "</div>";
}

/** 打开方式的取值口径（与后端 open_mode 一致）：只认 1 新标签页 / 2 原标签页，其它都是 0 默认 */
function appcOpenModeValue(mode) {
    var m = Number(mode);
    return (m === 1 || m === 2) ? m : 0;
}

/** 打开方式的中文说明（列表里展示） */
function appcOpenModeText(mode) {
    var m = appcOpenModeValue(mode);
    if (m === 1) { return "新标签页"; }
    if (m === 2) { return "原标签页"; }
    return "默认";
}

/** 人员链接（OA 人员ID + 姓名）：姓名缺失时退化为“ID xxx”，统一走 OaonesHrm.user（泛微人员卡片） */
function appcUserLink(id, name) {
    return appcHrmLink("user", id, name);
}

/**
 * 可见范围一栏：勾选「全员可见」= 不控制权限（工作台上所有登录人都能看到）；
 * 未勾选时四类满足其一即可见，四类都空 = 未设置（无权限，工作台上除维护人 / 超管外都看不到）。
 */
function appcVisNote(a) {
    if (Number(a && a.vis_all) === 1) {
        return "<div class='user-mini'>可见范围：<b>全员可见</b>（不控制权限）</div>";
    }
    var parts = [];
    function part(label, list, kind) {
        var items = appcPickRowsFrom(list);
        if (!items.length) { return; }
        parts.push(label + "：" + items.map(function (m) {
            return appcHrmLink(kind, m.id, m.name);
        }).join("、"));
    }
    part("角色", a.vis_roles, "role");
    part("分部", a.vis_subs, "sub");
    part("部门", a.vis_depts, "dept");
    part("人员", a.vis_users, "user");
    return "<div class='user-mini'>可见范围："
        + (parts.length ? parts.join(" · ") : "<span class='row-note'>未设置（无权限）</span>") + "</div>";
}

/** 应用归属一栏：创建人 + 维护人（应用由这两类人维护；系统管理员可维护全部，列表里只列出自己能看到的应用） */
function appcOwnerNote(a) {
    var html = "创建人：" + appcUserLink(a.create_by, a.creator_name);
    var maintainers = appcPickRowsFrom(a.maintainers).map(function (m) { return appcUserLink(m.id, m.name); });
    if (maintainers.length > 0) {
        html += " · 维护人：" + maintainers.join("、");
    }
    return "<div class='user-mini'>" + html + "</div>";
}

function renderAppcStats(rows) {
    var enabled = 0;
    var sso = 0;
    rows.forEach(function (a) {
        if (a.enable === 1) { enabled++; }
        if (String(a.sso_class || "").trim().length > 0) { sso++; }
    });
    appcById("appcStats").innerHTML = ""
        + "<span class='bind-stat'><span>应用</span><b>" + rows.length + "</b> 个</span>"
        + "<span class='bind-stat'><span>启用中</span><b>" + enabled + "</b> 个</span>"
        + "<span class='bind-stat'><span>已停用</span><b>" + (rows.length - enabled) + "</b> 个</span>"
        + "<span class='bind-stat'><span>配置了单点登录</span><b>" + sso + "</b> 个</span>";
}

/** 单点登录实现类一列：类名 + 方法说明 + 类自述 + 可用性提示 */
function ssoCellHtml(a) {
    var cls = String(a.sso_class || "").trim();
    if (cls.length === 0) {
        return "<span class='muted'>未配置：点击图标直接打开默认地址</span>";
    }
    var html = "<code class='appc-class'>" + appcEsc(cls) + "</code>";
    if (String(a.sso_method || "").trim().length > 0) {
        html += "<div class='user-mini'>方法说明：" + appcEsc(a.sso_method) + "</div>";
    }
    if (String(a.sso_describe || "").trim().length > 0) {
        html += "<div class='user-mini'>类自述：" + appcEsc(a.sso_describe) + "</div>";
    }
    html += a.sso_ok
        ? "<div class='user-mini'><span class='tag bound'>实现类可用</span></div>"
        : "<div class='row-note'>实现类不可用（点击会退回默认地址）：" + appcEsc(a.sso_error) + "</div>";
    return html;
}

function renderAppcList() {
    var rows = filterAppcRows();
    appcPager.resetIfChanged(appcKeywordValue());
    var pageRows = appcPager.apply(rows);
    renderAppcStats(rows);
    var body = "";
    pageRows.forEach(function (a) {
        var id = String(a.id);
        var statusHtml = a.enable === 1
            ? "<span class='tag bound'>启用</span>"
            : "<span class='tag off'>停用</span>";
        body += "<tr>"
            + "<td><div class='appc-name-cell'>" + appcTileHtml(a, 34)
            + "<div><b>" + appcEsc(a.app_name) + "</b>"
            + "<div class='user-mini'>排序号：" + appcEsc(a.sort_no) + "</div>"
            + "<div class='user-mini'>打开方式：" + appcEsc(appcOpenModeText(a.open_mode)) + "</div>"
            + appcOwnerNote(a)
            + appcVisNote(a)
            + appcIconNote(a)
            + (String(a.remark || "").trim().length > 0
                ? "<div class='user-mini'>备注：" + appcEsc(a.remark) + "</div>" : "")
            + "</div></div></td>"
            + "<td><code class='app-url'>" + appcEsc(a.app_url) + "</code></td>"
            + "<td>" + ssoCellHtml(a) + "</td>"
            + "<td>" + statusHtml + "</td>"
            + "<td>" + appcEsc(a.modify_time || a.create_time || "-") + "</td>"
            + "<td><div class='row-ops'>"
            + "<button type='button' class='link appc-edit-btn' data-id='" + appcEsc(id) + "'>编辑</button>"
            + "<button type='button' class='link appc-state-btn' data-id='" + appcEsc(id) + "' data-enable='"
            + (a.enable === 1 ? "0" : "1") + "'>" + (a.enable === 1 ? "停用" : "启用") + "</button>"
            + "<button type='button' class='link danger appc-del-btn' data-id='" + appcEsc(id) + "' data-name='"
            + appcEsc(a.app_name) + "'>删除</button>"
            + "</div></td>"
            + "</tr>";
    });
    appcById("appcBody").innerHTML = body;
    appcPager.show();
    appcPager.render();
    OaonesTable.resizable(appcById("appcTable"), { key: "appCenter.table" });
    var empty = appcById("appcEmpty");
    empty.style.display = pageRows.length ? "none" : "block";
    empty.textContent = appcKeywordValue()
        ? "没有匹配的应用"
        : "还没有应用，点右上角“新建应用”添加第一个";
}

function findAppc(id) {
    var key = String(id == null ? "" : id);
    for (var i = 0; i < appcState.apps.length; i++) {
        if (String(appcState.apps[i].id) === key) { return appcState.apps[i]; }
    }
    return null;
}

function loadAppc() {
    Oaones.api("appc.list", { keyword: "" }, function (res) {
        if (!res || res.code !== 0) {
            appcToast((res && res.msg) ? res.msg : "读取应用列表失败", 2);
            return;
        }
        appcState.apps = (res.data && res.data.apps) || [];
        renderAppcList();
    });
}

/* ------------------------------------------------------------------
 * 启动台自动跳转（从「登录设置」移过来；只保存 gopage_seconds 一列）
 * ------------------------------------------------------------------ */

var appcGopageDefault = 5;
var appcGopageMax = <%=OaonesConst.LOGIN_GOPAGE_MAX_SECONDS%>;

/** 读秒数输入框：空串 = 用系统默认值；非整数返回 null。 */
function appcReadGopageInput() {
    var raw = String(appcById("appcGopageSeconds").value || "").replace(/^\s+|\s+$/g, "");
    if (raw.length === 0) {
        return { blank: true, value: appcGopageDefault, raw: "" };
    }
    if (!/^\d+$/.test(raw)) {
        return null;
    }
    var value = Number(raw);
    if (!isFinite(value)) {
        return null;
    }
    return { blank: false, value: Math.round(value), raw: raw };
}

function appcGopageResult(text, mode) {
    var el = appcById("appcGopageResult");
    if (!el) { return; }
    el.className = "appc-gopage-result" + (mode ? " " + mode : "");
    el.textContent = text || "";
}

/** 回显：配置值（空 = 默认）、实际生效值与范围；不做任何预览。 */
function renderAppcGopage(data) {
    if (!appcById("appcGopageBox")) { return; }
    data = data || {};
    appcGopageMax = Number(data.gopage_max) > 0 ? Number(data.gopage_max) : appcGopageMax;
    appcGopageDefault = Number(data.gopage_default) >= 0 ? Number(data.gopage_default) : 5;
    appcById("appcGopageRange").setAttribute("max", String(appcGopageMax));
    appcById("appcGopageSeconds").setAttribute("max", String(appcGopageMax));
    appcById("appcGopageDefault").textContent = "留空 = 系统默认 " + appcGopageDefault + " 秒";

    var configured = data.gopage_seconds;
    appcById("appcGopageSeconds").value = (configured === "" || configured === null
        || configured === undefined) ? "" : String(configured);
    var effective = Number(data.gopage_effective);
    if (!isFinite(effective)) {
        effective = appcGopageDefault;
    }
    appcById("appcGopageRange").value = String(effective);
    appcGopageResult(effective <= 0
        ? "当前生效：0 秒，不自动跳转"
        : "当前生效：" + effective + " 秒后自动跳转"
            + (appcById("appcGopageSeconds").value === "" ? "（留空使用系统默认值）" : ""));
}

function loadAppcGopage() {
    if (!appcById("appcGopageBox")) { return; }
    Oaones.api("appc.gopage", {}, function (res) {
        if (!res || res.code !== 0) {
            appcGopageResult((res && res.msg) ? res.msg : "读取启动台跳转设置失败", "error");
            return;
        }
        renderAppcGopage(res.data || {});
    });
}

function saveAppcGopage() {
    var value = appcReadGopageInput();
    if (!value) {
        appcToast("启动台自动跳转秒数请填整数（0 = 不自动跳转；留空 = 用系统默认值）", 1);
        return;
    }
    if (!value.blank && (value.value < 0 || value.value > appcGopageMax)) {
        appcToast("启动台自动跳转秒数必须在 0 ~ " + appcGopageMax + " 秒之间", 1);
        return;
    }
    var btn = appcById("appcGopageSaveBtn");
    btn.disabled = true;
    Oaones.api("appc.gopageSave", {
        gopage_seconds: value.blank ? "" : String(value.value)
    }, function (res) {
        btn.disabled = false;
        if (!res || res.code !== 0) {
            appcToast((res && res.msg) ? res.msg : "保存启动台跳转设置失败", 2);
            return;
        }
        renderAppcGopage(res.data || {});
        appcToast("启动台自动跳转设置已保存");
    });
}

/* ------------------------------------------------------------------
 * 新建 / 编辑
 * ------------------------------------------------------------------ */

/**
 * 预览用的默认文字图标基准：编辑已有记录时是保存时固化的 appcIconSeed
 * （所以改名字时预览里的字与底色都不动，和保存后列表 / 桌面的显示一致）；
 * 新建与点过「清除图标」之后取当前输入的应用名。
 */
function appcPreviewSeed() {
    var seed = (appcById("appcIconSeed").value || "").trim();
    return seed.length > 0 ? seed : (appcById("appcName").value || "").trim();
}

function renderAppcIconPreview() {
    var icon = (appcById("appcIcon").value || "").trim();
    var seed = appcPreviewSeed();
    var inner = icon.length > 0
        ? "<img src='" + appcEsc(icon) + "' alt='' onerror=\"this.parentNode.innerHTML='?'\">"
        : appcEsc(seed.length > 0 ? seed.charAt(0).toUpperCase() : "?");
    appcById("appcIconPreview").innerHTML = "<span class='appc-icon' style='background:"
        + appcTileBg(seed) + "'>" + inner + "</span>";
}

/** 图标上传状态行（mode："" 普通 / "warn" 提醒 / "error" 失败） */
function appcIconResult(text, mode) {
    var el = appcById("appcIconResult");
    el.className = "appc-icon-result" + (mode ? " " + mode : "");
    el.textContent = text;
}

function appcIconFileName(url) {
    var value = String(url || "");
    var idx = value.lastIndexOf("/");
    return idx >= 0 ? value.substring(idx + 1) : value;
}

function appcIconHumanSize(bytes) {
    var size = Number(bytes) || 0;
    return size >= 1024 ? (Math.round(size / 1024) + "KB") : (size + "B");
}

/** 上传成功后的状态行 + 尺寸提醒（建议值与后端校验同一套常量） */
function appcIconUploaded(data) {
    var width = Number(data.width) || 0;
    var height = Number(data.height) || 0;
    var text = "已上传";
    if (width > 0 && height > 0) {
        text += " " + width + "×" + height;
    }
    if (data.size) {
        text += "，" + appcIconHumanSize(data.size);
    }
    var tips = [];
    if (width > 0 && height > 0 && width !== height) {
        tips.push("图标不是正方形（" + width + "×" + height + "），桌面上会按正方形裁切显示，"
            + "建议 " + APPC_ICON.suggest + "×" + APPC_ICON.suggest);
    } else if (width > 0 && width < APPC_ICON.min) {
        tips.push("图标偏小（" + width + "×" + height + "），放大显示会发虚，"
            + "建议 " + APPC_ICON.suggest + "×" + APPC_ICON.suggest);
    }
    if (tips.length > 0) {
        text += "（" + tips[0] + "）";
        appcIconResult(text, "warn");
        appcToast(tips[0], 1, 6000);
        return;
    }
    appcIconResult(text);
    appcToast("图标已上传");
}

/**
 * 图标上传：前端只按后缀与体积粗筛一次（少一次无谓上传），
 * 真正的判定在后端（按文件头判类型，再校验体积与长宽），前端这两道只是体验优化。
 */
function appcIconUpload(file) {
    if (!file) { return; }
    if (!/\.(png|jpe?g|gif|bmp|webp)$/i.test(String(file.name || ""))) {
        appcToast("图标只支持 png / jpg / gif / bmp / webp 图片", 2);
        return;
    }
    if (file.size > APPC_ICON.maxBytes) {
        appcToast("图片不能超过 " + (APPC_ICON.maxBytes / 1024) + "KB（建议 200KB 以内），请压缩后再上传", 2);
        return;
    }
    var form = new FormData();
    form.append("icon", file, file.name);
    APPC_ICON.uploader = true;
    appcIconResult("上传中…");
    fetch(APPC_ICON.url, { method: "POST", body: form, credentials: "same-origin" })
        .then(function (r) { return r.json(); })
        .then(function (res) {
            APPC_ICON.uploader = false;
            if (!res || res.code !== 0) {
                var msg = (res && res.msg) ? res.msg : "图标上传失败";
                appcIconResult(msg, "error");
                appcToast(msg, 2);
                return;
            }
            var data = res.data || {};
            appcById("appcIcon").value = String(data.url || "");
            renderAppcIconPreview();
            appcIconUploaded(data);
        })
        .catch(function (e) {
            APPC_ICON.uploader = false;
            appcIconResult("上传失败：" + e.message, "error");
            appcToast("图标上传失败：" + e.message, 2);
        });
}

/** 打开“选择图片”对话框（真正的 <input type="file"> 藏起来，界面上只留按钮） */
function appcIconPick() {
    if (APPC_ICON.uploader) {
        appcToast("上一张图片还在上传，请稍候", 1);
        return;
    }
    var input = appcById("appcIconFile");
    input.value = "";
    input.click();
}

/**
 * 清除图标：清掉当前记录上传的图片图标，并按**当前应用名称**重新生成系统默认文字图标
 * （即把固化的基准 appcIconSeed 刷成现在这个名字；保存后它又会固定在这个名字上）。
 * 已上传的图片文件留在 oaones/appsyn/img/ 下，由服务端按引用清理（换图标 / 删应用时）。
 */
function appcIconClear() {
    var name = (appcById("appcName").value || "").trim();
    appcById("appcIcon").value = "";
    appcById("appcIconSeed").value = name;
    renderAppcIconPreview();
    appcIconResult("已清除（按当前应用名称「" + (name || "?") + "」生成默认文字图标）");
}

/* ------------------------------------------------------------------
 * 多选选择框：维护人 + 可见范围（角色 / 分部 / 部门 / 人员）
 * 候选来源 4 种（人员 / 部门 / 分部 / 角色），五个选择框共用一套实现；
 * 弹框骨架、拖动、列表式 / 树状、全选、双击即确定由 js/picker.js 提供（见 skill.md 第一节第 5 条）。
 * ------------------------------------------------------------------ */

/**
 * 选择框定义：kind → { source 候选来源, box 点击区, chips 标签区, title 弹框标题 }
 * source：person = OA 人员（user.oaUsers）、dept = OA 部门、sub = OA 分部（后两者来自 user.orgOptions）、
 * role = OA 角色（appc.roles；角色本身没有上下级，选择框只用列表式）。
 */
var APPC_PICK_KINDS = {
    maintainer: {
        source: "person", box: "appcMtBox", chips: "appcMtChips",
        title: "选择维护人（可多选 OA 员工）"
    },
    visSub: {
        source: "sub", box: "appcVisSubBox", chips: "appcVisSubChips",
        title: "选择可见范围 · 分部（只对所选分部本身生效，不含下级分部）"
    },
    visRole: {
        source: "role", box: "appcVisRoleBox", chips: "appcVisRoleChips",
        title: "选择可见范围 · 角色（可多选；角色成员按当前组织归属判定）", tree: false
    },
    visDept: {
        source: "dept", box: "appcVisDeptBox", chips: "appcVisDeptChips",
        title: "选择可见范围 · 部门（只对所选部门本身生效，不含下级部门）"
    },
    visUser: {
        source: "person", box: "appcVisUserBox", chips: "appcVisUserChips",
        title: "选择可见范围 · 人员（可多选 OA 员工）"
    }
};
var APPC_PICK_KIND_LIST = ["maintainer", "visRole", "visSub", "visDept", "visUser"];

/** 已选：kind → [{id,name}]（保存时分别拼成 maintainer_ids / vis_role_ids / vis_sub_ids / vis_dept_ids / vis_user_ids） */
var appcPickSelected = { maintainer: [], visRole: [], visSub: [], visDept: [], visUser: [] };
var appcPickTemp = {};                        // 弹框内的临时选中，点“确定”才回填到标签里
var appcPickKind = "";                        // 当前打开的选择框
var appcPickData = { person: null, dept: null, sub: null, role: null };   // 候选缓存（按来源懒加载）
var APPC_PICK_MAX = 200;                      // 一次最多列出 / 全选的候选数（与模块其它页同一口径）

function appcPickDef(kind) { return APPC_PICK_KINDS[kind] || APPC_PICK_KINDS.maintainer; }

function appcPickSource(kind) { return appcPickDef(kind).source; }

/** picker.js 的 key：每个选择框各自记忆列表式 / 树状（见 skill.md 第一节第 5 条） */
function appcPickKey(kind) { return "appcPick:" + kind; }

/** 后端返回的明细（maintainers / vis_subs / vis_depts / vis_users）→ [{id,name}] */
function appcPickRowsFrom(list) {
    var out = [];
    (list || []).forEach(function (m) {
        if (!m || m.id === null || m.id === undefined) { return; }
        out.push({ id: String(m.id), name: m.name ? String(m.name) : "" });
    });
    return out;
}

/** 候选 / 已选对象的显示名（人员是姓名，角色 / 部门 / 分部是名称） */
function appcPickName(kind, row) {
    var src = appcPickSource(kind);
    if (src === "person") { return String(row.lastname || row.name || ("ID " + row.id)); }
    return String(row.name || row.fullname || ("ID " + row.id));
}

/** OA 对象链接：人员 → 人员卡片，部门 → 部门页，分部 → 分部页；角色没有统一查看页，保持纯文本 */
function appcHrmLink(kind, id, name) {
    var uid = String(id == null ? "" : id).trim();
    var text = String(name == null ? "" : name).trim();
    if (uid.length === 0) { return "<span class='muted'>未知</span>"; }
    if (text.length === 0) { text = "ID " + uid; }
    if (kind === "role") { return appcEsc(text); }
    if (kind === "dept") { return OaonesHrm.dept(uid, text); }
    if (kind === "sub") { return OaonesHrm.sub(uid, text); }
    return OaonesHrm.user(uid, text);
}

/** 已选标签为空时的提示：维护人按角色措辞，可见范围四类都是“未设置 = 该类别不限制” */
function appcPickEmptyText(kind) {
    if (kind === "maintainer") {
        return OaonesTips.pick(
            "未设置额外维护人（创建人 + 系统管理员可维护）",
            "未设置额外维护人（保存后只有创建人 + 系统管理员可维护）");
    }
    return "未设置（该类别不限制）";
}

/** 已选标签（人员 / 部门 / 分部名称都走泛微标准页面超链接） */
function renderAppcPickChips(kind) {
    var list = appcPickSelected[kind] || [];
    var src = appcPickSource(kind);
    var linkKind = src === "dept" ? "dept" : (src === "sub" ? "sub" : (src === "role" ? "role" : "user"));
    var html = "";
    list.forEach(function (m) {
        html += "<span class='mt-chip' title='OA 对象ID " + appcEsc(m.id) + "'>"
            + appcHrmLink(linkKind, m.id, m.name)
            + "<button type='button' class='mt-chip-del' data-id='" + appcEsc(m.id)
            + "' title='移除'>×</button></span>";
    });
    if (!list.length) {
        html = "<span class='mt-empty'>" + appcPickEmptyText(kind) + "</span>";
    }
    appcById(appcPickDef(kind).chips).innerHTML = html;
    renderAppcVisWarn();
}

/** 是否勾选了「全员可见」（不控制权限）：勾选后四类选择框禁用、保存时 vis_all=1 */
function appcVisAllOn() {
    var box = appcById("appcVisAll");
    return !!(box && box.checked);
}

/** 后端返回的 vis_all（1 = 全员可见）：老数据 / 缺字段按 0 处理 */
function appcVisAllFlag(app) {
    return Number(app && app.vis_all) === 1;
}

/**
 * 可见范围提示：
 *   勾选「全员可见」= 不控制权限（四类不再参与判定，不显示提示）；
 *   未勾选且四类都没设置 = **无权限**（工作台上除创建人 / 维护人 / 系统管理员外谁都看不到）。
 * 只跟可见范围有关，维护人选择框的变化不会触发它（也不需要）。
 */
function renderAppcVisWarn() {
    var unset = !appcVisAllOn()
        && appcPickSelected.visRole.length === 0
        && appcPickSelected.visSub.length === 0
        && appcPickSelected.visDept.length === 0
        && appcPickSelected.visUser.length === 0;
    var el = appcById("appcVisWarn");
    if (el) { el.style.display = unset ? "block" : "none"; }
}

/** 按「全员可见」勾选状态禁用 / 启用下面四类选择框（已选内容保留，取消勾选后恢复生效） */
function syncAppcVisAll() {
    var on = appcVisAllOn();
    ["visRole", "visSub", "visDept", "visUser"].forEach(function (kind) {
        var box = appcById(appcPickDef(kind).box);
        var row = (box && box.closest) ? box.closest(".appc-vis-row") : null;
        if (row) { row.classList.toggle("is-off", on); }
    });
    renderAppcVisWarn();
}

/** 打开编辑弹框时回填：维护人 + 可见范围（角色 / 分部 / 部门 / 人员） */
function setAppcPickSelection(app) {
    appcPickSelected.maintainer = appcPickRowsFrom(app && app.maintainers);
    appcPickSelected.visRole = appcPickRowsFrom(app && app.vis_roles);
    appcPickSelected.visSub = appcPickRowsFrom(app && app.vis_subs);
    appcPickSelected.visDept = appcPickRowsFrom(app && app.vis_depts);
    appcPickSelected.visUser = appcPickRowsFrom(app && app.vis_users);
    APPC_PICK_KIND_LIST.forEach(renderAppcPickChips);
}

/** 移除一个已选标签（点标签上的 ×） */
function onAppcPickChipClick(e, kind) {
    var t = e.target;
    if (!t || !t.classList || !t.classList.contains("mt-chip-del")) { return; }
    e.stopPropagation();
    var id = String(t.getAttribute("data-id"));
    appcPickSelected[kind] = (appcPickSelected[kind] || []).filter(function (m) { return m.id !== id; });
    renderAppcPickChips(kind);
}

/** 提交值：逗号分隔的 OA 对象ID（后端会再按“正整数、去重、限长”归一化一次） */
function appcPickIds(kind) {
    return (appcPickSelected[kind] || []).map(function (m) { return m.id; }).join(",");
}

/* ---------------- 通用选择弹框（多选：列表式 / 树状 + 搜索 + 全选） ---------------- */

/** 搜索匹配文本：人员按 姓名/登录名/工号/手机号/ID/部门，角色按 名称/标记/ID，部门与分部按 名称/全称/编号/ID */
function appcPickRowText(kind, row) {
    var src = appcPickSource(kind);
    var cols;
    if (src === "person") {
        cols = [row.lastname, row.loginid, row.workcode, row.mobile, row.id, row.departmentname];
    } else if (src === "role") {
        cols = [row.id, row.name, row.mark, row.role_name, row.subcompanyname];
    } else {
        cols = [row.id, row.name, row.fullname, row.code];
    }
    return cols.map(function (v) { return String(v == null ? "" : v); }).join(" ");
}

/** 候选行副标题 */
function appcPickRowSubText(kind, row) {
    var src = appcPickSource(kind);
    var cols;
    if (src === "person") {
        cols = [row.loginid, row.workcode, row.departmentname];
    } else if (src === "role") {
        cols = [row.mark ? ("标记 " + row.mark) : "", "角色编号 " + row.id,
            row.subcompanyname ? ("分部 " + row.subcompanyname) : ""];
    } else {
        cols = [row.code ? ("编号 " + row.code) : "", row.fullname];
    }
    return cols.filter(function (v) { return v !== null && v !== undefined && String(v).length > 0; })
        .join(" / ");
}

/** 当前候选（未过滤）：人员 / 部门 / 分部各自的缓存 */
function appcPickCandidates(kind) {
    return appcPickData[appcPickSource(kind)] || [];
}

/** 按搜索关键字过滤后的候选（列表式用） */
function appcPickFiltered(kind) {
    var rows = appcPickCandidates(kind);
    var k = (appcById("appcPickSearch").value || "").trim().toLowerCase();
    if (!k) { return rows; }
    return rows.filter(function (r) { return appcPickRowText(kind, r).toLowerCase().indexOf(k) >= 0; });
}

function appcPickSelectedCount() {
    var count = 0;
    Object.keys(appcPickTemp).forEach(function (id) { if (appcPickTemp[id]) { count++; } });
    return count;
}

/** 弹框说明行：写明“只对所选对象本身生效、不含下级”与匹配数提示 */
function updateAppcPickNote(kind, matchCount, shownCount) {
    var src = appcPickSource(kind);
    var unit = src === "person" ? "人" : (src === "dept" ? "个部门" : (src === "role" ? "个角色" : "个分部"));
    var text = "已选 " + appcPickSelectedCount() + " " + unit + "，确定后写入标签";
    if (src === "role") {
        text += "；角色成员按登录人当前所属角色判定（直接人员 / 部门 / 分部 / 岗位）";
    }
    if (kind === "visDept" || kind === "visSub") {
        text += "；只对所选" + (kind === "visDept" ? "部门本身" : "分部本身") + "生效，不含下级";
    }
    text += "；双击某行可直接确定并退出";
    if (matchCount !== undefined && matchCount > shownCount) {
        text += "；当前匹配 " + matchCount + " " + unit + "，仅显示前 " + shownCount + " " + unit
            + "，请输入更精确的关键词";
    }
    appcById("appcPickNote").textContent = text;
}

function renderAppcPickList() {
    var kind = appcPickKind;
    if (!kind) { return; }
    syncAppcPickAllState(kind);
    if (appcPickData[appcPickSource(kind)] === null) { return; }
    var mode = OaonesPicker.mode(appcPickKey(kind));
    var keyword = appcById("appcPickSearch").value;
    var html;
    var matchCount;
    var shownCount;
    if (mode === "tree") {
        // 树状：与列表模式同口径（同一批候选 + 同一份搜索关键字）
        var limited = appcPickCandidates(kind).slice(0, APPC_PICK_MAX);
        matchCount = limited.length;
        shownCount = limited.length;
        html = OaonesPicker.renderTree(appcPickKey(kind), appcPickTreeRows(kind, limited), {
            parentOf: function (r) { return r.parent; },
            textOf: function (r) { return r.searchText; },
            isGroup: function (r) { return r.group === true; },
            groupHtml: function (r, open, hit) {
                return OaonesPicker.groupHtml(appcPickKey(kind), r, open, hit);
            },
            keyword: keyword,
            emptyText: "没有匹配的候选",
            optionHtml: function (r) { return appcPickRowHtml(kind, r.row); }
        });
    } else {
        var rows = appcPickFiltered(kind);
        var shown = rows.slice(0, APPC_PICK_MAX);
        matchCount = rows.length;
        shownCount = shown.length;
        if (!shown.length) {
            html = "<div class='wx-pick-empty'>没有匹配的候选</div>";
        } else {
            var headSub = appcPickSource(kind) === "role"
                ? "标记 / 角色编号 / 分部" : "登录名 / 工号 / 部门";
            html = "<div class='wx-pick-head mt-pick-head'>"
                + "<span>选择</span><span>名称</span><span>" + headSub + "</span></div>";
            shown.forEach(function (r) { html += appcPickRowHtml(kind, r); });
        }
    }
    var listEl = appcById("appcPickList");
    listEl.className = "wx-pick-list" + (mode === "tree" ? " is-tree" : "");
    listEl.innerHTML = html;
    updateAppcPickNote(kind, matchCount, shownCount);
}

/** 当前列表可全选的候选（先取前 200，再按搜索关键字过滤，与两种显示模式一致）。 */
function appcPickAllRows(kind) {
    var limited = appcPickCandidates(kind).slice(0, APPC_PICK_MAX);
    var k = (appcById("appcPickSearch").value || "").trim().toLowerCase();
    return k ? limited.filter(function (r) { return appcPickRowText(kind, r).toLowerCase().indexOf(k) >= 0; }) : limited;
}

/** 同步“全选 / 全不选”按钮：返回当前列表候选是否已全部选中。 */
function syncAppcPickAllState(kind) {
    var rows = appcPickAllRows(kind);
    var allOn = rows.length > 0 && rows.every(function (r) { return appcPickTemp[String(r.id)] === true; });
    OaonesPicker.syncSelectAll(appcPickKey(kind), allOn);
    return allOn;
}

/** 候选行（列表式 / 树状共用），选中态取自 appcPickTemp。 */
function appcPickRowHtml(kind, row) {
    var id = String(row.id);
    var on = appcPickTemp[id] ? true : false;
    return "<label class='mt-pick-row" + (on ? " checked" : "") + "'>"
        + "<input type='checkbox' class='mt-pick-box' data-id='" + appcEsc(id) + "'"
        + (on ? " checked" : "") + ">"
        + "<span class='mt-pick-name'>" + appcEsc(appcPickName(kind, row)) + "</span>"
        + "<span class='mt-pick-sub'>" + appcEsc(appcPickRowSubText(kind, row)) + "</span>"
        + "</label>";
}

/** 分组节点的“下级候选数”（树状分组上显示） */
function appcCountGroupRows(rows) {
    var byParent = {};
    rows.forEach(function (r) {
        var p = String(r.parent == null ? "" : r.parent);
        (byParent[p] = byParent[p] || []).push(r);
    });
    function countLeaves(id) {
        var total = 0;
        (byParent[id] || []).forEach(function (r) { total += r.group === true ? countLeaves(r.id) : 1; });
        return total;
    }
    rows.forEach(function (r) { if (r.group === true) { r.count = countLeaves(r.id); } });
    return rows;
}

/** 候选缓存里的部门 / 分部索引（树状需要父级信息） */
function appcDeptIndex() {
    var byId = {};
    (appcPickData.dept || []).forEach(function (d) { byId[String(d.id)] = d; });
    return byId;
}

function appcSubIndex() {
    var byId = {};
    (appcPickData.sub || []).forEach(function (s) { byId[String(s.id)] = s; });
    return byId;
}

/**
 * 部门路径节点：候选部门连同其全部上级部门，按“分部（分组）→ 部门”挂树。
 * @param deptRows   候选部门行
 * @param selectable true = 部门节点本身可选（部门选择框）；false = 部门只做路径分组（人员树里用）
 * @return { rows: 节点数组, keep: {部门ID:true} }
 */
function appcDeptPathNodes(deptRows, selectable) {
    var deptById = appcDeptIndex();
    var subById = appcSubIndex();
    var keep = {};
    (deptRows || []).forEach(function (d) {
        var cur = String(d.id);
        var guard = 0;
        while (cur && deptById[cur] && !keep[cur] && guard++ < 100) {
            keep[cur] = true;
            var p = String(deptById[cur].parent == null ? "" : deptById[cur].parent);
            cur = (p && p !== "0" && deptById[p]) ? p : "";
        }
    });
    var rows = [];
    var addedSub = {};
    Object.keys(keep).forEach(function (id) {
        var d = deptById[id];
        var pid = String(d.parent == null ? "" : d.parent);
        var parent = (pid && pid !== "0" && deptById[pid] && keep[pid]) ? ("d:" + pid) : "";
        if (!parent) {
            // 顶层部门：挂到所属分部（没有分部则作为根节点）
            var sid = String(d.subcompanyid1 == null ? "" : d.subcompanyid1);
            if (sid) {
                if (!addedSub[sid]) {
                    addedSub[sid] = true;
                    var s = subById[sid];
                    rows.push({
                        group: true, id: "s:" + sid, parent: "", count: 0,
                        name: (s && (s.name || s.fullname)) || ("分部 " + sid)
                    });
                }
                parent = "s:" + sid;
            }
        }
        rows.push({
            group: selectable !== true, id: "d:" + id, parent: parent,
            name: d.name || d.fullname || ("部门 " + id),
            fullname: d.fullname || d.name || "",
            row: d, searchText: appcPickRowText("visDept", d), count: 0
        });
    });
    return { rows: rows, keep: keep };
}

/** 人员树：OA 分部（分组）→ 部门（分组）→ 人员（可选叶子），只保留含候选人的部门及其上级 */
function appcPersonTreeRows(personRows) {
    var deptById = appcDeptIndex();
    var deptIds = {};
    (personRows || []).forEach(function (u) {
        var did = String(u.departmentid == null ? "" : u.departmentid);
        if (did && deptById[did]) { deptIds[did] = true; }
    });
    var deptRows = Object.keys(deptIds).map(function (id) { return deptById[id]; });
    var path = appcDeptPathNodes(deptRows, false);
    var out = path.rows;
    var unassigned = null;
    (personRows || []).forEach(function (u) {
        var did = String(u.departmentid == null ? "" : u.departmentid);
        var parent = (did && path.keep[did]) ? ("d:" + did) : "";
        if (!parent) {
            if (!unassigned) {
                unassigned = { group: true, id: "d:none", parent: "", name: "未分配部门", count: 0 };
                out.push(unassigned);
            }
            parent = "d:none";
        }
        out.push({
            group: false, id: "u:" + String(u.id), parent: parent, row: u,
            searchText: appcPickRowText("visUser", u)
        });
    });
    return appcCountGroupRows(out);
}

/** 部门树：OA 分部（分组）→ 部门（可选，可继续展开下级；上级部门一并带出来做路径） */
function appcDeptTreeRows(deptRows) {
    return appcCountGroupRows(appcDeptPathNodes(deptRows, true).rows);
}

/** 分部树：分部本身可选，按上级分部（supsubcomid）挂树 */
function appcSubTreeRows(subRows) {
    var byId = appcSubIndex();
    var keep = {};
    (subRows || []).forEach(function (s) {
        var cur = String(s.id);
        var guard = 0;
        while (cur && byId[cur] && !keep[cur] && guard++ < 100) {
            keep[cur] = true;
            var p = String(byId[cur].parent == null ? "" : byId[cur].parent);
            cur = (p && p !== "0" && byId[p]) ? p : "";
        }
    });
    var out = [];
    Object.keys(keep).forEach(function (id) {
        var s = byId[id];
        var pid = String(s.parent == null ? "" : s.parent);
        out.push({
            group: false, id: "s:" + id,
            parent: (pid && pid !== "0" && byId[pid] && keep[pid]) ? ("s:" + pid) : "",
            name: s.name || s.fullname || ("分部 " + id),
            fullname: s.fullname || s.name || "",
            row: s, searchText: appcPickRowText("visSub", s)
        });
    });
    return out;
}

/** 按当前选择框的候选来源挑树模型 */
function appcPickTreeRows(kind, rows) {
    var src = appcPickSource(kind);
    // 角色本身没有上下级关系：即使历史状态落在树状，也按平铺行渲染（页面会隐藏树状切换）
    if (src === "role") {
        return (rows || []).map(function (r) {
            return {
                group: false, id: String(r.id), parent: "", row: r,
                searchText: appcPickRowText("visRole", r)
            };
        });
    }
    if (src === "person") { return appcPersonTreeRows(rows); }
    if (src === "dept") { return appcDeptTreeRows(rows); }
    return appcSubTreeRows(rows);
}

/** 当前选择框所需的候选是否已拉齐：人员 / 角色各一份缓存，部门与分部共用 user.orgOptions 一次拉齐。 */
function appcPickReady(kind) {
    var src = appcPickSource(kind);
    if (src === "person") { return appcPickData.person !== null; }
    if (src === "role") { return appcPickData.role !== null; }
    return appcPickData.dept !== null && appcPickData.sub !== null;
}

/** 懒加载候选：人员走 user.oaUsers，部门 / 分部走 user.orgOptions，角色走 appc.roles。 */
function appcPickLoadData(kind, done) {
    if (appcPickReady(kind)) { done(); return; }
    appcById("appcPickList").innerHTML = "<div class='wx-pick-empty'>正在加载 OA 组织数据…</div>";
    var src = appcPickSource(kind);
    if (src === "person") {
        Oaones.api("user.oaUsers", {}, function (res) {
            appcPickData.person = (res && res.code === 0 && res.data) ? res.data : [];
            done();
        });
        return;
    }
    if (src === "role") {
        Oaones.api("appc.roles", {}, function (res) {
            appcPickData.role = (res && res.code === 0 && res.data) ? res.data : [];
            done();
        });
        return;
    }
    Oaones.api("user.orgOptions", {}, function (res) {
        var data = (res && res.code === 0 && res.data) ? res.data : {};
        appcPickData.dept = data.oa_departments || [];
        appcPickData.sub = data.oa_subcompanies || [];
        done();
    });
}

function openAppcPick(kind) {
    appcPickKind = kind;
    var key = appcPickKey(kind);
    var def = appcPickDef(kind);
    appcPickTemp = {};
    (appcPickSelected[kind] || []).forEach(function (m) { appcPickTemp[m.id] = true; });
    appcById("appcPickTitle").textContent = def.title;
    // 每个选择框各自记忆列表式 / 树状：把弹框的 key 切到当前选择框，再同步切换按钮与全选按钮
    appcById("appcPickViewSwitch").setAttribute("data-view-key", key);
    appcById("appcPickAllBtn").setAttribute("data-pick-all", key);
    // 角色候选没有上下级关系：按 skill.md 第一节第 5 条只提供列表式，切到其它选择框时恢复树状按钮
    var treeBtn = appcById("appcPickViewSwitch").querySelector(".view-btn[data-view='tree']");
    if (treeBtn) { treeBtn.hidden = def.tree === false; }
    OaonesPicker.syncSwitches(key);
    appcById("appcPickSearch").value = "";
    // 树状默认收起分支：每次打开弹框重置展开状态
    OaonesPicker.resetTree(key);
    appcById("appcPickMask").style.display = "block";
    appcById("appcPickModal").style.display = "block";
    if (appcPickReady(kind)) { renderAppcPickList(); } else { appcPickLoadData(kind, renderAppcPickList); }
    appcById("appcPickSearch").focus();
}

function closeAppcPick() {
    appcById("appcPickMask").style.display = "none";
    appcById("appcPickModal").style.display = "none";
    appcPickTemp = {};
}

function clearAppcPick() {
    appcPickTemp = {};
    renderAppcPickList();
}

function onAppcPickListChange(e) {
    var t = e.target;
    if (!t || !t.classList || !t.classList.contains("mt-pick-box")) { return; }
    var id = String(t.getAttribute("data-id"));
    if (t.checked) { appcPickTemp[id] = true; } else { delete appcPickTemp[id]; }
    var row = t.parentNode;
    while (row && row.classList && !row.classList.contains("mt-pick-row")) { row = row.parentNode; }
    if (row && row.classList) { row.classList.toggle("checked", t.checked); }
    updateAppcPickNote(appcPickKind);
}

/** 确定：按候选顺序回填，保留已失效（不在当前缓存里）的历史值 */
function confirmAppcPick() {
    var kind = appcPickKind;
    if (!kind) { return; }
    var picked = [];
    var seen = {};
    appcPickCandidates(kind).forEach(function (r) {
        var id = String(r.id);
        if (appcPickTemp[id] && !seen[id]) {
            picked.push({ id: id, name: appcPickName(kind, r) });
            seen[id] = true;
        }
    });
    (appcPickSelected[kind] || []).forEach(function (m) {
        if (appcPickTemp[m.id] && !seen[m.id]) {
            picked.push(m);
            seen[m.id] = true;
        }
    });
    appcPickSelected[kind] = picked;
    renderAppcPickChips(kind);
    closeAppcPick();
}

/* 五个选择框各自注册重绘与“全选 / 全不选”（key 与弹框上的 data-view-key / data-pick-all 一致） */
APPC_PICK_KIND_LIST.forEach(function (kind) {
    OaonesPicker.register(appcPickKey(kind), function () { renderAppcPickList(); });
    OaonesPicker.setSelectAll(appcPickKey(kind), function () {
        var rows = appcPickAllRows(kind);
        var allOn = syncAppcPickAllState(kind);
        rows.forEach(function (r) {
            var id = String(r.id);
            if (allOn) { delete appcPickTemp[id]; } else { appcPickTemp[id] = true; }
        });
        renderAppcPickList();
        return syncAppcPickAllState(kind);
    });
});

function openAppcEdit(app) {
    appcById("appcEditTitle").textContent = app ? "编辑应用" : "新建应用";
    appcById("appcId").value = app ? String(app.id) : "";
    appcById("appcName").value = app ? String(app.app_name || "") : "";
    appcById("appcUrl").value = app ? String(app.app_url || "") : "";
    appcById("appcOpenMode").value = String(appcOpenModeValue(app ? app.open_mode : 0));
    appcById("appcSsoClass").value = app ? String(app.sso_class || "") : "";
    appcById("appcSsoMethod").value = app ? String(app.sso_method || "") : "";
    appcById("appcIcon").value = app ? String(app.icon || "") : "";
    appcById("appcIconSeed").value = app ? String(app.icon_seed || "") : "";
    appcById("appcIconFile").value = "";
    // 可见范围：先把「全员可见」勾选状态回填，再回填四类选择框（标签渲染时会按勾选状态给出提示）
    appcById("appcVisAll").checked = appcVisAllFlag(app);
    setAppcPickSelection(app);
    syncAppcVisAll();
    appcById("appcSortNo").value = app ? String(app.sort_no == null ? 0 : app.sort_no) : "0";
    appcById("appcEnable").value = app && app.enable === 0 ? "0" : "1";
    appcById("appcRemark").value = app ? String(app.remark || "") : "";
    appcById("appcClassResult").textContent = "";
    renderAppcIconPreview();
    var icon = (appcById("appcIcon").value || "").trim();
    if (icon.length === 0) {
        var seed = (appcById("appcIconSeed").value || "").trim();
        appcIconResult(seed.length > 0
            ? "未上传（默认文字图标首字「" + seed.charAt(0).toUpperCase() + "」，保存时固定、改名不跟随）"
            : "未上传（保存后按应用名称首字生成默认文字图标并固定）");
    } else if (icon.indexOf(APPC_ICON.prefix) === 0) {
        appcIconResult("当前图标：" + appcIconFileName(icon));
    } else {
        // 历史数据（图标不是从本页上传的地址）：保存会被后端拒绝，这里先提醒重新上传或清除
        appcIconResult("当前图标不是页面上传的地址，保存前请重新上传或点“清除图标”", "warn");
    }
    appcById("appcEditMask").style.display = "block";
    appcById("appcEditModal").style.display = "block";
    appcById("appcName").focus();
}

function closeAppcEdit() {
    appcById("appcEditMask").style.display = "none";
    appcById("appcEditModal").style.display = "none";
}

/** 校验“单点登录实现类”（后端按类名反射校验：是否存在、是否实现 AppSsoHandler、能否实例化） */
function checkAppcClass() {
    var cls = (appcById("appcSsoClass").value || "").trim();
    var result = appcById("appcClassResult");
    result.textContent = "校验中…";
    Oaones.api("appc.checkClass", { sso_class: cls }, function (res) {
        if (!res || res.code !== 0) {
            result.textContent = (res && res.msg) ? res.msg : "校验失败";
            return;
        }
        var data = res.data || {};
        if (data.ok) {
            result.textContent = cls.length > 0
                ? "校验通过：将调用 " + (data.method || "loginUrl(AppSsoContext)") + " 生成访问地址"
                : "留空表示不做单点登录（直接打开默认地址）";
        } else {
            result.textContent = "校验不通过：" + (data.msg || "");
        }
    });
}

function saveAppc() {
    if (appcState.saving) { return; }
    var name = (appcById("appcName").value || "").trim();
    var url = (appcById("appcUrl").value || "").trim();
    if (!name) {
        appcToast("请填写应用名称", 1);
        appcById("appcName").focus();
        return;
    }
    if (!url) {
        appcToast("请填写应用默认访问地址", 1);
        appcById("appcUrl").focus();
        return;
    }
    var params = {
        id: appcById("appcId").value,
        app_name: name,
        app_url: url,
        // 打开方式：0 默认（当前页面内打开）/ 1 新标签页 / 2 原标签页（整个页面 reload）
        open_mode: appcById("appcOpenMode").value,
        sso_class: (appcById("appcSsoClass").value || "").trim(),
        sso_method: (appcById("appcSsoMethod").value || "").trim(),
        icon: (appcById("appcIcon").value || "").trim(),
        // 默认文字图标的基准：非空 = 页面要求按这个名字固化（新建 / 点「清除图标」后）；
        // 为空 = 沿用后端已固化的值（改名不跟随图标）
        icon_seed: (appcById("appcIconSeed").value || "").trim(),
        // 维护人：逗号分隔的 OA 人员ID（创建人在后端自动记，不在这里重复送）
        maintainer_ids: appcPickIds("maintainer"),
        // 可见范围：勾选「全员可见」= 不控制权限（vis_all=1，四类不再参与判定）；
        // 未勾选时角色 / 分部 / 部门 / 人员四类各自多选，**满足其一即可见**（四类都为空 = 无权限）
        vis_all: appcVisAllOn() ? "1" : "0",
        vis_role_ids: appcPickIds("visRole"),
        vis_sub_ids: appcPickIds("visSub"),
        vis_dept_ids: appcPickIds("visDept"),
        vis_user_ids: appcPickIds("visUser"),
        sort_no: (appcById("appcSortNo").value || "0").trim(),
        enable: appcById("appcEnable").value,
        remark: appcById("appcRemark").value || ""
    };
    appcState.saving = true;
    appcById("appcSaveBtn").disabled = true;
    Oaones.api("appc.save", params, function (res) {
        appcState.saving = false;
        appcById("appcSaveBtn").disabled = false;
        if (res && res.code === 0) {
            appcToast("应用已保存");
            closeAppcEdit();
            loadAppc();
        } else {
            appcToast((res && res.msg) ? res.msg : "保存失败", 2);
        }
    });
}

function toggleAppcState(id) {
    var app = findAppc(id);
    if (!app) { return; }
    var next = app.enable === 1 ? "0" : "1";
    var tip = app.enable === 1
        ? "停用应用「" + app.app_name + "」？停用后桌面启动台不再显示该图标，直接访问也会被拒绝。"
        : "启用应用「" + app.app_name + "」？启用后它会出现在桌面启动台上，点击即可跳转。";
    OaonesToast.confirm(tip, function () {
        Oaones.api("appc.state", { id: id, enable: next }, function (res) {
            if (res && res.code === 0) {
                appcToast(next === "1" ? "应用已启用" : "应用已停用");
                loadAppc();
            } else {
                appcToast((res && res.msg) ? res.msg : "操作失败", 2);
            }
        });
    });
}

function delAppc(id, name) {
    OaonesToast.confirm("确认删除应用「" + (name || "") + "」？删除后桌面启动台上的该图标立即消失，"
        + "该应用的默认地址与单点登录实现类配置一并删除（不影响对方系统本身）。",
        function () {
            Oaones.api("appc.delete", { id: id }, function (res) {
                if (res && res.code === 0) {
                    appcToast("应用已删除");
                    loadAppc();
                } else {
                    appcToast((res && res.msg) ? res.msg : "删除失败", 2);
                }
            });
        }, { danger: true });
}

/**
 * 浏览器可能把保存的登录名自动填进“应用筛选”：初始化后清掉它，
 * 避免一进页面就按这个关键字过滤（用户自己输入过就不再干预）。
 */
var appcKeywordTouched = false;
function appcClearAutofilledKeyword() {
    var kw = appcById("appcKeyword");
    if (!kw || appcKeywordTouched) { return; }
    if (kw.value) { kw.value = ""; renderAppcList(); }
}

/* ------------------------------------------------------------------
 * 事件接线（片段脚本在解析期执行，这里只注册，不访问数据）
 * ------------------------------------------------------------------ */

appcById("appcSearchBtn").addEventListener("click", function () { renderAppcList(); });
appcById("appcResetBtn").addEventListener("click", function () {
    appcById("appcKeyword").value = "";
    renderAppcList();
});
appcById("appcKeyword").addEventListener("input", function () {
    appcKeywordTouched = true;
    renderAppcList();
});
if (appcById("appcGopageBox")) {
    appcById("appcGopageRange").addEventListener("input", function () {
        var value = Math.min(appcGopageMax, Math.max(0, Math.round(Number(this.value) || 0)));
        appcById("appcGopageSeconds").value = String(value);
        appcGopageResult(value <= 0
            ? "0 秒：不自动跳转（点“保存跳转设置”后生效）"
            : value + " 秒后自动跳转（点“保存跳转设置”后生效）");
    });
    appcById("appcGopageSeconds").addEventListener("input", function () {
        var value = appcReadGopageInput();
        if (!value || value.blank) {
            appcGopageResult("留空 = 使用系统默认 " + appcGopageDefault + " 秒，点保存后生效");
            return;
        }
        appcById("appcGopageRange").value = String(
            Math.min(appcGopageMax, Math.max(0, value.value)));
        appcGopageResult(value.value + " 秒后自动跳转（点“保存跳转设置”后生效）");
    });
    appcById("appcGopageResetBtn").addEventListener("click", function () {
        appcById("appcGopageSeconds").value = "";
        appcById("appcGopageRange").value = String(appcGopageDefault);
        appcGopageResult("已恢复为“留空”：系统默认 " + appcGopageDefault + " 秒，点保存后生效");
    });
    appcById("appcGopageSaveBtn").addEventListener("click", saveAppcGopage);
}
appcById("appcNewBtn").addEventListener("click", function () { openAppcEdit(null); });
appcById("appcRefreshBtn").addEventListener("click", function () { loadAppc(); });
appcById("appcBody").addEventListener("click", function (e) {
    var t = e.target;
    if (!t || !t.classList) { return; }
    if (t.classList.contains("appc-edit-btn")) {
        openAppcEdit(findAppc(t.getAttribute("data-id")));
        return;
    }
    if (t.classList.contains("appc-state-btn")) {
        toggleAppcState(t.getAttribute("data-id"));
        return;
    }
    if (t.classList.contains("appc-del-btn")) {
        delAppc(t.getAttribute("data-id"), t.getAttribute("data-name"));
    }
});
appcById("appcEditCloseBtn").addEventListener("click", closeAppcEdit);
appcById("appcEditCancelBtn").addEventListener("click", closeAppcEdit);
appcById("appcEditMask").addEventListener("click", closeAppcEdit);
appcById("appcSaveBtn").addEventListener("click", saveAppc);
appcById("appcCheckClassBtn").addEventListener("click", checkAppcClass);
appcById("appcName").addEventListener("input", renderAppcIconPreview);
appcById("appcIconPickBtn").addEventListener("click", appcIconPick);
appcById("appcIconClearBtn").addEventListener("click", appcIconClear);
appcById("appcIconFile").addEventListener("change", function () {
    appcIconUpload(this.files && this.files.length > 0 ? this.files[0] : null);
});
/* 五个多选框（维护人 + 可见范围的 角色 / 分部 / 部门 / 人员）：点选择框开弹框、标签上的 × 移除 */
APPC_PICK_KIND_LIST.forEach(function (kind) {
    appcById(appcPickDef(kind).box).addEventListener("click", function () { openAppcPick(kind); });
    appcById(appcPickDef(kind).chips).addEventListener("click", function (e) { onAppcPickChipClick(e, kind); });
});
/* 可见范围·全员可见开关：勾选 / 取消时切换下面四类选择框的可用状态（不控制权限 ↔ 按四类判定） */
appcById("appcVisAll").addEventListener("change", syncAppcVisAll);
/* 弹框内：搜索 / 勾选 / 全选（按钮由 picker.js 分派）/ 双击即确定 */
appcById("appcPickSearch").addEventListener("input", renderAppcPickList);
appcById("appcPickList").addEventListener("change", onAppcPickListChange);
appcById("appcPickMask").addEventListener("click", closeAppcPick);
appcById("appcPickCloseBtn").addEventListener("click", closeAppcPick);
appcById("appcPickCancelBtn").addEventListener("click", closeAppcPick);
appcById("appcPickClearBtn").addEventListener("click", clearAppcPick);
appcById("appcPickOkBtn").addEventListener("click", confirmAppcPick);
// 双击某行：选中该行后立即确定并退出弹框
appcById("appcPickList").addEventListener("dblclick", function (e) {
    var row = e.target && e.target.closest ? e.target.closest(".mt-pick-row") : null;
    if (!row) { return; }
    var box = row.querySelector(".mt-pick-box");
    if (!box) { return; }
    appcPickTemp[String(box.getAttribute("data-id"))] = true;
    confirmAppcPick();
});
OaonesPicker.syncSwitches();
APPC_PICK_KIND_LIST.forEach(renderAppcPickChips);
[0, 400, 1200].forEach(function (delay) {
    window.setTimeout(appcClearAutofilledKeyword, delay);
});
window.addEventListener("focus", appcClearAutofilledKeyword);

window.AppListPanel = {
    /** 页壳在 DOMContentLoaded 之后调用；重复调用必须幂等 */
    load: function () {
        loadAppc();
        loadAppcGopage();
    }
};
</script>
