package com.customization.oaones.appsyn.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesParam;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dao.BindOverviewMapper;
import com.customization.oaones.appsyn.dto.BindOverviewRow;
import com.customization.oaones.appsyn.service.BindOverviewService;
import weaver.general.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 绑定总览实现（DAO 层为 {@link BindOverviewMapper}）。
 *
 * <p>口径与各功能页保持一致，只是把三个平台合在一页：</p>
 * <ul>
 *   <li><b>平台租户</b>：全部平台租户（企微 / 钉钉 / 飞书）都列出，包含没有绑定关系的租户；
 *       本页是公共只读总览，因此不按“维护人”分权过滤，但**不返回任何 Secret / 密钥类字段**；</li>
 *   <li><b>有效绑定</b>：分部 yx=0、部门 yx=1、人员 bind_state=1；</li>
 *   <li><b>平台侧对象</b>：部门 / 成员名称取快照表（快照未刷新时留空，页面上退化为ID）；</li>
 *   <li><b>只读</b>：本服务只有查询，没有任何写库动作。</li>
 * </ul>
 */
public class BindOverviewServiceImpl implements BindOverviewService {

    @Override
    public JSONObject overview(String platform, String cfgId, String keyword) {
        String kw = OaonesUtil.s(keyword).trim();
        Integer platformValue = platformValue(platform);
        Integer cfgValue = cfgValue(cfgId);
        OaonesLog.step("查询绑定总览 platform=" + platformValue + " cfgId=" + cfgValue
                + " keyword=" + kw);

        Map<String, Object> params = OaonesParam.of();
        params.put("platform", platformValue);
        params.put("cfgId", cfgValue);
        params.put("keyword", kw.length() == 0 ? "" : "%" + kw + "%");

        List<BindOverviewRow> cfgRows = OaonesMyBatis.call(BindOverviewMapper.class,
                mapper -> mapper.listCfgs(params));
        List<BindOverviewRow> subRows = OaonesMyBatis.call(BindOverviewMapper.class,
                mapper -> mapper.listSubRows(params));
        List<BindOverviewRow> deptRows = OaonesMyBatis.call(BindOverviewMapper.class,
                mapper -> mapper.listDeptRows(params));
        List<BindOverviewRow> userRows = OaonesMyBatis.call(BindOverviewMapper.class,
                mapper -> mapper.listUserRows(params));

        JSONObject data = new JSONObject();
        JSONArray subs = rowJson(subRows, "sub");
        JSONArray depts = rowJson(deptRows, "dept");
        JSONArray users = rowJson(userRows, "user");
        // 分组用的租户列表：按平台/租户筛选；带关键字时只保留有关键字命中的租户
        // （否则每个没命中的租户都会占一行“该租户暂无…记录”，关键字搜索时会很吵）
        List<BindOverviewRow> groups = groupCfgs(cfgRows, platformValue, cfgValue, kw, subs, depts, users);
        data.put("cfgs", cfgJson(groups));
        // 租户下拉候选：全部平台租户（不含租户/关键字条件，否则选中某个租户后下拉里就只剩它自己；
        // 平台维度的收窄由页面按下拉所选平台过滤，不需要等接口）
        data.put("cfg_options", cfgJson(cfgRows));
        data.put("subs", subs);
        data.put("depts", depts);
        data.put("users", users);
        // 统计行与页面展示同一口径：都按当前筛选条件（筛选后无租户时租户数为 0）
        data.put("count", countJson(groups, subs, depts, users));
        JSONObject filter = new JSONObject();
        filter.put("platform", platform == null ? "" : platform.trim());
        filter.put("cfg_id", cfgId == null ? "" : cfgId.trim());
        filter.put("keyword", kw);
        data.put("filter", filter);
        return data;
    }

