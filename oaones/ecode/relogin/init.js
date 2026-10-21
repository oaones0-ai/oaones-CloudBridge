/**
 * 超时重新登录：把门户自带的「重新登录」弹框换成本模块统一登录页（/oaones/login）。
 *
 * 本文件 = **ecode 项目「开启认证超时退出到登录页」里的 init.js**（ecode 开发平台 /ecode/，
 * 项目里就一个 init.js 文件；仓库里这一份是留档，两边内容要一致）。
 *
 * ── 平台原来的行为（不改平台任何文件，只换弹框内容）────────────────────────────
 * 长时间未操作后，接口返回 errorCode 001（session过期）/ 002（登录超时）/ 003（被强制下线）/
 * 004（从另一个窗口登入）时，coms 框架（/cloudstore/resource/pc/com/v1/ecCom.min.js、
 * /spa/coms/index.mobx.js）会调 `window.doLoginPop()` 弹出平台自带的“重新登录”框
 * （React 组件 WeaLoginPop，DOM：.wea-login-pop / #LoginPopContainer，内容就是平台自带登录表单，
 * 标题“由于长时间未操作，系统自动退出，需要重新登录”）；后台把“启用超时登出弹窗功能”
 * （RemindSettings#timeOutSwitch）关掉时，改为只提示 + 整页跳 /wui/index.html。
 * 而本模块的未登录页是统一登录页 /oaones/login，于是超时弹框与正常登录成了两套界面。
 *
 * ── 本文件的改法 ──────────────────────────────────────────────────────────
 * ① 覆盖 `window.doLoginPop`：弹框里放**同源 iframe** 的本模块登录页
 *    `/oaones/login?popup=1`（popup=1 = 弹框版式；登录成功后 login.jsp 用 postMessage 回“登录成功”）。
 *    弹框本身只有一层遮罩 + 一张登录卡片（就是那层 iframe：白底、18px 圆角、柔和大阴影），
 *    **不带任何说明文字**；高度由登录页自己上报（页签切换、二维码出现时弹框跟着变高变矮），
 *    卡片高度不超过浏览器可视区（页面随浏览器大小自适应，框内不出现竖滚动条）。
 *    **这个弹框不给关**：没有关闭按钮、点空白处与 ESC 都不关（会话没恢复之前，用户就该待在这一步）；
 *    卡片里另有“通过登录页登录”按钮，需要整页登录时在新窗口打开完整登录页。
 *    登录成功后弹框**直接消失**并在本页轻提示“已登录成功”——**不刷新浏览器页面**，
 *    会话已经恢复，用户继续停在原来那一页就行。
 *    子框架（流程表单 / 文档页等 iframe 里发起的请求）里超时：与平台口径一致——整页跳登录页，
 *    并带 gopage 回到用户原来那一页；后台关掉“超时登出弹窗”时同样整页跳（落点换成本模块登录页）。
 * ② 平台认证（CAS / 单点）超时的原有口径（退出到 /login/logoutSSO.jsp）保持不变。
 *
 * ── 验证 ─────────────────────────────────────────────────────────────────
 * 任意已登录页面控制台执行 `window.oaonesRelogin.open()`（与平台调用的 window.doLoginPop() 同一入口）
 * → 弹出的应是本模块登录页；登录成功后弹框消失、提示“已登录成功”，当前页面不刷新。
 */

/* -------------------- 平台认证（CAS / 单点）超时退出：原有口径，保持不变 -------------------- */
window.customSSOConfig = {
  isUse: true,
  serverLogoutUrl: "/login/logoutSSO.jsp"
};

ecodeSDK.rewriteApiDataQueueSet({
  fn: function fn(url, params, data) {
    if (data.errorCode == '002' && window.ecCom) {
      if (window.localStorage.loginBySSO === 'true') {
        if (window.localStorage.casPath) {
          window.localStorage.setItem('casPath', "/login/logoutSSO.jsp");
        } else {
          window.location.href = "/login/logoutSSO.jsp";
        }
      }
    }
    return data;
  },
  desc: '开启认证，超时退出到登录页'
});

/* ==================== 以下为 OAones 重新登录（超时后弹本模块登录页） ==================== */

