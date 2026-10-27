package com.customization.oaones.appsyn.service.impl.feishu;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.api.feishu.FeishuApi;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesParam;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesScanToken;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dao.HrmMapper;
import com.customization.oaones.appsyn.dao.feishu.UserBindMapper;
import com.customization.oaones.appsyn.dao.feishu.UserTmpMapper;
import com.customization.oaones.appsyn.dto.OaAccountRow;
import com.customization.oaones.appsyn.dto.feishu.UserBindRow;
import com.customization.oaones.appsyn.dto.feishu.WxUserRow;
import com.customization.oaones.appsyn.service.feishu.AccountService;
import com.customization.oaones.appsyn.service.feishu.ConfigService;
import com.customization.oaones.appsyn.service.feishu.UserBindService;
import com.customization.oaones.appsyn.service.feishu.UserTmpService;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.net.URLDecoder;

/**
 * 个人账号中心实现（本人自助）。
 *
 * <p>数据来源：OA 账号信息读 hrmresource（{@link HrmMapper#getResourceAccount(int)}）；
 * 绑定关系统一读人员绑定表（{@link UserBindMapper#listRowsByOaUser(Map)}）；
 * 飞书成员名称/部门读人员快照（{@link UserTmpMapper#findNormalDetail(Map)}）；
 * 绑定写入复用人员绑定服务，保证与“人员绑定”页同一套校验与建模权限处理。</p>
 */
public class AccountServiceImpl implements AccountService {

    /** 扫码绑定回调页（相对 OA 根路径）。 */
    private static final String SCAN_CALLBACK_PATH = "/oaones/appsyn/accountCenter/Feishu/FeishuCallback.jsp";

    private ConfigService configService() {
        return new ConfigServiceImpl();
    }

    private UserTmpService userTmpService() {
        return new UserTmpServiceImpl();
    }

    private UserBindService userBindService() {
        return new UserBindServiceImpl();
    }

    @Override
    public JSONObject info(String oaUserId) {
        int id = OaonesUtil.i(oaUserId, 0);
        if (id <= 0) {
            throw new RuntimeException("登录会话无效，无法读取账号信息");
        }
        OaonesLog.step("读取个人账号中心账号信息 oaUserId=" + id);
        OaAccountRow row = OaonesMyBatis.call(HrmMapper.class, mapper -> mapper.getResourceAccount(id));
        if (row == null) {
            throw new RuntimeException("OA 账号不存在或已被删除（ID " + id + "）");
        }
        int photoId = row.getResourceImageId() == null ? 0 : row.getResourceImageId();
        JSONObject json = new JSONObject();
        json.put("oa_user_id", OaonesUtil.nullToEmpty(row.getId()));
        json.put("name", OaonesUtil.nullToEmpty(row.getLastname()));
        json.put("loginid", OaonesUtil.nullToEmpty(row.getLoginid()));
        json.put("mobile", OaonesUtil.nullToEmpty(row.getMobile()));
        json.put("email", OaonesUtil.nullToEmpty(row.getEmail()));
        json.put("workcode", OaonesUtil.nullToEmpty(row.getWorkcode()));
        json.put("oa_dept_id", OaonesUtil.nullToEmpty(row.getDepartmentId()));
        json.put("oa_dept_name", OaonesUtil.nullToEmpty(row.getDepartmentName()));
        json.put("oa_sub_id", OaonesUtil.nullToEmpty(row.getSubcompanyId()));
        json.put("oa_sub_name", OaonesUtil.nullToEmpty(row.getSubcompanyName()));
        json.put("oa_status", OaonesUtil.nullToEmpty(row.getStatus()));
        json.put("oa_status_text", statusText(row.getStatus()));
        // 头像走 E9 标准文件下载地址；未设置头像时返回空串，由页面显示姓名占位
        json.put("avatar_url", photoId > 0
                ? "/weaver/weaver.file.FileDownload?fileid=" + photoId : "");
        json.put("sys_admin", id == OaonesConst.SYS_ADMIN_USER_ID);
        // 账户类型（E9 标准 hrmresource.accounttype）：0 主账号=主账户 / 1 次账号=子账户；
        // 子账户由 belongto 指向归属的主账户，主账户自身 belongto 为空。
        int selfAccountType = row.getAccountType() == null ? 0 : row.getAccountType();
        Integer selfBelongTo = row.getBelongTo() == null || row.getBelongTo() <= 0
                ? null : row.getBelongTo();
        json.put("account_type", String.valueOf(selfAccountType));
        json.put("account_type_text", accountTypeText(selfAccountType));
        json.put("belong_to", selfBelongTo == null ? "" : String.valueOf(selfBelongTo));
        // “我的账户”：本人是子账户时按其主账户取全（主账户 + 全部子账户），本人行 is_self=true
        json.put("accounts", accountFamily(selfAccountType == 1 && selfBelongTo != null
                ? selfBelongTo : id, id));
        return json;
    }

