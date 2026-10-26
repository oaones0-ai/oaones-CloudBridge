package com.customization.oaones.appsyn.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.api.personalwx.PersonalWxApi;
import com.customization.oaones.appsyn.common.OaonesAdmin;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesModeRight;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesScanToken;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.common.QrLoginPlatform;
import com.customization.oaones.appsyn.common.ScanLoginState;
import com.customization.oaones.appsyn.dao.PersonalWxMapper;
import com.customization.oaones.appsyn.dao.wecom.ScanLoginMapper;
import com.customization.oaones.appsyn.dto.PersonalWxOaRow;
import com.customization.oaones.appsyn.entity.PersonalWxBind;
import com.customization.oaones.appsyn.entity.PersonalWxConfig;
import com.customization.oaones.appsyn.entity.wecom.ScanLogin;
import com.customization.oaones.appsyn.service.OaService;
import com.customization.oaones.appsyn.service.PersonalWxService;
import com.customization.oaones.appsyn.service.impl.OaServiceImpl;
import com.customization.oaones.appsyn.service.impl.wecom.LoginConfigServiceImpl;
import com.customization.oaones.appsyn.service.wecom.ScanLoginService;

import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人微信绑定实现（授权密钥表 + 绑定表，DAO 层为 {@link PersonalWxMapper}）。
 *
 * <p>与三个连接器（企微 / 钉钉 / 飞书）的关键差别：个人微信**没有“企业 / 租户”维度**。
 * 一套部署只有一份微信授权密钥（AppID + AppSecret），绑定关系按 OA 人员一对一存一条，
 * 因此本实现里看不到 cfg_id，也不做租户维护人分权，而是：</p>
 * <ul>
 *   <li><b>本人自助</b>（个人账号中心）：所有读写都限定在登录会话本人的那一行；</li>
 *   <li><b>管理员</b>（「微信设置」页）：系统管理员（user.id=1）与 `pw.admin.ids` 中的人员
 *       可维护授权密钥、可在绑定列表里为员工绑定 / 解绑；</li>
 *   <li><b>扫码回调</b>：没有登录会话（手机扫码/换设备打开），发起人身份取自带签名且未过期的
 *       state（{@link OaonesScanToken}，签名密钥就是微信 AppSecret），签名不符或过期一律拒绝。</li>
 * </ul>
 */
public class PersonalWxServiceImpl implements PersonalWxService {

    /**
     * state 里“谁发起的这次绑定”的标记（借用 OaonesScanToken 的 cfgId 字段——
     * 个人微信没有租户，这个字段正好空着不用）：
     * {@code S} = 本人自助绑定（bind_source=2 自行绑定）/ {@code A} = 管理员代为绑定（bind_source=0 手动绑定）。
     */
    private static final String MODE_ADMIN = "A";

    private String configTable() {
        return OaonesProps.personalWxConfigTable();
    }

    private String bindTable() {
        return OaonesProps.personalWxBindTable();
    }

    /* ------------------------------------------------------------------
     * 权限
     * ------------------------------------------------------------------ */

    @Override
    public boolean canMaintain(String operatorId) {
        String uid = OaonesUtil.s(operatorId).trim();
        if (uid.length() == 0) {
            return false;
        }
        if (OaonesAdmin.isTotalAdmin(uid)) {
            return true;
        }
        String admins = OaonesUtil.s(OaonesProps.pwAdminIds());
        String[] parts = admins.split(",");
        for (int i = 0; i < parts.length; i++) {
            if (uid.equals(parts[i].trim())) {
                return true;
            }
        }
        return false;
    }

    private void assertMaintain(String operatorId) {
        if (!canMaintain(operatorId)) {
            throw new RuntimeException("你不是「微信设置」的维护人，无权操作（仅系统管理员与 "
                    + "oaones.properties#pw.admin.ids 中列出的人员可维护）");
        }
    }

    @Override
    public boolean configured() {
        PersonalWxConfig cfg = readConfigRow();
        return cfg != null
                && OaonesUtil.s(cfg.getAppId()).trim().length() > 0
                && OaonesUtil.s(cfg.getAppSecret()).trim().length() > 0;
    }

    /* ------------------------------------------------------------------
     * 微信授权密钥
     * ------------------------------------------------------------------ */

    @Override
    public JSONObject getConfig(String operatorId, boolean showSetting) {
        assertMaintain(operatorId);
        return configJson(readConfigRow(), true, showSetting);
    }

    @Override
    public JSONObject saveConfig(JSONObject params, String operatorId, boolean showSetting) {
        assertMaintain(operatorId);
        String appId = OaonesUtil.s(params == null ? "" : params.getString("app_id")).trim();
        String appSecret = OaonesUtil.s(params == null ? "" : params.getString("app_secret")).trim();
        if (appId.length() == 0) {
            throw new RuntimeException("微信 AppID 不能为空（微信开放平台网站应用 / 公众号的 AppID）");
        }
        PersonalWxConfig row = readConfigRow();
        // AppSecret 只在填了新值时覆盖：页面留空表示“保持原密钥不变”，
        // 避免把密钥回显到页面上（回显密钥等于把密钥发给每一个能打开该页的人）
        if (appSecret.length() == 0) {
            if (row == null || OaonesUtil.s(row.getAppSecret()).trim().length() == 0) {
                throw new RuntimeException("微信 AppSecret 不能为空（首次配置必须填写；扫码授权换 openid 必需）");
            }
            appSecret = row.getAppSecret();
        }
        PersonalWxConfig entity = row == null ? new PersonalWxConfig() : row;
        entity.setAppId(appId);
        entity.setAppSecret(appSecret);
        entity.setRemark(OaonesUtil.s(params == null ? "" : params.getString("remark")));
        entity.setModedatamodifier(OaonesUtil.i(operatorId, 0));
        entity.setModedatamodifydatetime(OaonesUtil.now());
        if (row == null) {
            entity.setFormmodeid(OaonesProps.personalWxConfigModeId());
            entity.setModedatacreater(OaonesUtil.i(operatorId, 0));
            entity.setModedatacreatedate(OaonesUtil.nowDate());
            entity.setModedatacreatetime(OaonesUtil.nowTime());
            try {
                OaonesMyBatis.run(PersonalWxMapper.class, mapper -> mapper.insertConfig(configTable(), entity));
            } catch (Throwable t) {
                throw new RuntimeException(writeError("保存微信授权密钥", t));
            }
            PersonalWxConfig created = readConfigRow();
            if (created != null && created.getId() != null) {
                OaonesModeRight.shareNewData(configTable(), OaonesProps.personalWxConfigModeId(),
                        String.valueOf(created.getId()), operatorId);
            }
        } else {
            try {
                OaonesMyBatis.run(PersonalWxMapper.class, mapper -> mapper.updateConfig(configTable(), entity));
            } catch (Throwable t) {
                throw new RuntimeException(writeError("保存微信授权密钥", t));
            }
            OaonesModeRight.rebuildOnEdit(configTable(), OaonesProps.personalWxConfigModeId(),
                    String.valueOf(entity.getId()));
        }
        OaonesLog.step("保存个人微信授权密钥 operatorId=" + operatorId + " appId=" + appId
                + " showSetting=" + showSetting);
        return configJson(readConfigRow(), true, showSetting);
    }

