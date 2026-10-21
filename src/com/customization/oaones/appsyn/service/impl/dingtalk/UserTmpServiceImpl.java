package com.customization.oaones.appsyn.service.impl.dingtalk;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.api.dingtalk.DingTalkApi;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesParam;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dao.dingtalk.UserBindMapper;
import com.customization.oaones.appsyn.dao.dingtalk.UserTmpMapper;
import com.customization.oaones.appsyn.dto.dingtalk.WxUserRow;
import com.customization.oaones.appsyn.entity.dingtalk.UserTmp;
import com.customization.oaones.appsyn.service.dingtalk.ConfigService;
import com.customization.oaones.appsyn.service.dingtalk.UserTmpService;
import weaver.general.BaseBean;
import weaver.general.Util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 人员快照表维护实现（DAO 层为 {@link UserTmpMapper}）。
 */
public class UserTmpServiceImpl implements UserTmpService {

    private String table() {
        return OaonesProps.userTmpTable();
    }

    private ConfigService configService() {
        return new ConfigServiceImpl();
    }

    @Override
    public JSONObject refreshByCfgId(String cfgId) throws Exception {
        OaonesLog.step("刷新人员快照 cfgId=" + cfgId + " type=" + OaonesConst.TYPE_DINGTALK);
        JSONObject cfg = configService().get(cfgId);
        if (cfg == null) {
            throw new RuntimeException("钉钉租户配置不存在: id=" + cfgId);
        }
        DingTalkApi api = configService().getClient(cfgId);
        int rootDept = Util.getIntValue(OaonesUtil.s(cfg.getString("root_dept_id")), 1);
        JSONArray users = api.listUsers(rootDept, true);
        OaonesLog.step("钉钉接口返回成员数 cfgId=" + cfgId + " count=" + users.size());
        return refresh(cfg, users);
    }

    @Override
    public JSONObject refreshAll() {
        JSONObject result = new JSONObject();
        int okCount = 0;
        int failCount = 0;
        JSONArray cfgs = configService().list();
        for (int i = 0; i < cfgs.size(); i++) {
            JSONObject cfg = cfgs.getJSONObject(i);
            String cfgId = OaonesUtil.s(cfg.getString("id"));
            if (Util.getIntValue(OaonesUtil.s(cfg.getString("sync_enable")), 0) == 0) {
                continue;
            }
            try {
                refreshByCfgId(cfgId);
                okCount++;
            } catch (Throwable t) {
                failCount++;
                new BaseBean().writeLog("oaones user tmp refresh fail cfgId=" + cfgId, t);
            }
        }
        result.put("ok", okCount);
        result.put("fail", failCount);
        return result;
    }

    @Override
    public JSONObject refresh(JSONObject cfg, JSONArray users) throws Exception {
        String corpId = OaonesUtil.s(cfg.getString("corp_id"));
        if (corpId.length() == 0) {
            throw new RuntimeException("钉钉租户未配置 AppKey");
        }
        String now = OaonesUtil.now();
        final String corp = corpId;
        // 先把当前租户已有快照全部置为 status=1，
        // 未在本轮接口返回的成员保持关闭，后续逐条查询决定 update/insert
        OaonesMyBatis.call(UserTmpMapper.class, mapper -> mapper.closeAll(
                table(), corp, OaonesConst.TYPE_DINGTALK, OaonesConst.STATUS_CLOSED, now));

        for (int i = 0; i < users.size(); i++) {
            JSONObject wx = users.getJSONObject(i);
            String wxUserId = OaonesUtil.s(wx.getString("userid"));
            if (wxUserId.length() == 0) {
                continue;
            }
            upsertMember(corp, wx, now);
        }
        JSONObject result = new JSONObject();
        result.put("corp_id", corpId);
        result.put("user_count", users.size());
        result.put("msg", "钉钉人员快照更新成功");
        OaonesLog.step("人员快照更新完成 corpId=" + corpId + " count=" + users.size());
        return result;
    }