    @Override
    public JSONObject wxList(String oaUserId) throws Exception {
        int oaId = OaonesUtil.i(oaUserId, 0);
        if (oaId <= 0) {
            throw new RuntimeException("登录会话无效，无法读取绑定信息");
        }
        OaonesLog.step("读取个人账号中心飞书绑定 oaUserId=" + oaId);
        Map<String, Object> params = OaonesParam.of();
        params.put("oaUserId", oaId);
        List<UserBindRow> binds = OaonesMyBatis.call(UserBindMapper.class,
                mapper -> mapper.listRowsByOaUser(params));

        JSONArray tenants = new JSONArray();
        Set<String> listed = new HashSet<String>();
        int boundCount = 0;
        // 租户表是三个平台共用的一张表（type 区分企微 / 钉钉 / 飞书），而人员绑定表只按 cfg_id 存、
        // 不带平台维度：因此这里先记下**本平台的全部租户**（含停用的），第 2 步只认这些租户的绑定，
        // 否则会把本人**别的平台**的绑定当成“已停用的本平台租户”列到本页
        // （三个平台的个人账号中心各自独立，企微/钉钉/飞书的绑定不能串页）。
        JSONArray cfgs = configService().list();
        Set<String> platformCfgIds = new HashSet<String>();
        // 1) 启用中的租户：每行一个租户，未绑定的行作为“扫码绑定”入口
        for (int i = 0; i < cfgs.size(); i++) {
            JSONObject cfg = cfgs.getJSONObject(i);
            String cfgId = OaonesUtil.s(cfg.getString("id"));
            if (cfgId.length() == 0 || platformCfgIds.contains(cfgId)) {
                continue;
            }
            platformCfgIds.add(cfgId);
            if (OaonesUtil.i(cfg.getString("sync_enable"), 0) != 1) {
                continue;
            }
            listed.add(cfgId);
            JSONObject item = tenantItem(cfgId, OaonesUtil.s(cfg.getString("corp_name")), false);
            UserBindRow bind = findBind(binds, cfgId);
            if (bind != null) {
                boundCount++;
                fillBind(item, bind, cfg);
            }
            tenants.add(item);
        }
        // 2) 已绑定但租户已停用：仍然列出来（只可解绑），避免出现“页面上看不见的绑定”。
        //    不属于本平台的绑定直接跳过；租户配置在还有有效绑定时不允许删除
        //    （ConfigServiceImpl#delete 会拦截），所以不会再出现“租户记录已被删除”的绑定。
        for (int i = 0; binds != null && i < binds.size(); i++) {
            UserBindRow bind = binds.get(i);
            String cfgId = OaonesUtil.nullToEmpty(bind.getCfgId());
            if (cfgId.length() == 0 || listed.contains(cfgId)
                    || !platformCfgIds.contains(cfgId)) {
                continue;
            }
            listed.add(cfgId);
            String cfgName = OaonesUtil.s(bind.getCfgName());
            JSONObject item = tenantItem(cfgId, cfgName.length() > 0
                    ? cfgName + "（已停用）" : "已停用的飞书租户（ID " + cfgId + "）", true);
            boundCount++;
            fillBind(item, bind, null);
            tenants.add(item);
        }

        JSONObject data = new JSONObject();
        data.put("tenants", tenants);
        data.put("tenant_count", tenants.size());
        data.put("bound_count", boundCount);
        return data;
    }

