/*
  富文本编辑器（本模块自己的轻量控件，OaonesRichText）。

  用途：把一段可编辑的富文本（HTML）交给页面，值同步到一个隐藏域里随表单提交——
  目前用在「登录设置 → 登录页左侧文字」（loginConfig/config.jsp），
  登录页原样输出这段 HTML（设置什么显示什么）。

  为什么自己写而不是引平台的编辑器：本模块页面是独立页壳（不引 navbar、不引平台页面组件），
  引 FCKEditor / ueditor 会把平台的一整套资源与配置耦合进来；这里的需要只有
  “常见格式 + 能看到源码 + 值好提交”，用 contenteditable + document.execCommand 就够，
  样式也走本模块的设计令牌（css/style.css 的 .rt-* 一节），与其它页面同一套观感。

  口径：
  - 只用模块自己的提示框（OaonesToast）/ 内联输入行，**不用原生 alert / confirm / prompt**；
  - 颜色不改写十六进制：色块的颜色写在 CSS 里（.rt-c-* ），点击时用 getComputedStyle 读回来再套用；
  - 值同步：输入过程中（input）与切源码 / 失焦时把 HTML 写回隐藏域；
  - 源码模式：切到 textarea 直接改 HTML（“设置什么显示什么”的精确入口），切回来即生效；
  - 只读：setDisabled(true) 时不可编辑、工具栏禁用（用于非维护人）。
*/
(function () {
    "use strict";

    /** 字号档位（execCommand 的 fontSize 取值 1~7，这里给常用的四档） */
    var SIZES = [
        ["2", "小"],
        ["3", "正常"],
        ["4", "大"],
        ["5", "特大"]
    ];

    /** 文本颜色（色值写在 CSS 的 .rt-c-* 里，这里只记名字与标题） */
    var COLORS = [
        ["default", "默认色", "#333"],
        ["primary", "主色", "#4a6fa5"],
        ["danger", "红色", "#d93026"],
        ["success", "绿色", "#2e9e5b"],
        ["muted", "灰色", "#8c8c8c"]
    ];

    function byId(id) { return document.getElementById(id); }

    function el(tag, cls, text) {
        var node = document.createElement(tag);
        if (cls) { node.className = cls; }
        if (text !== undefined && text !== null) { node.textContent = text; }
        return node;
    }

    function toast(msg, mode) {
        if (msg === null || msg === undefined || String(msg) === "") { return; }
        if (window.OaonesToast) { window.OaonesToast.show(msg, { mode: mode }); return; }
        if (window.console && console.log) { console.log(msg); }
    }

    /** HTML 里有没有实际内容（空段落 / 换行 / &nbsp; 都算空） */
    function isEmptyHtml(html) {
        return String(html || "")
            .replace(/&nbsp;/g, "").replace(/\u00a0/g, "")
            .replace(/<[^>]*>/g, "").trim().length === 0;
    }

    /**
     * 创建一个富文本编辑器。
     *
     * @param opts.mount       容器元素（或它的 id）：编辑器会在这个容器里建工具栏与编辑区
     * @param opts.input       隐藏域元素（或它的 id）：页面提交时读它即可拿到 HTML
     * @param opts.placeholder 空内容时的提示文案
     * @param opts.onChange    内容变化回调（可选）
     */
    function init(opts) {
        opts = opts || {};
        var mount = typeof opts.mount === "string" ? byId(opts.mount) : opts.mount;
        var input = typeof opts.input === "string" ? byId(opts.input) : opts.input;
        if (!mount || !input) {
            if (window.console && console.warn) { console.warn("OaonesRichText：缺少 mount / input"); }
            return null;
        }

        var changing = false;      // 程序化赋值期间不回调 onChange
        var sourceOn = false;      // 是否处于“源码”模式
        var disabled = false;

        var tool = el("div", "rt-tool");
        var body = el("div", "rt-body");
        body.setAttribute("contenteditable", "true");
        body.setAttribute("role", "textbox");
        body.setAttribute("aria-multiline", "true");
        if (opts.placeholder) { body.setAttribute("data-placeholder", opts.placeholder); }
        var source = el("textarea", "rt-source");
        source.style.display = "none";
        source.setAttribute("spellcheck", "false");

        mount.className = mount.className ? mount.className + " rt" : "rt";
        mount.appendChild(tool);
        mount.appendChild(body);
        mount.appendChild(source);

        /* ---------------- 工具栏 ---------------- */

        function addButton(cls, text, title, onClick) {
            var btn = el("button", cls, text);
            btn.type = "button";
            if (title) { btn.title = title; }
            // 不让工具栏抢走选区：按下时不转移焦点
            btn.addEventListener("mousedown", function (e) { e.preventDefault(); });
            btn.addEventListener("click", function () { if (!disabled) { onClick(btn); } });
            tool.appendChild(btn);
            return btn;
        }

        function exec(cmd, value) {
            body.focus();
            try {
                document.execCommand(cmd, false, value === undefined ? null : value);
            } catch (e) {
                toast("该浏览器不支持这个格式操作", 1);
            }
            sync();
        }

        addButton("rt-btn rt-b", "B", "加粗", function () { exec("bold"); });
        addButton("rt-btn rt-i", "I", "斜体", function () { exec("italic"); });
        addButton("rt-btn rt-u", "U", "下划线", function () { exec("underline"); });
        tool.appendChild(el("span", "rt-sep"));

        var sizeSel = el("select", "rt-select");
        sizeSel.title = "字号";
        SIZES.forEach(function (one) {
            var opt = el("option", null, one[1]);
            opt.value = one[0];
            sizeSel.appendChild(opt);
        });
        sizeSel.value = "3";
        sizeSel.addEventListener("change", function () { exec("fontSize", sizeSel.value); });
        tool.appendChild(sizeSel);

        tool.appendChild(el("span", "rt-label", "颜色"));
        COLORS.forEach(function (one) {
            var dot = el("button", "rt-color rt-c-" + one[0]);
            dot.type = "button";
            dot.title = one[1];
            dot.setAttribute("aria-label", one[1]);
            dot.addEventListener("mousedown", function (e) { e.preventDefault(); });
            dot.addEventListener("click", function () {
                if (disabled) { return; }
                // 色值写在 CSS 里：读回来再套用，JS 里不出现新的一份配色
                var rgb = window.getComputedStyle(dot).backgroundColor;
                exec("foreColor", rgb);
            });
            tool.appendChild(dot);
        });
        tool.appendChild(el("span", "rt-sep"));

        addButton("rt-btn", "• 列表", "无序列表", function () { exec("insertUnorderedList"); });
        addButton("rt-btn", "1. 列表", "有序列表", function () { exec("insertOrderedList"); });
        tool.appendChild(el("span", "rt-sep"));

        addButton("rt-btn", "左", "左对齐", function () { exec("justifyLeft"); });
        addButton("rt-btn", "中", "居中", function () { exec("justifyCenter"); });
        addButton("rt-btn", "右", "右对齐", function () { exec("justifyRight"); });
        tool.appendChild(el("span", "rt-sep"));

        addButton("rt-btn", "链接", "给选中的文字加超链接", function () { toggleLinkRow(); });
        addButton("rt-btn", "清除格式", "去掉选中文字的格式", function () {
            exec("removeFormat");
            exec("formatBlock", "<div>");
        });

        var sourceBtn = addButton("rt-btn", "源码", "查看 / 直接编辑 HTML", function () {
            toggleSource();
        });

        /* 链接：不用原生 prompt，工具栏下方放一行输入框（确定 / 取消） */
        var linkRow = el("div", "rt-link-row");
        linkRow.style.display = "none";
        var linkInput = el("input", "rt-link-input");
        linkInput.type = "text";
        linkInput.setAttribute("placeholder", "https:// 或 /oaones/appsyn/... 开头的地址");
        linkInput.setAttribute("data-lpignore", "true");
        var linkOk = el("button", "btn small", "确定");
        linkOk.type = "button";
        var linkCancel = el("button", "btn small secondary", "取消");
        linkCancel.type = "button";
        linkRow.appendChild(linkInput);
        linkRow.appendChild(linkOk);
        linkRow.appendChild(linkCancel);
        mount.insertBefore(linkRow, mount.firstChild); // 放在工具栏上方，避免顶开编辑区

        function toggleLinkRow() {
            var show = linkRow.style.display === "none";
            linkInput.value = "";
            linkRow.style.display = show ? "flex" : "none";
            if (show) { linkInput.focus(); }
        }

        linkCancel.addEventListener("click", function () { toggleLinkRow(); });
        linkInput.addEventListener("keydown", function (e) {
            if (e.keyCode === 13) { e.preventDefault(); linkOk.click(); }
            if (e.keyCode === 27) { toggleLinkRow(); }
        });
        linkOk.addEventListener("click", function () {
            var url = String(linkInput.value || "").replace(/^\s+|\s+$/g, "");
            if (url.length === 0) {
                toast("请填链接地址（http://、https:// 或 / 开头的站内地址）", 1);
                return;
            }
            exec("createLink", url);
            toggleLinkRow();
        });

        /* ---------------- 值同步 / 源码模式 ---------------- */

        function fireChange() {
            if (changing) { return; }
            if (typeof opts.onChange === "function") { opts.onChange(getHtml()); }
        }

        function sync() {
            var html = sourceOn ? source.value : body.innerHTML;
            input.value = isEmptyHtml(html) ? "" : html;
            body.className = "rt-body" + (isEmptyHtml(html) ? " rt-empty" : "");
            fireChange();
        }

        function toggleSource() {
            sourceOn = !sourceOn;
            if (sourceOn) {
                source.value = body.innerHTML;
                body.style.display = "none";
                source.style.display = "block";
                sourceBtn.className = "rt-btn rt-on";
                source.focus();
            } else {
                body.innerHTML = source.value;
                source.style.display = "none";
                body.style.display = "block";
                sourceBtn.className = "rt-btn";
            }
            sync();
        }

        body.addEventListener("input", sync);
        body.addEventListener("blur", sync);
        source.addEventListener("input", sync);
        source.addEventListener("blur", sync);
        body.addEventListener("paste", function () {
            // 粘贴默认按 HTML 落进编辑区（“设置什么显示什么”），这里只保证粘贴后立刻同步
            window.setTimeout(sync, 0);
        });

        var api = {
            /** 当前 HTML（空内容返回空串） */
            getHtml: function () {
                var html = sourceOn ? source.value : body.innerHTML;
                return isEmptyHtml(html) ? "" : html;
            },
            /** 设置内容（页面回显 / 清除时调用） */
            setHtml: function (html) {
                changing = true;
                var value = html === null || html === undefined ? "" : String(html);
                body.innerHTML = value;
                if (sourceOn) { source.value = value; }
                input.value = isEmptyHtml(value) ? "" : value;
                body.className = "rt-body" + (isEmptyHtml(value) ? " rt-empty" : "");
                changing = false;
            },
            /** 只读（非维护人）：不可编辑、工具栏不可点 */
            setDisabled: function (flag) {
                disabled = !!flag;
                body.setAttribute("contenteditable", disabled ? "false" : "true");
                source.disabled = disabled;
                if (disabled) { linkRow.style.display = "none"; }
                mount.className = disabled ? mount.className.replace(/\s*rt-disabled/g, "") + " rt-disabled"
                    : mount.className.replace(/\s*rt-disabled/g, "");
                var buttons = tool.querySelectorAll("button,select");
                for (var i = 0; i < buttons.length; i++) {
                    buttons[i].disabled = disabled;
                }
            },
            /** 编辑区（页面需要做光标 / 高度处理时用） */
            body: body
        };

        api.setHtml(input.value || "");
        return api;
    }

    window.OaonesRichText = { init: init, isEmptyHtml: isEmptyHtml };
})();
