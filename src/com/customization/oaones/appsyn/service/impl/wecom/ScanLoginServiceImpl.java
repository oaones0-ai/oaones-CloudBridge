package com.customization.oaones.appsyn.service.impl.wecom;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.api.wecom.WeComApi;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesParam;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesSso;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.common.QrLoginPlatform;
import com.customization.oaones.appsyn.common.ScanLoginState;
import com.customization.oaones.appsyn.dao.HrmMapper;
import com.customization.oaones.appsyn.dao.wecom.ScanLoginMapper;
import com.customization.oaones.appsyn.dao.wecom.UserBindMapper;
import com.customization.oaones.appsyn.dao.wecom.UserTmpMapper;
import com.customization.oaones.appsyn.dto.OaAccountRow;
import com.customization.oaones.appsyn.dto.wecom.WxUserRow;
import com.customization.oaones.appsyn.entity.wecom.ScanLogin;
import com.customization.oaones.appsyn.service.wecom.ConfigService;
import com.customization.oaones.appsyn.service.wecom.LoginService;
import com.customization.oaones.appsyn.service.wecom.ScanLoginService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 扫码登录票据实现（自建二维码）。
 *
 * <p>一张二维码的完整链路：</p>
 * <ol>
 *   <li><b>PC 生成</b>：{@link #create} 建一条票据，二维码内容 = 本站扫码落地页 + ticket
 *       （{@code QrCodeImage} 在 {@code qrImage.jsp} 里把这段文本画成 PNG，
 *       不依赖企业微信托管的二维码页，因此同一张二维码能服务多个企业微信租户）；</li>
 *   <li><b>手机扫码</b>：{@link #scanStep} 按 UA 判断平台 → 依次用该平台已对接的租户配置
 *       走网页授权（企微 {@code scope=snsapi_base} 静默授权）→ 拿到成员身份后
 *       按人员绑定关系定位 OA 账号；不是本企业成员就自动试下一家（这就是“一个二维码覆盖多租户”的关键）；</li>
 *   <li><b>手机确认</b>：{@link #scanConfirm} 把票据推进到 2（已确认）并生成一次性登录令牌；</li>
 *   <li><b>PC 登录</b>：{@link #status} 轮询到 2 后由 PC 带令牌调 {@code login.qrLogin}，
 *       在 **PC 自己的请求**里写 E9 登录会话（保证 Cookie 落在 PC 浏览器），
 *       令牌消费是带条件的原子更新，用过即失效（不允许重放）。</li>
 * </ol>
 *
 * <p>安全口径：伪造/串用票据没有意义——票据随机且一次性、有有效期、与生成它的浏览器标识绑定；
 * 登录身份只能来自“平台 code 换回的成员 + 人员绑定关系”，页面无法指定要登录成谁；
 * 系统管理员账号（user.id=1）与状态 4 及以上的账号一律拒绝。</p>
 */
public class ScanLoginServiceImpl implements ScanLoginService {

    /** 票据随机串长度（32 位十六进制，二维码内容短、密度低）。 */
    private static final int TICKET_LENGTH = 32;
    /** 浏览器标识长度（与 ticket 同源生成）。 */
    private static final int BROWSER_KEY_LENGTH = 32;

    private String table() {
        return OaonesProps.scanLoginTable();
    }

    private LoginService loginService() {
        return new LoginServiceImpl();
    }

    private ConfigService configService() {
        return new ConfigServiceImpl();
    }

    /* ------------------------------------------------------------------
     * PC 侧：生成 / 轮询 / 换登录 / 作废
     * ------------------------------------------------------------------ */

    @Override
    public JSONObject create(String browserKey, String baseUrl, String landingUrl,
                             String ip, String userAgent) {
        long now = System.currentTimeMillis();
        cleanExpired(now);
        String key = OaonesUtil.s(browserKey).trim();
        if (key.length() < 16) {
            // 页面没给（或给得太短）时由服务端生成一个并回给页面，页面后续轮询必须带上它
            key = random(BROWSER_KEY_LENGTH);
        }
        String ticket = random(TICKET_LENGTH);
        String base = trimTrailingSlash(baseUrl);
        if (base.length() == 0) {
            throw new RuntimeException("无法确定本站访问地址（请检查访问地址或配置 `app.entry.base`），"
                    + "暂时无法生成扫码登录二维码");
        }
        String scanPath = OaonesProps.loginScanPath();
        String qrText = base + scanPath + (scanPath.indexOf('?') >= 0 ? "&" : "?") + "t=" + ticket;
        // 二维码有效期以“统一登录设置”为准（设置页按秒维护），没配时回退 properties 默认值
        long ttl = new LoginConfigServiceImpl().qrTtlSeconds() * 1000L;
        ScanLogin entity = new ScanLogin();
        entity.setTicket(ticket);
        entity.setBrowserKey(key);
        entity.setQrText(qrText);
        entity.setState(STATE_WAITING);
        entity.setExpireTime(now + ttl);
        entity.setLandingUrl(landingUrl(landingUrl));
        entity.setCreateIp(ip);
        entity.setCreateUa(userAgent);
        entity.setFormmodeid(OaonesProps.scanLoginModeId());
        entity.setModedatacreater(0);
        entity.setModedatacreatedate(OaonesUtil.nowDate());
        entity.setModedatacreatetime(OaonesUtil.nowTime());
        entity.setModedatamodifier(0);
        entity.setModedatamodifydatetime(OaonesUtil.now());
        OaonesMyBatis.run(ScanLoginMapper.class, mapper -> mapper.insert(table(), entity));
        OaonesLog.step("生成扫码登录二维码 ticket=" + ticket + " expireAt=" + entity.getExpireTime()
                + " ip=" + OaonesUtil.s(ip));
        JSONObject data = new JSONObject();
        data.put("ticket", ticket);
        data.put("browser_key", key);
        data.put("qr_text", qrText);
        // 二维码图片是统一登录页自己的接口（本站渲染的 SVG；旧的平台目录下只剩 302 跳转）
        data.put("qr_image_url", base + "/oaones/login/qrImage.jsp?t=" + ticket);
        data.put("expire_at", entity.getExpireTime());
        data.put("ttl_seconds", ttl / 1000);
        data.put("poll_ms", OaonesProps.loginQrPollMs());
        data.put("need_confirm", OaonesProps.loginQrConfirm() == 1);
        return data;
    }

    @Override
    public JSONObject status(String ticket, String browserKey) {
        ScanLogin row = requireRow(ticket, browserKey);
        int state = OaonesUtil.i(OaonesUtil.nullToEmpty(row.getState()), STATE_WAITING);
        long now = System.currentTimeMillis();
        if ((state == STATE_WAITING || state == STATE_SCANNED)
                && row.getExpireTime() != null && row.getExpireTime() <= now) {
            OaonesMyBatis.run(ScanLoginMapper.class, mapper -> mapper.markExpired(table(), row.getTicket()));
            state = STATE_EXPIRED;
        }
        JSONObject data = baseInfo(row);
        data.put("state", state);
        data.put("state_text", stateText(state));
        data.put("msg", state == STATE_EXPIRED
                ? "二维码已过期（超过有效期），请点“刷新二维码”重新扫码" : OaonesUtil.s(row.getMsg()));
        if (state == STATE_CONFIRMED) {
            data.put("login_token", OaonesUtil.s(row.getLoginToken()));
        }
        return data;
    }

    @Override
    public JSONObject take(String ticket, String browserKey, String loginToken) {
        ScanLogin row = requireRow(ticket, browserKey);
        String token = OaonesUtil.s(loginToken).trim();
        int state = OaonesUtil.i(OaonesUtil.nullToEmpty(row.getState()), STATE_WAITING);
        if (state != STATE_CONFIRMED) {
            throw new RuntimeException(state == STATE_USED
                    ? "本次扫码登录已经完成过，请刷新页面后重新登录"
                    : "扫码登录尚未确认（当前状态：" + stateText(state) + "），请在手机上点击“确认登录”");
        }
        if (token.length() == 0 || !token.equals(OaonesUtil.s(row.getLoginToken()))) {
            throw new RuntimeException("扫码登录令牌无效，请刷新二维码后重新扫码");
        }
        if (row.getExpireTime() != null && row.getExpireTime() <= System.currentTimeMillis()) {
            OaonesMyBatis.run(ScanLoginMapper.class, mapper -> mapper.markExpired(table(), row.getTicket()));
            throw new RuntimeException("二维码已过期（超过有效期），请点“刷新二维码”重新扫码");
        }
        // 条件更新消费令牌：并发或重放时只有一个请求能成功（state=2 且 login_token 相同的行才会被更新）
        int used = OaonesMyBatis.call(ScanLoginMapper.class,
                mapper -> mapper.markUsed(table(), row.getTicket(), token, System.currentTimeMillis()));
        if (used != 1) {
            throw new RuntimeException("扫码登录令牌已被使用，请刷新二维码后重新扫码");
        }
        JSONObject data = baseInfo(row);
        data.put("oa_user_id", String.valueOf(OaonesUtil.i(OaonesUtil.nullToEmpty(row.getOaUserId()), 0)));
        data.put("landing_url", landingUrl(row.getLandingUrl()));
        return data;
    }

    @Override
    public JSONObject cancel(String ticket, String browserKey) {
        ScanLogin row = findRow(ticket);
        if (row == null) {
            return new JSONObject();
        }
        if (OaonesUtil.s(browserKey).length() > 0
                && !OaonesUtil.s(browserKey).equals(OaonesUtil.s(row.getBrowserKey()))) {
            // 不是同一个浏览器发的取消请求：忽略（不让别人作废你屏幕上的二维码）
            return new JSONObject();
        }
        int state = OaonesUtil.i(OaonesUtil.nullToEmpty(row.getState()), STATE_WAITING);
        if (state == STATE_WAITING || state == STATE_SCANNED) {
            OaonesMyBatis.run(ScanLoginMapper.class, mapper -> mapper.markCanceled(table(), row.getTicket()));
        }
        return new JSONObject();
    }

    @Override
    public String qrText(String ticket) {
        ScanLogin row = findRow(ticket);
        return row == null ? "" : OaonesUtil.s(row.getQrText());
    }

    /* ------------------------------------------------------------------
     * 手机侧：平台识别 / 换取身份 → 待确认 → 确认
     * ------------------------------------------------------------------ */

    @Override
    public JSONObject scanStep(String ticket, String platformHint, String cfgIdHint, String code,
                               String baseUrl, String ip, String userAgent) throws Exception {
        ScanLogin row = findRow(ticket);
        if (row == null) {
            throw new RuntimeException("二维码不存在或已失效，请在电脑上刷新二维码后重新扫码");
        }
        String platform = OaonesUtil.s(QrLoginPlatform.detect(userAgent));
        if (platform.length() == 0) {
            platform = OaonesUtil.s(platformHint);
        }
        int state = OaonesUtil.i(OaonesUtil.nullToEmpty(row.getState()), STATE_WAITING);
        // 已经进入后续状态的票据：无论页面怎么刷新，都直接展示对应结果，不重复换身份
        if (state == STATE_USED) {
            return mode("closed", "本次扫码登录已经完成，请在电脑上继续操作（若没有反应，请刷新电脑上的登录页）");
        }
        if (state == STATE_EXPIRED) {
            return mode("closed", "二维码已过期（或已被刷新作废），请在电脑上点“刷新二维码”后重新扫码");
        }
        if (state == STATE_FAILED) {
            if (row.getExpireTime() != null && row.getExpireTime() <= System.currentTimeMillis()) {
                OaonesMyBatis.run(ScanLoginMapper.class, mapper -> mapper.markExpired(table(), row.getTicket()));
                return mode("closed", "二维码已过期（超过有效期），请在电脑上点“刷新二维码”后重新扫码");
            }
            // 上一次扫码失败（例如选错了企业）：只要二维码还没过期就允许用同一张二维码重试，
            // 把票据退回“待扫码”后继续往下识别，不必让用户回电脑上刷新二维码
            OaonesMyBatis.run(ScanLoginMapper.class, mapper -> mapper.resetToWaiting(table(), row.getTicket()));
            OaonesLog.step("扫码登录：同一张二维码重试 ticket=" + row.getTicket());
            state = STATE_WAITING;
        }
        if (state == STATE_CONFIRMED) {
            return mode("done", "已确认登录，请回到电脑上继续");
        }
        if (state == STATE_SCANNED) {
            return confirmView(row);
        }
        if (row.getExpireTime() != null && row.getExpireTime() <= System.currentTimeMillis()) {
            OaonesMyBatis.run(ScanLoginMapper.class, mapper -> mapper.markExpired(table(), row.getTicket()));
            return mode("closed", "二维码已过期（超过有效期），请在电脑上点“刷新二维码”后重新扫码");
        }
        String authCode = OaonesUtil.s(code).trim();
        if (authCode.length() == 0) {
            // 刚打开二维码里的链接：识别平台，再决定“直接授权”还是“先选企业”
            if (platform.length() == 0) {
                // 客户端名单只列“登录设置”里已开启扫码的平台（后台没启用飞书就不提飞书），
                // 与统一落地页 controller.LoginController#scanStep 同一口径
                String clients = QrLoginPlatform.enabledLabelText(new LoginConfigServiceImpl().publicSettings());
                return mode("unsupported", clients.length() == 0
                        ? "扫码登录已在“登录设置”里关闭，请在电脑上改用账号密码 / 手机号验证登录。"
                        : "请使用本人的" + clients + "扫一扫打开本页；"
                        + "普通相机、微信扫一扫无法完成登录。");
            }
            if (!QrLoginPlatform.supported(platform)) {
                // 统一落地页已经按客户端分派，正常不会把别家平台送到这里；这条只作兜底提示
                return mode("unsupported", "识别到的是 " + QrLoginPlatform.label(platform)
                        + " 客户端，而本实现只处理企业微信身份；请在电脑上刷新二维码后重新扫码。");
            }
        }
        if (platform.length() == 0 || !QrLoginPlatform.supported(platform)) {
            return mode("unsupported", "无法识别扫码客户端，请用本人的"
                    + QrLoginPlatform.enabledLabelText(new LoginConfigServiceImpl().publicSettings())
                    + "扫一扫重新扫码。");
        }
        JSONArray tenants = tenantsOf(platform);
        if (tenants.isEmpty()) {
            String msg = "当前没有可用的" + QrLoginPlatform.label(platform)
                    + "租户配置（未启用或未维护企业ID/Secret/AgentId），请联系管理员";
            fail(ticket, msg, platform, ip, userAgent);
            return mode("failed", msg);
        }
        String cfgId = usableCfgId(tenants, cfgIdHint);
        if (authCode.length() == 0) {
            if (cfgId.length() == 0) {
                // 配了多家企业、页面还没指定是哪家：让扫码人在手机上选一次
                // （不能拿同一个 code 逐家试探：跨企业授权在客户端会报错，code 也可能被消耗）
                return chooseView(platform, tenants, ticket);
            }
            // 决定了企业：跳该企业的网页授权（scope=snsapi_base 静默授权，用户无感）
            return oauthView(platform, tenants, cfgId, ticket, baseUrl);
        }
        if (cfgId.length() == 0) {
            if (tenants.size() > 1) {
                // 授权回来却没带上企业（state 被改过/丢过）：让扫码人重新选一次，不直接判失败
                return chooseView(platform, tenants, ticket);
            }
            String msg = "扫码登录没有指定企业（或该企业已停用），请回到电脑上刷新二维码后重新扫码";
            fail(ticket, msg, platform, ip, userAgent);
            return mode("failed", msg);
        }
        return resolveIdentity(row, platform, tenants, cfgId, authCode,
                baseUrl, ip, userAgent);
    }

    @Override
    public JSONObject scanConfirm(String ticket, String act) {
        ScanLogin row = findRow(ticket);
        if (row == null) {
            return mode("closed", "二维码不存在或已失效，请在电脑上刷新二维码后重新扫码");
        }
        int state = OaonesUtil.i(OaonesUtil.nullToEmpty(row.getState()), STATE_WAITING);
        if (state == STATE_USED) {
            return mode("closed", "本次扫码登录已经完成，请在电脑上继续操作");
        }
        if (state == STATE_EXPIRED) {
            return mode("closed", "二维码已过期（或已被刷新作废），请在电脑上刷新后重新扫码");
        }
        if (state == STATE_FAILED) {
            return mode("failed", OaonesUtil.s(row.getMsg()));
        }
        if (state == STATE_CONFIRMED) {
            return mode("done", "已确认登录，请回到电脑上继续");
        }
        if (state != STATE_SCANNED) {
            return mode("failed", "尚未完成" + QrLoginPlatform.label(OaonesUtil.s(row.getPlatform()))
                    + "身份核实，请重新扫码");
        }
        if ("cancel".equalsIgnoreCase(OaonesUtil.s(act))) {
            String msg = "本次扫码登录已在手机上取消，请在电脑上重新扫码";
            fail(row.getTicket(), msg, row.getPlatform(), row.getScanIp(), row.getScanUa());
            return mode("failed", msg);
        }
        String token = random(TICKET_LENGTH);
        OaonesMyBatis.run(ScanLoginMapper.class, mapper ->
                mapper.markConfirmed(table(), row.getTicket(), token, System.currentTimeMillis()));
        OaonesLog.step("扫码登录已确认 ticket=" + row.getTicket()
                + " oaUserId=" + OaonesUtil.nullToEmpty(row.getOaUserId())
                + " platform=" + OaonesUtil.s(row.getPlatform()));
        JSONObject data = confirmView(row);
        data.put("mode", "done");
        data.put("msg", "已确认登录，请回到电脑上继续");
        return data;
    }

    /**
     * 用平台授权 code 换扫码人身份，并按人员绑定关系定位 OA 账号。
     *
     * <p>“不是本企业成员 / code 不属于该企业”是**预期内**的情况：
     * 一张二维码要覆盖多家企业，所以此时自动切到下一家企业的授权地址继续探测，
     * 全部试完才判定失败——这就是“一个二维码覆盖多企业微信租户”的实现方式。</p>
     */
    private JSONObject resolveIdentity(ScanLogin row, String platform, JSONArray tenants,
                                       String cfgId, String code,
                                       String baseUrl, String ip, String userAgent) throws Exception {
        JSONObject tenant = tenantOf(tenants, cfgId);
        String cfgName = OaonesUtil.s(tenant.getString("cfg_name"));
        JSONObject cfg = configService().get(cfgId);
        String corpSecret = cfg == null ? "" : OaonesUtil.s(cfg.getString("corp_secret"));
        String wxUserId = "";
        String lastError = "";
        try {
            WeComApi api = new WeComApi(OaonesUtil.s(cfg == null ? "" : cfg.getString("corp_id")), corpSecret);
            JSONObject auth = api.getAuthUserInfo(code);
            wxUserId = OaonesUtil.s(auth.getString("userid")).trim();
        } catch (Exception e) {
            // code 属于别的企业时，企业微信会以 errcode 拒绝；按“不是这家企业”继续探测下一家
            lastError = OaonesUtil.s(e.getMessage());
            OaonesLog.step("扫码登录换取身份失败（按“不是该企业成员”继续探测下一家）cfgId=" + cfgId
                    + " cfgName=" + cfgName + " err=" + lastError);
        }
        if (wxUserId.length() == 0) {
            if (tenants.size() > 1) {
                // 选错了企业（或不是该企业成员）：不把票据判失败，直接让扫码人重选一次，
                // 避免“多企业场景下选错就得到电脑上刷新二维码”这种死路
                JSONObject choose = chooseView(platform, tenants, row.getTicket());
                choose.put("msg", "在「" + cfgName + "」没有找到你的成员身份，请重新选择你所在的企业：");
                choose.put("picked_cfg_id", cfgId);
                OaonesLog.step("扫码登录：所选企业未命中成员，回到选企业 ticket=" + row.getTicket()
                        + " cfgId=" + cfgId + " err=" + lastError);
                return choose;
            }
            String msg = "该" + QrLoginPlatform.label(platform) + "账号不在本系统对接的企业范围内"
                    + "（可能不是「" + cfgName + "」的成员，或不在该企业应用的可见范围内），无法登录 OA；"
                    + "请确认在手机上选择的企业是否正确，或联系管理员核对租户配置与人员绑定"
                    + (lastError.length() == 0 ? "" : "（企业微信返回：" + lastError + "）");
            fail(row.getTicket(), msg, platform, ip, userAgent);
            return mode("failed", msg);
        }
        Integer oaUserId = oaUserIdOfScanUser(platform, cfgId, wxUserId);
        if (oaUserId == null || oaUserId <= 0) {
            String msg = QrLoginPlatform.label(platform) + "成员「" + wxUserId + "」还没有绑定 OA 账号，"
                    + "无法扫码登录；请先在“个人账号中心”用本人" + QrLoginPlatform.label(platform)
                    + "扫码绑定 OA 账号（或让管理员在“人员绑定”页补绑）后重试";
            fail(row.getTicket(), msg, platform, ip, userAgent);
            return mode("failed", msg);
        }
        assertLoginableOaUser(oaUserId, platform);
        JSONObject detail = memberDetail(cfg, wxUserId);
        boolean needConfirm = OaonesProps.loginQrConfirm() == 1;
        ScanLogin entity = new ScanLogin();
        entity.setTicket(row.getTicket());
        entity.setPlatform(platform);
        entity.setCfgId(OaonesUtil.integer(cfgId));
        entity.setOutUserId(wxUserId);
        entity.setOutUserName(OaonesUtil.s(detail.getString("wx_name")));
        entity.setOaUserId(oaUserId);
        entity.setState(needConfirm ? STATE_SCANNED : STATE_CONFIRMED);
        // 二维码只负责“扫到”。身份已经核实成功后，从此刻起重新计算确认有效期，
        // 避免用户在最后几秒扫码、授权回跳较慢时立刻被判过期。
        entity.setExpireTime(scanConfirmedExpireAt(row));
        entity.setMsg("");
        entity.setScanIp(ip);
        entity.setScanUa(userAgent);
        entity.setModedatamodifier(oaUserId);
        entity.setModedatamodifydatetime(OaonesUtil.now());
        OaonesMyBatis.run(ScanLoginMapper.class, mapper -> mapper.markScanned(table(), entity));
        if (!needConfirm) {
            // 配置成“扫码即登录”（login.qr.confirm=0）时，扫码成功就直接签发一次性令牌
            OaonesMyBatis.run(ScanLoginMapper.class, mapper ->
                    mapper.markConfirmed(table(), row.getTicket(), random(TICKET_LENGTH),
                            System.currentTimeMillis()));
        }
        OaonesLog.step("扫码登录已识别身份 ticket=" + row.getTicket() + " platform=" + platform
                + " cfgId=" + cfgId + " cfgName=" + cfgName + " outUserId=" + wxUserId
                + " oaUserId=" + oaUserId + " needConfirm=" + needConfirm);
        JSONObject data = confirmView(row);
        data.put("cfg_name", cfgName);
        data.put("need_confirm", needConfirm);
        data.put("mode", needConfirm ? "confirm" : "done");
        data.put("msg", needConfirm ? "" : "已确认登录，请回到电脑上继续");
        return data;
    }

    /** 多家企业时的“选企业”视图：扫码人在手机上点一下自己所在的企业，再走该企业的网页授权。 */
    private JSONObject chooseView(String platform, JSONArray tenants, String ticket) {
        JSONObject data = mode("choose", "请选择你所在的企业：");
        data.put("platform", platform);
        data.put("platform_label", QrLoginPlatform.label(platform));
        data.put("ticket", ticket);
        data.put("tenants", tenants);
        return data;
    }

    /** 拼接某个租户的授权地址（扫码落地页只做 302 跳转，不渲染任何第三方内容）。 */
    private JSONObject oauthView(String platform, JSONArray tenants, String cfgId, String ticket,
                                 String baseUrl) throws Exception {
        JSONObject tenant = tenantOf(tenants, cfgId);
        if (tenant == null) {
            return mode("failed", "扫码登录指定的企业不存在或已停用，请在电脑上刷新二维码后重试");
        }
        String base = trimTrailingSlash(baseUrl);
        // 回调地址不带任何查询串：上下文（票据/平台/哪家企业）全部放在 state 里，
        // 避免企业微信在 redirect_uri 后面追加 code/state 时把我们的参数挤乱
        String callback = base + OaonesProps.loginScanPath();
        String state = scanState(ticket, platform, cfgId);
        String url = OaonesProps.appOauthBase() + OaonesProps.appOauthPath()
                + "?appid=" + OaonesUtil.urlEncode(OaonesUtil.s(tenant.getString("corp_id")))
                + "&agentid=" + OaonesUtil.urlEncode(OaonesUtil.s(tenant.getString("agent_id")))
                + "&redirect_uri=" + OaonesUtil.urlEncode(callback)
                + "&response_type=code&scope=snsapi_base"
                + "&state=" + OaonesUtil.urlEncode(state)
                + "#wechat_redirect";
        JSONObject data = mode("oauth", "");
        data.put("auth_url", url);
        data.put("cfg_id", cfgId);
        data.put("cfg_name", OaonesUtil.s(tenant.getString("cfg_name")));
        OaonesLog.step("扫码登录跳转" + QrLoginPlatform.label(platform) + "网页授权 ticket=" + ticket
                + " cfgId=" + cfgId);
        return data;
    }

    /** 页面指定/配置指定的租户ID：必须在“可用租户”列表里，否则返回空串（走选企业）。 */
    private String usableCfgId(JSONArray tenants, String cfgIdHint) {
        String want = OaonesUtil.s(cfgIdHint).trim();
        if (want.length() == 0) {
            // 页面上没选企业时：单租户直接用；多租户时取 properties 里配的主租户作为默认
            // （绝大多数员工在主租户，省掉一次选择；选错了下面会让他重选，不会卡死）
            if (tenants.size() == 1) {
                return OaonesUtil.nullToEmpty(tenants.getJSONObject(0).getString("cfg_id"));
            }
            String preferred = OaonesUtil.s(OaonesProps.loginQywxCfgId()).trim();
            if (preferred.length() > 0 && tenantOf(tenants, preferred) != null) {
                return preferred;
            }
            return "";
        }
        return tenantOf(tenants, want) == null ? "" : want;
    }

    /** 在可用租户列表里按ID取租户（取不到返回 null）。 */
    private JSONObject tenantOf(JSONArray tenants, String cfgId) {
        String want = OaonesUtil.s(cfgId).trim();
        if (want.length() == 0) {
            return null;
        }
        for (int i = 0; i < tenants.size(); i++) {
            JSONObject item = tenants.getJSONObject(i);
            if (want.equals(OaonesUtil.nullToEmpty(item.getString("cfg_id")))) {
                return item;
            }
        }
        return null;
    }

    /** 该平台可用于扫码登录的租户（启用中且 corp_id/agent_id/secret 齐备），顺序即探测顺序。 */
    private JSONArray tenantsOf(String platform) {
        if (!QrLoginPlatform.WECOM.equals(platform)) {
            // 钉钉：接入后在这里换成钉钉的租户配置来源（见 QrLoginPlatform#supported 的说明）
            return new JSONArray();
        }
        JSONArray all = loginService().tenants();
        JSONArray usable = new JSONArray();
        String preferred = OaonesUtil.s(OaonesProps.loginQywxCfgId()).trim();
        // properties 指定的租户优先探测（单租户部署时一次就中，不用多跳）
        for (int i = 0; i < all.size(); i++) {
            JSONObject item = all.getJSONObject(i);
            if (item.getBooleanValue("usable")
                    && preferred.length() > 0
                    && preferred.equals(OaonesUtil.nullToEmpty(item.getString("cfg_id")))) {
                usable.add(item);
            }
        }
        for (int i = 0; i < all.size(); i++) {
            JSONObject item = all.getJSONObject(i);
            if (item.getBooleanValue("usable")
                    && !(preferred.length() > 0
                        && preferred.equals(OaonesUtil.nullToEmpty(item.getString("cfg_id"))))) {
                usable.add(item);
            }
        }
        return usable;
    }

    /** 授权上下文（放在企业微信 state 里回传）：base64url(ticket|platform|cfgId)。 */
    private String scanState(String ticket, String platform, String cfgId) {
        return ScanLoginState.encode(ticket, platform, cfgId);
    }

    /** 解析企业微信回传的 state（解析不出时返回空 Map，由调用方用页面上的参数兜底）。 */
    public static JSONObject parseScanState(String state) {
        return ScanLoginState.parse(state);
    }

    /* ------------------------------------------------------------------
     * 公共小工具
     * ------------------------------------------------------------------ */

    private ScanLogin findRow(String ticket) {
        String value = OaonesUtil.s(ticket).trim();
        if (value.length() == 0) {
            return null;
        }
        return OaonesMyBatis.call(ScanLoginMapper.class, mapper -> mapper.findByTicket(table(), value));
    }

    /** 取票据并校验“是不是生成它的那个浏览器在问”（轮询/换登录都必须一致）。 */
    private ScanLogin requireRow(String ticket, String browserKey) {
        ScanLogin row = findRow(ticket);
        if (row == null) {
            throw new RuntimeException("二维码不存在或已失效，请点“刷新二维码”重新获取");
        }
        String key = OaonesUtil.s(browserKey).trim();
        if (key.length() == 0 || !key.equals(OaonesUtil.s(row.getBrowserKey()))) {
            throw new RuntimeException("二维码与当前浏览器不匹配（可能是把二维码地址复制到了别的浏览器），"
                    + "请点“刷新二维码”重新获取");
        }
        return row;
    }

    /** 手机侧失败落库：把原因写进票据，PC 轮询就能看到同一句话。 */
    private void fail(String ticket, String msg, String platform, String ip, String userAgent) {
        OaonesMyBatis.run(ScanLoginMapper.class, mapper ->
                mapper.markFailed(table(), ticket, msg, OaonesUtil.s(platform), ip, userAgent));
    }

    /** 清理过期票据（新建票据时顺手做，避免再挂一个计划任务）。 */
    private void cleanExpired(long now) {
        long keep = OaonesProps.loginQrKeepHours() * 3600L * 1000L;
        try {
            OaonesMyBatis.run(ScanLoginMapper.class,
                    mapper -> mapper.deleteExpired(table(), now - keep));
        } catch (Throwable t) {
            // 清理失败不影响生成二维码（表不存在等问题在插入时已经会报出来）
            OaonesLog.error("清理过期扫码票据失败（忽略）", t);
        }
    }

    /** 平台成员 → 已绑定的 OA 人员ID（多对一时取ID最小的一条，与免登录入口同口径）。 */
    private Integer oaUserIdOfScanUser(String platform, String cfgId, String outUserId) {
        if (!QrLoginPlatform.WECOM.equals(platform)) {
            return null;
        }
        Map<String, Object> params = OaonesParam.of();
        params.put("cfgId", OaonesUtil.integer(cfgId));
        params.put("wxUserId", outUserId);
        List<Integer> ids = OaonesMyBatis.call(UserBindMapper.class,
                mapper -> mapper.listBoundOaUserIdsByWxUser(params));
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        Integer min = null;
        for (Integer id : ids) {
            if (id == null || id <= 0) {
                continue;
            }
            if (min == null || id < min) {
                min = id;
            }
        }
        if (ids.size() > 1) {
            OaonesLog.step("扫码登录：平台成员绑定了多个 OA 账号（多对一），取ID最小的一条 cfgId=" + cfgId
                    + " outUserId=" + outUserId + " oaUserId=" + min + " count=" + ids.size());
        }
        return min;
    }

    /** 可登录账号校验：存在、不是系统管理员、状态 0-3（与免登录入口/扫码绑定同一口径）。 */
    private void assertLoginableOaUser(int oaUserId, String platform) {
        OaAccountRow row = OaonesMyBatis.call(HrmMapper.class, mapper -> mapper.getResourceAccount(oaUserId));
        if (row == null) {
            throw new RuntimeException("绑定的 OA 账号不存在或已被删除，无法扫码登录；请联系管理员核对绑定关系");
        }
        if (oaUserId == OaonesConst.SYS_ADMIN_USER_ID) {
            throw new RuntimeException("系统管理员账号（user.id=1）不支持" + QrLoginPlatform.label(platform)
                    + "扫码登录，请用员工账号扫码，或用系统管理员账号密码登录");
        }
        int status = OaonesUtil.i(OaonesUtil.nullToEmpty(row.getStatus()), -1);
        if (status >= 4) {
            throw new RuntimeException("绑定的 OA 账号状态为「" + statusText(row.getStatus())
                    + "」，不能扫码登录；如有疑问请联系管理员");
        }
    }

    /** 平台成员名称 / 部门名称（取人员快照，取不到时页面退化为只显示成员ID）。 */
    private JSONObject memberDetail(JSONObject cfg, String outUserId) {
        JSONObject json = new JSONObject();
        json.put("wx_name", "");
        json.put("wx_dept_id", "");
        json.put("wx_dept_name", "");
        String corpId = cfg == null ? "" : OaonesUtil.s(cfg.getString("corp_id"));
        if (corpId.length() == 0 || OaonesUtil.s(outUserId).length() == 0) {
            return json;
        }
        Map<String, Object> params = OaonesParam.of();
        params.put("corpid", corpId);
        params.put("wxUserId", OaonesUtil.s(outUserId));
        params.put("type", OaonesConst.TYPE_WECHAT);
        params.put("status", OaonesConst.STATUS_NORMAL);
        WxUserRow row = OaonesMyBatis.call(UserTmpMapper.class, mapper -> mapper.findNormalDetail(params));
        if (row == null) {
            return json;
        }
        json.put("wx_name", OaonesUtil.nullToEmpty(row.getWxName()));
        json.put("wx_dept_id", OaonesUtil.nullToEmpty(row.getWxDeptId()));
        json.put("wx_dept_name", OaonesUtil.nullToEmpty(row.getWxDeptName()));
        return json;
    }

    /** 票据基础信息（PC 轮询与手机确认页共用：谁、哪家企业、哪家平台）。 */
    private JSONObject baseInfo(ScanLogin row) {
        JSONObject data = new JSONObject();
        String platform = OaonesUtil.s(row.getPlatform());
        long expireAt = row.getExpireTime() == null ? 0L : row.getExpireTime().longValue();
        long expiresIn = expireAt <= 0 ? 0L
                : Math.max(0L, (expireAt - System.currentTimeMillis() + 999L) / 1000L);
        data.put("ticket", OaonesUtil.s(row.getTicket()));
        data.put("platform", platform);
        data.put("platform_label", platform.length() == 0 ? "" : QrLoginPlatform.label(platform));
        data.put("expire_at", row.getExpireTime());
        data.put("expires_in", Long.valueOf(expiresIn));
        data.put("cfg_id", OaonesUtil.nullToEmpty(row.getCfgId()));
        data.put("out_user_id", OaonesUtil.s(row.getOutUserId()));
        data.put("out_user_name", OaonesUtil.s(row.getOutUserName()));
        data.put("oa_user_id", OaonesUtil.nullToEmpty(row.getOaUserId()));
        data.put("oa_name", oaName(row.getOaUserId()));
        data.put("landing_url", OaonesUtil.s(row.getLandingUrl()));
        return data;
    }

    private long scanConfirmedExpireAt(ScanLogin row) {
        long original = row.getExpireTime() == null ? 0L : row.getExpireTime().longValue();
        long extended = System.currentTimeMillis()
                + new LoginConfigServiceImpl().qrTtlSeconds() * 1000L;
        return Math.max(original, extended);
    }

    /** 确认页展示：平台成员 + 企业名 + 即将登录的 OA 账号。 */
    private JSONObject confirmView(ScanLogin row) {
        JSONObject data = baseInfo(row);
        JSONObject cfg = row.getCfgId() == null ? null : configService().get(String.valueOf(row.getCfgId()));
        data.put("cfg_name", cfg == null ? "" : OaonesUtil.s(cfg.getString("corp_name")));
        data.put("mode", "confirm");
        return data;
    }

    private String oaName(Integer oaUserId) {
        if (oaUserId == null || oaUserId <= 0) {
            return "";
        }
        OaAccountRow row = OaonesMyBatis.call(HrmMapper.class, mapper -> mapper.getResourceAccount(oaUserId));
        return row == null ? "" : OaonesUtil.s(row.getLastname());
    }

    private JSONObject mode(String mode, String msg) {
        JSONObject data = new JSONObject();
        data.put("mode", mode);
        data.put("msg", OaonesUtil.s(msg));
        return data;
    }

    /** 状态文本（PC 页面提示用）。 */
    private String stateText(int state) {
        switch (state) {
            case STATE_WAITING: return "等待扫码";
            case STATE_SCANNED: return "已扫码，等待手机确认";
            case STATE_CONFIRMED: return "已确认，可以登录";
            case STATE_FAILED: return "扫码失败";
            case STATE_EXPIRED: return "已过期";
            case STATE_USED: return "已使用";
            default: return "未知状态";
        }
    }

    /** OA 人员状态文本（与人员绑定页、免登录入口同一口径）。 */
    private String statusText(String status) {
        int value = OaonesUtil.i(status, -1);
        switch (value) {
            case 0: return "正式";
            case 1: return "试用";
            case 2: return "临时";
            case 3: return "试用延期";
            case 4: return "解聘";
            case 5: return "离职";
            case 6: return "退休";
            case 7: return "返聘";
            default: return status == null || status.length() == 0 ? "未知" : "其它（" + status + "）";
        }
    }

    /** 落地地址：接受站内路径或 http(s) 网址（默认取配置项）。 */
    private String landingUrl(String url) {
        String target = OaonesSso.safeRedirectUrl(url, "");
        if (target.length() > 0) {
            return target;
        }
        String configured = OaonesUtil.s(OaonesProps.loginLandingUrl()).trim();
        if (configured.length() == 0) {
            configured = OaonesConst.DEFAULT_OA_HOME_URL;
        }
        String safe = OaonesSso.safeInternalUrl(configured, "");
        return safe.length() == 0 ? OaonesConst.DEFAULT_OA_HOME_URL : safe;
    }

    private String trimTrailingSlash(String url) {
        String value = OaonesUtil.s(url).trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String random(int length) {
        String uuid = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        return uuid.substring(0, Math.min(length, uuid.length()));
    }

}
