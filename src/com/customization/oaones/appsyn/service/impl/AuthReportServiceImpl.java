package com.customization.oaones.appsyn.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesParam;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dao.AuthReportMapper;
import com.customization.oaones.appsyn.service.AuthReportService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一认证报表实现（DAO 层为 {@link AuthReportMapper}）。
 *
 * <p>装配口径：</p>
 * <ul>
 *   <li><b>总人数</b> = 该租户平台成员快照的有效成员数（{@code uf_oaones_appsyn_user_tmp}，corpid + type、status = 0）；</li>
 *   <li><b>已绑定</b> = 该租户 {@code uf_oaones_appsyn_user_bind}（bind_state = 1）去重 OA 人员数，可按 OA 分部过滤；</li>
 *   <li><b>未绑定</b> = 总人数 - 已绑定（不小于 0，页面展示）；</li>
 *   <li><b>今日新增绑定</b> = 绑定日期（bind_time 优先，空值退回建模创建日期）落在今日的去重人数；</li>
 *   <li><b>今日登录</b> = {@code uf_oaones_appsyn_op_log} 里 op_type = 'login' 的今日去重人数（按 OA 人员 → 租户绑定归属租户）；</li>
 *   <li><b>走势</b> = 上面两个指标按“租户 + 日 / 月”分组，统计区间取筛选的日期范围（缺省近 30 天）。</li>
 * </ul>
 *
 * <p>只读：本类没有任何写库动作。</p>
 */
public class AuthReportServiceImpl implements AuthReportService {

    /** 走势缺省统计天数（筛选未给日期范围时，取“今天往前推 29 天”到“今天”）。 */
    private static final int DEFAULT_RANGE_DAYS = 30;

    /** 操作监控表（uf_oaones_appsyn_op_log）是否缺失：缺表时登录相关指标退化为空并在页面提示。 */
    private boolean loginMissing;

    @Override
    public JSONObject data(JSONObject params) {
        List<Integer> cfgIds = intList(params == null ? null : params.getString("cfg_ids"));
        List<Integer> subIds = intList(params == null ? null : params.getString("sub_ids"));
        String today = OaonesUtil.nowDate();
        String fromDate = OaonesUtil.s(params == null ? null : params.getString("from_date")).trim();
        String toDate = OaonesUtil.s(params == null ? null : params.getString("to_date")).trim();
        if (fromDate.length() == 0 && toDate.length() == 0) {
            toDate = today;
            fromDate = OaonesUtil.shiftDate(today, -(DEFAULT_RANGE_DAYS - 1));
        } else {
            if (fromDate.length() == 0) {
                fromDate = OaonesUtil.shiftDate(toDate, -(DEFAULT_RANGE_DAYS - 1));
            }
            if (toDate.length() == 0) {
                toDate = today;
            }
        }
        OaonesLog.step("查询统一认证报表 cfgIds=" + cfgIds + " subIds=" + subIds
                + " range=" + fromDate + "~" + toDate);

        Map<String, Object> filtered = OaonesParam.of();
        filtered.put("cfgIds", cfgIds.isEmpty() ? null : cfgIds);
        filtered.put("subIds", subIds.isEmpty() ? null : subIds);

        Map<String, Object> allCfgsParam = OaonesParam.of();
        allCfgsParam.put("cfgIds", null);
        allCfgsParam.put("subIds", null);

        List<Map<String, Object>> cfgRows = call(AuthReportMapper.class, m -> m.listCfgs(filtered));
        List<Map<String, Object>> cfgOptionRows = call(AuthReportMapper.class, m -> m.listCfgs(allCfgsParam));
        List<Map<String, Object>> subRows = call(AuthReportMapper.class, m -> m.listSubs());
        List<Map<String, Object>> memberRows = call(AuthReportMapper.class, m -> m.memberCounts(filtered));
        List<Map<String, Object>> boundRows = call(AuthReportMapper.class, m -> m.boundCounts(filtered));

        Map<Integer, Integer> memberCounts = countMap(memberRows, "cfgId", "memberCount");
        Map<Integer, Integer> boundCounts = countMap(boundRows, "cfgId", "boundCount");

        // 今日：固定按“今天”统计，与页面选择的日期范围无关
        Map<String, Object> todayParam = new LinkedHashMap<String, Object>(filtered);
        todayParam.put("granularity", "day");
        todayParam.put("fromDate", today);
        todayParam.put("toDate", today);
        List<Map<String, Object>> todayBind = call(AuthReportMapper.class, m -> m.bindTrend(todayParam));
        List<Map<String, Object>> todayLogin = callLoginTrend(todayParam);

        // 走势：按日 / 按月各取一份，页面切换粒度不再回后端
        Map<String, Object> dayParam = new LinkedHashMap<String, Object>(filtered);
        dayParam.put("granularity", "day");
        dayParam.put("fromDate", fromDate);
        dayParam.put("toDate", toDate);
        Map<String, Object> monthParam = new LinkedHashMap<String, Object>(filtered);
        monthParam.put("granularity", "month");
        monthParam.put("fromDate", fromDate);
        monthParam.put("toDate", toDate);
        List<Map<String, Object>> bindDay = call(AuthReportMapper.class, m -> m.bindTrend(dayParam));
        List<Map<String, Object>> bindMonth = call(AuthReportMapper.class, m -> m.bindTrend(monthParam));
        List<Map<String, Object>> loginDay = callLoginTrend(dayParam);
        List<Map<String, Object>> loginMonth = callLoginTrend(monthParam);

        // ---- 组装返回 ----
        JSONObject data = new JSONObject();
        data.put("filter", filterJson(cfgIds, subIds, fromDate, toDate));
        data.put("cfg_options", cfgJson(cfgOptionRows, null, null));
        data.put("subs", subJson(subRows));
        data.put("cfgs", cfgJson(cfgRows, memberCounts, boundCounts));
        data.put("totals", totalsJson(cfgRows, memberCounts, boundCounts));
        data.put("today", todayJson(today, todayBind, todayLogin));
        JSONObject trend = new JSONObject();
        trend.put("day", trendJson(bindDay, loginDay));
        trend.put("month", trendJson(bindMonth, loginMonth));
        data.put("trend", trend);
        data.put("login_missing", Boolean.valueOf(loginMissing));
        return data;
    }