    /** 读授权密钥行（表还没建 / 没数据时返回 null，调用方按“未配置”处理）。 */
    private PersonalWxConfig readConfigRow() {
        try {
            return OaonesMyBatis.call(PersonalWxMapper.class, mapper -> mapper.findConfig(configTable()));
        } catch (Throwable t) {
            OaonesLog.error("读取个人微信授权密钥失败（请确认已执行 sql/public/create_oaones_personal_wx.sql，"
                    + "且 oaones.properties 的 oaones_personal_wx_config_table 指向该表）", t);
            return null;
        }
    }

    /**
     * 授权密钥转 JSON：**不下发 AppSecret 明文**，只回“是否已配置”与掩码。
     *
     * <p>{@code showSetting=false}（页面地址没带 {@code ?showSetting=true}，只展示绑定列表）时
     * 只回 {@code configured} / {@code can_maintain} / {@code show_setting}，
     * **不带 AppID、密钥掩码、备注、修改人等设置参数**——这些只属于“微信设置”那一部分，
     * 列表页用不到，不下发就不会被别处顺手用上。</p>
     */
    private JSONObject configJson(PersonalWxConfig row, boolean canMaintain, boolean showSetting) {
        String appId = OaonesUtil.s(row == null ? "" : row.getAppId()).trim();
        String secret = OaonesUtil.s(row == null ? "" : row.getAppSecret()).trim();
        JSONObject json = new JSONObject();
        // 列表页也必须知道“密钥有没有配好”（没配好时扫码绑定会被拒绝），因此这一项始终下发
        json.put("configured", appId.length() > 0 && secret.length() > 0);
        json.put("can_maintain", canMaintain);
        json.put("show_setting", showSetting);
        if (!showSetting) {
            return json;
        }
        json.put("app_id", appId);
        json.put("app_secret_set", secret.length() > 0);
        json.put("app_secret_masked", maskSecret(secret));
        json.put("remark", OaonesUtil.s(row == null ? "" : row.getRemark()));
        json.put("update_time", OaonesUtil.s(row == null ? "" : row.getModedatamodifydatetime()));
        json.put("update_by", row == null || row.getModedatamodifier() == null
                ? "" : String.valueOf(row.getModedatamodifier()));
        return json;
    }

    /** 密钥掩码：只露前 4 位与后 4 位，避免页面 / 抓包直接看到完整密钥。 */
    private String maskSecret(String secret) {
        String v = OaonesUtil.s(secret);
        if (v.length() == 0) {
            return "";
        }
        if (v.length() <= 8) {
            return "****";
        }
        return v.substring(0, 4) + "****" + v.substring(v.length() - 4);
    }

    /* ------------------------------------------------------------------
     * 绑定列表（微信设置页：一行一个 OA 有效账号，含系统管理员账号、不含子账户）
     * ------------------------------------------------------------------ */

    @Override
    public JSONObject list(String keyword, String state, String operatorId) {
        assertMaintain(operatorId);
        PersonalWxConfig cfg = readConfigRow();
        Map<String, PersonalWxBind> byUser = new HashMap<String, PersonalWxBind>();
        List<PersonalWxBind> binds = listBindRows();
        for (int i = 0; binds != null && i < binds.size(); i++) {
            PersonalWxBind bind = binds.get(i);
            if (bind.getOaUserId() != null) {
                byUser.put(String.valueOf(bind.getOaUserId()), bind);
            }
        }
        String kw = OaonesUtil.s(keyword).trim();
        // 子账户（hrmresource.accounttype = 1）不在这里出：listOaUsers 已把子账户过滤掉，
        // 因此列表不显示子账户，下面的“已绑定 / 未绑定”统计（boundCount / unboundCount）
        // 与页面上从 items 汇总的“OA 有效账号合计”都**不含子账户**。
        List<PersonalWxOaRow> users = OaonesMyBatis.call(PersonalWxMapper.class,
                mapper -> mapper.listOaUsers(kw.length() == 0 ? "" : "%" + kw + "%"));
        String stateFilter = OaonesUtil.s(state).trim();
        JSONArray items = new JSONArray();
        int boundCount = 0;
        int unboundCount = 0;
        for (int i = 0; users != null && i < users.size(); i++) {
            PersonalWxOaRow user = users.get(i);
            String oaUserId = OaonesUtil.nullToEmpty(user.getId());
            PersonalWxBind bind = byUser.get(oaUserId);
            boolean bound = bind != null;
            if (bound) {
                boundCount++;
            } else {
                unboundCount++;
            }
            // 状态筛选：空 = 全部，"1" = 只看已绑定，"0" = 只看未绑定
            if ("1".equals(stateFilter) && !bound) {
                continue;
            }
            if ("0".equals(stateFilter) && bound) {
                continue;
            }
            items.add(itemJson(user, bind));
        }
        JSONObject data = new JSONObject();
        data.put("items", items);
        data.put("total", items.size());
        data.put("bound_count", boundCount);
        data.put("unbound_count", unboundCount);
        data.put("bind_total", binds == null ? 0 : binds.size());
        data.put("configured", cfg != null
                && OaonesUtil.s(cfg.getAppId()).trim().length() > 0
                && OaonesUtil.s(cfg.getAppSecret()).trim().length() > 0);
        data.put("state", stateFilter);
        data.put("keyword", kw);
        return data;
    }

