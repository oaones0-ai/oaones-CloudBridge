<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%
    /* 三个平台共用本片段：平台由引入方决定（request 属性 oaonesPBPlatform 优先，其次 platform 参数）；
       平台文案统一用 oaonesPBShort（企微 / 钉钉 / 飞书）与 oaonesPBFull（企业微信 / 钉钉 / 飞书）。

       本页签的“消息类型”完全引用泛微标准页
       （后台 应用中心 → 消息类型，页面 /spa/smallApp/static4engine/engine.html#/ecAndEM/messagetypeconfig）：
       标准页的【模块设置 / 消息来源 / 消息类型】原样列出来，本模块只在每个类型上补
       “推不推（推送开关）+ 推到哪个绑定的第三方应用 + 用什么形态推（可选细化）”。 */
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
    // 消息形态在各平台的默认口径：企微“文本卡片”，钉钉 / 飞书“markdown / 富文本”
    boolean oaonesPBMsgWxStyle = !oaonesPBFs && !oaonesPBDd;
    String oaonesPBMsgDefaultForm = oaonesPBMsgWxStyle ? "文本卡片" : "markdown";
    String oaonesPBMsgJsonPlaceholder = oaonesPBMsgWxStyle ? "仅图文消息使用" : "仅链接卡片（link）使用";
    String oaonesPBMsgDefaultFormCode = oaonesPBMsgWxStyle ? "textcard" : "text";
%>

<%--
  统一消息中心 → 消息类型

  列表 = 泛微标准页里的“消息类型”一行一条（模块 / 消息来源 / 消息类型都从标准表实时读取）：
    * 标准页新建 / 改名 / 改来源 / 改模块后，刷新本页即可看到，本模块不再自己维护类型清单；
    * 列表只按消息类型平铺（不再按模块分段，模块只在筛选框里用弹出选择框挑），
      “消息来源”列占 30% 宽，表头可拖拽调列宽（js/table.js#resizable）；
    * 每行“状态”列显示启用 / 停用，改状态用行内“启用 / 停用”按钮，或勾选多行后批量启用 / 停用；
      推送目标（【应用绑定】里维护的第三方应用）仍是行内下拉、改完即存；
    * 点“设置”可按类型细化消息形态 / 模板 / 卡片动作（不配就走平台默认形态）。
  未被任何标准消息类型覆盖的消息来源（平台自建来源、建模“自定义提醒”等）单独一块列出，仍按来源编号推送。
  * 泛微标准页里还有来源**没挂到任何消息类型上**时（本模块表里没有行），
    这类来源也一并列在下面那块列表里（接口给的 `configured=false` / `rowId=0`：“说明”列写明
    “本页还没单独配置”，状态 / 设置 / 测试 / 目标应用都可改，改完按默认口径建一行；没建行时不给“删除”），
    该块上方再用一行提示给出个数与去向（数据来自接口 summary.uncoveredCount / summary.uncoveredSources）。
--%>
<div class="help-source" id="msgTypeHelpText">
    <b>本页的消息类型就是泛微标准后台「应用中心 → 消息类型」里的那份设置</b>
    （模块 / 消息来源 / 消息类型三层口径完全一致，本页只读引用、不重复维护）：
    <span class="rule">标准页里新建一个消息类型，回到本页刷新就能看到</span>；
    标准页把某个「消息来源」挂到哪个类型上，本页就按哪个类型推。
    <p><b>列表里的「消息类型」「消息来源」按 <code>&lt;ID&gt;#&lt;名称&gt;</code> 显示</b>
    （<span class="rule">先显示系统里的ID</span>）：消息类型的 ID = 标准页「消息类型」的 id
    （内置类型是 <code>20</code> 这类数字编号、建模 / 自定义类型是 <code>1LbVgIrt</code> 这类 8 位编号）；
    消息来源的 ID = 来源编号 typecode（推送就是按它匹配的），便于与标准页 / 数据库里的编号对照；
    弹框（设置 / 明细 / 测试）里的类型名同样按这个格式显示。
    <span class="rule">该行没绑任何来源时仍提示“标准页里还没给这个类型挂消息来源”</span>。</p>
    <p><b>状态</b>列：一行一个开关——“启用”这类消息就发到<%=oaonesPBFull%>，
    “停用”就不发（同一个标准消息类型下的多个来源一次配好，
    不需要像以前那样逐个来源编号配一遍）；改状态两种方式：行内“启用 / 停用”按钮，
    或勾选多行后用工具栏的<b>批量启用 / 批量停用</b>一次改
    （还没单独配置过的类型按默认口径建一行再改）。</p>
    <p><b>推送目标应用</b>列：这类消息发到“哪个租户的哪个应用”——<b>按租户逐个设置</b>：
    列里显示已设置目标应用的租户数量，<span class="rule">点这个数量</span>在弹出框里按租户列表
    逐行绑定应用（<span class="rule">同一个消息类型每个租户只能设一个应用</span>；
    不设的租户按默认口径推，即该租户在「<%=oaonesPBShort%>租户设置 → 应用绑定」里维护的启用应用）；
    行内“设置”弹框里的目标应用也是这张明细表，保存时与形态 / 模板一起提交。</p>
    <p><b>已禁用提示</b>：泛微标准页把某个消息类型停用时，
    本页「消息类型」列会在名称后追加“(已禁用)”，
    <span class="rule">并且本页状态还是“启用”的行整行标灰</span>——提示这类消息在标准侧已经停用，
    本页的推送设置对端到端效果已不生效。</p>
    <p><b>列表已去掉「标准侧状态」列</b>：本模块替代泛微内置推送，推不推只看本页的“状态”列；
    标准页那边停了哪个类型，靠上面「消息类型」列名称后的“(已禁用)”与整行标灰来体现
    （不必再来一列重复展示标准页的启用 / 停用）。</p>
    <p><b>筛选</b>：模块只在筛选框里选——点开“模块”选择框可<span class="rule">多选</span>
    （标准页各模块 + “未模块”，带“全选”；不选 = 全部模块）；旁边的“状态”按<%=oaonesPBShort%>侧
    启用 / 停用筛（两个列表都生效）；“消息来源”框按类型名 / 来源名 / 所属模块筛。
    列表本身按消息类型平铺展示、不再按模块分段。</p>
    <p><b>分页与勾选</b>：消息类型列表按 10 / 20 / 50 / 100 / 1000 条分页（默认 10 条）；
    行首复选框用于批量启停——<span class="rule">表头复选框只全选当前页</span>，
    要一次选中当前筛选条件下的全部（含未翻到的页）请勾选表头上方的“跨页全选”，
    列表上方的“仅显示已选择数据”可只看已勾选的行核对（<span class="rule">本页不显示消息个数统计</span>）；
    下方的“未被标准消息类型包含的消息来源”
    列表数据量小、整表展示，它的表头复选框一条就是该列表全部。</p>
    <p><b>标准侧还没归类的来源</b>：泛微标准页里有消息来源没挂到任何消息类型上时，
    它们会<b>一并列在下面那块列表里</b>（“说明”列写明“本页还没单独配置”，上方另有一行提示给出个数）——
    这类来源不在上面的消息类型里，推送按来源编号单独匹配、本页没配过时按默认口径推送；
    在这里改任何一项（启用 / 停用 / 设置 / 测试 / 目标应用）都会按默认口径建一行，
    <span class="rule">建行后就和“已配过的来源”完全一样</span>（说明列不再写那句、并出现“删除”按钮）；
    想跟着标准类型一起管，请到泛微标准页把它们挂到某个消息类型上。</p>
    <p><b>设置</b>：按类型细化 <%=oaonesPBShort%>消息形态、标题 / 正文模板、跳转地址与卡片动作，
    留空即用平台默认（<%=oaonesPBMsgDefaultForm%> + 不处理卡片）。模板支持占位符
    <code>&#36;{title}</code> <code>&#36;{context}</code> <code>&#36;{targetName}</code> <code>&#36;{targetId}</code>
    <code>&#36;{date}</code> <code>&#36;{time}</code> <code>&#36;{creater}</code> <code>&#36;{receiver}</code>
    <code>&#36;{bizId}</code> <code>&#36;{linkUrl}</code> <code>&#36;{params.xxx}</code>；
    未配置的跳转地址自动取标准消息自带链接（<code>&#36;{linkUrl}</code>，流程即流程详情页）并按 OA 访问地址补全。
    行内“测试”可向指定接收人真实推一条消息，用于上线前验证样式。</p>
</div>

<div class="toolbar">
    <label style="font-size:15px;font-weight:600;">消息类型</label>
    <span class="help-tip" data-help="msgTypeHelpText" role="button" tabindex="0"
          title="查看消息类型说明" aria-label="查看消息类型说明">?</span>
    <div class="filter-bar" style="margin:0 0 0 12px;">
        <span class="filter-label">模块</span>
        <%-- 模块用弹出选择框（不用下拉框，可多选）：候选是标准页「模块设置」里的模块，另加“未模块” --%>
        <%-- 宽度按现场反馈收窄到默认（250px）的一半：只做筛选，不需要那么长
             （宽度与输入框 min-width 都在 css/style.css 的 .filter-picker.narrow 里，
              只写行内宽度没用——全局 input{min-width:140px} 会把框撑出去盖住“状态”标签） --%>
        <div class="filter-picker narrow" id="msgTypeGroupPicker"
             title="点击选择模块（可多选，可选“未模块”）">
            <input type="text" id="msgTypeGroupInput" class="filter-input" readonly
                   placeholder="全部模块" autocomplete="off" data-lpignore="true">
            <span class="filter-arrow">▾</span>
            <button type="button" class="filter-clear" id="msgTypeGroupClear" title="清除模块筛选">×</button>
        </div>
        <input type="hidden" id="msgTypeGroupFilter" value="">
        <span class="filter-label">状态</span>
        <select id="msgTypeStatusFilter" style="min-width:110px;">
            <option value="">全部</option>
            <option value="0">启用</option>
            <option value="1">停用</option>
        </select>
        <span class="filter-label">消息来源</span>
        <input type="text" id="msgTypeKeyword" autocomplete="off" data-lpignore="true"
               placeholder="消息类型 / 消息来源 / 所属模块">
    </div>
    <%-- 只用于现场环境异常时的红字提示（标准表读不到等）；不再显示任何个数统计 --%>
    <span class="notice" id="msgTypeStdTip" style="margin-left:12px;"></span>
    <span style="flex:1;"></span>
    <button class="btn secondary" id="msgTypeBatchEnableBtn" disabled
            title="把勾选的消息类型设为“启用”（未单独配置过的按默认口径建行后再启用）">批量启用</button>
    <button class="btn secondary" id="msgTypeBatchDisableBtn" disabled
            title="把勾选的消息类型设为“停用”（未单独配置过的按默认口径建行后再停用）">批量停用</button>
    <button class="btn secondary" id="msgTypeReloadBtn">刷新</button>