    @Override
    public JSONObject qrStart(String cfgId, String oaUserId, String callbackBase) throws Exception {
        int oaId = OaonesUtil.i(oaUserId, 0);
        if (oaId <= 0) {
            throw new RuntimeException("登录会话无效，无法生成二维码");
        }
        JSONObject cfg = configService().get(cfgId);
        if (cfg == null) {
            throw new RuntimeException("飞书租户配置不存在，无法扫码绑定");
        }
        String cfgName = OaonesUtil.s(cfg.getString("corp_name"));
        if (OaonesUtil.i(cfg.getString("sync_enable"), 0) != 1) {
            throw new RuntimeException("飞书租户「" + cfgName + "」已停用，无法扫码绑定");
        }
        String corpId = OaonesUtil.s(cfg.getString("corp_id"));
        String corpSecret = OaonesUtil.s(cfg.getString("corp_secret"));
        if (corpId.length() == 0 || corpSecret.length() == 0) {
            throw new RuntimeException("飞书租户「" + cfgName
                    + "」未维护 App ID / App Secret（企业自建应用），无法扫码绑定，"
                    + "请联系管理员在“飞书租户配置”页补充");
        }
        String state = OaonesScanToken.issue(String.valueOf(oaId), cfgId, corpSecret,
                OaonesProps.accountScanStateTtl());
        String callback = callbackUrl(callbackBase);
        // 扫码绑定用的是飞书“登录/网页授权”地址：网关与路径都可配置（properties），换网关无需改代码
        String qrUrl = OaonesProps.fsLoginBase() + OaonesProps.fsLoginAuthPath()
                + "?app_id=" + OaonesUtil.urlEncode(corpId)
                + "&redirect_uri=" + OaonesUtil.urlEncode(callback)
                + "&state=" + OaonesUtil.urlEncode(state);
        OaonesLog.step("生成扫码绑定二维码 oaUserId=" + oaId + " cfgId=" + cfgId
                + " callback=" + callback);
        JSONObject data = new JSONObject();
        data.put("cfg_id", cfgId);
        data.put("cfg_name", cfgName);
        data.put("corp_id", corpId);
        data.put("qr_url", qrUrl);
        data.put("state", state);
        data.put("callback_url", callback);
        data.put("expire_at", OaonesScanToken.expireAt(state));
        return data;
    }