    @Override
    public JSONObject orgOptions(String operatorId) {
        OaService oaService = new OaServiceImpl();
        JSONObject data = new JSONObject();
        // 分部 / 部门候选按登录人的 OA 分权范围过滤（OA 没开分权时=全量）
        data.put("oa_subcompanies", oaService.listSubCompaniesFromCache(operatorId));
        data.put("oa_departments", oaService.listDepartmentsFromCache(operatorId));
        return data;
    }

    @Override
    public JSONObject mine(String oaUserId) {
        int oaId = OaonesUtil.i(oaUserId, 0);
        if (oaId <= 0) {
            throw new RuntimeException("登录会话无效，无法读取个人微信绑定");
        }
        PersonalWxOaRow user = getOaUserRow(oaId);
        if (user == null) {
            throw new RuntimeException("你的 OA 账号不存在或已被删除（ID " + oaId + "），无法绑定个人微信");
        }
        PersonalWxConfig cfg = readConfigRow();
        PersonalWxBind bind = findBindByOaUser(oaId);
        JSONObject json = new JSONObject();
        json.put("oa_user_id", String.valueOf(oaId));
        json.put("name", OaonesUtil.nullToEmpty(user.getLastname()));
        json.put("loginid", OaonesUtil.nullToEmpty(user.getLoginid()));
        json.put("oa_dept_id", OaonesUtil.nullToEmpty(user.getDepartmentId()));
        json.put("oa_dept_name", OaonesUtil.nullToEmpty(user.getDepartmentName()));
        json.put("sys_admin", oaId == OaonesConst.SYS_ADMIN_USER_ID);
        // 子账户（hrmresource.accounttype = 1）不支持绑定个人微信：页面据此提示并禁用“扫码绑定”，
        // 真正发起绑定时 assertBindableOaUser 还会再拦一次（服务端才是最终口径）。
        boolean subAccount = isSubAccount(user);
        json.put("account_type", subAccount ? "1" : "0");
        json.put("sub_account", subAccount);
        json.put("configured", cfg != null
                && OaonesUtil.s(cfg.getAppId()).trim().length() > 0
                && OaonesUtil.s(cfg.getAppSecret()).trim().length() > 0);
        fillBind(json, bind);
        return json;
    }

    /** 一行绑定 / 未绑定（微信设置页的绑定列表用）。 */
    private JSONObject itemJson(PersonalWxOaRow user, PersonalWxBind bind) {
        String oaUserId = OaonesUtil.nullToEmpty(user.getId());
        JSONObject json = new JSONObject();
        json.put("oa_user_id", oaUserId);
        json.put("name", OaonesUtil.nullToEmpty(user.getLastname()));
        json.put("loginid", OaonesUtil.nullToEmpty(user.getLoginid()));
        json.put("workcode", OaonesUtil.nullToEmpty(user.getWorkcode()));
        json.put("mobile", OaonesUtil.nullToEmpty(user.getMobile()));
        json.put("oa_dept_id", OaonesUtil.nullToEmpty(user.getDepartmentId()));
        json.put("oa_dept_name", OaonesUtil.nullToEmpty(user.getDepartmentName()));
        json.put("oa_sub_id", OaonesUtil.nullToEmpty(user.getSubcompanyId()));
        json.put("oa_sub_name", OaonesUtil.nullToEmpty(user.getSubcompanyName()));
        json.put("sys_admin", user.getId() != null && user.getId().intValue() == OaonesConst.SYS_ADMIN_USER_ID);
        fillBind(json, bind);
        return json;
    }

    /** 绑定明细（未绑定时各字段为空、bound=false）。 */
    private void fillBind(JSONObject json, PersonalWxBind bind) {
        boolean bound = bind != null;
        int bindSource = bound && bind.getBindSource() != null ? bind.getBindSource() : 0;
        json.put("bound", bound);
        json.put("openid", bound ? OaonesUtil.nullToEmpty(bind.getOpenid()) : "");
        json.put("openid_masked", bound ? maskOpenid(bind.getOpenid()) : "");
        json.put("unionid", bound ? OaonesUtil.nullToEmpty(bind.getUnionid()) : "");
        json.put("nickname", bound ? OaonesUtil.nullToEmpty(bind.getNickname()) : "");
        json.put("headimgurl", bound ? OaonesUtil.nullToEmpty(bind.getHeadimgurl()) : "");
        json.put("bind_source", bound ? String.valueOf(bindSource) : "");
        json.put("bind_source_text", bound ? OaonesConst.bindSourceText(bindSource) : "");
        json.put("bind_time", bound ? OaonesUtil.nullToEmpty(bind.getBindTime()) : "");
        json.put("remark", bound ? OaonesUtil.nullToEmpty(bind.getRemark()) : "");
    }

    private List<PersonalWxBind> listBindRows() {
        try {
            return OaonesMyBatis.call(PersonalWxMapper.class, mapper -> mapper.listBinds(bindTable()));
        } catch (Throwable t) {
            OaonesLog.error("读取个人微信绑定列表失败（请确认已执行 sql/public/create_oaones_personal_wx.sql）", t);
            return null;
        }
    }

    private PersonalWxBind findBindByOaUser(int oaUserId) {
        try {
            return OaonesMyBatis.call(PersonalWxMapper.class,
                    mapper -> mapper.findByOaUser(bindTable(), oaUserId));
        } catch (Throwable t) {
            OaonesLog.error("读取个人微信绑定失败 oaUserId=" + oaUserId, t);
            return null;
        }
    }

    private PersonalWxBind findBindByOpenid(String openid) {
        String key = OaonesUtil.s(openid).trim();
        if (key.length() == 0) {
            return null;
        }
        try {
            return OaonesMyBatis.call(PersonalWxMapper.class,
                    mapper -> mapper.findByOpenid(bindTable(), key));
        } catch (Throwable t) {
            OaonesLog.error("按 openid 读取个人微信绑定失败", t);
            return null;
        }
    }

    private PersonalWxOaRow getOaUserRow(int oaUserId) {
        if (oaUserId <= 0) {
            return null;
        }
        return OaonesMyBatis.call(PersonalWxMapper.class, mapper -> mapper.getOaUser(oaUserId));
    }

    /* ------------------------------------------------------------------
     * 扫码绑定：生成二维码 / 处理回调
     * ------------------------------------------------------------------ */