// 超时后的形态："popup" = 弹出本模块登录页（默认）；"page" = 不弹框，整页跳登录页
var OAONES_RELOGIN_MODE = "popup";
// 统一登录页对外地址（oaones.properties#route./oaones/login；真实页 /oaones/login/login.jsp 被 302 挡回）
var OAONES_RELOGIN_PAGE = "/oaones/login";
// 弹框模式参数：登录页据此走弹框版式，并在登录成功后用 postMessage 回“登录成功”
var OAONES_RELOGIN_POPUP = "popup=1";
var OAONES_RELOGIN_MASK_ID = "oaonesReloginMask";
var OAONES_RELOGIN_FRAME_ID = "oaonesReloginFrame";
var OAONES_RELOGIN_TOAST_ID = "oaonesReloginToast";
// 弹框尺寸：宽度与登录卡片同宽，高度由登录页上报（上报前用兜底高度，避免闪一下 0 高度）
var OAONES_RELOGIN_WIDTH = 420;
var OAONES_RELOGIN_FALLBACK_H = 430;
// 弹框四周至少留出的空隙（超出可用高度时在卡片内部滚动）
var OAONES_RELOGIN_GAP = 32;
// 登录页上报过的最近一次高度（窗口缩放时按它重新收一次，不改变登录页的意图）
var oaonesReloginLastHeight = 0;

function oaonesReloginContentPath() {
  return window.ecologyContentPath || "";
}

function oaonesReloginIsTop() {
  try {
    return window.self === window.top;
  } catch (e) {
    return false;
  }
}

/** 用户“现在在哪一页”（站内相对地址，hash 里的页面参数整段保留），登录成功后要回到这里 */
function oaonesReloginBack() {
  try {
    var win = oaonesReloginIsTop() ? window : window.top;
    return win.location.pathname + win.location.search + win.location.hash;
  } catch (e) {
    return "";
  }
}

/** 登录页地址：popup=true 取弹框用地址；否则带上 gopage，登录成功后直接回到原来那一页 */
function oaonesReloginUrl(popup) {
  var url = oaonesReloginContentPath() + OAONES_RELOGIN_PAGE;
  if (popup) {
    // 弹框模式：把“弹框现在有多高可用”一起带过去——登录页按它决定二维码多大
    // （窗口矮时码图跟着缩小，弹框里不出现竖滚动条）；之后窗口缩放时用 postMessage 再同步
    return url + "?" + OAONES_RELOGIN_POPUP + "&avail=" + oaonesReloginMaxHeight();
  }
  var back = oaonesReloginBack();
  return back ? url + "?gopage=" + encodeURIComponent(back) : url;
}

/** 登录页自己（含弹框里的登录页）不要再弹一层 */
function oaonesReloginOnLoginPage() {
  try {
    return window.location.pathname.indexOf(oaonesReloginContentPath() + OAONES_RELOGIN_PAGE) === 0;
  } catch (e) {
    return false;
  }
}

/** 整页跳到登录页（带 gopage 回到当前页）；在子框架里跳的是顶层窗口 */
function oaonesReloginGoPage() {
  var url = oaonesReloginUrl(false);
  try {
    if (!oaonesReloginIsTop() && window.top && window.top.location) {
      window.top.location.href = url;
      return;
    }
  } catch (e) {
    /* 跨域框架（理论上不会有）：退回本框架跳转 */
  }
  window.location.href = url;
}

function oaonesReloginStyle(el, css) {
  el.setAttribute("style", css);
  return el;
}

/** 弹框可用高度：窗口高度留出上下空隙（登录卡片比它还高时，在卡片内部滚动） */
function oaonesReloginMaxHeight() {
  var view = window.innerHeight || (document.documentElement && document.documentElement.clientHeight) || 0;
  var max = view - OAONES_RELOGIN_GAP;
  return max > 240 ? Math.round(max) : 240;
}

function oaonesReloginClose() {
  var mask = document.getElementById(OAONES_RELOGIN_MASK_ID);
  if (mask && mask.parentNode) {
    mask.parentNode.removeChild(mask);
  }
}