</div>

<%-- 操作行：只放“仅显示已选择数据”切换（个数统计已按现场反馈去掉；跨页全选在表头上方那一条） --%>
<div class="bind-stats" id="msgTypeStats"></div>

<div class="table-wrap">
    <table class="grid" id="msgTypeTable">
        <thead>
        <tr>
            <th style="width:36px;"><input type="checkbox" id="msgTypeCheckAll"
                                           title="全选/取消全选当前列表的消息类型（勾选后可批量启用 / 停用）"></th>
            <th style="width:190px;">消息类型</th>
            <th style="width:30%;">消息来源</th>
            <th style="width:100px;">所属模块</th>
            <th style="width:80px;">状态</th>
            <th style="width:130px;">推送目标应用</th>
            <th style="width:100px;">消息形态</th>
            <th style="width:220px;">操作</th>
        </tr>
        </thead>
        <tbody id="msgTypeBody"></tbody>
    </table>
</div>
<div class="pager" id="msgTypePager"></div>

<%-- 标准页里“还没挂到任何消息类型上”的来源提示（数据来自接口 summary.uncoveredSources，最多 20 条） --%>
<div class="notice" id="msgTypeUncoveredTip" style="margin:10px 0 0 2px; display:none;"></div>
<div class="notice" id="msgTypeOrphanTip" style="margin:10px 0 0 2px;"></div>
<div class="table-wrap">
    <table class="grid" id="msgTypeOrphanTable">
        <thead>
        <tr>
            <th style="width:36px;"><input type="checkbox" id="msgTypeOrphanCheckAll"
                                           title="全选/取消全选未归类的消息来源（勾选后可批量启用 / 停用）"></th>
            <th style="width:220px;">未被标准消息类型包含的消息来源</th>
            <th>说明</th>
            <th style="width:80px;">状态</th>
            <th style="width:130px;">推送目标应用</th>
            <th style="width:100px;">消息形态</th>
            <th style="width:210px;">操作</th>
        </tr>
        </thead>
        <tbody id="msgTypeOrphanBody"></tbody>
    </table>
</div>

<%-- 模块筛选：弹出选择框（模块本身没有上下级关系，只提供列表式，可多选，见 skill.md 第一节第 5 条） --%>
<div class="modal-mask" id="msgTypeGroupMask"></div>
<div class="modal wx-pick-modal" id="msgTypeGroupModal">
    <div class="modal-head">
        <span id="msgTypeGroupTitle">选择模块</span>
        <button type="button" class="link" id="msgTypeGroupCloseBtn" title="关闭">×</button>
    </div>
    <div class="modal-body">
        <div class="pick-view-bar">
            <input type="text" id="msgTypeGroupSearch" placeholder="输入模块名称搜索" autocomplete="off"
                   data-lpignore="true">
            <button type="button" class="btn small secondary pick-all-btn" id="msgTypeGroupAllBtn"
                    data-pick-all="msgTypeGroup">全选</button>
        </div>
        <div class="wx-pick-list" id="msgTypeGroupList"></div>
        <div class="muted wx-pick-note">模块取自泛微标准页「模块设置」，只用于筛选本页列表；
            <span class="rule">可多选</span>（单击选中 / 再点取消，搜索框只影响候选范围，“全选”只作用于当前候选），
            <span class="rule">双击即确定并退出</span>；
            不选（或点“清空选择”）= 全部模块。</div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="msgTypeGroupClearBtn">清空选择</button>
        <button type="button" class="btn secondary" id="msgTypeGroupCancelBtn">取消</button>
        <button type="button" class="btn" id="msgTypeGroupOkBtn">确定</button>
    </div>
</div>

<%-- 推送目标应用明细弹框：点列表里“N 个租户”打开；租户清单随列表接口一次预加载（打开弹框不再取数） --%>
<div class="modal-mask" id="msgAppModalMask"></div>
<div class="modal" id="msgAppModal" style="width:760px;display:none;">
    <div class="modal-head">
        <span id="msgAppModalTitle">推送目标应用</span>
        <button type="button" class="modal-close" id="msgAppModalClose">×</button>
    </div>
    <div class="modal-body">
        <p class="notice" id="msgAppModalTip" style="margin:0 0 10px 0;"></p>
        <div class="filter-bar" style="margin:0 0 8px 0;">
            <span class="filter-label">租户</span>
            <input type="text" id="msgAppModalSearch" autocomplete="off" data-lpignore="true"
                   placeholder="输入租户名称 / 企业标识搜索">
            <span class="muted" id="msgAppModalCount"></span>
        </div>
        <div class="app-detail-wrap" id="msgAppModalDetail"></div>
    </div>
    <div class="modal-foot">
        <button class="btn secondary" id="msgAppModalCancelBtn">取消</button>
        <button class="btn" id="msgAppModalSaveBtn">保存</button>
    </div>
</div>

<div class="modal-mask" id="msgTypeModalMask"></div>
<div class="modal" id="msgTypeModal" style="width:780px;display:none;">
    <div class="modal-head">
        <span id="msgTypeModalTitle">消息类型推送设置</span>
        <button type="button" class="modal-close" id="msgTypeModalClose">×</button>
    </div>
    <div class="modal-body">
        <input type="hidden" id="msgTypeId" name="msgTypeId">
        <input type="hidden" id="msgTypeStdConfigId" name="msgTypeStdConfigId">
        <input type="hidden" id="msgTypeStdType" name="msgTypeStdType">
        <div class="form-row">
            <label>消息类型</label>
            <div class="field">
                <div id="msgTypeStdInfo" class="notice" style="margin:0;"></div>
            </div>
        </div>
        <div class="form-row">
            <label>状态</label>
            <div class="field" style="max-width:200px;">
                <select id="msgTypeYx" name="msgTypeYx">
                    <option value="0">启用</option>
                    <option value="1">停用</option>
                </select>
            </div>
        </div>
        <%-- 推送目标应用：按租户逐行设置的明细表（同一个消息类型每个租户只能设一个应用） --%>
        <div class="form-row">
            <label>推送目标应用</label>
            <div class="field" style="max-width:none;">
                <div class="muted app-detail-tip">按租户逐行设置：<span class="rule">同一个消息类型每个租户只能设一个应用</span>；
                    “默认”＝按该租户在「<%=oaonesPBShort%>租户设置 → 应用绑定」里维护的启用应用推送。</div>
                <div class="muted" id="msgTypeAppDetailTip" style="margin:0 0 6px 0;"></div>
                <div class="app-detail-wrap" id="msgTypeAppDetail"></div>
            </div>
        </div>
        <div class="form-row">
            <label><%= oaonesPBShort %>消息形态</label>
            <div class="field" style="max-width:260px;">
                <select id="msgTypeWxMsgtype" name="msgTypeWxMsgtype"></select>
            </div>
            <label style="width:110px;text-align:right;padding-right:12px;">卡片动作</label>
            <div class="field" style="max-width:260px;">
                <select id="msgTypeCardAction" name="msgTypeCardAction"></select>
            </div>
        </div>
        <div class="form-row">
            <label>标题模板</label>
            <div class="field"><input type="text" id="msgTypeTitleTpl" name="msgTypeTitleTpl" autocomplete="off"></div>
        </div>
        <div class="form-row">
            <label>正文模板</label>
            <div class="field"><textarea id="msgTypeContextTpl" name="msgTypeContextTpl" rows="4"></textarea></div>
        </div>
        <div class="form-row">
            <label>跳转地址模板</label>
            <div class="field"><input type="text" id="msgTypeUrlTpl" name="msgTypeUrlTpl" autocomplete="off"
                                      placeholder="留空取标准消息自带链接，可写 &#36;{params.url} 等"></div>
        </div>
        <div class="form-row">
            <label>移动端跳转</label>
            <div class="field"><input type="text" id="msgTypeMobileUrlTpl" name="msgTypeMobileUrlTpl"
                                      autocomplete="off" placeholder="留空复用跳转地址"></div>
        </div>
        <div class="form-row">
            <label>封面图</label>
            <div class="field"><input type="text" id="msgTypeCoverUrl" name="msgTypeCoverUrl" autocomplete="off"
                                      placeholder="<%= oaonesPBMsgJsonPlaceholder %>，可为空"></div>
        </div>
        <div class="form-row">
            <label>卡片按钮</label>
            <div class="field">
                <textarea id="msgTypeBtnJson" name="msgTypeBtnJson" rows="3"
                          placeholder='[{"key":"agree","name":"同意","replace_name":"已同意","color":"blue","is_bold":true}]'></textarea>
                <span class="notice">JSON 数组；任务卡片用 key/name/replace_name/color/is_bold，模板卡片取 name 作为按钮文字</span>
            </div>
        </div>
        <div class="form-row">
            <label>排序 / 备注</label>
            <div class="field" style="max-width:200px;">
                <input type="text" id="msgTypeSortNo" name="msgTypeSortNo" autocomplete="off" placeholder="排序号">
            </div>
            <div class="field"><input type="text" id="msgTypeRemark" name="msgTypeRemark" autocomplete="off" placeholder="备注"></div>
        </div>
    </div>
    <div class="modal-foot">
        <button class="btn secondary" id="msgTypeCancelBtn">取消</button>
        <button class="btn" id="msgTypeSaveBtn">保存</button>
    </div>
</div>