    /* ------------------------------------------------------------------
     * 查询包装
     * ------------------------------------------------------------------ */

    /** 通用 Mapper 调用模板（与模块其它 Service 同一口径）。 */
    private <R> R call(Class<AuthReportMapper> type,
                       java.util.function.Function<AuthReportMapper, R> work) {
        return OaonesMyBatis.call(type, work);
    }

    /** 登录走势查询：操作监控表可能尚未建表，读不到时退化为空（页面给出提示）。 */
    private List<Map<String, Object>> callLoginTrend(Map<String, Object> params) {
        try {
            List<Map<String, Object>> rows = call(AuthReportMapper.class, m -> m.loginTrend(params));
            loginMissing = false;
            return rows;
        } catch (Throwable t) {
            loginMissing = true;
            OaonesLog.error("统一认证登录走势查询失败（请确认已执行 uf_oaones_appsyn_op_log 建表脚本）", t);
            return new ArrayList<Map<String, Object>>();
        }
    }

    /* ------------------------------------------------------------------
     * JSON 组装
     * ------------------------------------------------------------------ */

    private JSONObject filterJson(List<Integer> cfgIds, List<Integer> subIds,
                                  String fromDate, String toDate) {
        JSONObject json = new JSONObject();
        json.put("cfg_ids", join(cfgIds));
        json.put("sub_ids", join(subIds));
        json.put("from_date", OaonesUtil.s(fromDate));
        json.put("to_date", OaonesUtil.s(toDate));
        json.put("granularity", "day");
        return json;
    }