    /**
     * 页面分组用的租户列表（统计行里的租户数也按这份列表算，见 {@link #countJson}）。
     *
     * <p>规则：先按平台 / 租户筛选；**带关键字时只保留有关键字命中的租户**
     * （否则关键字搜索时，每个没命中的租户都会占一行“该租户暂无…记录”，反而看不清结果）。
     * 不带关键字时列出全部（含没有任何绑定关系的租户），便于确认“租户是不是真的没绑”。</p>
     */
    private List<BindOverviewRow> groupCfgs(List<BindOverviewRow> cfgs, Integer platformValue,
                                            Integer cfgValue, String keyword, JSONArray subs,
                                            JSONArray depts, JSONArray users) {
        List<BindOverviewRow> result = new ArrayList<BindOverviewRow>();
        if (cfgs == null) {
            return result;
        }
        boolean keywordOn = keyword != null && keyword.length() > 0;
        for (BindOverviewRow cfg : cfgs) {
            if (platformValue != null && !platformValue.equals(cfg.getPlatformType())) {
                continue;
            }
            if (cfgValue != null && !cfgValue.equals(cfg.getId())) {
                continue;
            }
            if (keywordOn && !hitAny(subs, cfg) && !hitAny(depts, cfg) && !hitAny(users, cfg)) {
                continue;
            }
            result.add(cfg);
        }
        return result;
    }