    @Override
    public JSONObject scanConfirm(String code, String state) throws Exception {
        String authCode = OaonesUtil.s(code).trim();
        if (authCode.length() == 0) {
            throw new RuntimeException("扫码回调缺少 code 参数，无法完成绑定");
        }
        state = normalizeState(state);
        JSONObject payload = OaonesScanToken.read(state);
        if (payload == null) {
            throw new RuntimeException("扫码状态无效，请回到“个人账号中心”重新点击“扫码绑定”");
        }
        String cfgId = OaonesUtil.s(payload.getString("cfg_id"));
        String oaUserId = OaonesUtil.s(payload.getString("oa_user_id"));
        JSONObject cfg = configService().get(cfgId);
        if (cfg == null) {
            throw new RuntimeException("飞书租户配置不存在，无法完成扫码绑定");
        }
        if (!OaonesScanToken.verify(state, OaonesUtil.s(cfg.getString("corp_secret")))) {
            // 区分“已过期”与“签名不符”，便于排查（例如 state 被截断、回调地址被改写）
            long expireAt = OaonesScanToken.expireAt(state);
            long now = System.currentTimeMillis();
            OaonesLog.step("扫码 state 校验未通过 cfgId=" + cfgId
                    + " expireAt=" + expireAt + " now=" + now
                    + (expireAt > 0 && expireAt <= now ? "（已过期）" : "（签名不符）"));
            throw new RuntimeException(expireAt > 0 && expireAt <= now
                    ? "扫码状态已过期（超过有效期），请回到“个人账号中心”重新点击“扫码绑定”"
                    : "扫码状态校验未通过（回调带回的 state 与本次生成的不一致），"
                        + "请回到“个人账号中心”重新点击“扫码绑定”");
        }
        String cfgName = OaonesUtil.s(cfg.getString("corp_name"));
        OaonesLog.step("确认扫码绑定 oaUserId=" + oaUserId + " cfgId=" + cfgId);
        assertBindableOaUser(oaUserId);
        FeishuApi api = configService().getClient(cfgId);
        JSONObject auth = api.getAuthUserInfo(authCode);
        String wxUserId = OaonesUtil.s(auth.getString("userid")).trim();
        if (wxUserId.length() == 0) {
            // 非本企业成员扫码时飞书只返回 openid，据此给出明确提示
            throw new RuntimeException("该微信账号不是飞书「" + cfgName
                    + "」的企业成员，无法绑定；请确认你已加入该企业后再扫码");
        }
        // 扫码人可能是刚加入企业的新成员，整租户快照里还没有他：
        // 先按 userid 精确拉取并写入快照，再做绑定校验与展示
        userTmpService().refreshMember(cfgId, wxUserId);
        assertNotNoBind(cfg, cfgId, oaUserId, wxUserId);

        JSONObject saveParams = new JSONObject();
        saveParams.put("cfg_id", cfgId);
        saveParams.put("oa_user_id", oaUserId);
        saveParams.put("wx_user_id", wxUserId);
        // 本人扫码绑定：绑定方式记为 2 自行绑定（页面显示“自行绑定”，更换/解绑会二次确认）
        saveParams.put("bind_source", OaonesConst.BIND_SOURCE_SELF);
        saveParams.put("remark", OaonesConst.SCAN_BIND_REMARK);
        // 复用人员绑定服务：飞书成员状态、两侧免绑定标记、飞书部门推导与建模权限处理口径一致
        JSONObject bind = userBindService().save(saveParams, oaUserId);

        JSONObject detail = memberDetail(cfg, wxUserId);
        JSONObject data = new JSONObject();
        data.put("cfg_id", cfgId);
        data.put("cfg_name", cfgName);
        data.put("oa_user_id", oaUserId);
        data.put("wx_user_id", wxUserId);
        data.put("wx_name", OaonesUtil.s(detail.getString("wx_name")));
        data.put("wx_dept_name", OaonesUtil.s(detail.getString("wx_dept_name")));
        data.put("bind_id", bind == null ? "" : OaonesUtil.nullToEmpty(bind.getString("id")));
        return data;
    }

    /**
     * 免绑定预校验：本人口径的提示语（人员绑定页那句“请先在 XX 页取消免绑定”面向管理员，
     * 放在本人自助页里会让员工看不懂该找谁）。
     */
    private void assertNotNoBind(JSONObject cfg, String cfgId, String oaUserId, String wxUserId)
            throws Exception {
        JSONArray oaNoBind = userBindService().listOaNoBind(cfgId);
        for (int i = 0; i < oaNoBind.size(); i++) {
            if (oaUserId.equals(OaonesUtil.s(oaNoBind.getString(i)))) {
                throw new RuntimeException("你的 OA 账号在飞书租户「" + OaonesUtil.s(cfg.getString("corp_name"))
                        + "」下已被标记为“免绑定”（不需要绑定飞书账号），无法扫码绑定；"
                        + "如需绑定请联系管理员在“人员绑定 → OA数据基准”页取消免绑定");
            }
        }
        String corpId = OaonesUtil.s(cfg.getString("corp_id"));
        if (userTmpService().isNoBind(corpId, wxUserId)) {
            throw new RuntimeException("该飞书成员已被标记为“免绑定”（不参与飞书绑定），无法绑定；"
                    + "如需绑定请联系管理员在“人员绑定 → 飞书数据基准”页取消免绑定");
        }
    }