    @Override
    public JSONObject qrStart(String operatorId, String targetOaUserId, String callbackBase) {
        int self = OaonesUtil.i(operatorId, 0);
        if (self <= 0) {
            throw new RuntimeException("登录会话无效，无法生成二维码");
        }
        int target = OaonesUtil.i(targetOaUserId, 0);
        // 指定了“别人”才是代绑（管理员能力）；空值或本人一律按本人自助口径处理
        boolean forOthers = target > 0 && target != self;
        if (forOthers) {
            assertMaintain(operatorId);
        } else {
            target = self;
        }
        PersonalWxOaRow user = assertBindableOaUser(target);
        PersonalWxConfig cfg = readConfigRow();
        String appId = OaonesUtil.s(cfg == null ? "" : cfg.getAppId()).trim();
        String appSecret = OaonesUtil.s(cfg == null ? "" : cfg.getAppSecret()).trim();
        if (appId.length() == 0 || appSecret.length() == 0) {
            throw new RuntimeException("尚未维护微信授权密钥（AppID / AppSecret），无法扫码绑定；"
                    + "请联系管理员在「微信设置」页补充");
        }
        // state：发起绑定的 OA 人员 + 绑定方式标记（S 本人 / A 管理员代绑）+ 有效期，用微信 AppSecret 签名
        String state = OaonesScanToken.issue(String.valueOf(target),
                forOthers ? MODE_ADMIN : "S", appSecret, OaonesProps.pwScanStateTtl());
        String callback = callbackUrl(callbackBase, forOthers);
        String extra = OaonesUtil.s(OaonesProps.pwOauthExtraParams()).trim();
        String qrUrl;
        try {
            qrUrl = OaonesProps.pwOauthBase() + OaonesProps.pwOauthPath()
                    + "?appid=" + OaonesUtil.urlEncode(appId)
                    + "&redirect_uri=" + OaonesUtil.urlEncode(callback)
                    + "&response_type=code"
                    + "&scope=" + OaonesUtil.urlEncode(OaonesProps.pwOauthScope())
                    + "&state=" + OaonesUtil.urlEncode(state)
                    + (extra.length() == 0 ? "" : "&" + extra)
                    // 微信开放平台 / 公众号的网页授权地址都要求以 #wechat_redirect 结尾
                    + "#wechat_redirect";
        } catch (Exception e) {
            throw new RuntimeException("生成微信扫码授权地址失败：" + e.getMessage());
        }
        OaonesLog.step("生成个人微信扫码绑定二维码 oaUserId=" + target
                + " asAdmin=" + forOthers + " operatorId=" + operatorId + " callback=" + callback);
        JSONObject data = new JSONObject();
        data.put("target_oa_user_id", String.valueOf(target));
        data.put("target_oa_user_name", OaonesUtil.nullToEmpty(user.getLastname()));
        data.put("as_admin", forOthers);
        data.put("app_id", appId);
        data.put("qr_url", qrUrl);
        data.put("state", state);
        data.put("callback_url", callback);
        data.put("expire_at", OaonesScanToken.expireAt(state));
        return data;
    }

    /* ------------------------------------------------------------------
     * 个人微信登录：复用统一登录票据，只把“换身份”的入口换成个人微信绑定表
     * ------------------------------------------------------------------ */

    @Override
    public JSONObject loginQrStart(String ticket, String callbackBase) {
        String scanTicket = OaonesUtil.s(ticket).trim();
        if (scanTicket.length() == 0) {
            throw new RuntimeException("登录二维码票据为空，请在电脑上刷新登录页后重试");
        }
        ScanLogin row = findScanTicket(scanTicket);
        if (row == null || row.getExpireTime() == null
                || row.getExpireTime() <= System.currentTimeMillis()) {
            throw new RuntimeException("登录二维码已失效，请在电脑上刷新后重试");
        }
        PersonalWxConfig cfg = readConfigRow();
        String appId = OaonesUtil.s(cfg == null ? "" : cfg.getAppId()).trim();
        String appSecret = OaonesUtil.s(cfg == null ? "" : cfg.getAppSecret()).trim();
        if (appId.length() == 0 || appSecret.length() == 0) {
            throw new RuntimeException("尚未维护微信授权密钥（AppID / AppSecret），个人微信登录不可用；"
                    + "请联系管理员在「微信设置」页补充");
        }
        String callback = loginCallbackUrl(callbackBase);
        String state = ScanLoginState.encode(scanTicket, QrLoginPlatform.PERSONAL_WX, "");
        String extra = OaonesUtil.s(OaonesProps.pwOauthExtraParams()).trim();
        String authUrl;
        try {
            authUrl = OaonesProps.pwOauthBase() + OaonesProps.pwOauthPath()
                    + "?appid=" + OaonesUtil.urlEncode(appId)
                    + "&redirect_uri=" + OaonesUtil.urlEncode(callback)
                    + "&response_type=code"
                    + "&scope=" + OaonesUtil.urlEncode(OaonesProps.pwOauthScope())
                    + "&state=" + OaonesUtil.urlEncode(state)
                    + (extra.length() == 0 ? "" : "&" + extra)
                    + "#wechat_redirect";
        } catch (Exception e) {
            throw new RuntimeException("生成个人微信登录授权地址失败：" + e.getMessage());
        }
        OaonesLog.step("生成个人微信登录二维码 ticket=" + scanTicket + " callback=" + callback);
        JSONObject data = new JSONObject();
        data.put("ticket", scanTicket);
        data.put("auth_url", authUrl);
        data.put("callback_url", callback);
        data.put("expire_at", row.getExpireTime());
        return data;
    }