/**
 * 按登录页上报的高度调整卡片大小（登录页里的页签切换、二维码出现都会重新上报）。
 * 高度上限是“窗口高度 - 空隙”，超过就在卡片内部滚动；
 * 第一次拿到高度时把卡片显出来（上报前卡片是透明的，避免用户看到半张卡片在跳）。
 */
function oaonesReloginResize(height) {
  var frame = document.getElementById(OAONES_RELOGIN_FRAME_ID);
  if (!frame) {
    return;
  }
  var h = Number(height) > 0 ? Number(height) : OAONES_RELOGIN_FALLBACK_H;
  h = Math.max(200, Math.min(Math.round(h), oaonesReloginMaxHeight()));
  frame.style.height = h + "px";
  frame.style.opacity = "1";
}

function oaonesReloginSyncHeight() {
  oaonesReloginResize(oaonesReloginLastHeight || OAONES_RELOGIN_FALLBACK_H);
}

/** 把“弹框现在有多高可用”同步给登录页（登录页按它决定二维码多大；窗口缩放时同步一次） */
function oaonesReloginTellAvail() {
  var frame = document.getElementById(OAONES_RELOGIN_FRAME_ID);
  if (!frame || !frame.contentWindow) {
    return;
  }
  try {
    frame.contentWindow.postMessage(
      { oaonesRelogin: "avail", height: oaonesReloginMaxHeight() },
      window.location.origin);
  } catch (e) {
    /* 跨域（理论上不会有）：登录页按 URL 上的 avail 自己算 */
  }
}

/**
 * 弹框里的登录卡片：一层同源 iframe（就是那张卡片，白底 + 18px 圆角 + 柔和大阴影，
 * 与 UI设计规范.md 的弹窗口径一致），里面是本模块统一登录页的弹框版式 `/oaones/login?popup=1`。
 * 卡片里不放任何说明文字（登录方式、校验口径全部由登录页自己决定），
 * 位置居中、大小随登录页上报的高度变化。
 *
 * 这个弹框**不给关**（没有关闭按钮，点遮罩、按 ESC 都不关）：会话没恢复之前用户就该待在这一步；
 * 唯一的收口是登录成功（登录页发 ok 过来）。
 */
function oaonesReloginOpen() {
  if (document.getElementById(OAONES_RELOGIN_MASK_ID)) {
    return;
  }
  if (!document.body) {
    // 页面还没解析出 body：稍后再弹（正常不会走到）
    window.setTimeout(function () {
      oaonesReloginOpen();
    }, 200);
    return;
  }

  var mask = document.createElement("div");
  mask.id = OAONES_RELOGIN_MASK_ID;
  oaonesReloginStyle(mask, "position:fixed;left:0;top:0;right:0;bottom:0;z-index:2147483000;"
    + "background:rgba(35,39,47,.28);-webkit-backdrop-filter:blur(2px);backdrop-filter:blur(2px);"
    + "display:flex;align-items:center;justify-content:center;padding:16px;box-sizing:border-box;");

  var frame = document.createElement("iframe");
  frame.id = OAONES_RELOGIN_FRAME_ID;
  frame.setAttribute("frameborder", "0");
  frame.setAttribute("title", "重新登录");
  oaonesReloginStyle(frame, "width:100%;max-width:" + OAONES_RELOGIN_WIDTH + "px;"
    + "height:" + OAONES_RELOGIN_FALLBACK_H + "px;border:0;display:block;background:#fff;"
    + "border-radius:18px;overflow:hidden;"
    + "box-shadow:0 8px 20px rgba(23,29,41,.08),0 28px 64px rgba(23,29,41,.14);"
    + "opacity:0;transition:opacity .16s cubic-bezier(.4,0,.2,1);");
  frame.src = oaonesReloginUrl(true);

  mask.appendChild(frame);
  document.body.appendChild(mask);

  // 登录页迟迟没上报高度（比如浏览器缓存了老版本页面）时也要能看见卡片，不能一直透明
  window.setTimeout(function () {
    var f = document.getElementById(OAONES_RELOGIN_FRAME_ID);
    if (f) {
      f.style.opacity = "1";
    }
  }, 600);
}