    /**
     * 规范化回调带回的 state。
     *
     * <p>正常情况下 {@code request.getParameter} 已经解过一次 URL 编码；
     * 但少数反向代理 / 网关会把 state 再编码一次，这里做一层兜底：
     * 只有当原值解析不出令牌、且值里确实含 {@code %} 时才再解一层
     * （base64url 令牌不会出现 {@code %} 或 {@code +}，因此不会误伤正常值）。</p>
     */
    private String normalizeState(String state) {
        String value = OaonesUtil.s(state).trim();
        if (value.length() == 0 || OaonesScanToken.read(value) != null || value.indexOf('%') < 0) {
            return value;
        }
        try {
            String decoded = URLDecoder.decode(value, "UTF-8");
            if (OaonesScanToken.read(decoded) != null) {
                OaonesLog.step("扫码 state 被二次编码，已自动解码后再校验");
                return decoded;
            }
        } catch (Exception e) {
            OaonesLog.error("扫码 state 二次解码失败（按原值继续校验）", e);
        }
        return value;
    }

    /**
     * 扫码绑定前的本人口径校验：本人账号能被读取、且属于参与绑定的人员。
     *
     * <p>与人员绑定页一致（那里用 {@code OaService.getUser} 过滤）：系统管理员账号
     * （user.id=1）不参与人员绑定，离职/解聘等状态 4 及以上的账号同样不参与；
     * 这里提前给出本人口径的提示，避免用户看到“OA 人员不存在或已被删除”这类
     * 面向管理员页面的措辞。</p>
     */
    private void assertBindableOaUser(String oaUserId) {
        int oaId = OaonesUtil.i(oaUserId, 0);
        OaAccountRow row = OaonesMyBatis.call(HrmMapper.class, mapper -> mapper.getResourceAccount(oaId));
        if (row == null) {
            throw new RuntimeException("你的 OA 账号不存在或已被删除，无法绑定飞书账号");
        }
        if (oaId == OaonesConst.SYS_ADMIN_USER_ID) {
            throw new RuntimeException("系统管理员账号（user.id=1）不参与飞书人员绑定，"
                    + "请用员工账号登录“个人账号中心”后扫码绑定");
        }
        int status = OaonesUtil.i(OaonesUtil.nullToEmpty(row.getStatus()), -1);
        if (status >= 4) {
            throw new RuntimeException("你的 OA 账号状态为「" + statusText(row.getStatus())
                    + "」，不参与飞书人员绑定；如有疑问请联系管理员");
        }
    }

    @Override
    public JSONObject unbind(String cfgId, String oaUserId) {
        int oaId = OaonesUtil.i(oaUserId, 0);
        Integer cfgKey = OaonesUtil.integer(cfgId);
        if (oaId <= 0) {
            throw new RuntimeException("登录会话无效，无法解除绑定");
        }
        if (cfgKey == null || cfgKey <= 0) {
            throw new RuntimeException("缺少飞书租户，无法解除绑定");
        }
        OaonesLog.step("个人账号中心解除绑定 oaUserId=" + oaId + " cfgId=" + cfgKey);
        Integer bindId = OaonesMyBatis.call(UserBindMapper.class,
                mapper -> mapper.findActiveIdByOaUser(OaonesProps.userBindTable(), cfgKey, oaId));
        if (bindId == null) {
            throw new RuntimeException("该飞书租户下没有你的绑定记录，可能已被管理员解除");
        }
        // 只删除本人这一条绑定（飞书侧为多对一，其它 OA 人员的绑定不受影响）；
        // 免绑定行（bind_state=0）不会被 findActiveIdByOaUser（bind_state=1）取到，因此不会被误删。
        OaonesMyBatis.call(UserBindMapper.class,
                mapper -> mapper.deleteById(OaonesProps.userBindTable(), bindId));
        JSONObject data = new JSONObject();
        data.put("cfg_id", cfgId);
        data.put("bind_id", bindId);
        return data;
    }