    @Override
    public JSONObject refreshMember(String cfgId, String wxUserId) throws Exception {
        OaonesLog.step("刷新单个钉钉成员快照 cfgId=" + cfgId + " wxUserId=" + wxUserId);
        JSONObject cfg = configService().get(cfgId);
        if (cfg == null) {
            throw new RuntimeException("钉钉租户配置不存在: id=" + cfgId);
        }
        String corpId = OaonesUtil.s(cfg.getString("corp_id"));
        String userId = OaonesUtil.s(wxUserId).trim();
        if (corpId.length() == 0 || userId.length() == 0) {
            throw new RuntimeException("刷新钉钉成员快照缺少 AppKey 或成员ID");
        }
        DingTalkApi api = configService().getClient(cfgId);
        JSONObject wx = api.getUser(userId);
        if (wx == null) {
            throw new RuntimeException("钉钉中不存在成员 " + userId
                    + "（该账号可能已从企业通讯录删除）");
        }
        upsertMember(corpId, wx, OaonesUtil.now());
        JSONObject result = new JSONObject();
        result.put("corp_id", corpId);
        result.put("wx_user_id", userId);
        result.put("msg", "钉钉成员快照已更新");
        return result;
    }

    /**
     * 把接口返回的单个成员写入快照（存在则更新，不存在则新增）。
     *
     * <p>被 {@link #refresh(JSONObject, JSONArray)}（整租户刷新，此前已把未返回成员置为关闭）
     * 与 {@link #refreshMember(String, String)}（只刷新单条，不动其它成员）共用。</p>
     */
    private void upsertMember(String corp, JSONObject wx, String now) {
        String corpId = OaonesUtil.s(corp);
        String wxUserId = OaonesUtil.s(wx == null ? null : wx.getString("userid"));
        if (corpId.length() == 0 || wxUserId.length() == 0) {
            return;
        }
        UserTmp entity = new UserTmp();
        entity.setCorpid(corpId);
        entity.setWxUserId(wxUserId);
        entity.setWxName(OaonesUtil.s(wx.getString("name")));
        entity.setWxDeptId(primaryDeptId(wx));
        entity.setWxPosition(OaonesUtil.s(wx.getString("position")));
        entity.setWxMobile(OaonesUtil.s(wx.getString("mobile")));
        entity.setWxEmail(OaonesUtil.s(wx.getString("email")));
        entity.setWxBizMail(OaonesUtil.s(wx.getString("biz_mail")));
        entity.setWxGender(mapGender(wx.getString("gender")));
        entity.setWxAvatar(OaonesUtil.s(wx.getString("avatar")));
        entity.setWxTelephone(OaonesUtil.s(wx.getString("telephone")));
        entity.setWxStatus(mapStatus(wx.getString("status")));
        entity.setStatus(OaonesConst.STATUS_NORMAL);
        entity.setType(OaonesConst.TYPE_DINGTALK);
        entity.setLastSyncTime(now);

        String exists = OaonesMyBatis.call(UserTmpMapper.class,
                mapper -> mapper.findWxUserId(table(), corpId, wxUserId, OaonesConst.TYPE_DINGTALK));
        if (exists != null) {
            // 快照中已存在：恢复为正常并更新成员主要数据（不覆盖免绑定标记）
            OaonesMyBatis.call(UserTmpMapper.class, mapper -> mapper.updateUser(table(), entity));
            return;
        }
        // 快照中不存在：新增
        entity.setRemark("");
        entity.setFormmodeid(OaonesProps.userTmpModeId());
        OaonesMyBatis.call(UserTmpMapper.class, mapper -> {
            if (mapper.insertUser(table(), entity) <= 0) {
                throw new RuntimeException("人员快照写入失败：AppKey=" + corpId
                        + ", wx_user_id=" + entity.getWxUserId()
                        + "，请检查 " + table() + " 表字段是否与 SQL 脚本一致");
            }
            return 1;
        });
    }

