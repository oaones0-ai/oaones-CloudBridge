/**
 * gopage 跳转地址校验自检（JDK 8 Nashorn）。
 *
 * 先编译改动后的 OaonesSso，再执行：
 * jjs -cp "<编译输出>:classbean:WEB-INF/classes:WEB-INF/lib/*" \
 *     oaones/appsyn/tools/OaonesSsoRedirectSmokeTest.js
 */
(function () {
    "use strict";

    var Sso = Java.type("com.customization.oaones.appsyn.common.OaonesSso");
    var cases = [
        ["https://www.baidu.com", "https://www.baidu.com"],
        ["https://example.com/path?q=1#part", "https://example.com/path?q=1#part"],
        ["/wui/index.html#/main", "/wui/index.html#/main"],
        ["javascript:alert(1)", ""],
        ["data:text/html,test", ""],
        ["//www.baidu.com", ""],
        ["https://user:pass@www.baidu.com", ""]
    ];
    var failed = 0;
    cases.forEach(function (item) {
        var actual = String(Sso.safeRedirectUrl(item[0], ""));
        var ok = actual.equals(item[1]);
        print((ok ? "  ✓ " : "  ✗ ") + item[0] + " -> " + actual);
        if (!ok) {
            failed++;
        }
    });
    if (failed > 0) {
        throw new Error(failed + " 项未通过");
    }
    print("\n全部通过 ✓");
})();