    /**
     * 租户行骨架：未绑定时只带租户信息，已绑定时由 {@link #fillBind} 补齐绑定明细。
     *
     * <p>只输出租户ID与名称（不下发 App ID/Secret），个人账号中心用不到租户密钥。</p>
     */
    private JSONObject tenantItem(String cfgId, String cfgName, boolean disabled) {
        JSONObject item = new JSONObject();
        item.put("cfg_id", cfgId);
        item.put("cfg_name", cfgName);
        item.put("bound", false);
        item.put("disabled", disabled);
        item.put("wx_user_id", "");
        item.put("wx_name", "");
        item.put("wx_dept_id", "");
        item.put("wx_dept_name", "");
        item.put("bind_id", "");
        item.put("bind_source", "");
        item.put("bind_source_text", "");
        item.put("bind_time", "");
        item.put("last_sync_direction", "");
        item.put("last_sync_text", "");
        item.put("last_sync_time", "");
        item.put("remark", "");
        return item;
    }

    /** 把绑定记录（+飞书成员快照明细）写入租户行。 */
    private void fillBind(JSONObject item, UserBindRow bind, JSONObject cfg) {
        String wxUserId = OaonesUtil.s(bind.getWxUserId());
        item.put("bound", true);
        item.put("bind_id", OaonesUtil.nullToEmpty(bind.getId()));
        item.put("wx_user_id", wxUserId);
        item.put("bind_source", OaonesUtil.nullToEmpty(bind.getBindSource()));
        item.put("bind_source_text", OaonesConst.bindSourceText(
                OaonesUtil.i(OaonesUtil.nullToEmpty(bind.getBindSource()), 0)));
        item.put("bind_time", OaonesUtil.nullToEmpty(bind.getBindTime()));
        item.put("last_sync_direction", OaonesUtil.nullToEmpty(bind.getLastSyncDirection()));
        item.put("last_sync_time", OaonesUtil.nullToEmpty(bind.getLastSyncTime()));
        item.put("last_sync_text", syncText(OaonesUtil.nullToEmpty(bind.getLastSyncDirection()),
                OaonesUtil.nullToEmpty(bind.getLastSyncTime())));
        item.put("remark", OaonesUtil.nullToEmpty(bind.getRemark()));
        JSONObject detail = memberDetail(cfg, wxUserId);
        item.put("wx_name", OaonesUtil.s(detail.getString("wx_name")));
        item.put("wx_dept_id", OaonesUtil.s(detail.getString("wx_dept_id")));
        item.put("wx_dept_name", OaonesUtil.s(detail.getString("wx_dept_name")));
    }

    /** 飞书成员名称 / 部门名称（取人员快照，取不到时页面退化为只显示成员ID）。 */
    private JSONObject memberDetail(JSONObject cfg, String wxUserId) {
        JSONObject json = new JSONObject();
        json.put("wx_name", "");
        json.put("wx_dept_id", "");
        json.put("wx_dept_name", "");
        String corpId = cfg == null ? "" : OaonesUtil.s(cfg.getString("corp_id"));
        if (corpId.length() == 0 || OaonesUtil.s(wxUserId).length() == 0) {
            return json;
        }
        Map<String, Object> params = OaonesParam.of();
        params.put("corpid", corpId);
        params.put("wxUserId", OaonesUtil.s(wxUserId));
        params.put("type", OaonesConst.TYPE_FEISHU);
        params.put("status", OaonesConst.STATUS_NORMAL);
        WxUserRow row = OaonesMyBatis.call(UserTmpMapper.class,
                mapper -> mapper.findNormalDetail(params));
        if (row == null) {
            return json;
        }
        json.put("wx_name", OaonesUtil.nullToEmpty(row.getWxName()));
        json.put("wx_dept_id", OaonesUtil.nullToEmpty(row.getWxDeptId()));
        json.put("wx_dept_name", OaonesUtil.nullToEmpty(row.getWxDeptName()));
        return json;
    }