    @Override
    public JSONObject loginScanStep(String ticket, String code, String baseUrl,
                                    String ip, String userAgent) {
        String scanTicket = OaonesUtil.s(ticket).trim();
        ScanLogin row = findScanTicket(scanTicket);
        if (row == null) {
            return loginMode("closed", "二维码不存在或已失效，请在电脑上刷新二维码后重新扫码");
        }
        int state = OaonesUtil.i(OaonesUtil.nullToEmpty(row.getState()),
                ScanLoginService.STATE_WAITING);
        if (state == ScanLoginService.STATE_USED) {
            return loginMode("closed", "本次扫码登录已经完成，请在电脑上继续操作");
        }
        if (state == ScanLoginService.STATE_EXPIRED) {
            return loginMode("closed", "二维码已过期，请在电脑上刷新后重新扫码");
        }
        if (state == ScanLoginService.STATE_FAILED) {
            if (row.getExpireTime() != null && row.getExpireTime() <= System.currentTimeMillis()) {
                markLoginExpired(scanTicket);
                return loginMode("closed", "二维码已过期，请在电脑上刷新后重新扫码");
            }
            OaonesMyBatis.run(ScanLoginMapper.class,
                    mapper -> mapper.resetToWaiting(OaonesProps.scanLoginTable(), scanTicket));
            state = ScanLoginService.STATE_WAITING;
        }
        if (state == ScanLoginService.STATE_CONFIRMED) {
            return loginMode("done", "已确认登录，请回到电脑上继续");
        }
        if (state == ScanLoginService.STATE_SCANNED) {
            return loginConfirmView(row, true);
        }
        if (row.getExpireTime() != null && row.getExpireTime() <= System.currentTimeMillis()) {
            markLoginExpired(scanTicket);
            return loginMode("closed", "二维码已过期，请在电脑上刷新后重新扫码");
        }
        String authCode = OaonesUtil.s(code).trim();
        if (authCode.length() == 0) {
            try {
                JSONObject oauth = loginQrStart(scanTicket, baseUrl);
                JSONObject data = loginMode("oauth", "");
                data.put("auth_url", OaonesUtil.s(oauth.getString("auth_url")));
                return data;
            } catch (Exception e) {
                return failLoginScan(scanTicket, e.getMessage(), ip, userAgent);
            }
        }
        try {
            return resolvePersonalWxLogin(row, authCode, ip, userAgent);
        } catch (Exception e) {
            String msg = e.getMessage() == null || e.getMessage().trim().length() == 0
                    ? "个人微信登录身份核实失败，请重新扫码" : e.getMessage();
            OaonesLog.error("个人微信扫码登录失败 ticket=" + scanTicket, e);
            return failLoginScan(scanTicket, msg, ip, userAgent);
        }
    }