    @Override
    public JSONArray listNormal(String cfgId) throws Exception {
        OaonesLog.step("查询正常人员快照 cfgId=" + cfgId + " type=" + OaonesConst.TYPE_DINGTALK);
        JSONArray result = new JSONArray();
        JSONObject cfg = configService().get(cfgId);
        if (cfg == null) {
            return result;
        }
        String corpId = OaonesUtil.s(cfg.getString("corp_id"));
        if (corpId.length() == 0) {
            return result;
        }
        Map<String, Object> params = OaonesParam.of();
        params.put("corpid", corpId);
        params.put("type", OaonesConst.TYPE_DINGTALK);
        params.put("status", OaonesConst.STATUS_NORMAL);
        List<WxUserRow> rows = OaonesMyBatis.call(UserTmpMapper.class, mapper -> mapper.listNormal(params));
        if (rows == null) {
            return result;
        }
        for (WxUserRow row : rows) {
            JSONObject json = new JSONObject();
            json.put("wx_user_id", OaonesUtil.nullToEmpty(row.getWxUserId()));
            json.put("wx_name", OaonesUtil.nullToEmpty(row.getWxName()));
            json.put("wx_dept_id", OaonesUtil.nullToEmpty(row.getWxDeptId()));
            json.put("wx_dept_name", OaonesUtil.nullToEmpty(row.getWxDeptName()));
            json.put("mobile", OaonesUtil.nullToEmpty(row.getWxMobile()));
            json.put("email", OaonesUtil.nullToEmpty(row.getWxEmail()));
            json.put("biz_mail", OaonesUtil.nullToEmpty(row.getWxBizMail()));
            json.put("workcode", "");
            json.put("position", OaonesUtil.nullToEmpty(row.getWxPosition()));
            json.put("gender", OaonesUtil.nullToEmpty(row.getWxGender()));
            json.put("avatar", OaonesUtil.nullToEmpty(row.getWxAvatar()));
            json.put("telephone", OaonesUtil.nullToEmpty(row.getWxTelephone()));
            json.put("wx_status", OaonesUtil.nullToEmpty(row.getWxStatus()));
            json.put("no_bind", OaonesUtil.nullToEmpty(row.getNoBind()));
            json.put("last_sync_time", OaonesUtil.nullToEmpty(row.getLastSyncTime()));
            result.add(json);
        }
        return result;
    }

    @Override
    public JSONObject markNoBind(String cfgId, String wxUserId) {
        return setNoBindFlag(cfgId, wxUserId, true);
    }

    @Override
    public JSONObject cancelNoBind(String cfgId, String wxUserId) {
        return setNoBindFlag(cfgId, wxUserId, false);
    }

    @Override
    public JSONObject batchNoBind(String cfgId, String wxUserIds) {
        OaonesLog.step("批量标记钉钉成员免绑定 cfgId=" + cfgId + " wxUserIds=" + wxUserIds);
        if (cfgId == null || cfgId.trim().length() == 0) {
            throw new RuntimeException("缺少钉钉租户");
        }
        String[] ids = splitCsv(wxUserIds);
        if (ids.length == 0) {
            throw new RuntimeException("请先勾选要设置免绑定的钉钉成员");
        }
        Set<String> seen = new HashSet<String>();
        for (String id : ids) {
            if (!seen.add(id)) {
                throw new RuntimeException("批量免绑定中钉钉成员ID " + id + " 重复");
            }
        }
        int success = 0;
        for (String id : ids) {
            setNoBindFlag(cfgId, id, true);
            success++;
        }
        JSONObject result = new JSONObject();
        result.put("success", success);
        result.put("total", ids.length);
        return result;
    }

    @Override
    public boolean isNoBind(String corpId, String wxUserId) throws Exception {
        if (corpId == null || corpId.trim().length() == 0
                || wxUserId == null || wxUserId.trim().length() == 0) {
            return false;
        }
        String found = OaonesMyBatis.call(UserTmpMapper.class, mapper -> mapper.findNoBindWxUserId(
                table(), corpId.trim(), wxUserId.trim(), OaonesConst.TYPE_DINGTALK));
        return found != null;
    }

    @Override
    public boolean isNormal(String corpId, String wxUserId) throws Exception {
        if (corpId == null || corpId.trim().length() == 0
                || wxUserId == null || wxUserId.trim().length() == 0) {
            return false;
        }
        String found = OaonesMyBatis.call(UserTmpMapper.class, mapper -> mapper.findNormalWxUserId(
                table(), corpId.trim(), wxUserId.trim(),
                OaonesConst.TYPE_DINGTALK, OaonesConst.STATUS_NORMAL));
        return found != null;
    }