/**
 * 登录成功：弹框直接消失 + 在本页轻提示“已登录成功”。
 * **不刷新浏览器页面**——会话已经恢复，用户原来那一页继续用就行（正在填的表单不会被刷掉）。
 */
function oaonesReloginOk() {
  oaonesReloginClose();
  oaonesReloginToast("已登录成功，如页面显示不正确，请手动刷新浏览器。");
}

/**
 * 本页轻提示（不依赖门户与本模块的样式，自己带一份，口径与 css/style.css 的 .toast 一致：
 * 深色底 + 左侧功能色 + 圆点图标，2 秒后自己消失）。只用于弹框关掉之后的收口提示。
 */
function oaonesReloginToast(text) {
  if (!document.body) {
    return;
  }
  var old = document.getElementById(OAONES_RELOGIN_TOAST_ID);
  if (old && old.parentNode) {
    old.parentNode.removeChild(old);
  }
  var box = document.createElement("div");
  box.id = OAONES_RELOGIN_TOAST_ID;
  box.setAttribute("role", "status");
  oaonesReloginStyle(box, "position:fixed;left:50%;top:76px;transform:translateX(-50%);z-index:2147483001;"
    + "display:flex;align-items:center;gap:8px;max-width:80vw;padding:11px 16px;border-radius:10px;"
    + "background:rgba(32,40,52,.94);color:#fff;font-size:13.5px;line-height:1.6;"
    + "box-shadow:0 8px 24px rgba(0,0,0,.22);pointer-events:none;opacity:0;"
    + "transition:opacity .16s cubic-bezier(.4,0,.2,1);");
  var icon = document.createElement("span");
  oaonesReloginStyle(icon, "flex:0 0 auto;width:16px;height:16px;border-radius:50%;background:#3f7a5e;"
    + "display:inline-flex;align-items:center;justify-content:center;color:#fff;");
  // 对勾用**线性 SVG**，不写字形：✓(U+2713) 这类字符在有些机器的字体里没有，
  // 浏览器会退化成“?”看着像出错；与 UI设计规范.md 里“图标不要写字形（如 ✕）”同一口径
  icon.innerHTML = '<svg viewBox="0 0 24 24" width="11" height="11" fill="none" stroke="currentColor"'
    + ' stroke-width="3.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">'
    + '<path d="M4 12.6l5.2 5.2L20 7"></path></svg>';
  var msg = document.createElement("span");
  msg.textContent = text;
  box.appendChild(icon);
  box.appendChild(msg);
  document.body.appendChild(box);
  window.setTimeout(function () {
    box.style.opacity = "1";
  }, 20);
  window.setTimeout(function () {
    box.style.opacity = "0";
    window.setTimeout(function () {
      if (box.parentNode) {
        box.parentNode.removeChild(box);
      }
    }, 200);
  }, 2200);
}

/**
 * 接收弹框里登录页的消息（同源）。login.jsp 在弹框模式下会发这几条：
 *   ok      登录成功 → 关弹框 + 提示“已登录成功”（不刷新页面）
 *   height  卡片内容高度 → 调整弹框大小
 *   cas     平台要求先去 CAS / 千里眼认证 → 关弹框后整页跳（这类页面不能困在 iframe 里）
 * （弹框不给用户关：没有 close 这条。）
 */
function oaonesReloginOnMessage(event) {
  if (!event || !event.origin || event.origin !== window.location.origin) {
    return;
  }
  var frame = document.getElementById(OAONES_RELOGIN_FRAME_ID);
  if (event.source && frame && event.source !== frame.contentWindow) {
    // 同源页面里其它 iframe 发的消息：不是弹框里的登录页，不处理
    return;
  }
  var data = event.data;
  if (typeof data === "string") {
    try {
      data = JSON.parse(data);
    } catch (e) {
      return;
    }
  }
  if (!data || typeof data !== "object") {
    return;
  }
  if (data.oaonesRelogin === "ok") {
    oaonesReloginOk();
  } else if (data.oaonesRelogin === "height") {
    oaonesReloginLastHeight = Number(data.height) || 0;
    oaonesReloginSyncHeight();
  } else if (data.oaonesRelogin === "cas" && data.url) {
    // 平台要求先去 CAS / 千里眼认证：整页跳（不能困在 iframe 里）
    oaonesReloginClose();
    window.location.href = data.url;
  }
}