    private JSONObject resolvePersonalWxLogin(ScanLogin row, String code,
                                              String ip, String userAgent) {
        PersonalWxConfig cfg = readConfigRow();
        String appId = OaonesUtil.s(cfg == null ? "" : cfg.getAppId()).trim();
        String appSecret = OaonesUtil.s(cfg == null ? "" : cfg.getAppSecret()).trim();
        if (appId.length() == 0 || appSecret.length() == 0) {
            throw new RuntimeException("微信授权密钥尚未维护（AppID / AppSecret），无法使用个人微信登录；"
                    + "请联系管理员在「微信设置」页补充");
        }
        JSONObject token;
        try {
            token = new PersonalWxApi(appId, appSecret).exchangeCode(code);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage() == null
                    ? "个人微信授权失败，请重新扫码" : e.getMessage());
        }
        String openid = OaonesUtil.s(token.getString("openid")).trim();
        if (openid.length() == 0) {
            throw new RuntimeException("微信未返回 openid，无法完成个人微信登录");
        }
        PersonalWxBind bind = findBindByOpenid(openid);
        if (bind == null || bind.getOaUserId() == null || bind.getOaUserId().intValue() <= 0) {
            throw new RuntimeException("这个个人微信还没有绑定 OA 账号，无法登录；"
                    + "请先登录 OA，在“个人账号中心 → 个人微信”用本人微信完成绑定，"
                    + "或联系管理员在「微信设置」页绑定后再试");
        }
        int oaUserId = bind.getOaUserId().intValue();
        PersonalWxOaRow oaUser = assertLoginablePersonalWx(oaUserId);
        String nickname = OaonesUtil.s(bind.getNickname()).trim();
        boolean needConfirm = OaonesProps.loginQrConfirm() == 1;
        ScanLogin entity = new ScanLogin();
        entity.setTicket(row.getTicket());
        entity.setPlatform(QrLoginPlatform.PERSONAL_WX);
        entity.setCfgId(null);
        entity.setOutUserId(maskOpenid(openid));
        entity.setOutUserName(nickname);
        entity.setOaUserId(Integer.valueOf(oaUserId));
        entity.setState(Integer.valueOf(needConfirm
                ? ScanLoginService.STATE_SCANNED : ScanLoginService.STATE_CONFIRMED));
        // 扫码成功后重新计算确认有效期：个人微信官方二维码的有效期与本系统票据不同，
        // 不能因为用户恰好在原票据倒计时末尾扫码，就让刚完成的身份核实立即失效。
        long originalExpireAt = row.getExpireTime() == null ? 0L : row.getExpireTime().longValue();
        long confirmExpireAt = System.currentTimeMillis()
                + new LoginConfigServiceImpl().qrTtlSeconds() * 1000L;
        entity.setExpireTime(Long.valueOf(Math.max(originalExpireAt, confirmExpireAt)));
        entity.setMsg("");
        entity.setScanIp(ip);
        entity.setScanUa(userAgent);
        entity.setModedatamodifier(Integer.valueOf(oaUserId));
        entity.setModedatamodifydatetime(OaonesUtil.now());
        OaonesMyBatis.run(ScanLoginMapper.class,
                mapper -> mapper.markScanned(OaonesProps.scanLoginTable(), entity));
        if (!needConfirm) {
            OaonesMyBatis.run(ScanLoginMapper.class, mapper ->
                    mapper.markConfirmed(OaonesProps.scanLoginTable(), row.getTicket(),
                            randomLoginToken(), System.currentTimeMillis()));
        }
        OaonesLog.step("个人微信扫码登录已识别身份 ticket=" + row.getTicket()
                + " oaUserId=" + oaUserId + " nickname=" + nickname
                + " needConfirm=" + needConfirm);
        row.setPlatform(QrLoginPlatform.PERSONAL_WX);
        row.setOutUserId(maskOpenid(openid));
        row.setOutUserName(nickname);
        row.setOaUserId(Integer.valueOf(oaUserId));
        row.setState(Integer.valueOf(needConfirm
                ? ScanLoginService.STATE_SCANNED : ScanLoginService.STATE_CONFIRMED));
        JSONObject data = loginConfirmView(row, needConfirm);
        data.put("need_confirm", Boolean.valueOf(needConfirm));
        data.put("mode", needConfirm ? "confirm" : "done");
        data.put("msg", needConfirm ? "" : "已确认登录，请回到电脑上继续");
        data.put("oa_user_name", OaonesUtil.s(oaUser.getLastname()));
        return data;
    }

    private PersonalWxOaRow assertLoginablePersonalWx(int oaUserId) {
        PersonalWxOaRow user = getOaUserRow(oaUserId);
        if (user == null) {
            throw new RuntimeException("绑定的 OA 账号不存在或已被删除，无法登录；请联系管理员核对绑定关系");
        }
        if (oaUserId == OaonesConst.SYS_ADMIN_USER_ID) {
            throw new RuntimeException("系统管理员账号不支持个人微信扫码登录，请使用账号密码登录");
        }
        if (isSubAccount(user)) {
            throw new RuntimeException("子账户（次账号）不支持个人微信登录，请使用该人员的主账户");
        }
        int status = user.getStatus() == null ? -1 : user.getStatus().intValue();
        if (status >= 4) {
            throw new RuntimeException("绑定的 OA 账号状态为「" + statusText(status)
                    + "」，不能扫码登录；如有疑问请联系管理员");
        }
        return user;
    }

    private JSONObject loginConfirmView(ScanLogin row, boolean needConfirm) {
        JSONObject data = new JSONObject();
        data.put("ticket", OaonesUtil.s(row.getTicket()));
        data.put("platform", QrLoginPlatform.PERSONAL_WX);
        data.put("platform_label", QrLoginPlatform.label(QrLoginPlatform.PERSONAL_WX));
        data.put("cfg_name", "");
        data.put("out_user_id", OaonesUtil.s(row.getOutUserId()));
        data.put("out_user_name", OaonesUtil.s(row.getOutUserName()));
        data.put("oa_user_id", OaonesUtil.nullToEmpty(row.getOaUserId()));
        data.put("oa_name", oaUserName(OaonesUtil.i(OaonesUtil.nullToEmpty(row.getOaUserId()), 0)));
        data.put("mode", needConfirm ? "confirm" : "done");
        return data;
    }

    private JSONObject failLoginScan(String ticket, String msg, String ip, String userAgent) {
        final String scanTicket = OaonesUtil.s(ticket).trim();
        String reason = OaonesUtil.s(msg).trim();
        if (reason.length() == 0) {
            reason = "个人微信登录失败，请在电脑上刷新二维码后重试";
        }
        final String failureReason = reason;
        try {
            OaonesMyBatis.run(ScanLoginMapper.class, mapper ->
                    mapper.markFailed(OaonesProps.scanLoginTable(), scanTicket, failureReason,
                            QrLoginPlatform.PERSONAL_WX, ip, userAgent));
        } catch (Throwable t) {
            OaonesLog.error("写入个人微信扫码登录失败状态失败 ticket=" + scanTicket, t);
        }
        return loginMode("failed", reason);
    }

    private void markLoginExpired(String ticket) {
        OaonesMyBatis.run(ScanLoginMapper.class,
                mapper -> mapper.markExpired(OaonesProps.scanLoginTable(), ticket));
    }

    private ScanLogin findScanTicket(String ticket) {
        String value = OaonesUtil.s(ticket).trim();
        if (value.length() == 0) {
            return null;
        }
        return OaonesMyBatis.call(ScanLoginMapper.class,
                mapper -> mapper.findByTicket(OaonesProps.scanLoginTable(), value));
    }

    private JSONObject loginMode(String mode, String msg) {
        JSONObject data = new JSONObject();
        data.put("mode", mode);
        data.put("msg", OaonesUtil.s(msg));
        return data;
    }

    private String loginCallbackUrl(String callbackBase) {
        String base = OaonesUtil.s(OaonesProps.pwCallbackBase()).trim();
        if (base.length() == 0) {
            base = OaonesUtil.s(callbackBase).trim();
        }
        if (base.length() == 0) {
            throw new RuntimeException("无法确定个人微信登录回调地址，请配置 pw.bind.callback.base");
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = OaonesUtil.s(OaonesProps.loginScanPath()).trim();
        if (path.length() == 0) {
            path = "/oaones/login/scan.jsp";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return base + path;
    }

    private String randomLoginToken() {
        return java.util.UUID.randomUUID().toString().replace("-", "")
                + java.util.UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public JSONObject scanConfirm(String code, String state) {
        String authCode = OaonesUtil.s(code).trim();
        if (authCode.length() == 0) {
            throw new RuntimeException("扫码回调缺少 code 参数，无法完成绑定");
        }
        state = normalizeState(state);
        JSONObject payload = OaonesScanToken.read(state);
        if (payload == null) {
            throw new RuntimeException("扫码状态无效，请回到页面重新点击“绑定微信”");
        }
        int targetOaUserId = OaonesUtil.i(OaonesUtil.s(payload.getString("oa_user_id")), 0);
        boolean asAdmin = MODE_ADMIN.equalsIgnoreCase(OaonesUtil.s(payload.getString("cfg_id")));
        PersonalWxConfig cfg = readConfigRow();
        String appId = OaonesUtil.s(cfg == null ? "" : cfg.getAppId()).trim();
        String appSecret = OaonesUtil.s(cfg == null ? "" : cfg.getAppSecret()).trim();
        if (appId.length() == 0 || appSecret.length() == 0) {
            throw new RuntimeException("微信授权密钥尚未维护（AppID / AppSecret），无法完成绑定；"
                    + "请联系管理员在「微信设置」页补充");
        }
        if (!OaonesScanToken.verify(state, appSecret)) {
            // 区分“已过期”与“签名不符”，便于排查（state 被截断、回调地址被改写、换了密钥等）
            long expireAt = OaonesScanToken.expireAt(state);
            long now = System.currentTimeMillis();
            OaonesLog.step("个人微信扫码 state 校验未通过 oaUserId=" + targetOaUserId
                    + " expireAt=" + expireAt + " now=" + now
                    + (expireAt > 0 && expireAt <= now ? "（已过期）" : "（签名不符）"));
            throw new RuntimeException(expireAt > 0 && expireAt <= now
                    ? "扫码状态已过期（超过有效期），请回到页面重新点击“绑定微信”"
                    : "扫码状态校验未通过（回调带回的 state 与本次生成的不一致），"
                        + "请回到页面重新点击“绑定微信”");
        }
        PersonalWxOaRow user = assertBindableOaUser(targetOaUserId);
        PersonalWxApi api = new PersonalWxApi(appId, appSecret);
        JSONObject token;
        try {
            token = api.exchangeCode(authCode);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage() == null ? "微信授权失败，请重新扫码" : e.getMessage());
        }
        String openid = OaonesUtil.s(token.getString("openid")).trim();
        String unionid = OaonesUtil.s(token.getString("unionid")).trim();
        String accessToken = OaonesUtil.s(token.getString("access_token")).trim();
        String nickname = "";
        String headimgurl = "";
        try {
            JSONObject info = api.getUserInfo(accessToken, openid);
            nickname = OaonesUtil.s(info.getString("nickname")).trim();
            headimgurl = OaonesUtil.s(info.getString("headimgurl")).trim();
            if (unionid.length() == 0) {
                unionid = OaonesUtil.s(info.getString("unionid")).trim();
            }
        } catch (Exception e) {
            // 拿不到昵称/头像不阻断绑定：openid 已经拿到，绑定关系本身是有效的
            OaonesLog.error("读取个人微信用户信息失败（按只有 openid 继续绑定）openid=" + openid, e);
        }
        // 一个微信号只能绑一个 OA 账号：同一微信号已绑到别人时直接拒绝，避免“一人多绑 / 一绑多人”两头不一致
        PersonalWxBind other = findBindByOpenid(openid);
        if (other != null && other.getOaUserId() != null && other.getOaUserId().intValue() != targetOaUserId) {
            String otherName = oaUserName(other.getOaUserId().intValue());
            throw new RuntimeException("该微信号已绑定到其它 OA 账号"
                    + (otherName.length() == 0 ? "" : "「" + otherName + "」")
                    + "（一个微信号只能绑定一个 OA 账号）；请先用那个账号在“个人账号中心 → 个人微信”解绑，"
                    + "或联系管理员在「微信设置」页解绑后再试");
        }
        int bindSource = asAdmin ? OaonesConst.BIND_SOURCE_MANUAL : OaonesConst.BIND_SOURCE_SELF;
        String remark = asAdmin ? OaonesConst.PERSONAL_WX_BIND_REMARK_ADMIN
                : OaonesConst.PERSONAL_WX_BIND_REMARK_SELF;
        PersonalWxBind existing = findBindByOaUser(targetOaUserId);
        PersonalWxBind entity = existing == null ? new PersonalWxBind() : existing;
        entity.setOaUserId(targetOaUserId);
        entity.setOpenid(openid);
        entity.setUnionid(unionid);
        entity.setNickname(nickname);
        entity.setHeadimgurl(headimgurl);
        entity.setBindSource(bindSource);
        entity.setBindTime(OaonesUtil.now());
        entity.setRemark(remark);
        if (existing == null) {
            entity.setFormmodeid(OaonesProps.personalWxBindModeId());
            entity.setModedatacreater(targetOaUserId);
            entity.setModedatacreatedate(OaonesUtil.nowDate());
            entity.setModedatacreatetime(OaonesUtil.nowTime());
            entity.setModedatamodifier(targetOaUserId);
            entity.setModedatamodifydatetime(OaonesUtil.now());
            try {
                OaonesMyBatis.run(PersonalWxMapper.class, mapper -> mapper.insertBind(bindTable(), entity));
            } catch (Throwable t) {
                throw new RuntimeException(writeError("保存个人微信绑定", t));
            }
            PersonalWxBind created = findBindByOaUser(targetOaUserId);
            if (created != null && created.getId() != null) {
                // 绑定记录归属该 OA 人员本人（建模里“谁的数据”按创建人判定），
                // 管理员代绑时同样把数据落到被绑人身上，便于本人在个人账号中心看到
                OaonesModeRight.shareNewData(bindTable(), OaonesProps.personalWxBindModeId(),
                        String.valueOf(created.getId()), String.valueOf(targetOaUserId));
            }
        } else {
            entity.setModedatamodifier(targetOaUserId);
            entity.setModedatamodifydatetime(OaonesUtil.now());
            try {
                OaonesMyBatis.run(PersonalWxMapper.class, mapper -> mapper.updateBind(bindTable(), entity));
            } catch (Throwable t) {
                throw new RuntimeException(writeError("保存个人微信绑定", t));
            }
            OaonesModeRight.rebuildOnEdit(bindTable(), OaonesProps.personalWxBindModeId(),
                    String.valueOf(entity.getId()));
        }
        OaonesLog.step("个人微信扫码绑定完成 oaUserId=" + targetOaUserId + " asAdmin=" + asAdmin
                + " openid=" + maskOpenid(openid));
        JSONObject data = new JSONObject();
        data.put("oa_user_id", String.valueOf(targetOaUserId));
        data.put("oa_user_name", OaonesUtil.nullToEmpty(user.getLastname()));
        data.put("openid", openid);
        data.put("openid_masked", maskOpenid(openid));
        data.put("nickname", nickname);
        data.put("headimgurl", headimgurl);
        data.put("bind_source", String.valueOf(bindSource));
        data.put("bind_source_text", OaonesConst.bindSourceText(bindSource));
        data.put("as_admin", asAdmin);
        return data;
    }

    @Override
    public JSONObject unbind(String operatorId, String targetOaUserId) {
        int self = OaonesUtil.i(operatorId, 0);
        if (self <= 0) {
            throw new RuntimeException("登录会话无效，无法解除绑定");
        }
        int target = OaonesUtil.i(targetOaUserId, 0);
        boolean forOthers = target > 0 && target != self;
        if (forOthers) {
            assertMaintain(operatorId);
        } else {
            target = self;
        }
        PersonalWxBind bind = findBindByOaUser(target);
        if (bind == null) {
            throw new RuntimeException(forOthers
                    ? "该 OA 账号当前没有绑定个人微信，可能已被解除"
                    : "你当前没有绑定个人微信");
        }
        final int targetOaId = target;
        try {
            OaonesMyBatis.run(PersonalWxMapper.class,
                    mapper -> mapper.deleteByOaUser(bindTable(), targetOaId));
        } catch (Throwable t) {
            throw new RuntimeException(writeError("解除个人微信绑定", t));
        }
        String name = oaUserName(target);
        OaonesLog.step("解除个人微信绑定 oaUserId=" + target + " operatorId=" + operatorId
                + " asAdmin=" + forOthers + " openid=" + maskOpenid(bind.getOpenid()));
        JSONObject data = new JSONObject();
        data.put("oa_user_id", String.valueOf(target));
        data.put("oa_user_name", name);
        data.put("nickname", OaonesUtil.nullToEmpty(bind.getNickname()));
        data.put("openid_masked", maskOpenid(bind.getOpenid()));
        data.put("as_admin", forOthers);
        return data;
    }

    /* ------------------------------------------------------------------
     * 公共校验与工具
     * ------------------------------------------------------------------ */

    /**
     * 绑定前的账号校验（本人自助与管理员代绑共用）。
     *
     * <p>与人员绑定页的区别只有一处：**系统管理员账号（user.id=1）可以绑定个人微信**
     * （需求要求“超管也可以绑定一个微信号”），因此这里不做 sysadmin 拦截；
     * 离职 / 解聘（status ≥ 4）与账号不存在仍然拒绝。</p>
     *
     * <p>**子账户（{@code hrmresource.accounttype = 1}）一律拒绝**：子账户不显示在绑定列表里、
     * 也不参与统计，绑定入口（本人自助 / 管理员代绑 / 扫描回调）都在这里统一拦下，
     * 提示改用其主账户绑定。列表侧的过滤在 {@code PersonalWxMapper#listOaUsers}。</p>
     */
    private PersonalWxOaRow assertBindableOaUser(int oaUserId) {
        PersonalWxOaRow row = getOaUserRow(oaUserId);
        if (row == null) {
            throw new RuntimeException("OA 账号不存在或已被删除（ID " + oaUserId + "），无法绑定个人微信");
        }
        if (isSubAccount(row)) {
            throw new RuntimeException("OA 账号「" + OaonesUtil.nullToEmpty(row.getLastname())
                    + "」是子账户（次账号），不参与个人微信绑定；请改用该人员的主账户绑定个人微信");
        }
        int status = row.getStatus() == null ? 0 : row.getStatus().intValue();
        if (status >= 4) {
            throw new RuntimeException("OA 账号「" + OaonesUtil.nullToEmpty(row.getLastname())
                    + "」状态为「" + statusText(status) + "」，不参与个人微信绑定；如有疑问请联系管理员");
        }
        return row;
    }

    /** 是否子账户（E9 标准 {@code hrmresource.accounttype}：0 / 空 = 主账户，1 = 子账户/次账号）。 */
    private boolean isSubAccount(PersonalWxOaRow row) {
        return row != null && row.getAccountType() != null && row.getAccountType().intValue() == 1;
    }

    /** OA 账号状态文本（hrmresource.status，E9 标准取值，与人员绑定页同口径）。 */
    private String statusText(int status) {
        switch (status) {
            case 0: return "正式";
            case 1: return "试用";
            case 2: return "临时";
            case 3: return "试用延期";
            case 4: return "解聘";
            case 5: return "离职";
            case 6: return "退休";
            case 7: return "返聘";
            default: return "其它（" + status + "）";
        }
    }

    private String oaUserName(int oaUserId) {
        PersonalWxOaRow row = getOaUserRow(oaUserId);
        return row == null ? "" : OaonesUtil.nullToEmpty(row.getLastname());
    }

    /**
     * 规范化回调带回的 state。
     *
     * <p>正常情况下 {@code request.getParameter} 已经解过一次 URL 编码；少数反向代理 / 网关
     * 会把 state 再编码一次，这里做一层兜底：只有当原值解析不出令牌、且值里确实含 {@code %} 时
     * 才再解一层（base64url 令牌不会出现 {@code %}，不会误伤正常值）。</p>
     */
    private String normalizeState(String state) {
        String value = OaonesUtil.s(state).trim();
        if (value.length() == 0 || OaonesScanToken.read(value) != null || value.indexOf('%') < 0) {
            return value;
        }
        try {
            String decoded = URLDecoder.decode(value, "UTF-8");
            if (OaonesScanToken.read(decoded) != null) {
                OaonesLog.step("个人微信扫码 state 被二次编码，已自动解码后再校验");
                return decoded;
            }
        } catch (Exception e) {
            OaonesLog.error("个人微信扫码 state 二次解码失败（按原值继续校验）", e);
        }
        return value;
    }

    /**
     * 回调地址：properties 配置优先（反向代理 / 多域名场景），未配置时按浏览器访问的域名拼。
     *
     * <p>末尾带一个 {@code from=admin|self} 参数，只用于回调页“绑定成功后往哪跳”的兜底
     * （管理员在「微信设置」页代绑时回微信设置，本人在个人账号中心绑定时回个人账号中心）；
     * 微信只校验 redirect_uri 的**域名**，附加查询参数不会影响授权。</p>
     */
    private String callbackUrl(String callbackBase, boolean asAdmin) {
        String base = OaonesUtil.s(OaonesProps.pwCallbackBase()).trim();
        if (base.length() == 0) {
            base = OaonesUtil.s(callbackBase).trim();
        }
        if (base.length() == 0) {
            throw new RuntimeException("无法确定扫码回调地址，请在 oaones.properties 配置 pw.bind.callback.base");
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = OaonesUtil.s(OaonesProps.pwCallbackPath()).trim();
        if (path.length() == 0) {
            path = "/oaones/personalWx/PersonalWxCallback.jsp";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return base + path + (asAdmin ? "?from=admin" : "?from=self");
    }

    /** openid 掩码：只露前 6 位与后 4 位（页面展示与日志都用它，避免把完整 openid 铺开）。 */
    private String maskOpenid(String openid) {
        String v = OaonesUtil.s(openid);
        if (v.length() == 0) {
            return "";
        }
        if (v.length() <= 10) {
            return v.substring(0, Math.min(3, v.length())) + "****";
        }
        return v.substring(0, 6) + "****" + v.substring(v.length() - 4);
    }

    /** 写入失败时的可执行提示：最常见的原因就是没跑建表脚本。 */
    private String writeError(String action, Throwable t) {
        OaonesLog.error(action + "失败", t);
        return action + "失败：请确认已执行 sql/public/create_oaones_personal_wx.sql 建好 "
                + "uf_oaones_personal_wx_config / uf_oaones_personal_wx_bind 两张表，"
                + "且 oaones.properties 的 oaones_personal_wx_config_table / "
                + "oaones_personal_wx_bind_table 指向它们"
                + (t.getMessage() == null ? "" : "（数据库返回：" + t.getMessage() + "）");
    }
}