    private JSONArray cfgJson(List<Map<String, Object>> rows,
                              Map<Integer, Integer> memberCounts,
                              Map<Integer, Integer> boundCounts) {
        JSONArray result = new JSONArray();
        if (rows == null) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            int id = num(row.get("id"));
            JSONObject json = new JSONObject();
            json.put("id", OaonesUtil.nullToEmpty(id));
            json.put("platform", OaonesUtil.nullToEmpty(num(row.get("platform"))));
            json.put("platform_text", platformText(num(row.get("platform"))));
            json.put("cfg_name", OaonesUtil.s(str(row.get("cfgName"))));
            json.put("corp_id", OaonesUtil.s(str(row.get("corpId"))));
            json.put("cfg_enable", OaonesUtil.nullToEmpty(num(row.get("cfgEnable"))));
            if (memberCounts != null) {
                int member = countOf(memberCounts, id);
                int bound = boundCounts == null ? 0 : countOf(boundCounts, id);
                json.put("member_count", Integer.valueOf(member));
                json.put("bound_count", Integer.valueOf(bound));
                json.put("unbound_count", Integer.valueOf(Math.max(member - bound, 0)));
            }
            result.add(json);
        }
        return result;
    }

    private JSONArray subJson(List<Map<String, Object>> rows) {
        JSONArray result = new JSONArray();
        if (rows == null) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            JSONObject json = new JSONObject();
            json.put("id", OaonesUtil.nullToEmpty(num(row.get("id"))));
            json.put("name", OaonesUtil.s(str(row.get("name"))));
            result.add(json);
        }
        return result;
    }

    private JSONObject totalsJson(List<Map<String, Object>> cfgRows,
                                  Map<Integer, Integer> memberCounts,
                                  Map<Integer, Integer> boundCounts) {
        int member = 0;
        int bound = 0;
        if (cfgRows != null) {
            for (Map<String, Object> row : cfgRows) {
                int id = num(row.get("id"));
                member += countOf(memberCounts, id);
                bound += countOf(boundCounts, id);
            }
        }
        JSONObject json = new JSONObject();
        json.put("member_count", Integer.valueOf(member));
        json.put("bound_count", Integer.valueOf(bound));
        json.put("unbound_count", Integer.valueOf(Math.max(member - bound, 0)));
        return json;
    }

    private JSONObject todayJson(String today,
                                 List<Map<String, Object>> bindRows,
                                 List<Map<String, Object>> loginRows) {
        JSONObject json = new JSONObject();
        json.put("date", OaonesUtil.s(today));
        json.put("bind", periodCfgJson(bindRows));
        json.put("login", periodCfgJson(loginRows));
        json.put("bind_total", Integer.valueOf(sum(periodCfgJson(bindRows))));
        json.put("login_total", Integer.valueOf(sum(periodCfgJson(loginRows))));
        return json;
    }

    private JSONObject trendJson(List<Map<String, Object>> bindRows,
                                 List<Map<String, Object>> loginRows) {
        JSONObject json = new JSONObject();
        json.put("bind", periodCfgJson(bindRows));
        json.put("login", periodCfgJson(loginRows));
        return json;
    }

    /** 把 (cfgId, period, cnt) 行转成 [{cfg_id, period, cnt}]。 */
    private JSONArray periodCfgJson(List<Map<String, Object>> rows) {
        JSONArray result = new JSONArray();
        if (rows == null) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            JSONObject json = new JSONObject();
            json.put("cfg_id", OaonesUtil.nullToEmpty(num(row.get("cfgId"))));
            json.put("period", OaonesUtil.s(str(row.get("period"))));
            json.put("cnt", Integer.valueOf(num(row.get("cnt"))));
            result.add(json);
        }
        return result;
    }

    private int sum(JSONArray rows) {
        int total = 0;
        for (int i = 0; i < rows.size(); i++) {
            total += rows.getJSONObject(i).getIntValue("cnt");
        }
        return total;
    }

    /* ------------------------------------------------------------------
     * 工具
     * ------------------------------------------------------------------ */

    private Map<Integer, Integer> countMap(List<Map<String, Object>> rows,
                                           String idKey, String countKey) {
        Map<Integer, Integer> map = new LinkedHashMap<Integer, Integer>();
        if (rows == null) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            map.put(Integer.valueOf(num(row.get(idKey))), Integer.valueOf(num(row.get(countKey))));
        }
        return map;
    }

    private int countOf(Map<Integer, Integer> map, int id) {
        if (map == null) {
            return 0;
        }
        Integer value = map.get(Integer.valueOf(id));
        return value == null ? 0 : value.intValue();
    }

    /** 逗号分隔的 ID 列表 → 去重整数列表（非法项忽略）。 */
    private List<Integer> intList(String value) {
        List<Integer> list = new ArrayList<Integer>();
        String text = OaonesUtil.s(value);
        if (text.trim().length() == 0) {
            return list;
        }
        for (String item : text.split(",")) {
            String one = item.trim();
            if (one.length() == 0) {
                continue;
            }
            int id = OaonesUtil.i(one, 0);
            if (id > 0 && !list.contains(Integer.valueOf(id))) {
                list.add(Integer.valueOf(id));
            }
        }
        return list;
    }

    private String join(List<Integer> list) {
        StringBuilder sb = new StringBuilder();
        for (Integer item : list) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(item);
        }
        return sb.toString();
    }

    private String platformText(int type) {
        if (type == OaonesConst.TYPE_WECHAT) {
            return "企业微信";
        }
        if (type == OaonesConst.TYPE_DINGTALK) {
            return "钉钉";
        }
        if (type == OaonesConst.TYPE_FEISHU) {
            return "飞书";
        }
        return "平台" + type;
    }

    private int num(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return OaonesUtil.i(String.valueOf(value), 0);
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