    private UserBindRow findBind(List<UserBindRow> binds, String cfgId) {
        for (int i = 0; binds != null && i < binds.size(); i++) {
            if (cfgId.equals(OaonesUtil.nullToEmpty(binds.get(i).getCfgId()))) {
                return binds.get(i);
            }
        }
        return null;
    }

    /** 回调地址：properties 配置优先（反向代理/多域名场景），未配置时按浏览器访问的域名拼。 */
    private String callbackUrl(String callbackBase) {
        String base = OaonesUtil.s(OaonesProps.fsAccountCallbackBase()).trim();
        if (base.length() == 0) {
            base = OaonesUtil.s(callbackBase).trim();
        }
        if (base.length() == 0) {
            throw new RuntimeException("无法确定扫码回调地址，请在 oaones.appsyn.properties 配置 dd.account.callback.base");
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + SCAN_CALLBACK_PATH;
    }

    /** 最近同步文案：方向 + 时间；从未同步过时给出提示。 */
    private String syncText(String direction, String syncTime) {
        if (syncTime == null || syncTime.length() == 0 || "-".equals(syncTime)) {
            return "尚未同步";
        }
        if ("0".equals(direction)) {
            return "OA → 飞书 " + syncTime;
        }
        if ("1".equals(direction)) {
            return "飞书 → OA " + syncTime;
        }
        return syncTime;
    }

    /**
     * OA 账号状态文本（hrmresource.status，E9 标准取值）。
     * 与人员绑定页一致：0-3 视为有效账号。
     */
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

    /** 账户类型文案：E9 标准 hrmresource.accounttype（0 主账号=主账户 / 1 次账号=子账户）。 */
    private static String accountTypeText(int accountType) {
        return accountType == 1 ? "子账户" : "主账户";
    }

    /**
     * “我的账户”列表：同一主账户下的主账户 + 全部子账户（本人行 is_self=true）。
     *
     * <p>本人是子账户时先按 belongto 找到归属的主账户ID再取全，页面既能标出
     * “本人是子账户”，也能看到归属的主账户与同一主账户下的其它子账户；
     * 主账户已被删除（belongto 指向不存在的人员）时退化为只返回本人一行。</p>
     */
    private JSONArray accountFamily(int mainId, int selfId) {
        List<OaAccountRow> rows = OaonesMyBatis.call(HrmMapper.class,
                mapper -> mapper.listAccountFamily(mainId));
        JSONArray list = new JSONArray();
        for (int i = 0; rows != null && i < rows.size(); i++) {
            OaAccountRow item = rows.get(i);
            int itemType = item.getAccountType() == null ? 0 : item.getAccountType();
            JSONObject json = new JSONObject();
            json.put("oa_user_id", OaonesUtil.nullToEmpty(item.getId()));
            json.put("name", OaonesUtil.nullToEmpty(item.getLastname()));
            json.put("loginid", OaonesUtil.nullToEmpty(item.getLoginid()));
            json.put("mobile", OaonesUtil.nullToEmpty(item.getMobile()));
            json.put("workcode", OaonesUtil.nullToEmpty(item.getWorkcode()));
            json.put("oa_dept_id", OaonesUtil.nullToEmpty(item.getDepartmentId()));
            json.put("oa_dept_name", OaonesUtil.nullToEmpty(item.getDepartmentName()));
            json.put("oa_status", OaonesUtil.nullToEmpty(item.getStatus()));
            json.put("oa_status_text", statusText(item.getStatus()));
            json.put("account_type", String.valueOf(itemType));
            json.put("account_type_text", accountTypeText(itemType));
            json.put("is_self", item.getId() != null && item.getId().intValue() == selfId);
            json.put("is_main", itemType != 1);
            list.add(json);
        }
        return list;
    }
}