<div class="modal-mask" id="msgTypeTestMask"></div>
<div class="modal" id="msgTypeTestModal" style="width:560px;display:none;">
    <div class="modal-head">
        <span>测试推送</span>
        <button type="button" class="modal-close" id="msgTypeTestClose">×</button>
    </div>
    <div class="modal-body">
        <input type="hidden" id="msgTestTypeId" name="msgTestTypeId">
        <input type="hidden" id="msgTestStdConfigId" name="msgTestStdConfigId">
        <input type="hidden" id="msgTestStdType" name="msgTestStdType">
        <p class="notice" id="msgTestTypeName"></p>
        <div class="form-row">
            <label>接收人</label>
            <div class="field">
                <input type="text" id="msgTestAccounts" name="msgTestAccounts" autocomplete="off"
                       placeholder="OA 登录名或工号，多个用逗号分隔">
            </div>
        </div>
        <div class="form-row">
            <label><%= oaonesPBShort %>账号</label>
            <div class="field">
                <input type="text" id="msgTestWxUserids" name="msgTestWxUserids" autocomplete="off"
                       placeholder="<%= oaonesPBShort %> userid（可选，未绑定的 OA 账号也可直接填）">
            </div>
        </div>
        <div class="form-row">
            <label>业务单号</label>
            <div class="field">
                <input type="text" id="msgTestBizId" name="msgTestBizId" autocomplete="off"
                       placeholder="可空；填写后用于卡片定位与去重">
            </div>
        </div>
        <div class="form-row">
            <label>标题变量</label>
            <div class="field"><input type="text" id="msgTestTitle" name="msgTestTitle" autocomplete="off"
                                      placeholder="模板里的 &#36;{title} / &#36;{targetName}"></div>
        </div>
        <div class="form-row">
            <label>正文变量</label>
            <div class="field"><input type="text" id="msgTestContext" name="msgTestContext" autocomplete="off"
                                      placeholder="模板里的 &#36;{context}"></div>
        </div>
    </div>
    <div class="modal-foot">
        <button class="btn secondary" id="msgTestCancelBtn">取消</button>
        <button class="btn" id="msgTestSendBtn">发送测试</button>
    </div>
</div>