/** 超时统一入口：弹框（默认）或整页跳（名字与下面的调试对象 window.oaonesRelogin 区分开） */
function oaonesReloginRun() {
  if (oaonesReloginOnLoginPage()) {
    return;
  }
  if (OAONES_RELOGIN_MODE !== "popup" || !oaonesReloginIsTop()) {
    // 配置成整页跳 / 子框架里发起的请求：整页跳登录页并回到原来那一页
    oaonesReloginGoPage();
    return;
  }
  oaonesReloginOpen();
}

/**
 * 覆盖门户的重新登录弹框入口：门户脚本（/spa/portal/public/index.js）后面会执行
 * `window.doLoginPop = function(){...}`，这里用带 setter 的属性把它接管成我们的实现
 * （普通赋值不会覆盖本实现，也不会因为只有 getter 而报错），平台其它判断逻辑一律不动。
 */
(function oaonesReloginInstallDoLoginPop() {
  function show() {
    oaonesReloginRun();
  }

  function install() {
    var descriptor = null;
    try {
      descriptor = Object.getOwnPropertyDescriptor(window, "doLoginPop");
    } catch (e) {
      descriptor = null;
    }
    if (descriptor && descriptor.get === show) {
      return;
    }
    try {
      Object.defineProperty(window, "doLoginPop", {
        configurable: true,
        enumerable: true,
        get: function () {
          return show;
        },
        set: function () {
          /* 门户自带的实现由本实现接管：吞掉赋值 */
        }
      });
    } catch (e) {
      // 兜底（不支持 defineProperty 的老浏览器）：直接覆盖，并在门户赋值后再压几次
      window.doLoginPop = show;
      [0, 200, 1000, 3000].forEach(function (delay) {
        window.setTimeout(function () {
          if (window.doLoginPop !== show) {
            window.doLoginPop = show;
          }
        }, delay);
      });
    }
  }

  if (window.addEventListener) {
    window.addEventListener("message", oaonesReloginOnMessage, false);
    // 窗口缩放时按登录页上报过的高度重新收一次（弹框不能顶出可视区）
    window.addEventListener("resize", function () {
      if (document.getElementById(OAONES_RELOGIN_MASK_ID)) {
        oaonesReloginSyncHeight();
        oaonesReloginTellAvail();
      }
    }, false);
  } else if (window.attachEvent) {
    window.attachEvent("onmessage", oaonesReloginOnMessage);
  }

  install();

  // 调试 / 验证入口（也便于以后手工弹一次）
  window.oaonesRelogin = {
    open: show,
    close: oaonesReloginClose,
    mode: OAONES_RELOGIN_MODE,
    url: function () {
      return oaonesReloginUrl(OAONES_RELOGIN_MODE === "popup");
    }
  };
})();

/**
 * 后台把“启用超时登出弹窗功能”（RemindSettings#timeOutSwitch）关掉时，平台的处理是
 * “提示一句 + 整页跳 /wui/index.html”（平台默认登录页）：这里把落点换成本模块统一登录页
 * （带 gopage 回到当前页），并把 errorCode 摘掉，避免平台再跳一次。
 * 开关打开时（默认，也是现场现在的状态）不动任何数据，照常走上面的重新登录弹框。
 */
ecodeSDK.rewriteApiDataQueueSet({
  fn: function fn(url, params, data) {
    if (!data || !window.ecCom || window.e9TimeOutSwitch !== true) {
      return data;
    }
    var code = data.errorCode === undefined || data.errorCode === null ? "" : String(data.errorCode);
    if (code !== "001" && code !== "002" && code !== "003" && code !== "004") {
      return data;
    }
    if (window.localStorage.loginBySSO === "true") {
      // 平台认证（CAS / 单点）那条口径交给上面的钩子处理
      return data;
    }
    data.errorCode = "";
    oaonesReloginGoPage();
    return data;
  },
  desc: 'OAones：超时整页跳本模块登录页（后台关掉超时弹窗开关时）'
});
