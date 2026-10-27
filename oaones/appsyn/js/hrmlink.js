/**
 * 泛微标准对象页面跳转（人员卡片 / 部门·分部组织页面）。
 *
 * 页面上出现的人员姓名、部门名称、分部名称统一用本文件渲染成超链接，点击后在新页签打开
 * 泛微（E9）标准页面，口径与平台内部跳转一致：
 *
 *  - 人员 → 人员卡片  /spa/hrm/index_mobx.html#/main/hrm/card/cardInfo/<OA人员ID>
 *  - 部门 → 组织架构  /spa/hrm/engine.html#/hrmengine/organization?showTree=false&isView=1&type=department&id=<OA部门ID>
 *  - 分部 → 组织架构  /spa/hrm/engine.html#/hrmengine/organization?showTree=false&isView=1&type=subcompany&id=<OA分部ID>
 *
 * 约定（详见 skill.md 第一节第 9 条）：
 *  1. 只对“泛微（OA）侧”的对象加链接；企业微信侧成员/部门在泛微没有标准页面，
 *     仍按普通文本展示，不能拼泛微链接。
 *  2. 只传 OA 标准 ID（hrmresource.id / hrmdepartment.id / hrmsubcompany.id），
 *     不要传企微 ID，否则会跳到不存在的人员/部门。
 *  3. ID 为空、0、非数字时退化为纯文本，避免出现点开 404 的死链。
 *  4. 页面上一律用 OaonesHrm.user/dept/sub(id, text) 输出，不要自行拼链接、自行 esc 姓名，
 *     文本转义由本文件统一处理（这里接受原始文本，不接受已转义文本）。
 */
(function () {
    "use strict";

    var USER_BASE = "/spa/hrm/index_mobx.html#/main/hrm/card/cardInfo/";
    var ORG_BASE = "/spa/hrm/engine.html#/hrmengine/organization?showTree=false&isView=1&type=";

    function esc(v) {
        if (v === null || v === undefined) { return ""; }
        if (window.Oaones && window.Oaones.esc) { return window.Oaones.esc(v); }
        return String(v)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }

    /** 泛微标准 ID 只认正整数；空值、0、非数字一律视为“不可跳转”。 */
    function oaId(id) {
        var v = String(id == null ? "" : id).trim();
        return (/^[1-9][0-9]*$/).test(v) ? v : "";
    }

    function userUrl(id) { return USER_BASE + encodeURIComponent(oaId(id)); }

    function orgUrl(type, id) {
        return ORG_BASE + type + "&id=" + encodeURIComponent(oaId(id));
    }

    function deptUrl(id) { return orgUrl("department", id); }

    function subUrl(id) { return orgUrl("subcompany", id); }

    /**
     * 链接统一带 onclick 阻止冒泡：姓名/部门名可能出现在“整行/整卡可点击”的容器里
     * （例如租户卡片点击即打开编辑弹框），避免点开人员卡片的同时又触发容器的点击行为。
     */
    function anchor(url, text, tip) {
        return "<a class='hrm-link' href=\"" + esc(url) + "\" target='_blank' rel='noopener'"
            + " title=\"" + esc(tip) + "\" onclick=\"if(event){event.stopPropagation();}\">"
            + esc(text) + "</a>";
    }

    /** 名称为空时给一个可读兜底，避免链接上没有文字。 */
    function labelOf(text, fallback) {
        var t = String(text == null ? "" : text).trim();
        return t.length ? t : fallback;
    }

    function nameLink(id, text, kind, title) {
        var v = oaId(id);
        var name = labelOf(text, v ? "OA " + kind + " " + v : "—");
        if (!v) { return esc(name); }
        return anchor(kind === "人员" ? userUrl(v) : orgUrl(kind === "部门" ? "department" : "subcompany", v),
            name, "查看" + title + "：" + name);
    }

    /** 人员姓名 → 泛微人员卡片；无有效 OA 人员ID时退化为纯文本。 */
    function user(id, text) { return nameLink(id, text, "人员", "人员卡片"); }

    /** 部门名称 → 泛微部门显示页面。 */
    function dept(id, text) { return nameLink(id, text, "部门", "部门信息"); }

    /** 分部名称 → 泛微分部显示页面。 */
    function sub(id, text) { return nameLink(id, text, "分部", "分部信息"); }

    window.OaonesHrm = {
        userUrl: userUrl,
        deptUrl: deptUrl,
        subUrl: subUrl,
        user: user,
        dept: dept,
        sub: sub
    };
})();
