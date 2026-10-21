package com.customization.oaones.appsyn.service.impl.wecom;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.api.wecom.WeComApi;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesDevice;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesModeRight;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesParam;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesSso;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dao.HrmMapper;
import com.customization.oaones.appsyn.dao.wecom.AppBindMapper;
import com.customization.oaones.appsyn.dao.wecom.UserBindMapper;
import com.customization.oaones.appsyn.dto.OaAccountRow;
import com.customization.oaones.appsyn.dto.wecom.AppBindRow;
import com.customization.oaones.appsyn.entity.wecom.AppBind;
import com.customization.oaones.appsyn.service.wecom.AppBindService;
import com.customization.oaones.appsyn.service.wecom.ConfigService;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * 企微工作台应用绑定维护实现（DAO 层为 {@link AppBindMapper}）。
 *
 * <p>绑定状态 yx 口径：<b>0 有效 / 1 无效（停用）</b>，见 {@link OaonesConst#APP_BIND_STATE_VALID}。</p>
 */
public class AppBindServiceImpl implements AppBindService {

    /** AgentId 允许的字符（企业微信 AgentId 为数字串，这里放宽到字母/数字/下划线/中划线）。 */
    private static final String AGENT_ID_PATTERN = "[0-9A-Za-z_\\-]{1,64}";
    /** 站内地址最大长度（与建模表 oa_page_url / oa_page_url_mobile 的 nvarchar(500) 对齐）。 */
    private static final int URL_MAX_LENGTH = 500;

    private String table() {
        return OaonesProps.appBindTable();
    }

    private ConfigService configService() {
        return new ConfigServiceImpl();
    }

    @Override
    public JSONArray list(String cfgId, String keyword) {
        Map<String, Object> params = OaonesParam.of();
        params.put("cfgId", OaonesUtil.integer(cfgId));
        params.put("cfgIds", null);
        return listBy(params, keyword);
    }

    @Override
    public JSONArray listForCfgs(List<Integer> cfgIds, String keyword) {
        if (cfgIds == null || cfgIds.isEmpty()) {
            // 当前登录人没有任何可维护租户：不查库，直接返回空列表
            return new JSONArray();
        }
        Map<String, Object> params = OaonesParam.of();
        params.put("cfgId", null);
        params.put("cfgIds", new ArrayList<Integer>(new HashSet<Integer>(cfgIds)));
        return listBy(params, keyword);
    }

    private JSONArray listBy(Map<String, Object> params, String keyword) {
        OaonesLog.step("查询应用绑定列表 cfgId=" + params.get("cfgId")
                + " cfgIds=" + params.get("cfgIds") + " keyword=" + keyword);
        JSONArray result = new JSONArray();
        String k = keyword == null ? "" : keyword.trim();
        params.put("keyword", k.length() == 0 ? "" : "%" + k + "%");
        List<AppBindRow> rows = OaonesMyBatis.call(AppBindMapper.class, mapper -> mapper.listRows(params));
        if (rows == null) {
            return result;
        }
        for (AppBindRow row : rows) {
            result.add(toJson(row));
        }
        return result;
    }

    @Override
    public JSONObject get(String id) {
        Integer bindId = OaonesUtil.integer(id);
        if (bindId == null || bindId <= 0) {
            return null;
        }
        AppBind row = OaonesMyBatis.call(AppBindMapper.class, mapper -> mapper.findById(table(), bindId));
        return row == null ? null : toJson(row);
    }

    @Override
    public Integer cfgIdOf(String bindId) {
        Integer id = OaonesUtil.integer(bindId);
        if (id == null || id <= 0) {
            return null;
        }
        AppBind row = OaonesMyBatis.call(AppBindMapper.class, mapper -> mapper.findById(table(), id));
        return row == null ? null : row.getCfgId();
    }

    @Override
    public JSONObject save(JSONObject params, String operatorId) {
        String id = OaonesUtil.s(params.getString("id")).trim();
        String cfgId = OaonesUtil.s(params.getString("cfg_id")).trim();
        String agentId = OaonesUtil.s(params.getString("agent_id")).trim();
        String appSecret = OaonesUtil.s(params.getString("app_secret")).trim();
        String appName = OaonesUtil.s(params.getString("app_name")).trim();
        String oaPageUrl = OaonesUtil.s(params.getString("oa_page_url")).trim();
        String oaPageUrlMobile = OaonesUtil.s(params.getString("oa_page_url_mobile")).trim();
        String state = OaonesUtil.s(params.getString("bind_state")).trim();
        String remark = OaonesUtil.s(params.getString("remark"));
        OaonesLog.step("保存应用绑定 id=" + id + " cfgId=" + cfgId + " agentId=" + agentId
                + " oaPageUrl=" + oaPageUrl + " oaPageUrlMobile=" + oaPageUrlMobile);

        if (cfgId.length() == 0 || OaonesUtil.i(cfgId, 0) <= 0) {
            throw new RuntimeException("请选择该应用所属的企业微信租户");
        }
        JSONObject cfg = configService().get(cfgId);
        if (cfg == null) {
            throw new RuntimeException("企微租户配置不存在");
        }
        if (agentId.length() == 0) {
            throw new RuntimeException("请填写企业微信自建应用的 AgentId（在企微后台“应用管理 → 自建 → 应用详情”里查看）");
        }
        if (!agentId.matches(AGENT_ID_PATTERN)) {
            throw new RuntimeException("AgentId 只能填数字、字母、下划线或中划线，且不超过 64 个字符");
        }
        if (agentId.length() > 64) {
            throw new RuntimeException("AgentId 不能超过 64 个字符");
        }
        // OA 地址只允许站内路径：避免把“免登录后的落地页”配成站外地址
        String safePageUrl = checkInternalUrl(oaPageUrl, "PC端 OA 页面地址");
        String safePageUrlMobile = checkInternalUrl(oaPageUrlMobile, "手机端 OA 页面地址");
        if (safePageUrl.length() == 0) {
            throw new RuntimeException("请填写 PC端 OA 页面地址（必填）：从企业微信电脑版打开时进入的 OA 页面");
        }
        int cfgKey = OaonesUtil.i(cfgId, 0);
        int bindId = OaonesUtil.i(id, 0);
        // 同一租户下同一个 AgentId 只允许一条绑定
        Integer existId = OaonesMyBatis.call(AppBindMapper.class,
                mapper -> mapper.findIdByCfgAgent(table(), cfgKey, agentId));
        if (existId != null && existId != bindId) {
            throw new RuntimeException("该企微租户下 AgentId " + agentId
                    + " 已存在绑定（绑定ID " + existId + "），同一应用只能绑定一条；请直接编辑该条记录");
        }
        int yx = "1".equals(state) ? OaonesConst.APP_BIND_STATE_INVALID
                : OaonesConst.APP_BIND_STATE_VALID;
        String now = OaonesUtil.now();
        int modeId = OaonesProps.appBindModeId();
        if (bindId > 0) {
            AppBind exist = OaonesMyBatis.call(AppBindMapper.class, mapper -> mapper.findById(table(), bindId));
            if (exist == null) {
                throw new RuntimeException("应用绑定记录不存在或已删除");
            }
            AppBind entity = new AppBind();
            entity.setId(bindId);
            entity.setCfgId(cfgKey);
            entity.setAgentId(agentId);
            entity.setAppSecret(appSecret.length() > 0 ? appSecret : OaonesUtil.s(exist.getAppSecret()));
            entity.setAppName(appName);
            entity.setOaPageUrl(safePageUrl);
            entity.setOaPageUrlMobile(safePageUrlMobile);
            entity.setYx(yx);
            entity.setRemark(remark);
            entity.setModedatamodifier(OaonesUtil.i(operatorId, 0));
            entity.setModedatamodifydatetime(now);
            OaonesMyBatis.call(AppBindMapper.class, mapper -> {
                if (mapper.update(table(), entity) <= 0) {
                    throw new RuntimeException("应用绑定更新失败");
                }
                return 1;
            });
            OaonesModeRight.rebuildOnEdit(table(), modeId, String.valueOf(bindId));
            return get(String.valueOf(bindId));
        }
        AppBind entity = new AppBind();
        entity.setCfgId(cfgKey);
        entity.setAgentId(agentId);
        entity.setAppSecret(appSecret);
        entity.setAppName(appName);
        entity.setOaPageUrl(safePageUrl);
        entity.setOaPageUrlMobile(safePageUrlMobile);
        entity.setYx(yx);
        entity.setRemark(remark);
        entity.setFormmodeid(modeId);
        entity.setModedatacreater(OaonesUtil.i(operatorId, 0));
        entity.setModedatacreatedate(OaonesUtil.nowDate());
        entity.setModedatacreatetime(OaonesUtil.nowTime());
        entity.setModedatamodifier(OaonesUtil.i(operatorId, 0));
        entity.setModedatamodifydatetime(now);
        OaonesMyBatis.call(AppBindMapper.class, mapper -> {
            if (mapper.insert(table(), entity) <= 0) {
                throw new RuntimeException("应用绑定保存失败");
            }
            return 1;
        });
        Integer newId = OaonesMyBatis.call(AppBindMapper.class,
                mapper -> mapper.findIdByCfgAgent(table(), cfgKey, agentId));
        if (newId == null) {
            return null;
        }
        OaonesModeRight.shareNewData(table(), modeId, String.valueOf(newId), operatorId);
        return get(String.valueOf(newId));
    }

    @Override
    public JSONObject setState(String bindId, String state, String operatorId) {
        Integer id = OaonesUtil.integer(bindId);
        if (id == null || id <= 0) {
            throw new RuntimeException("缺少应用绑定记录ID");
        }
        AppBind exist = OaonesMyBatis.call(AppBindMapper.class, mapper -> mapper.findById(table(), id));
        if (exist == null) {
            throw new RuntimeException("应用绑定记录不存在或已删除");
        }
        int yx = "1".equals(OaonesUtil.s(state).trim())
                ? OaonesConst.APP_BIND_STATE_INVALID : OaonesConst.APP_BIND_STATE_VALID;
        OaonesLog.step("设置应用绑定状态 bindId=" + bindId + " yx=" + yx);
        OaonesMyBatis.run(AppBindMapper.class, mapper -> mapper.updateState(table(), id, yx,
                OaonesUtil.i(operatorId, 0), OaonesUtil.now()));
        OaonesModeRight.rebuildOnEdit(table(), OaonesProps.appBindModeId(), String.valueOf(id));
        return get(String.valueOf(id));
    }

    @Override
    public void delete(String bindId) {
        Integer id = OaonesUtil.integer(bindId);
        if (id == null || id <= 0) {
            throw new RuntimeException("缺少应用绑定记录ID");
        }
        AppBind exist = OaonesMyBatis.call(AppBindMapper.class, mapper -> mapper.findById(table(), id));
        if (exist == null) {
            throw new RuntimeException("应用绑定记录不存在或已删除");
        }
        OaonesMyBatis.run(AppBindMapper.class, mapper -> mapper.deleteById(table(), id));
        OaonesLog.step("删除应用绑定 bindId=" + bindId + " agentId=" + OaonesUtil.s(exist.getAgentId()));
    }

    @Override
    public JSONObject workbenchLogin(String bindId, String cfgId, String agentId, String code, String state,
                                    String userAgent, String device) throws Exception {
        String authCode = OaonesUtil.s(code).trim();
        if (authCode.length() == 0) {
            throw new RuntimeException("免登录入口缺少企业微信授权参数（code），"
                    + "请从企业微信工作台打开该应用，不要直接在浏览器里访问应用主页地址");
        }
        AppBind bind = resolveEnabledBind(bindId, cfgId, agentId);
        String cfgKey = OaonesUtil.nullToEmpty(bind.getCfgId());
        JSONObject cfg = configService().get(cfgKey);
        if (cfg == null) {
            throw new RuntimeException("该应用绑定的企微租户配置已不存在，请联系管理员在“应用绑定”页检查");
        }
        String cfgName = OaonesUtil.s(cfg.getString("corp_name"));
        if (OaonesUtil.i(cfg.getString("sync_enable"), 0) != 1) {
            throw new RuntimeException("企微租户「" + cfgName + "」已停用，无法从企微工作台免登录进入 OA");
        }
        String corpId = OaonesUtil.s(cfg.getString("corp_id"));
        String secret = resolveSecret(bind, cfg);
        if (corpId.length() == 0 || secret.length() == 0) {
            throw new RuntimeException("企微租户「" + cfgName
                    + "」未维护企业ID(corpid)或应用 Secret，无法校验企业微信身份；"
                    + "请联系管理员在“企微租户配置/应用绑定”页补充");
        }
        String appLabel = appLabel(bind);
        OaonesLog.step("应用免登录校验 bindId=" + OaonesUtil.nullToEmpty(bind.getId())
                + " cfgId=" + cfgKey + " agentId=" + OaonesUtil.s(bind.getAgentId())
                + " app=" + appLabel);
        WeComApi api = new WeComApi(corpId, secret);
        JSONObject auth = api.getAuthUserInfo(authCode);
        String wxUserId = OaonesUtil.s(auth.getString("userid")).trim();
        if (wxUserId.length() == 0) {
            // 非本企业成员时企业微信只返回 openid（成员还要在应用的可见范围内）
            throw new RuntimeException("该微信账号不是企业微信「" + cfgName + "」的成员，"
                    + "或不在应用「" + appLabel + "」的可见范围内，无法免登录进入 OA；"
                    + "请让管理员把本人加入该应用的可见范围");
        }
        Integer oaUserId = oaUserIdOfWxUser(cfgKey, wxUserId);
        if (oaUserId == null || oaUserId <= 0) {
            throw new RuntimeException("企业微信成员「" + wxUserId + "」还没有绑定 OA 账号"
                    + "（或绑定已被解除），无法免登录进入 OA；"
                    + "请用本人企业微信在“个人账号中心”扫码绑定 OA 账号后再从企微工作台打开该应用");
        }
        assertBindableOaUser(oaUserId);
        String client = OaonesDevice.detect(device, userAgent);
        OaonesLog.step("免登录客户端识别 client=" + client + " isWeCom=" + OaonesDevice.isWeCom(userAgent)
                + " deviceParam=" + OaonesUtil.s(device) + " ua=" + OaonesUtil.s(userAgent));
        String redirectUrl = landingUrl(bind, state, userAgent, device);
        JSONObject data = new JSONObject();
        data.put("bind_id", OaonesUtil.nullToEmpty(bind.getId()));
        data.put("cfg_id", cfgKey);
        data.put("cfg_name", cfgName);
        data.put("agent_id", OaonesUtil.s(bind.getAgentId()));
        data.put("app_name", appLabel);
        data.put("wx_user_id", wxUserId);
        data.put("oa_user_id", oaUserId);
        data.put("client", client);
        data.put("redirect_url", redirectUrl);
        return data;
    }

    @Override
    public String oauthAuthorizeUrl(String bindId, String cfgId, String agentId, String redirectUri,
                                    String state) throws Exception {
        AppBind bind = resolveEnabledBind(bindId, cfgId, agentId);
        JSONObject cfg = configService().get(OaonesUtil.nullToEmpty(bind.getCfgId()));
        if (cfg == null) {
            throw new RuntimeException("该应用绑定的企微租户配置已不存在，请联系管理员在“应用绑定”页检查");
        }
        String cfgName = OaonesUtil.s(cfg.getString("corp_name"));
        if (OaonesUtil.i(cfg.getString("sync_enable"), 0) != 1) {
            throw new RuntimeException("企微租户「" + cfgName + "」已停用，无法从企微工作台免登录进入 OA");
        }
        String corpId = OaonesUtil.s(cfg.getString("corp_id"));
        if (corpId.length() == 0) {
            throw new RuntimeException("企微租户「" + cfgName
                    + "」未维护企业ID(corpid)，无法发起企业微信网页授权；"
                    + "请联系管理员在“企微租户配置”页补充");
        }
        Map<String, String> params = new HashMap<String, String>();
        params.put("appid", corpId);
        params.put("redirect_uri", OaonesUtil.s(redirectUri));
        params.put("response_type", "code");
        params.put("scope", "snsapi_base");
        params.put("agentid", OaonesUtil.s(bind.getAgentId()));
        String st = OaonesUtil.s(state).trim();
        if (st.length() > 0) {
            params.put("state", st);
        }
        String url = OaonesProps.appOauthBase() + OaonesProps.appOauthPath() + "?"
                + OaonesUtil.buildQuery(params) + "#wechat_redirect";
        OaonesLog.step("应用免登录入口缺少 code，已跳转企业微信网页授权 appid=" + corpId
                + " agentid=" + OaonesUtil.s(bind.getAgentId())
                + " redirect_uri=" + OaonesUtil.s(redirectUri));
        return url;
    }

    @Override
    public String landingUrl(AppBind bind, String state, String userAgent, String device) {
        String fromState = statePath(state);
        String target = OaonesSso.safeInternalUrl(fromState, "");
        String source = target.length() > 0 ? "入口 state" : "";
        String client = OaonesDevice.detect(device, userAgent);
        if (target.length() == 0 && bind != null) {
            String pcUrl = OaonesUtil.s(bind.getOaPageUrl());
            String mobileUrl = OaonesUtil.s(bind.getOaPageUrlMobile());
            // 手机端优先“手机端 OA 页面地址”，未配置回退 PC 端地址；桌面端反之
            if (OaonesDevice.MOBILE.equals(client)) {
                target = OaonesSso.safeInternalUrl(mobileUrl, "");
                if (target.length() > 0) {
                    source = "手机端OA页面地址";
                } else {
                    target = OaonesSso.safeInternalUrl(pcUrl, "");
                    if (target.length() > 0) {
                        source = "PC端OA页面地址（手机端未配置，回退）";
                    }
                }
            } else {
                target = OaonesSso.safeInternalUrl(pcUrl, "");
                if (target.length() > 0) {
                    source = "PC端OA页面地址";
                } else {
                    target = OaonesSso.safeInternalUrl(mobileUrl, "");
                    if (target.length() > 0) {
                        source = "手机端OA页面地址（PC端未配置，回退）";
                    }
                }
            }
        }
        if (target.length() == 0) {
            target = OaonesConst.DEFAULT_OA_HOME_URL;
            source = "默认地址";
        }
        OaonesLog.step("免登录落地页解析结果: " + target + "（来源：" + source + "；客户端：" + client
                + "；oa_page_url=" + OaonesUtil.s(bind == null ? "" : bind.getOaPageUrl())
                + " oa_page_url_mobile=" + OaonesUtil.s(bind == null ? "" : bind.getOaPageUrlMobile()) + "）");
        return target;
    }

    /**
     * 定位启用中的应用绑定：优先按绑定记录ID（入口参数 b），其次按“租户 + AgentId”。
     *
     * <p>入口地址推荐用 {@code cfg_id + agent_id}：绑定记录删掉重建后地址不变，
     * 不必回到企业微信后台改应用主页。</p>
     */
    private AppBind resolveEnabledBind(String bindId, String cfgId, String agentId) {
        Integer id = OaonesUtil.integer(bindId);
        if (id != null && id > 0) {
            AppBind row = OaonesMyBatis.call(AppBindMapper.class, mapper -> mapper.findById(table(), id));
            if (row == null) {
                throw new RuntimeException("应用绑定记录不存在（可能已被删除），请联系管理员在“应用绑定”页检查");
            }
            if (row.getYx() == null || row.getYx() != OaonesConst.APP_BIND_STATE_VALID) {
                throw new RuntimeException("应用「" + appLabel(row) + "」的绑定已停用，"
                        + "请联系管理员在“应用绑定”页启用后再从企微工作台打开");
            }
            return row;
        }
        int cfgKey = OaonesUtil.i(cfgId, 0);
        String agent = OaonesUtil.s(agentId).trim();
        if (cfgKey <= 0 || agent.length() == 0) {
            throw new RuntimeException("应用主页地址缺少入口参数（cfg_id/agent_id 或 b），"
                    + "请把“应用绑定”页里复制的应用主页地址原样配置到企业微信后台");
        }
        AppBind row = OaonesMyBatis.call(AppBindMapper.class,
                mapper -> mapper.findEnabledByAgent(table(), cfgKey, agent));
        if (row == null) {
            throw new RuntimeException("没有找到租户ID " + cfgKey + " 下 AgentId 为 " + agent
                    + " 的启用中应用绑定，请联系管理员在“应用绑定”页检查（是否已停用/绑定被删除）");
        }
        return row;
    }

    /** 应用免登录 Web 授权用的 Secret：优先用绑定里维护的应用 Secret，留空回退租户通讯录 Secret。 */
    private String resolveSecret(AppBind bind, JSONObject cfg) {
        String appSecret = OaonesUtil.s(bind.getAppSecret()).trim();
        if (appSecret.length() > 0) {
            return appSecret;
        }
        return OaonesUtil.s(cfg.getString("corp_secret")).trim();
    }

    /** 该企微成员在指定租户下已绑定的 OA 人员ID（多对一时取ID最小的一条免登录）。 */
    private Integer oaUserIdOfWxUser(String cfgId, String wxUserId) {
        Map<String, Object> params = OaonesParam.of();
        params.put("cfgId", OaonesUtil.integer(cfgId));
        params.put("wxUserId", wxUserId);
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
            OaonesLog.step("企微成员绑定了多个 OA 账号（多对一），免登录取ID最小的一条 cfgId=" + cfgId
                    + " wxUserId=" + wxUserId + " oaUserId=" + min + " count=" + ids.size());
        }
        return min;
    }

    /**
     * 免登录前的 OA 账号校验：账号存在、不是系统管理员账号、状态为有效（0-3）。
     *
     * <p>口径与“个人账号中心”一致（编码按本人口径提示，不出现面向管理员的措辞）。</p>
     */
    private void assertBindableOaUser(int oaUserId) {
        OaAccountRow row = OaonesMyBatis.call(HrmMapper.class, mapper -> mapper.getResourceAccount(oaUserId));
        if (row == null) {
            throw new RuntimeException("你的 OA 账号不存在或已被删除，无法免登录进入 OA；请联系管理员");
        }
        if (oaUserId == OaonesConst.SYS_ADMIN_USER_ID) {
            throw new RuntimeException("系统管理员账号（user.id=1）不支持从企微工作台免登录进入 OA，"
                    + "请用员工账号绑定企业微信后再从工作台打开");
        }
        int status = OaonesUtil.i(OaonesUtil.nullToEmpty(row.getStatus()), -1);
        if (status >= 4) {
            throw new RuntimeException("你的 OA 账号状态为「" + statusText(row.getStatus())
                    + "」，不能免登录进入 OA；如有疑问请联系管理员");
        }
    }

    /** 站内地址校验：只接受以 / 开头的站内路径；返回规范化后的值（不合法直接报错）。 */
    private String checkInternalUrl(String url, String label) {
        // 允许直接粘贴“浏览器地址栏里的完整网址”（保存前归一成站内路径，保留 #hash 里的页面参数）
        String value = OaonesSso.internalPath(url);
        if (value.length() == 0) {
            return "";
        }
        if (value.length() > URL_MAX_LENGTH) {
            throw new RuntimeException(label + "过长（最多 " + URL_MAX_LENGTH + " 个字符）");
        }
        String safe = OaonesSso.safeInternalUrl(value, "");
        if (safe.length() == 0) {
            throw new RuntimeException(label + "必须是本站内的相对地址（以 / 开头，"
                    + "例如 /wui/index.html 或 /spa/portal/index.html），不能填写完整域名");
        }
        return safe;
    }

    /**
     * 解析入口 state 里的站内目标页面。
     *
     * <p>支持两种写法：直接写站内路径（{@code /xxx}），或写该路径的
     * base64url 编码（企业微信 state 里带 {@code &} / {@code =} 容易被截断时用）。
     * 都不是合法站内路径时返回空串（按绑定的默认落地页处理）。</p>
     */
    private String statePath(String state) {
        String value = OaonesUtil.s(state).trim();
        if (value.length() == 0) {
            return "";
        }
        if (value.startsWith("/")) {
            return value;
        }
        if (value.indexOf('%') >= 0) {
            try {
                String decoded = URLDecoder.decode(value, "UTF-8");
                if (decoded.startsWith("/")) {
                    return decoded;
                }
            } catch (Exception e) {
                OaonesLog.error("应用免登录 state 解码失败（按默认落地页处理）: " + value, e);
            }
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value), "UTF-8");
            if (decoded.startsWith("/")) {
                return decoded;
            }
        } catch (Exception ignore) {
            // 不是 base64url：按默认落地页处理
        }
        return "";
    }

    private String appLabel(AppBind bind) {
        String name = OaonesUtil.s(bind.getAppName()).trim();
        if (name.length() > 0) {
            return name;
        }
        String agentId = OaonesUtil.s(bind.getAgentId()).trim();
        return agentId.length() > 0 ? "AgentId " + agentId : "未命名应用";
    }

    private JSONObject toJson(AppBind row) {
        JSONObject json = new JSONObject();
        json.put("id", OaonesUtil.nullToEmpty(row.getId()));
        json.put("cfg_id", OaonesUtil.nullToEmpty(row.getCfgId()));
        json.put("agent_id", OaonesUtil.s(row.getAgentId()));
        json.put("app_secret", OaonesUtil.s(row.getAppSecret()));
        json.put("app_name", OaonesUtil.s(row.getAppName()));
        json.put("oa_page_url", OaonesUtil.s(row.getOaPageUrl()));
        json.put("oa_page_url_mobile", OaonesUtil.s(row.getOaPageUrlMobile()));
        json.put("bind_state", OaonesUtil.nullToEmpty(row.getYx()));
        json.put("enable", row.getYx() != null && row.getYx() == OaonesConst.APP_BIND_STATE_VALID);
        json.put("remark", OaonesUtil.nullToEmpty(row.getRemark()));
        json.put("formmodeid", OaonesUtil.nullToEmpty(row.getFormmodeid()));
        json.put("create_by", OaonesUtil.nullToEmpty(row.getModedatacreater()));
        String createDate = OaonesUtil.s(row.getModedatacreatedate());
        String createTime = OaonesUtil.s(row.getModedatacreatetime());
        json.put("create_time", createDate.length() == 0 && createTime.length() == 0
                ? "" : createDate + " " + createTime);
        json.put("update_time", OaonesUtil.nullToEmpty(row.getModedatamodifydatetime()));
        return json;
    }

    private JSONObject toJson(AppBindRow row) {
        JSONObject json = new JSONObject();
        json.put("id", OaonesUtil.nullToEmpty(row.getId()));
        json.put("cfg_id", OaonesUtil.nullToEmpty(row.getCfgId()));
        json.put("cfg_name", OaonesUtil.nullToEmpty(row.getCfgName()));
        json.put("cfg_corp_id", OaonesUtil.nullToEmpty(row.getCfgCorpId()));
        json.put("cfg_sync_enable", OaonesUtil.nullToEmpty(row.getCfgSyncEnable()));
        json.put("agent_id", OaonesUtil.s(row.getAgentId()));
        json.put("app_secret", OaonesUtil.s(row.getAppSecret()));
        json.put("app_name", OaonesUtil.s(row.getAppName()));
        json.put("oa_page_url", OaonesUtil.s(row.getOaPageUrl()));
        json.put("oa_page_url_mobile", OaonesUtil.s(row.getOaPageUrlMobile()));
        json.put("bind_state", OaonesUtil.nullToEmpty(row.getBindState()));
        json.put("enable", row.getBindState() != null
                && row.getBindState() == OaonesConst.APP_BIND_STATE_VALID);
        json.put("remark", OaonesUtil.nullToEmpty(row.getRemark()));
        json.put("formmodeid", OaonesUtil.nullToEmpty(row.getFormmodeid()));
        json.put("create_by", OaonesUtil.nullToEmpty(row.getModedatacreater()));
        json.put("creator_name", OaonesUtil.nullToEmpty(row.getCreatorName()));
        String createDate = OaonesUtil.s(row.getModedatacreatedate());
        String createTime = OaonesUtil.s(row.getModedatacreatetime());
        json.put("create_time", createDate.length() == 0 && createTime.length() == 0
                ? "" : createDate + " " + createTime);
        json.put("update_time", OaonesUtil.nullToEmpty(row.getModedatamodifydatetime()));
        return json;
    }

    /** OA 账号状态文本（hrmresource.status，E9 标准取值）：0-3 视为有效账号。 */
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
}