    /** 该租户在某一类绑定行里是否至少有一条命中（关键字过滤后的行）。 */
    private boolean hitAny(JSONArray rows, BindOverviewRow cfg) {
        String cfgId = OaonesUtil.nullToEmpty(cfg.getId());
        String platformType = OaonesUtil.nullToEmpty(cfg.getPlatformType());
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if (cfgId.equals(OaonesUtil.s(row.getString("cfg_id")))
                    && platformType.equals(OaonesUtil.s(row.getString("platform_type")))) {
                return true;
            }
        }
        return false;
    }

    /** 平台筛选值：空 / all 表示全部；非法值按“全部”处理。 */
    private Integer platformValue(String platform) {
        String value = OaonesUtil.s(platform).trim();
        if (value.length() == 0 || "all".equalsIgnoreCase(value)) {
            return null;
        }
        Integer type = OaonesUtil.integer(value);
        if (type == null
                || (type != OaonesConst.TYPE_WECHAT && type != OaonesConst.TYPE_DINGTALK
                    && type != OaonesConst.TYPE_FEISHU)) {
            return null;
        }
        return type;
    }

    /** 租户筛选值：空 / 0 表示全部租户。 */
    private Integer cfgValue(String cfgId) {
        Integer id = OaonesUtil.integer(OaonesUtil.s(cfgId).trim());
        return id == null || id <= 0 ? null : id;
    }

    /* ==================================================================
     * 一、JSON 组装
     * ================================================================== */

    /** 全部平台租户（筛选框 + 统计用；不含 Secret 等敏感字段）。 */
    private JSONArray cfgJson(List<BindOverviewRow> rows) {
        JSONArray result = new JSONArray();
        if (rows == null) {
            return result;
        }
        for (BindOverviewRow row : rows) {
            JSONObject json = new JSONObject();
            json.put("id", OaonesUtil.nullToEmpty(row.getId()));
            json.put("platform_type", OaonesUtil.nullToEmpty(row.getPlatformType()));
            json.put("platform", platformText(row.getPlatformType()));
            json.put("corp_name", OaonesUtil.nullToEmpty(row.getCfgName()));
            json.put("corp_id", OaonesUtil.nullToEmpty(row.getCorpId()));
            json.put("enable", OaonesUtil.nullToEmpty(row.getCfgEnable()));
            json.put("root_dept_ref", rootDeptRef(row));
            json.put("root_dept_name", OaonesUtil.nullToEmpty(row.getRootDeptName()));
            result.add(json);
        }
        return result;
    }

    private JSONArray rowJson(List<BindOverviewRow> rows, String kind) {
        JSONArray result = new JSONArray();
        if (rows == null) {
            return result;
        }
        for (BindOverviewRow row : rows) {
            JSONObject json = new JSONObject();
            json.put("kind", kind);
            json.put("id", OaonesUtil.nullToEmpty(row.getId()));
            json.put("platform_type", OaonesUtil.nullToEmpty(row.getPlatformType()));
            json.put("platform", platformText(row.getPlatformType()));
            json.put("cfg_id", OaonesUtil.nullToEmpty(row.getCfgId()));
            json.put("cfg_name", OaonesUtil.nullToEmpty(row.getCfgName()));
            json.put("corp_id", OaonesUtil.nullToEmpty(row.getCorpId()));
            json.put("cfg_enable", OaonesUtil.nullToEmpty(row.getCfgEnable()));
            json.put("bind_state", OaonesUtil.nullToEmpty(row.getBindState()));
            json.put("remark", OaonesUtil.nullToEmpty(row.getRemark()));
            json.put("update_time", OaonesUtil.nullToEmpty(row.getUpdateTime()));
            json.put("oa_id", OaonesUtil.nullToEmpty(row.getOaId()));
            json.put("oa_name", oaText(row.getOaName()));
            json.put("oa_canceled", OaonesUtil.nullToEmpty(row.getOaCanceled()));

            if ("sub".equals(kind)) {
                json.put("oa_parent_id", OaonesUtil.nullToEmpty(row.getOaParentId()));
                json.put("oa_parent_name", oaText(row.getOaParentName()));
                // 分部在平台侧对应的部门：优先取绑定记录上的部门，老数据为空时退化为租户根部门
                String ref = deptRef(row.getWxDeptKey(), row.getWxDeptId());
                String name = OaonesUtil.s(row.getWxDeptName());
                if (ref.length() == 0) {
                    ref = rootDeptRef(row);
                    name = OaonesUtil.s(row.getRootDeptName());
                }
                json.put("platform_dept_ref", ref);
                json.put("platform_dept_name", name);
                json.put("bind_time", createdTime(row));
            } else if ("dept".equals(kind)) {
                json.put("oa_parent_id", OaonesUtil.nullToEmpty(row.getOaParentId()));
                json.put("oa_parent_name", oaText(row.getOaParentName()));
                json.put("oa_sub_id", OaonesUtil.nullToEmpty(row.getOaSubId()));
                json.put("oa_sub_name", oaText(row.getOaSubName()));
                json.put("platform_dept_ref", deptRef(row.getWxDeptKey(), row.getWxDeptId()));
                json.put("platform_dept_name", OaonesUtil.nullToEmpty(row.getWxDeptName()));
                json.put("bind_time", createdTime(row));
            } else {
                json.put("oa_dept_id", OaonesUtil.nullToEmpty(row.getOaDeptId()));
                json.put("oa_dept_name", oaText(row.getOaDeptName()));
                json.put("oa_sub_id", OaonesUtil.nullToEmpty(row.getOaSubId()));
                json.put("oa_sub_name", oaText(row.getOaSubName()));
                json.put("oa_login_id", OaonesUtil.nullToEmpty(row.getOaLoginId()));
                json.put("oa_workcode", OaonesUtil.nullToEmpty(row.getOaWorkcode()));
                json.put("oa_mobile", OaonesUtil.nullToEmpty(row.getOaMobile()));
                json.put("oa_status", OaonesUtil.nullToEmpty(row.getOaStatus()));
                json.put("platform_user_ref", OaonesUtil.nullToEmpty(row.getWxUserId()));
                json.put("platform_user_name", OaonesUtil.nullToEmpty(row.getWxUserName()));
                json.put("platform_dept_ref", deptRef(row.getWxUserDeptKey(), row.getWxUserDeptId()));
                json.put("platform_dept_name", OaonesUtil.nullToEmpty(row.getWxUserDeptName()));
                json.put("bind_time", OaonesUtil.nullToEmpty(row.getBindTime()));
            }
            result.add(json);
        }
        return result;
    }

    private JSONObject countJson(List<BindOverviewRow> cfgs, JSONArray subs, JSONArray depts,
                                 JSONArray users) {
        int wecom = 0;
        int dingtalk = 0;
        int feishu = 0;
        if (cfgs != null) {
            for (BindOverviewRow cfg : cfgs) {
                Integer type = cfg.getPlatformType();
                if (type == null) {
                    continue;
                }
                if (type == OaonesConst.TYPE_DINGTALK) {
                    dingtalk++;
                } else if (type == OaonesConst.TYPE_FEISHU) {
                    feishu++;
                } else {
                    wecom++;
                }
            }
        }
        JSONObject count = new JSONObject();
        count.put("tenant", cfgs == null ? 0 : cfgs.size());
        count.put("tenant_wecom", wecom);
        count.put("tenant_dingtalk", dingtalk);
        count.put("tenant_feishu", feishu);
        count.put("sub", subs.size());
        count.put("dept", depts.size());
        count.put("user", users.size());
        return count;
    }

    /* ==================================================================
     * 二、公共取值
     * ================================================================== */

    /** 平台文案：0 企业微信 / 1 钉钉 / 2 飞书。 */
    private String platformText(Integer type) {
        if (type == null) {
            return "未知平台";
        }
        if (type == OaonesConst.TYPE_DINGTALK) {
            return "钉钉";
        }
        if (type == OaonesConst.TYPE_FEISHU) {
            return "飞书";
        }
        return "企业微信";
    }

    /**
     * 平台侧部门标识：飞书用字符串列（od-xxxx），企微 / 钉钉用数值列。
     *
     * <p>取不到（例如老数据只登记了租户）时返回空串，由调用方按“租户根部门”兜底。</p>
     */
    private String deptRef(String deptKey, Integer deptId) {
        String key = OaonesUtil.s(deptKey).trim();
        if (key.length() > 0) {
            return key;
        }
        return deptId == null || deptId <= 0 ? "" : String.valueOf(deptId);
    }

    /** 租户根部门标识（飞书取字符串列；企微 / 钉钉取数值列；未维护时为空）。 */
    private String rootDeptRef(BindOverviewRow row) {
        String key = OaonesUtil.s(row.getRootDeptKey()).trim();
        if (key.length() > 0) {
            return key;
        }
        Integer id = row.getRootDeptId();
        return id == null || id <= 0 ? "" : String.valueOf(id);
    }

    /**
     * OA 组织名称（分部 / 部门）的多语言取值。
     *
     * <p>E9 的多语言字段在库里存成 {@code ~`~`7 中文`~`8 English`~`~} 这样的分段串，
     * 直接显示会是一串标记。这里按当前登录语言挑一段（挑不到时取第一段），
     * 不含标记的值原样返回，因此普通名称不受影响。</p>
     */
    private String oaText(String value) {
        String raw = OaonesUtil.nullToEmpty(value);
        if (raw.indexOf("`~`") < 0) {
            return raw;
        }
        String preferred = "";
        try {
            preferred = String.valueOf(Util.threadVarLanguage());
        } catch (Throwable ignored) {
            preferred = "";
        }
        String first = "";
        String[] parts = raw.split("`~`");
        for (String part : parts) {
            String piece = part == null ? "" : part.trim();
            int space = piece.indexOf(' ');
            if (space <= 0) {
                continue;
            }
            String lang = piece.substring(0, space).trim();
            String text = piece.substring(space + 1).trim();
            if (lang.length() == 0 || !lang.matches("\\d+")) {
                continue;
            }
            if (first.length() == 0) {
                first = text;
            }
            if (preferred.length() > 0 && lang.equals(preferred)) {
                return text;
            }
        }
        return first.length() > 0 ? first : raw;
    }

    /** 分部 / 部门绑定没有单独的绑定时间列，用建模创建日期 + 时间拼接（与列表页口径一致）。 */
    private String createdTime(BindOverviewRow row) {
        String date = OaonesUtil.s(row.getCreatedDate()).trim();
        String time = OaonesUtil.s(row.getCreatedTime()).trim();
        if (date.length() == 0) {
            return time;
        }
        return time.length() == 0 ? date : (date + " " + time);
    }
}