<script>
"use strict";
(function () {
    /* 说明：本片段只声明函数、解析期不访问 DOM；事件注册放在 MsgTypePanel.load()（幂等）。 */
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
    function setValue(id, v) {
        var el = byId(id);
        if (el) { el.value = v == null ? "" : v; }
    }
    function optionHtml(list, selected, emptyLabel) {
        var html = emptyLabel ? "<option value=\"\">" + esc(emptyLabel) + "</option>" : "";
        (list || []).forEach(function (item) {
            var v = String(item.value == null ? "" : item.value);
            html += "<option value=\"" + esc(v) + "\"" + (String(selected) === v ? " selected" : "") + ">"
                + esc(item.label) + "</option>";
        });
        return html;
    }

    /** 标准消息类型（来自泛微标准页）；未纳入标准类型的独立来源在 orphans 里单独一块。 */
    var types = [];
    var orphans = [];
    var groups = [];
    var summary = {};
    var options = {msgtypes: [], cardActions: [], apps: [], stdOptions: [], tenants: []};
    var bound = false;
    var saving = false;
    /** 明细弹框（点“N 个租户”打开）当前编辑的消息类型定位；null = 弹框未打开。 */
    var appModalCtx = null;
    /** 行内“设置”弹框里那张明细表要保存到哪个消息类型（openModal 时确定）。 */
    var saveCtx = null;
    /** “仅显示已选择数据”（只改显示范围，不动勾选集合） */
    var onlyChecked = false;
    /** 当前页可勾选的标准消息类型（表头复选框只作用于当页；跨页全选见表头上方那一条） */
    var pageSelectableKeys = [];
    /** 主列表分页器（懒创建：解析期不访问 DOM） */
    var typePager = null;

    /* ---------------- 勾选（行首复选框 + 批量启用 / 停用） ----------------
       勾选集合的键 = 「类型 + 冒号 + 行键」：标准消息类型用 std_config_id（{@code std:12}）、
       未纳入标准类型的独立来源用来源编号（{@code orphan:20}），与列表行上的 data-kind / data-key 一致。 */
    var checkedKeys = [];

    function rowKey(kind, key) { return kind + ":" + String(key); }

    function isChecked(kind, key) { return checkedKeys.indexOf(rowKey(kind, key)) >= 0; }

    function setChecked(kind, key, on) {
        var rk = rowKey(kind, key);
        var idx = checkedKeys.indexOf(rk);
        if (on && idx < 0) { checkedKeys.push(rk); }
        if (!on && idx >= 0) { checkedKeys.splice(idx, 1); }
    }

    /** 行首复选框（标准列表与未归类来源列表共用）。 */
    function rowCheckHtml(kind, key) {
        return "<td class=\"chk-col\"><input type=\"checkbox\" class=\"msg-type-chk\""
            + " data-kind=\"" + esc(kind) + "\" data-key=\"" + esc(key) + "\""
            + (isChecked(kind, key) ? " checked" : "")
            + " title=\"勾选后可批量启用 / 停用\"></td>";
    }

    /** 某个列表当前筛选条件下的可勾选行（键集合）。 */
    function selectableKeys(kind) {
        var out = [];
        var rows = kind === "std" ? visibleTypes() : visibleOrphans();
        (rows || []).forEach(function (row) {
            out.push(kind === "std" ? String(row.stdConfigId) : String(row.stdType));
        });
        return out;
    }

    /** 两个列表合并后的可勾选行（带类型前缀）：跨页全选、统计行与“已选 N / 共 M”都按它算。 */
    function allSelectableRowKeys() {
        var out = [];
        selectableKeys("std").forEach(function (key) { out.push(rowKey("std", key)); });
        selectableKeys("orphan").forEach(function (key) { out.push(rowKey("orphan", key)); });
        return out;
    }

    /** 当前筛选条件下的已选数量（两个列表合并）。 */
    function checkedCount() {
        var all = allSelectableRowKeys();
        var n = 0;
        checkedKeys.forEach(function (rk) { if (all.indexOf(rk) >= 0) { n++; } });
        return n;
    }

    /** 勾选态就地刷新：表头三态（当页）、跨页全选条、统计行与批量按钮可用性。 */
    function updateCheckedState() {
        // 表头复选框只反映当前页（三态按当页数量算）
        var stdBox = byId("msgTypeCheckAll");
        if (stdBox) {
            var pageChecked = 0;
            pageSelectableKeys.forEach(function (key) { if (isChecked("std", key)) { pageChecked++; } });
            OaonesTable.checkAllTitle(stdBox, "消息类型");
            OaonesTable.syncCheckAll(stdBox, pageChecked, pageSelectableKeys.length);
        }
        // 未归类来源列表数据量小、整表渲染、不单独分页，因此表头复选框一条就是全部
        var orphanBox = byId("msgTypeOrphanCheckAll");
        if (orphanBox) {
            var orphanKeys = selectableKeys("orphan");
            var orphanChecked = 0;
            orphanKeys.forEach(function (key) { if (isChecked("orphan", key)) { orphanChecked++; } });
            OaonesTable.syncCheckAll(orphanBox, orphanChecked, orphanKeys.length);
        }
        var total = allSelectableRowKeys().length;
        var checked = checkedCount();
        // 表头上方“跨页全选”条：选中当前筛选条件下的全部可批量操作行（含未翻到的页）
        OaonesTable.crossPageBar(byId("msgTypeTable"), {
            total: total,
            unit: "个",
            allSelected: total > 0 && checked >= total,
            checked: checked,
            what: "可批量启停的消息类型 / 未归类来源",
            onChange: toggleAllChecks
        });
        // 统计行末尾的“仅显示已选择数据 / 显示所有数据”
        OaonesTable.refreshListActions(byId("msgTypeStats"), {
            total: total,
            unit: "个",
            what: "可批量启停的消息类型 / 未归类来源",
            onlyChecked: onlyChecked,
            checked: checked
        });
        var enable = byId("msgTypeBatchEnableBtn");
        var disable = byId("msgTypeBatchDisableBtn");
        if (enable) { enable.disabled = checked === 0; }
        if (disable) { disable.disabled = checked === 0; }
    }

    /** 清掉已不在当前列表里的勾选（关键字 / 模块筛选变化、数据重载后调用）。 */
    function pruneChecked() {
        var alive = {};
        selectableKeys("std").forEach(function (key) { alive[rowKey("std", key)] = true; });
        selectableKeys("orphan").forEach(function (key) { alive[rowKey("orphan", key)] = true; });
        checkedKeys = checkedKeys.filter(function (rk) { return !!alive[rk]; });
    }

    function wxMsgtypeText(code) {
        var found = "";
        (options.msgtypes || []).forEach(function (item) {
            if (String(item.value) === String(code)) { found = item.label; }
        });
        return found || code || "";
    }

    function cardActionText(code) {
        var found = "";
        (options.cardActions || []).forEach(function (item) {
            if (String(item.value) === String(code)) { found = item.label; }
        });
        return found || code || "不处理卡片";
    }

    /* ---------------- 「系统ID#名称」展示口径 ----------------
       列表与弹框里显示「消息类型」「消息来源」时统一按 `id#名称` 排版（**先显示系统里的ID**）：
       消息类型的 ID = 标准页「消息类型」的 id（uf_oaones_appsyn_msg_type.std_config_id，如 20 / 1LbVgIrt）；
       消息来源的 ID = 来源编号 typecode（ecology_message_type.typecode，推送就是按它匹配的）。 */

    /** 单个「系统ID#名称」（ID 取不到时只显示名称）。 */
    function idNameHtml(id, name) {
        var key = String(id == null ? "" : id);
        var text = String(name == null ? "" : name);
        if (key.length === 0 || key === "0") { return esc(text); }
        return esc(key) + "#" + esc(text);
    }

    /** 某个类型的“消息来源”列：多个来源按「系统ID#名称」顿号拼接（老接口没有 sources 时退回名称文本）。 */
    function sourceIdNameHtml(row) {
        var list = row.sources || [];
        var parts = [];
        list.forEach(function (one) {
            if (one) { parts.push(idNameHtml(one.code, one.name)); }
        });
        if (parts.length) { return parts.join("、"); }
        return row.sourceNames ? esc(row.sourceNames) : "";
    }

    /** 本模块的推送状态（启用 / 停用）——只展示，改状态用“启用 / 停用”按钮。 */
    function statusHtml(row) {
        return Number(row.yx == null ? 0 : row.yx) === 1
            ? "<span class=\"tag off\">停用</span>"
            : "<span class=\"tag bound\">启用</span>";
    }

    /** 操作列的“启用 / 停用”按钮（改完即存：调 msg.type.quick，未配置过的按默认口径建行）。 */
    function toggleBtnHtml(row, key, kind) {
        var off = Number(row.yx == null ? 0 : row.yx) === 1;
        return "<button class=\"btn small secondary\" data-act=\"" + (off ? "enable" : "disable")
            + "\" data-kind=\"" + esc(kind) + "\" data-key=\"" + esc(key) + "\""
            + " title=\"" + (off ? "启用后这类消息恢复推送" : "停用后这类消息不再推送") + "\">"
            + (off ? "启用" : "停用") + "</button>";
    }

    /* ---------------- 推送目标应用（按租户明细） ----------------
       一行 = 一个租户 + 一个应用：「推送目标应用」列显示已设置目标应用的租户数量，
       点数量打开明细弹框（租户清单随列表接口一次预加载，见 options.tenants）；
       行内“设置”弹框里也是同一张明细表，保存时一起提交。 */

    /** 本平台租户清单（接口预加载；没有租户时为空数组）。 */
    function tenants() { return options.tenants || []; }

    /** 某个租户在本平台的启用应用（options.apps 已带 cfgId；同一租户多个应用时逐个列出）。 */
    function tenantApps(cfgId) {
        return (options.apps || []).filter(function (app) {
            return String(app.yx) === "0" && String(app.cfgId == null ? 0 : app.cfgId) === String(cfgId);
        });
    }

    /** 某一行已设置目标应用的租户数量（只有一个应用也按 1 个租户算）。 */
    function bindCount(row) {
        var binds = row.appBinds || {};
        var n = 0;
        for (var key in binds) {
            if (binds.hasOwnProperty(key) && Number(binds[key]) > 0) { n++; }
        }
        return n;
    }

    /** 行内“推送目标应用”列：按“已设置N个租户”显示已设置目标应用的租户数量，点它打开明细弹框。 */
    function appCountHtml(row, key, kind) {
        var n = bindCount(row);
        return "<button type=\"button\" class=\"link msg-app-count\" data-act=\"apps\" data-kind=\"" + esc(kind)
            + "\" data-key=\"" + esc(key) + "\" title=\"已设置目标应用 " + n + " 个（每个租户一个）；"
            + "点击按租户设置推送目标应用（同一个消息类型每个租户只能设一个应用）\">"
            + "<span" + (n > 0 ? "" : " class=\"muted\"") + ">已设置" + n + "个租户</span></button>";
    }

    /**
     * 明细表：租户列表一行一条，每行一个应用下拉（候选＝该租户在本平台的启用应用）。
     *
     * <p>绑定过的应用被停用 / 删除时保留一条“应用#已停用或已删除”的选项：
     * 打开明细不会把已有绑定悄悄清掉（要清掉得显式选“默认”再保存）。</p>
     */
    function appDetailHtml(row) {
        var binds = row.appBinds || {};
        var list = tenants();
        if (!list.length) {
            return "<div class=\"empty\">这个平台还没有租户，请先在「" + esc(P_SHORT)
                + "租户设置」里新增租户，再回到本页设置目标应用</div>";
        }
        var html = "<table class=\"grid app-detail-table\"><thead><tr>"
            + "<th style=\"width:34%;\">租户</th>"
            + "<th style=\"width:26%;\">企业标识</th>"
            + "<th>目标应用</th>"
            + "</tr></thead><tbody>";
        list.forEach(function (item) {
            var cfgId = String(item.cfg_id == null ? "" : item.cfg_id);
            var corpId = String(item.corp_id == null ? "" : item.corp_id);
            var name = String(item.cfg_name == null || item.cfg_name === "" ? "" : item.cfg_name);
            var bound = binds[cfgId] == null ? "0" : String(binds[cfgId]);
            var apps = tenantApps(cfgId);
            var html2 = "<select class=\"msg-tenant-app\" data-cfg-id=\"" + esc(cfgId) + "\">"
                + "<option value=\"0\"" + ("0" === bound ? " selected" : "") + ">默认（按该租户的启用应用推送）</option>";
            var hit = false;
            apps.forEach(function (app) {
                if (String(app.id) === bound) { hit = true; }
                html2 += "<option value=\"" + esc(app.id) + "\"" + (String(app.id) === bound ? " selected" : "")
                    + ">" + esc(app.appName) + (Number(app.isDefault) === 1 ? "（缺省）" : "") + "</option>";
            });
            if (bound !== "0" && !hit) {
                html2 += "<option value=\"" + esc(bound) + "\" selected>(应用已停用或已删除 #"
                    + esc(bound) + ")</option>";
            }
            html2 += "</select>";
            if (!apps.length && bound === "0") {
                html2 += " <span class=\"muted\">该租户还没有可用应用</span>";
            }
            html += "<tr class=\"msg-tenant-row\""
                + " data-search=\"" + esc((name + " " + corpId).toLowerCase()) + "\">"
                + "<td>" + esc(name === "" ? ("租户 " + cfgId) : name) + "</td>"
                + "<td>" + (corpId === "" ? "<span class=\"muted\">-</span>"
                    : "<span class=\"muted\">" + esc(corpId) + "</span>") + "</td>"
                + "<td>" + html2 + "</td></tr>";
        });
        return html + "</tbody></table>";
    }

    /** 明细容器里读回“租户 → 应用”（只提交租户ID > 0 的行，应用 0 = 该租户不绑定）。 */
    function readAppBinds(container) {
        var out = [];
        if (!container) { return out; }
        var selects = container.querySelectorAll("select.msg-tenant-app");
        for (var i = 0; i < selects.length; i++) {
            var cfgId = String(selects[i].getAttribute("data-cfg-id") || "");
            if (!(Number(cfgId) > 0)) { continue; }
            out.push(cfgId + ":" + String(selects[i].value == null ? "0" : selects[i].value));
        }
        return out;
    }

    /** 明细表里的行按关键字过滤（租户名 / 企业标识）；只改显示，不动选择值。 */
    function filterAppDetail(container, keyword) {
        if (!container) { return 0; }
        var k = String(keyword == null ? "" : keyword).trim().toLowerCase();
        var rows = container.querySelectorAll("tr.msg-tenant-row");
        var shown = 0;
        for (var i = 0; i < rows.length; i++) {
            var text = String(rows[i].getAttribute("data-search") || "");
            var hit = !k || text.indexOf(k) >= 0;
            rows[i].style.display = hit ? "" : "none";
            if (hit) { shown++; }
        }
        return shown;
    }

    /** 状态筛选值："" 全部 / "0" 启用 / "1" 停用。 */
    function statusFilterValue() { return value("msgTypeStatusFilter"); }

    /** 行是否命中「状态」筛选（两个列表共用；行上的 yx 缺省按启用）。 */
    function matchStatus(row) {
        var statusKey = statusFilterValue();
        if (statusKey === "") { return true; }
        return String(row.yx == null ? 0 : row.yx) === statusKey;
    }

    /** 关键字 / 模块 / 状态筛选后的标准消息类型（勾选与展示都按“所见即所选”）。 */
    function visibleTypes() {
        var keyword = value("msgTypeKeyword").toLowerCase();
        var groupKeys = selectedGroupKeys();
        return types.filter(function (row) {
            if (groupKeys.length) {
                // 模块可多选：行所属模块在选中集合里才保留（“未模块”按 __none__ 匹配 groupId = 0 的行）
                var key = Number(row.groupId || 0) > 0 ? String(row.groupId) : GROUP_NONE;
                if (groupKeys.indexOf(key) < 0) { return false; }
            }
            if (!matchStatus(row)) { return false; }
            if (!keyword) { return true; }
            var text = [row.name, row.groupName, row.moduleName, row.sourceNames, row.typeCodes]
                .join(" ").toLowerCase();
            return text.indexOf(keyword) >= 0;
        });
    }

    /** 未归类来源列表（只受「状态」筛，数据量小、整表渲染、不单独分页）。 */
    function visibleOrphans() {
        return (orphans || []).filter(matchStatus);
    }

    /** 筛选签名：任一筛选条件或“仅显示已选”变化都要回到第 1 页。 */
    function filterSignature() {
        return [value("msgTypeKeyword"), selectedGroupKeys().join(","), statusFilterValue(),
            onlyChecked ? "1" : "0"].join("|");
    }

    /** 主列表分页器（懒创建，见 js/table.js 的分页三步走）。 */
    function pagerOf() {
        if (!typePager) {
            typePager = OaonesTable.pager({
                key: "msgCenter.typeList",
                el: byId("msgTypePager"),
                onChange: renderTypes
            });
        }
        return typePager;
    }

    /**
     * 列表操作行：只渲染“仅显示已选择数据”切换按钮（现场反馈：不需要消息个数统计，故不再显示计数）。
     *
     * <p>保留这一行是为了让“仅显示已选择数据 / 显示所有数据”和批量启停配合可用。</p>
     */
    function renderStats() {
        var statsEl = byId("msgTypeStats");
        if (!statsEl) { return; }
        statsEl.innerHTML = OaonesTable.listActionsHtml({
            total: allSelectableRowKeys().length,
            unit: "个",
            what: "可批量启停的消息类型 / 未归类来源",
            onlyChecked: onlyChecked,
            checked: checkedCount()
        });
    }

    /** 标准页「模块设置」的排序号（模块 id → 顺序号），列表排序用。 */
    var groupOrderById = {};

    function refreshGroupOrder() {
        groupOrderById = {};
        (groups || []).forEach(function (group, index) {
            groupOrderById[String(group.id)] = group.showNum == null ? index : Number(group.showNum);
        });
    }

    /** 列表排序：先按模块顺序（未模块排最后），再按消息类型名称。 */
    function compareTypes(a, b) {
        var ga = Number(a.groupId || 0) > 0 ? groupOrderById[String(a.groupId)] : null;
        var gb = Number(b.groupId || 0) > 0 ? groupOrderById[String(b.groupId)] : null;
        var oa = ga == null ? 99999 : Number(ga);
        var ob = gb == null ? 99999 : Number(gb);
        if (oa !== ob) { return oa - ob; }
        return String(a.name || "").localeCompare(String(b.name || ""), "zh-Hans-CN");
    }

    function renderTypes() {
        var body = byId("msgTypeBody");
        if (!body) { return; }
        var list = visibleTypes();
        // 平铺排序：先按标准页「模块设置」的排序号，同组内按消息类型名称（列表里不出现模块标题行）
        refreshGroupOrder();
        list.sort(compareTypes);
        // 勾选集合只保留当前候选里的行（筛选变化后不会出现“看不见的已选”）
        pruneChecked();
        // “仅显示已选择数据”：只把显示范围收窄到已勾选的行（含其它页已选），勾选集合不变
        var displayRows = onlyChecked
            ? list.filter(function (row) { return isChecked("std", String(row.stdConfigId)); })
            : list;
        // 分页三步走：筛选签名变了回第 1 页 → 切出当页 → 渲染分页条
        var pg = pagerOf();
        pg.resetIfChanged(filterSignature());
        var pageRows = pg.apply(displayRows);
        pageSelectableKeys = [];
        pageRows.forEach(function (row) { pageSelectableKeys.push(String(row.stdConfigId)); });
        renderStats();
        if (!pageRows.length) {
            body.innerHTML = "<tr><td colspan=\"8\"><div class=\"empty\">"
                + (!types.length ? "泛微标准页里还没有配置消息类型（后台 应用中心 → 消息类型）"
                    : (onlyChecked ? "当前没有勾选的消息类型（点列表上方“显示所有数据”可查看全部）"
                        : "没有匹配的消息类型"))
                + "</div></td></tr>";
            pg.render();
            updateCheckedState();
            return;
        }
        var html = "";
        pageRows.forEach(function (row) {
            var key2 = String(row.stdConfigId);
            // 泛微标准页停用：名称后追加“(已禁用)”；本页还是“启用”的行整行标灰（提示端到端已不生效）
            var stdOff = row.stdEnable !== "y";
            var rowOff = stdOff && Number(row.yx == null ? 0 : row.yx) === 0;
            html += "<tr" + (rowOff ? " class=\"row-off\"" : "") + ">"
                + rowCheckHtml("std", key2)
                // “消息类型”列按「系统ID#名称」显示（先显示系统里的ID）
                + "<td>" + idNameHtml(key2, row.name)
                    + (stdOff ? " <span class=\"notice\">(已禁用)</span>" : "") + "</td>"
                // “消息来源”列同样按「系统ID#名称」显示（来源ID = 来源编号 typecode）
                + "<td>" + (sourceIdNameHtml(row).length ? sourceIdNameHtml(row)
                    : "<span class=\"notice\">标准页里还没给这个类型挂消息来源</span>")
                + "</td>"
                + "<td>" + esc(row.moduleName || "-") + "</td>"
                + "<td>" + statusHtml(row) + "</td>"
                + "<td>" + appCountHtml(row, key2, "std") + "</td>"
                + "<td>" + esc(wxMsgtypeText(row.wxMsgtype)) + "</td>"
                + "<td>"
                + "<button class=\"btn small secondary\" data-act=\"edit\" data-kind=\"std\" data-key=\""
                + esc(key2) + "\">设置</button> "
                + toggleBtnHtml(row, key2, "std") + " "
                + "<button class=\"btn small secondary\" data-act=\"test\" data-kind=\"std\" data-key=\""
                + esc(key2) + "\">测试</button>"
                + (row.configured
                    ? " <button class=\"btn small ghost-danger\" data-act=\"reset\" data-kind=\"std\" data-key=\""
                        + esc(key2) + "\">恢复默认</button>"
                    : "")
                + "</td></tr>";
        });
        body.innerHTML = html;
        pg.render();
        updateCheckedState();
    }

    function renderOrphans() {
        var body = byId("msgTypeOrphanBody");
        var tip = byId("msgTypeOrphanTip");
        if (!body) { return; }
        if (tip) {
            var text = "以下是<b>没有归到任何标准消息类型</b>的消息来源（平台自建来源、建模“自定义提醒”等）："
                + "它们仍按来源编号单独推送，可以在这里决定启用 / 停用、推到哪个应用；"
                + "标准页里有、本页还没单独配过的来源也列在这里（“说明”列会写明还没配过、按默认口径推送，"
                + "在这里改一项即按默认口径建行）；"
                + "想让它们跟着标准类型一起管，请在泛微标准页把来源挂到某个消息类型上。";
            // 现场反馈：不再显示“还有 N 个来源没归类”这类个数统计（下面这块列表本身就是清单）
            tip.innerHTML = text;
        }
        var list = visibleOrphans();
        var rows = onlyChecked
            ? list.filter(function (row) { return isChecked("orphan", String(row.stdType)); })
            : list;
        if (!rows.length) {
            var emptyText = !orphans.length ? "没有未归类的消息来源"
                : (onlyChecked && list.length ? "当前没有勾选的未归类来源（点列表上方“显示所有数据”可查看全部）"
                    : "没有匹配的未归类来源");
            body.innerHTML = "<tr><td colspan=\"7\"><div class=\"empty\">" + emptyText + "</div></td></tr>";
            updateCheckedState();
            return;
        }
        var html = "";
        rows.forEach(function (row) {
            var key = String(row.stdType);
            html += "<tr>"
                + rowCheckHtml("orphan", key)
                // “消息来源”列按「系统ID#名称」显示（独立来源的ID = 来源编号 typecode）
                + "<td>" + idNameHtml(key, row.name) + "</td>"
                + "<td>" + (row.covered
                    ? "<span class=\"notice\">已被标准消息类型「" + esc(row.coveredBy)
                        + "」包含，本行不再生效（可删除）</span>"
                    : (row.configured === false
                        ? "<span class=\"notice\">标准页里还没挂到任何消息类型上，本页还没单独配置："
                            + "按来源编号单独推送（形态取平台默认、应用按接收人租户匹配）；"
                            + "<span class=\"rule\">在这里改一项即按默认口径建一行</span></span>"
                        : "<span class=\"notice\">未纳入标准消息类型，按来源编号单独推送</span>")) + "</td>"
                + "<td>" + statusHtml(row) + "</td>"
                + "<td>" + appCountHtml(row, key, "orphan") + "</td>"
                + "<td>" + esc(wxMsgtypeText(row.wxMsgtype)) + "</td>"
                + "<td>"
                + "<button class=\"btn small secondary\" data-act=\"edit\" data-kind=\"orphan\" data-key=\""
                + esc(key) + "\">设置</button> "
                + toggleBtnHtml(row, key, "orphan") + " "
                + "<button class=\"btn small secondary\" data-act=\"test\" data-kind=\"orphan\" data-key=\""
                + esc(key) + "\">测试</button>"
                // 还没建行的来源（接口 configured=false）没有可删的设置，不给“删除”
                + (row.configured === false ? ""
                    : " <button class=\"btn small ghost-danger\" data-act=\"del\" data-kind=\"orphan\" data-key=\""
                        + esc(key) + "\">删除</button>")
                + "</td></tr>";
        });
        body.innerHTML = html;
        updateCheckedState();
    }

    function renderSummary() {
        // 现场反馈：本页不再显示消息类型 / 模块 / 来源的个数统计，
        // 这里只把“标准表读不到”这类环境原因显示出来，避免“页面空了不知道为什么”。
        var stdTip = byId("msgTypeStdTip");
        var warnings = summary.warnings || [];
        if (stdTip) {
            stdTip.textContent = warnings.length ? warnings.join("；") : "";
        }
        renderUncoveredTip();
    }

    /**
     * 泛微标准页里“还没挂到任何消息类型上”的来源提示。
     *
     * <p>这类来源在标准来源表（ecology_message_type）里有、但没被任何标准消息类型的 messagetypeid 引用；
     * 后端的 {@code standardView} 已经把它们的行一并放进 {@code orphans}
     * （{@code configured=false} / {@code rowId=0}），所以在下面那块列表里能直接启用 / 停用 / 设置 / 测试，
     * 这里只再给一行提示说明“还有几个没归类、去哪儿归类”，不再重复铺一遍编号与名称。</p>
     *
     * <p>数据来自接口 summary.uncoveredCount（总个数）。</p>
     */
    function renderUncoveredTip() {
        var el = byId("msgTypeUncoveredTip");
        if (!el) { return; }
        var count = Number(summary.uncoveredCount == null ? 0 : summary.uncoveredCount);
        if (!(count > 0)) {
            el.innerHTML = "";
            el.style.display = "none";
            return;
        }
        var text = "泛微标准页还有 <b>" + count + "</b> 个消息来源没挂到任何消息类型上"
            // 这些来源已经一并列在下面那块列表里（“说明”列写明“本页还没单独配置”），
            // 所以这里只给个数与去向，不再重复把编号与名称铺一遍
            + "（下面「未被标准消息类型包含的消息来源」里“本页还没单独配置”的那几行就是它们，"
            + "在那里可以直接启用 / 停用 / 设置 / 测试）。"
            + "它们按来源编号单独推送，想跟着标准类型一起管，"
            + "请到泛微标准页「后台 应用中心 → 消息类型」把它们挂到某个消息类型上。";
        el.innerHTML = text;
        el.title = "标准页里把来源挂到某个消息类型上后，本页刷新这条提示即消失；"
            + "“来源编号#名称”里的编号就是推送匹配用的 typecode。";
        el.style.display = "";
    }

    function render() {
        renderTypes();
        renderOrphans();
        renderSummary();
        // 表头列宽拖拽：消息来源列初始占 30%，拖动后按列表 key 记住列宽（弹框打开/页签切回都保持）
        if (window.OaonesTable) {
            OaonesTable.resizable(byId("msgTypeTable"), {key: "msgCenter.type"});
            OaonesTable.resizable(byId("msgTypeOrphanTable"), {key: "msgCenter.typeOrphan"});
        }
    }

    /* ---------------- 模块筛选（弹出选择框，不用下拉框） ----------------
       候选 = 全部模块 + 标准页「模块设置」里的模块 + 未模块；模块之间没有上下级关系，
       因此只提供列表式（不开树状，见 skill.md 第一节第 5 条）；单选，双击即确定并退出。 */
    var GROUP_NONE = "__none__";
    /** 弹框里的“待选模块”（可多选）：单击只改它，点“确定”或双击才落到真正的筛选值上（取消 / 关闭即丢弃） */
    var pendingGroups = [];

    /** 已生效的模块筛选（空数组 = 全部模块）；值存 `#msgTypeGroupFilter`，逗号分隔（`__none__` = 未模块）。 */
    function selectedGroupKeys() {
        var raw = value("msgTypeGroupFilter");
        if (!raw) { return []; }
        return raw.split(",").map(function (one) { return one.trim(); })
            .filter(function (one) { return one !== ""; });
    }

    /** 模块候选：标准页「模块设置」里的模块 + “未模块”（带该模块下的消息类型数）。 */
    function groupOptions() {
        var list = [];
        (groups || []).forEach(function (group) {
            var id = String(group.id);
            var count = 0;
            types.forEach(function (row) { if (String(row.groupId || 0) === id) { count++; } });
            list.push({value: id, name: group.name || ("模块 " + id), count: count});
        });
        var noneCount = 0;
        types.forEach(function (row) { if (Number(row.groupId || 0) <= 0) { noneCount++; } });
        list.push({value: GROUP_NONE, name: "未模块", count: noneCount});
        return list;
    }

    /** 模块键 → 名称（生成框文案与弹框标题共用）。 */
    function groupNames(keys) {
        var options1 = groupOptions();
        var names = [];
        (keys || []).forEach(function (key) {
            options1.forEach(function (one) { if (one.value === String(key)) { names.push(one.name); } });
        });
        return names.join("、");
    }

    /** 生成框文案与“清除”按钮显隐（框只读，点它打开选择框）。 */
    function syncGroupInput() {
        var keys = selectedGroupKeys();
        setValue("msgTypeGroupInput", keys.length ? groupNames(keys) : "全部模块");
        var picker = byId("msgTypeGroupPicker");
        if (picker) {
            picker.classList.toggle("has-value", keys.length > 0);
            picker.title = keys.length
                ? "已选模块：" + groupNames(keys) + "（可多选，点开可改）"
                : "点击选择模块（可多选，可选“未模块”）";
        }
    }

    function groupRows(keyword) {
        var k = String(keyword == null ? value("msgTypeGroupSearch") : keyword).trim().toLowerCase();
        return groupOptions().filter(function (one) {
            return !k || one.name.toLowerCase().indexOf(k) >= 0;
        });
    }

    function groupOptionHtml(one) {
        var active = pendingGroups.indexOf(one.value) >= 0;
        return "<button type='button' class='wx-pick-option pick-cols-wide" + (active ? " active" : "")
            + "' data-value='" + esc(one.value) + "'"
            + " title='" + esc((active ? "已选中（再点即取消）" : "点击选中该模块")
                + "｜双击即确定并退出") + "'>"
            + "<span class='wx-pick-opt-name'>" + esc(one.name) + "</span>"
            + "<span>" + one.count + " 个消息类型</span>"
            + "</button>";
    }

    /** 当前候选（受搜索框过滤）里是否已全选；“全选”按钮文案与高亮由 OaonesPicker 同步。 */
    function groupAllOn() {
        var rows = groupRows(null);
        if (!rows.length) { return false; }
        return rows.every(function (one) { return pendingGroups.indexOf(one.value) >= 0; });
    }

    function renderGroupList() {
        var listEl = byId("msgTypeGroupList");
        if (!listEl) { return; }
        syncGroupPickState();
        var rows = groupRows(null);
        if (!rows.length) {
            listEl.innerHTML = "<div class='wx-pick-empty'>没有匹配的模块</div>";
            return;
        }
        var html = "";
        rows.forEach(function (one) { html += groupOptionHtml(one); });
        listEl.innerHTML = html;
    }

    function openGroupPick() {
        pendingGroups = selectedGroupKeys();
        setValue("msgTypeGroupSearch", "");
        renderGroupList();
        byId("msgTypeGroupMask").style.display = "block";
        byId("msgTypeGroupModal").style.display = "block";
        byId("msgTypeGroupSearch").focus();
    }

    function hideGroupPick() {
        byId("msgTypeGroupMask").style.display = "none";
        byId("msgTypeGroupModal").style.display = "none";
    }

    /** 弹框标题（已选 N 个模块）与“全选 / 全不选”按钮文案（勾选变化或列表重绘后调用）。 */
    function syncGroupPickState() {
        var title = byId("msgTypeGroupTitle");
        if (title) {
            title.textContent = pendingGroups.length
                ? "选择模块（已选 " + pendingGroups.length + " 个）" : "选择模块";
        }
        OaonesPicker.syncSelectAll("msgTypeGroup", groupAllOn());
    }

    /** 勾选变化后就地刷新每行的选中态（<b>不重建列表</b>，否则会打断双击）。 */
    function refreshGroupOptionDom() {
        var listEl = byId("msgTypeGroupList");
        if (!listEl) { return; }
        var opts = listEl.querySelectorAll(".wx-pick-option");
        for (var i = 0; i < opts.length; i++) {
            var one = String(opts[i].getAttribute("data-value") || "");
            opts[i].classList.toggle("active", pendingGroups.indexOf(one) >= 0);
        }
        syncGroupPickState();
    }

    /** 单击切换该模块的选中态（可多选）。 */
    function toggleGroup(groupKey) {
        var one = String(groupKey == null ? "" : groupKey);
        if (one === "") { return; }
        var idx = pendingGroups.indexOf(one);
        if (idx >= 0) { pendingGroups.splice(idx, 1); } else { pendingGroups.push(one); }
        refreshGroupOptionDom();
    }

    /** “全选”按钮：按当前搜索关键字过滤后的候选全选 / 全不选，返回操作后是否已全选。 */
    function toggleGroupAll() {
        var rows = groupRows(null);
        var on = groupAllOn();
        rows.forEach(function (one) {
            var idx = pendingGroups.indexOf(one.value);
            if (on && idx >= 0) { pendingGroups.splice(idx, 1); }
            if (!on && idx < 0) { pendingGroups.push(one.value); }
        });
        refreshGroupOptionDom();
        return groupAllOn();
    }

    /** 模块 / 状态 / 关键字筛选变化：修剪勾选（不在当前候选里的清掉）后重绘两个列表。 */
    function applyFilter() {
        pruneChecked();
        render();
    }

    function confirmGroup() {
        var next = pendingGroups.join(",");
        var changed = next !== value("msgTypeGroupFilter");
        setValue("msgTypeGroupFilter", next);
        hideGroupPick();
        syncGroupInput();
        if (changed) { applyFilter(); }
    }

    /** 弹框里的“清空选择”：表示不限模块（不选任何模块）。 */
    function clearGroupPick() {
        pendingGroups = [];
        confirmGroup();
    }

    /** 生成框右侧的 ×：直接清掉已生效的模块筛选，不用打开弹框。 */
    function resetGroupFilter() {
        pendingGroups = [];
        setValue("msgTypeGroupFilter", "");
        syncGroupInput();
        applyFilter();
    }

    /** 表头复选框：只全选 / 取消全选<b>当页</b>的行（要选全部含未翻到的页用表头上方的“跨页全选”）。 */
    function togglePageChecks(on) {
        pageSelectableKeys.forEach(function (key) { setChecked("std", key, on); });
        renderTypes();
    }

    /** 未归类来源列表的表头复选框：它整表渲染、不分页，所以一条即该列表全部行。 */
    function toggleOrphanChecks(on) {
        selectableKeys("orphan").forEach(function (key) { setChecked("orphan", key, on); });
        renderOrphans();
    }

    /** 跨页全选条：选中 / 取消选中“当前筛选条件下的全部可批量操作行”（两个列表合并，含未翻到的页）。 */
    function toggleAllChecks(on) {
        allSelectableRowKeys().forEach(function (rk) {
            var sep = rk.indexOf(":");
            setChecked(rk.substring(0, sep), rk.substring(sep + 1), on);
        });
        render();
    }

    /** 统计行末尾的“仅显示已选择数据 / 显示所有数据”：只改显示范围，不动勾选集合。 */
    function toggleOnlyChecked() {
        onlyChecked = !onlyChecked;
        render();
    }

    /** 批量启用 / 停用：勾选行一次提交（未配置过的行由后端按默认口径建行后再改）。 */
    function batchSave(yx) {
        if (saving) { return; }
        if (!checkedKeys.length) { toast("请先勾选要操作的消息类型", 1); return; }
        saving = true;
        var enableBtn = byId("msgTypeBatchEnableBtn");
        var disableBtn = byId("msgTypeBatchDisableBtn");
        if (enableBtn) { enableBtn.disabled = true; }
        if (disableBtn) { disableBtn.disabled = true; }
        Oaones.api("msg.type.batch", {keys: checkedKeys.join(","), yx: yx}, function (res) {
            saving = false;
            if (res.code !== 0) {
                toast(res.msg || "批量操作失败", 2);
                updateCheckedState();
                return;
            }
            var data = res.data || {};
            var msg = (yx === 1 ? "已停用 " : "已启用 ") + (data.rows || 0) + " 个消息类型";
            if (data.failed > 0) { msg += "，失败 " + data.failed + " 个（可能已在标准页删除）"; }
            toast(msg, data.failed > 0 ? 1 : 0);
            checkedKeys = [];
            load();
        });
    }

    function findByKey(kind, key) {
        var list = kind === "orphan" ? orphans : types;
        for (var i = 0; i < list.length; i++) {
            var one = list[i];
            if (kind === "orphan") {
                if (String(one.stdType) === String(key)) { return one; }
            } else if (String(one.stdConfigId) === String(key)) {
                return one;
            }
        }
        return null;
    }

    /** 行内快速保存：只提交“状态 / 推送目标应用”（没配过的类型由后端按默认口径建行）。 */
    function quickSave(kind, key, params, selectEl, okMsg) {
        if (saving) { return; }
        // 兜底：标准消息类型ID / 来源编号都取不到时不要发请求
        // （否则后端两个编号都是空，只会回一句“缺少消息来源编号”，用户看不懂）
        var raw = String(key == null ? "" : key).replace(/^\s+|\s+$/g, "");
        if (kind === "orphan" ? !(Number(raw) > 0) : !(raw.length && raw !== "0")) {
            toast("这一行的消息类型编号取不到，请刷新页面后重试", 2);
            return;
        }
        saving = true;
        var params2 = params || {};
        if (kind === "orphan") { params2.std_type = key; } else { params2.std_config_id = key; }
        if (selectEl) { selectEl.disabled = true; }
        Oaones.api("msg.type.quick", params2, function (res) {
            saving = false;
            // 成功时下面会整表重载（行元素被重建），先还原按钮 / 下拉的可用态，避免失败分支留下禁用态
            if (selectEl) { selectEl.disabled = false; }
            if (res.code !== 0) {
                toast(res.msg || "保存失败", 2);
                load();
                return;
            }
            toast(okMsg || "已保存", 0);
            load();
        });
    }

    /** 兼容：接口没带应用清单时单独取一次（老版本接口）。 */
    function loadAppsFallback() {
        if (options.apps && options.apps.length) { return; }
        Oaones.api("msg.app.list", {}, function (res) {
            if (res.code === 0) {
                options.apps = (res.data && res.data.list) || [];
                render();
            }
        });
    }

    function load() {
        Oaones.api("msg.type.list", {}, function (res) {
            if (res.code !== 0) { toast(res.msg || "加载消息类型失败", 2); return; }
            var data = res.data || {};
            types = data.types || [];
            orphans = data.orphans || [];
            groups = data.groups || [];
            summary = data.summary || {};
            options.msgtypes = data.options || [];
            options.cardActions = data.cardActions || [];
            options.stdOptions = data.stdOptions || [];
            options.apps = data.apps || [];
            // 租户清单随列表一次预加载：明细弹框打开即用（不再单独取数）
            options.tenants = data.tenants || [];
            pruneChecked();
            syncGroupInput();
            render();
            loadAppsFallback();
        });
    }

    function openModal(kind, key) {
        var row = findByKey(kind, key);
        if (!row) { toast("记录不存在，请刷新后重试", 2); return; }
        byId("msgTypeModalTitle").textContent = kind === "orphan" ? "消息来源推送设置" : "消息类型推送设置";
        setValue("msgTypeId", row.rowId || "");
        setValue("msgTypeStdConfigId", kind === "orphan" ? "" : row.stdConfigId);
        setValue("msgTypeStdType", kind === "orphan" ? row.stdType : "");
        var info = byId("msgTypeStdInfo");
        if (info) {
            info.innerHTML = kind === "orphan"
                ? "消息来源：" + idNameHtml(row.stdType, row.name)
                    + (row.covered ? "　<span class=\"tag off\">已被标准消息类型「" + esc(row.coveredBy)
                        + "」包含，本行不再生效</span>" : "")
                : "标准消息类型：" + idNameHtml(row.stdConfigId, row.name)
                    + "　（模块：" + esc(row.groupName || "未模块") + "；来源："
                    + (sourceIdNameHtml(row).length ? sourceIdNameHtml(row) : "未挂来源") + "）";
        }
        byId("msgTypeWxMsgtype").innerHTML = optionHtml(options.msgtypes,
            row.wxMsgtype || "<%= oaonesPBMsgDefaultFormCode %>", "");
        byId("msgTypeCardAction").innerHTML = optionHtml(options.cardActions, row.cardAction || "none", "");
        // 推送目标应用：按租户逐行的明细表（与列里“N 个租户”弹框是同一份设置）
        saveCtx = appCtxOf(kind, row);
        saveCtx.row = row;
        saveCtx.kind = kind;
        byId("msgTypeAppDetail").innerHTML = appDetailHtml(row);
        updateInlineAppCount();
        setValue("msgTypeYx", String(row.yx == null ? 0 : row.yx));
        setValue("msgTypeTitleTpl", row.titleTpl || "");
        setValue("msgTypeContextTpl", row.contextTpl || "");
        setValue("msgTypeUrlTpl", row.urlTpl || "");
        setValue("msgTypeMobileUrlTpl", row.mobileUrlTpl || "");
        setValue("msgTypeCoverUrl", row.coverUrl || "");
        setValue("msgTypeBtnJson", row.btnJson || "");
        setValue("msgTypeSortNo", row.sortNo == null ? "" : row.sortNo);
        setValue("msgTypeRemark", row.remark || "");
        byId("msgTypeModalMask").style.display = "block";
        byId("msgTypeModal").style.display = "block";
    }

    function closeModal() {
        byId("msgTypeModalMask").style.display = "none";
        byId("msgTypeModal").style.display = "none";
    }

    /* ---------------- 推送目标应用明细弹框（点列里的“N 个租户”打开） ---------------- */

    /**
     * 明细提交用的定位信息：标准消息类型用 stdConfigId，未归类的独立来源用 stdType。
     *
     * <p>顺带记住该类型“以前固定指定的应用”（{@code uf_oaones_appsyn_msg_type.app_id}）：
     * 保存“设置”时原样回写，避免只是改个模板就把老的固定应用悄悄清掉
     * （要清掉请点明细表上方的“清除固定应用”）。</p>
     */
    function appCtxOf(kind, row) {
        var appId = Number(row.appId == null ? 0 : row.appId);
        return kind === "orphan"
            ? {stdConfigId: "", stdType: String(row.stdType == null ? 0 : row.stdType), appId: appId}
            : {stdConfigId: String(row.stdConfigId == null ? "" : row.stdConfigId), stdType: "0", appId: appId};
    }

    /**
     * 该类型“以前固定指定过应用”（{@code uf_oaones_appsyn_msg_type.app_id > 0}）时的提示行：
     * 说明它只对没有按租户绑定的租户生效，并提供一键清除（清除后这些租户改按“租户的启用应用 → 缺省应用”推）。
     */
    function legacyAppTipHtml(row, kind) {
        var appId = Number(row.appId == null ? 0 : row.appId);
        if (!(appId > 0)) { return ""; }
        return "<div class=\"muted\" style=\"margin:0 0 6px 0;\">该消息类型以前固定指定了应用 <b>#"
            + esc(appId) + "</b>（只对下面没有绑定应用的租户生效）："
            + "<button type=\"button\" class=\"link\" data-act=\"legacy-app-clear\" data-kind=\"" + esc(kind)
            + "\" data-key=\"" + esc(kind === "orphan" ? row.stdType : row.stdConfigId)
            + "\">清除固定应用</button></div>";
    }

    /** 清除某个类型上“以前固定指定的应用”（app_id → 0；本页不再提供单应用下拉）。 */
    function clearLegacyApp(kind, key) {
        if (saving) { return; }
        OaonesToast.confirm("清除后这个消息类型不再固定指定应用：没有按租户绑定应用的租户，"
            + "改按“该租户自己的启用应用 → 缺省应用”的顺序推送。确定继续？", function () {
            saving = true;
            Oaones.api("msg.type.quick", {
                std_config_id: kind === "orphan" ? "" : key,
                std_type: kind === "orphan" ? key : "",
                app_id: "0"
            }, function (res) {
                saving = false;
                if (res.code !== 0) { toast(res.msg || "清除失败", 2); return; }
                toast("已清除固定应用", 0);
                closeAppModal();
                closeModal();
                load();
            });
        }, {danger: true});
    }

    /** 明细表里的“清除固定应用”链接：明细弹框与行内“设置”弹框共用。 */
    function onAppDetailClick(e) {
        var btn = e.target && e.target.closest ? e.target.closest("button[data-act='legacy-app-clear']") : null;
        if (!btn) { return; }
        clearLegacyApp(btn.getAttribute("data-kind"), btn.getAttribute("data-key"));
    }

    /** 保存“租户 → 应用”明细（整体覆盖：容器里没出现在提交串里的租户＝不绑定）。 */
    function postAppBinds(ctx, container, onDone) {
        Oaones.api("msg.type.app.save", {
            std_config_id: ctx && ctx.stdConfigId ? ctx.stdConfigId : "",
            std_type: ctx && ctx.stdType ? ctx.stdType : "0",
            binds: readAppBinds(container).join(",")
        }, function (res) {
            if (res.code !== 0) {
                toast(res.msg || "推送目标应用保存失败，请重试", 2);
                onDone(false);
                return;
            }
            onDone(true);
        });
    }

    function openAppModal(kind, key) {
        var row = findByKey(kind, key);
        if (!row) { toast("记录不存在，请刷新后重试", 2); return; }
        appModalCtx = appCtxOf(kind, row);
        byId("msgAppModalTitle").textContent = "推送目标应用 - "
            + (kind === "orphan"
                ? String(row.stdType == null ? "" : row.stdType) + "#" + (row.name || "")
                : String(row.stdConfigId == null ? "" : row.stdConfigId) + "#" + (row.name || ""));
        byId("msgAppModalTip").innerHTML = legacyAppTipHtml(row, kind)
            + "按租户逐行设置：<span class=\"rule\">同一个消息类型每个租户只能设一个应用</span>；"
            + "“默认”＝按该租户在「" + esc(P_SHORT) + "租户设置 → 应用绑定」里维护的启用应用推送。"
            + "（共 " + tenants().length + " 个租户，随列表一次预加载）";
        byId("msgAppModalDetail").innerHTML = appDetailHtml(row);
        setValue("msgAppModalSearch", "");
        filterAppDetail(byId("msgAppModalDetail"), "");
        updateAppModalCount();
        byId("msgAppModalMask").style.display = "block";
        byId("msgAppModal").style.display = "block";
    }

    function closeAppModal() {
        appModalCtx = null;
        byId("msgAppModalMask").style.display = "none";
        byId("msgAppModal").style.display = "none";
    }

    /** 明细弹框里“当前显示 N / 共 M 个租户”，并按已绑定应用数给出提示。 */
    function updateAppModalCount() {
        var el = byId("msgAppModalCount");
        if (!el) { return; }
        var shown = 0;
        var rows = byId("msgAppModalDetail").querySelectorAll("tr.msg-tenant-row");
        for (var i = 0; i < rows.length; i++) {
            if (rows[i].style.display !== "none") { shown++; }
        }
        var bound = 0;
        var selects = byId("msgAppModalDetail").querySelectorAll("select.msg-tenant-app");
        for (var j = 0; j < selects.length; j++) {
            if (Number(selects[j].value) > 0) { bound++; }
        }
        el.textContent = "显示 " + shown + " / " + selects.length + " 个租户，已设置目标应用 " + bound + " 个";
    }

    /** 行内“设置”弹框里那张明细表上方的数量提示（共 M 个租户 / 已设置 N 个）。 */
    function updateInlineAppCount() {
        var tip = byId("msgTypeAppDetailTip");
        if (!tip) { return; }
        var n = 0;
        var selects = byId("msgTypeAppDetail").querySelectorAll("select.msg-tenant-app");
        for (var i = 0; i < selects.length; i++) {
            if (Number(selects[i].value) > 0) { n++; }
        }
        var legacy = saveCtx && saveCtx.row ? legacyAppTipHtml(saveCtx.row, saveCtx.kind) : "";
        tip.innerHTML = legacy + "共 " + selects.length + " 个租户，已设置目标应用 " + n + " 个（保存后生效）";
    }

    function saveAppModal() {
        if (saving || !appModalCtx) { return; }
        saving = true;
        postAppBinds(appModalCtx, byId("msgAppModalDetail"), function (ok) {
            saving = false;
            if (!ok) { return; }
            toast("已保存推送目标应用", 0);
            closeAppModal();
            load();
        });
    }

    function save() {
        if (saving) { return; }
        saving = true;
        Oaones.api("msg.type.save", {
            id: value("msgTypeId"),
            std_config_id: value("msgTypeStdConfigId"),
            std_type: value("msgTypeStdType"),
            wx_msgtype: value("msgTypeWxMsgtype"),
            card_action: value("msgTypeCardAction"),
            title_tpl: value("msgTypeTitleTpl"),
            context_tpl: value("msgTypeContextTpl"),
            url_tpl: value("msgTypeUrlTpl"),
            mobile_url_tpl: value("msgTypeMobileUrlTpl"),
            cover_url: value("msgTypeCoverUrl"),
            btn_json: value("msgTypeBtnJson"),
            // 老的“整个类型一个应用”原样回写：本页已改成按租户明细，不在这里改它
            // （要清掉点明细表上方的“清除固定应用”）
            app_id: saveCtx && saveCtx.appId ? saveCtx.appId : 0,
            yx: value("msgTypeYx"),
            sort_no: value("msgTypeSortNo"),
            remark: value("msgTypeRemark")
        }, function (res) {
            if (res.code !== 0) { saving = false; toast(res.msg || "保存失败", 2); return; }
            // 设置保存后，把弹框里这张“租户 → 应用”明细一起保存（失败时弹框不关，便于重试）
            postAppBinds(saveCtx, byId("msgTypeAppDetail"), function (ok) {
                saving = false;
                if (!ok) { return; }
                toast("已保存", 0);
                closeModal();
                load();
            });
        });
    }

    function openTest(kind, key) {
        var row = findByKey(kind, key);
        if (!row) { toast("记录不存在，请刷新后重试", 2); return; }
        setValue("msgTestTypeId", row.rowId || "");
        setValue("msgTestStdConfigId", kind === "orphan" ? "" : row.stdConfigId);
        setValue("msgTestStdType", kind === "orphan" ? row.stdType : "");
        byId("msgTestTypeName").textContent = (kind === "orphan"
                ? "消息来源：" + String(row.stdType == null ? "" : row.stdType) + "#"
                : "消息类型：" + String(row.stdConfigId == null ? "" : row.stdConfigId) + "#")
            + (row.name || "")
            + "（" + wxMsgtypeText(row.wxMsgtype) + " / " + cardActionText(row.cardAction)
            + "；推送目标：" + (bindCount(row) > 0
                ? "按租户绑定的 " + bindCount(row) + " 个应用" : "默认应用（按接收人租户自动匹配）") + "）";
        byId("msgTypeTestMask").style.display = "block";
        byId("msgTypeTestModal").style.display = "block";
    }

    function closeTest() {
        byId("msgTypeTestMask").style.display = "none";
        byId("msgTypeTestModal").style.display = "none";
    }

    function sendTest() {
        Oaones.api("msg.type.test", {
            type_id: value("msgTestTypeId"),
            std_config_id: value("msgTestStdConfigId"),
            std_type: value("msgTestStdType"),
            accounts: value("msgTestAccounts"),
            wx_userids: value("msgTestWxUserids"),
            biz_id: value("msgTestBizId"),
            biz_type: "custom",
            title: value("msgTestTitle"),
            target_name: value("msgTestTitle"),
            context: value("msgTestContext")
        }, function (res) {
            if (res.code !== 0) { toast(res.msg || "测试推送失败", 2); return; }
            var data = res.data || {};
            toast(data.msg || "测试推送已提交，请在" + P_SHORT + "客户端查看", data.failed > 0 ? 1 : 0);
        });
    }

    /** 行内变化：行首勾选框（只改勾选态）；“推送目标应用”已改为弹框明细，这里不再有行内下拉。 */
    function onTableChange(e) {
        var el = e.target;
        if (!el || !el.classList || typeof el.classList.contains !== "function") { return; }
        if (el.classList.contains("msg-type-chk")) {
            var kind = String(el.getAttribute("data-kind") || "std");
            setChecked(kind, String(el.getAttribute("data-key") || ""), !!el.checked);
            // “仅显示已选择数据”时取消勾选要立即把该行移出列表，否则只同步勾选态与统计行
            if (onlyChecked) {
                if (kind === "orphan") { renderOrphans(); } else { renderTypes(); }
            } else {
                updateCheckedState();
            }
        }
    }

    function onRowClick(e) {
        var btn = e.target && e.target.closest ? e.target.closest("button[data-act]") : null;
        if (!btn) { return; }
        var act = btn.getAttribute("data-act");
        var kind = btn.getAttribute("data-kind");
        var key = btn.getAttribute("data-key");
        if (act === "enable" || act === "disable") {
            // 行内启用 / 停用：只改状态（改完即存，未配置过的行按默认口径建行后再改）
            var yx = act === "disable" ? "1" : "0";
            quickSave(kind, key, {yx: yx}, btn, yx === "1"
                ? "已停用（这类消息不再发到" + P_FULL + "）"
                : "已启用（这类消息恢复推送到" + P_FULL + "）");
        } else if (act === "edit") {
            openModal(kind, key);
        } else if (act === "apps") {
            // 列里的“N 个租户”：打开租户明细弹框，按租户逐个绑定目标应用
            openAppModal(kind, key);
        } else if (act === "test") {
            openTest(kind, key);
        } else if (act === "reset") {
            OaonesToast.confirm("恢复默认后，这个标准消息类型按平台默认口径推送（默认形态 + 默认应用），"
                + "本页为它配置的形态 / 模板会被清除。确定继续？", function () {
                Oaones.api("msg.type.delete", {std_config_id: key}, function (res) {
                    if (res.code !== 0) { toast(res.msg || "操作失败", 2); return; }
                    toast("已恢复默认口径", 0);
                    load();
                });
            }, {danger: true});
        } else if (act === "del") {
            OaonesToast.confirm("确定删除这个消息来源的推送设置？删除后它按平台默认口径推送"
                + "（要“不推送”请把状态改为“停用”）。", function () {
                Oaones.api("msg.type.delete", {std_type: key}, function (res) {
                    if (res.code !== 0) { toast(res.msg || "删除失败", 2); return; }
                    toast("已删除", 0);
                    load();
                });
            }, {danger: true});
        }
    }

    function bindEvents() {
        if (bound) { return; }
        bound = true;
        byId("msgTypeReloadBtn").addEventListener("click", load);
        byId("msgTypeKeyword").addEventListener("input", applyFilter);
        byId("msgTypeStatusFilter").addEventListener("change", applyFilter);
        // 模块：点生成框打开选择框（只读框，不用下拉框）
        byId("msgTypeGroupInput").addEventListener("click", openGroupPick);
        byId("msgTypeGroupClear").addEventListener("click", resetGroupFilter);
        byId("msgTypeGroupCloseBtn").addEventListener("click", hideGroupPick);
        byId("msgTypeGroupCancelBtn").addEventListener("click", hideGroupPick);
        byId("msgTypeGroupMask").addEventListener("click", hideGroupPick);
        byId("msgTypeGroupOkBtn").addEventListener("click", confirmGroup);
        byId("msgTypeGroupClearBtn").addEventListener("click", clearGroupPick);
        byId("msgTypeGroupSearch").addEventListener("input", renderGroupList);
        // 多选弹框的“全选”：按当前搜索关键字过滤后的候选全选 / 全不选（组件负责文案与高亮）
        OaonesPicker.setSelectAll("msgTypeGroup", toggleGroupAll);
        byId("msgTypeGroupList").addEventListener("click", function (e) {
            var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
            if (btn) { toggleGroup(btn.getAttribute("data-value") || ""); }
        });
        byId("msgTypeGroupList").addEventListener("dblclick", function (e) {
            var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
            if (!btn) { return; }
            // 双击前已收到两次单击（同一行重复点不会取消选择），这里确保选中态后再确定退出
            if (pendingGroups.indexOf(String(btn.getAttribute("data-value") || "")) < 0) {
                toggleGroup(btn.getAttribute("data-value") || "");
            }
            confirmGroup();
        });
        byId("msgTypeCheckAll").addEventListener("change", function () {
            togglePageChecks(!!this.checked);
        });
        byId("msgTypeOrphanCheckAll").addEventListener("change", function () {
            toggleOrphanChecks(!!this.checked);
        });
        // 统计行末尾的操作组：仅显示已选择数据（按钮由渲染时拼进统计行，这里用事件委托接住）
        byId("msgTypeStats").addEventListener("click", function (e) {
            var t = e.target;
            if (t && t.classList && t.classList.contains("only-checked-btn")) {
                toggleOnlyChecked();
            }
        });
        byId("msgTypeBatchEnableBtn").addEventListener("click", function () { batchSave(0); });
        byId("msgTypeBatchDisableBtn").addEventListener("click", function () { batchSave(1); });
        byId("msgTypeCancelBtn").addEventListener("click", closeModal);
        byId("msgTypeModalClose").addEventListener("click", closeModal);
        byId("msgTypeModalMask").addEventListener("click", closeModal);
        byId("msgTypeSaveBtn").addEventListener("click", save);
        byId("msgTestCancelBtn").addEventListener("click", closeTest);
        byId("msgTypeTestClose").addEventListener("click", closeTest);
        byId("msgTypeTestMask").addEventListener("click", closeTest);
        byId("msgTestSendBtn").addEventListener("click", sendTest);
        byId("msgTypeTable").addEventListener("change", onTableChange);
        byId("msgTypeOrphanTable").addEventListener("change", onTableChange);
        byId("msgTypeBody").addEventListener("click", onRowClick);
        byId("msgTypeOrphanBody").addEventListener("click", onRowClick);
        // 推送目标应用明细弹框（点列里的“N 个租户”打开）
        byId("msgAppModalMask").addEventListener("click", closeAppModal);
        byId("msgAppModalClose").addEventListener("click", closeAppModal);
        byId("msgAppModalCancelBtn").addEventListener("click", closeAppModal);
        byId("msgAppModalSaveBtn").addEventListener("click", saveAppModal);
        byId("msgAppModalSearch").addEventListener("input", function () {
            filterAppDetail(byId("msgAppModalDetail"), this.value);
            updateAppModalCount();
        });
        // 改任意一个租户的应用，就把“已设置目标应用 N 个”刷新到最新
        byId("msgAppModalDetail").addEventListener("change", function (e) {
            var el = e.target;
            if (el && el.classList && el.classList.contains("msg-tenant-app")) {
                updateAppModalCount();
            }
        });
        // 行内“设置”弹框里的明细表：改了应用即时刷新数量，保存时随设置一起提交
        byId("msgTypeAppDetail").addEventListener("change", function (e) {
            var el = e.target;
            if (el && el.classList && el.classList.contains("msg-tenant-app")) {
                updateInlineAppCount();
            }
        });
        // 两个明细容器里“清除固定应用”的链接（老的 uf_oaones_appsyn_msg_type.app_id）
        byId("msgAppModal").addEventListener("click", onAppDetailClick);
        byId("msgTypeModal").addEventListener("click", onAppDetailClick);
    }

    window.MsgTypePanel = {
        load: function () {
            bindEvents();
            load();
        }
    };
})();
</script>