    @Override
    public String statusText(String status) {
        if (OaonesConst.STATUS_NORMAL == Util.getIntValue(status, -1)) {
            return "正常";
        }
        if (OaonesConst.STATUS_CLOSED == Util.getIntValue(status, -1)) {
            return "关闭/封存/已删除";
        }
        return "未知";
    }

/**
 * 钉钉成员侧“免绑定”标记：维护 uf_oaones_user_tmp.no_bind。
 *
 * <p>本表只用 {@link OaonesConst#NO_BIND_MARK}（0）表达“免绑定”，取消其它状态值：
 * 标记免绑定写 0，取消免绑定把该列置回 NULL（正常态不写值）。
 * 口径与钉钉部门侧（uf_oaones_dep_tmp.no_bind，1 = 免绑定）相反，两处不可混用；
 * OA 人员侧的免绑定已并入 uf_oaones_user_bind.bind_state（0 = 免绑定）。</p>
 */
private JSONObject setNoBindFlag(String cfgId, String wxUserId, boolean noBind) {
        OaonesLog.step((noBind ? "标记钉钉成员免绑定" : "取消钉钉成员免绑定")
                + " cfgId=" + cfgId + " wxUserId=" + wxUserId);
        JSONObject cfg = configService().get(cfgId);
        if (cfg == null) {
            throw new RuntimeException("钉钉租户配置不存在: id=" + cfgId);
        }
        String corpId = OaonesUtil.s(cfg.getString("corp_id"));
        if (corpId.length() == 0) {
            throw new RuntimeException("钉钉租户未配置 AppKey");
        }
        String userId = wxUserId == null ? "" : wxUserId.trim();
        if (userId.length() == 0) {
            throw new RuntimeException("缺少钉钉成员ID");
        }
        String exists = OaonesMyBatis.call(UserTmpMapper.class,
                mapper -> mapper.findWxUserId(table(), corpId, userId, OaonesConst.TYPE_DINGTALK));
        if (exists == null) {
            throw new RuntimeException("钉钉人员快照中不存在该成员，请先更新钉钉人员快照");
        }
        if (noBind) {
            Integer cfgKey = OaonesUtil.integer(cfgId);
            Integer bound = OaonesMyBatis.call(UserBindMapper.class, mapper -> mapper.findActiveIdByWxUser(
                    OaonesProps.userBindTable(), cfgKey == null ? 0 : cfgKey, userId));
            if (bound != null) {
                throw new RuntimeException("该钉钉成员已绑定 OA 人员，不能标记为免绑定");
            }
        }
    OaonesMyBatis.call(UserTmpMapper.class, mapper -> {
        int rows = mapper.updateNoBind(table(), corpId, userId,
                OaonesConst.TYPE_DINGTALK, noBind ? Integer.valueOf(OaonesConst.NO_BIND_MARK) : null);
        if (rows <= 0) {
            throw new RuntimeException(noBind ? "设置免绑定失败" : "取消免绑定失败");
        }
        return rows;
    });
    JSONObject result = new JSONObject();
    result.put("cfg_id", cfgId);
    result.put("wx_user_id", userId);
    result.put("no_bind", noBind ? OaonesConst.NO_BIND_MARK : "");
    return result;
}

    /** 成员主部门ID；无部门时返回 null。 */
    private Integer primaryDeptId(JSONObject wx) {
        JSONArray depts = wx.getJSONArray("department");
        if (depts != null && depts.size() > 0) {
            return (int) depts.getLongValue(0);
        }
        return null;
    }

    /**
     * 性别统一为 0男/1女（序号从 0 开始）；
     * 钉钉返回 1=男、2=女，映射为 0/1，其余为空。
     */
    private Integer mapGender(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if ("1".equals(value)) {
            return 0;
        }
        if ("2".equals(value)) {
            return 1;
        }
        return null;
    }

    /**
     * 成员状态统一为从 0 开始的序号：
     * 钉钉返回 1=已激活、2=已禁用、4=未激活、5=退出企业，
     * 映射为 0/1/2/3，其余为空。
     */
    private Integer mapStatus(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if ("1".equals(value)) {
            return 0;
        }
        if ("2".equals(value)) {
            return 1;
        }
        if ("4".equals(value)) {
            return 2;
        }
        if ("5".equals(value)) {
            return 3;
        }
        return null;
    }

    /** 逗号分隔的钉钉成员ID：去空格、去空项。 */
    private String[] splitCsv(String value) {
        if (value == null || value.trim().length() == 0) {
            return new String[0];
        }
        List<String> list = new ArrayList<String>();
        for (String s : value.split(",")) {
            if (s != null && s.trim().length() > 0) {
                list.add(s.trim());
            }
        }
        return list.toArray(new String[0]);
    }
}
