package com.customization.oaones.appsyn.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesAdmin;
import com.customization.oaones.appsyn.common.LogRetentionCatalog;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesOpLog;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dao.OpLogMapper;
import com.customization.oaones.appsyn.entity.OpLog;
import com.customization.oaones.appsyn.service.OpLogService;

import java.util.List;
import java.util.Map;

/**
 * 操作监控平台服务实现（DAO 层为 {@link OpLogMapper}）。
 *
 * <p>分权：只有系统管理员（user.id=1）与 {@code oaones.properties#opmonitor.admin.ids}
 * 可以查看；其它登录人打开页面只看到说明，接口层一律拒绝（本页不做“按租户 / 按部门”的裁剪，
 * 避免把审计数据按业务分权拆碎）。</p>
 */
public class OpLogServiceImpl implements OpLogService {

    private String table() {
        return OaonesProps.opLogTable();
    }

    @Override
    public boolean canView(String operatorId) {
        return isViewer(operatorId);
    }

    @Override
    public JSONObject query(JSONObject params, String operatorId) {
        requireViewer(operatorId);
        Integer userId = nullableInt(params.getString("user_id"));
        String opType = OaonesUtil.s(params.getString("op_type")).trim();
        String menuId = OaonesUtil.s(params.getString("menu_id")).trim();
        String keyword = OaonesUtil.s(params.getString("keyword")).trim();
        String userKeyword = OaonesUtil.s(params.getString("user_keyword")).trim();
        String[] from = OaonesUtil.splitDateTime(OaonesUtil.s(params.getString("from_time")), "00:00:00");
        String[] to = OaonesUtil.splitDateTime(OaonesUtil.s(params.getString("to_time")), "23:59:59");
        int page = Math.max(OaonesUtil.i(OaonesUtil.s(params.getString("page")), 1), 1);
        int size = OaonesUtil.i(OaonesUtil.s(params.getString("size")), 200);
        size = size < 1 ? 200 : (size > 1000 ? 1000 : size);
        int offset = (page - 1) * size;

        final Integer fUser = userId;
        final String fType = opType.length() == 0 ? null : opType;
        final String fMenu = menuId.length() == 0 ? null : menuId;
        final String fKeyword = keyword.length() == 0 ? null : keyword;
        final String fUserKeyword = userKeyword.length() == 0 ? null : userKeyword;
        final String fFromDate = from[0];
        final String fFromTime = from[1];
        final String fToDate = to[0];
        final String fToTime = to[1];
        final int fOffset = offset;
        final int fSize = size;

        JSONObject data = new JSONObject();
        JSONArray rows = new JSONArray();
        int total = 0;
        try {
            List<OpLog> list = OaonesMyBatis.call(OpLogMapper.class, mapper -> mapper.page(
                    table(), fUser, fType, fMenu, fKeyword, fUserKeyword,
                    fFromDate, fFromTime, fToDate, fToTime, fOffset, fSize));
            if (list != null) {
                for (OpLog row : list) {
                    rows.add(rowJson(row));
                }
            }
            Integer count = OaonesMyBatis.call(OpLogMapper.class, mapper -> mapper.count(
                    table(), fUser, fType, fMenu, fKeyword, fUserKeyword,
                    fFromDate, fFromTime, fToDate, fToTime));
            total = count == null ? 0 : count.intValue();
        } catch (Throwable t) {
            // 表还没建：给出可读提示，而不是 500
            OaonesLog.error("查询操作监控日志失败（请确认已执行 uf_oaones_op_log 建表脚本）", t);
            data.put("rows", rows);
            data.put("total", Integer.valueOf(0));
            data.put("page", Integer.valueOf(page));
            data.put("size", Integer.valueOf(size));
            data.put("stats", new JSONObject());
            data.put("missing", Boolean.TRUE);
            return data;
        }
        data.put("rows", rows);
        data.put("total", Integer.valueOf(total));
        data.put("page", Integer.valueOf(page));
        data.put("size", Integer.valueOf(size));
        data.put("stats", stats(fUser, fMenu, fKeyword, fUserKeyword, fFromDate, fFromTime, fToDate, fToTime));
        return data;
    }

    @Override
    public JSONObject options(String operatorId) {
        JSONObject data = new JSONObject();
        data.put("admin", Boolean.valueOf(isViewer(operatorId)));
        data.put("enabled", Boolean.valueOf(OaonesProps.opLogEnabled()));
        data.put("keepDays", Integer.valueOf(new LogRetentionServiceImpl().keepDays(LogRetentionCatalog.OP_LOG)));
        data.put("dedupeSeconds", Integer.valueOf(OaonesProps.opLogDedupeSeconds()));

        JSONArray types = new JSONArray();
        types.add(typeOption(OpLog.TYPE_LOGIN));
        types.add(typeOption(OpLog.TYPE_LOGOUT));
        types.add(typeOption(OpLog.TYPE_MENU));
        data.put("types", types);

        JSONArray menus = new JSONArray();
        try {
            List<OaonesOpLog.MenuItem> items = OaonesOpLog.allMenus();
            if (items != null) {
                for (OaonesOpLog.MenuItem item : items) {
                    JSONObject json = new JSONObject();
                    json.put("value", item.getId());
                    String name = OaonesUtil.s(item.getName());
                    json.put("text", (name.length() == 0 ? ("菜单#" + item.getId()) : name)
                            + "（#" + item.getId() + "）");
                    menus.add(json);
                }
            }
        } catch (Throwable t) {
            OaonesLog.error("读取 E9 菜单列表失败（页面“菜单”筛选项为空）", t);
        }
        java.util.Collections.sort(menus, new java.util.Comparator<Object>() {
            @Override
            public int compare(Object a, Object b) {
                String sa = ((JSONObject) a).getString("text");
                String sb = ((JSONObject) b).getString("text");
                return OaonesUtil.s(sa).compareTo(OaonesUtil.s(sb));
            }
        });
        data.put("menus", menus);
        return data;
    }

    @Override
    public void cleanIfDue() {
        // 自动清理由 OaonesLogRetentionScheduler 每天统一执行；这里仅确保调度器已启动。
        com.customization.oaones.appsyn.job.OaonesLogRetentionScheduler.ensureStarted();
    }

    @Override
    public JSONObject clean(String operatorId) {
        requireViewer(operatorId);
        int keepDays = new LogRetentionServiceImpl().keepDays(LogRetentionCatalog.OP_LOG);
        JSONObject data = new JSONObject();
        if (keepDays <= 0) {
            data.put("deleted", Integer.valueOf(0));
            data.put("message", "当前为永久保留（系统设置 → 日志保留），未清理");
            return data;
        }
        return new LogRetentionServiceImpl().cleanNow(LogRetentionCatalog.OP_LOG);
    }

    /* ------------------------------------------------------------------
     * 内部
     * ------------------------------------------------------------------ */

    private JSONObject stats(Integer userId, String menuId, String keyword,
                             String userKeyword,
                             String fromDate, String fromTime, String toDate, String toTime) {
        JSONObject stats = new JSONObject();
        try {
            List<Map<String, Object>> list = OaonesMyBatis.call(OpLogMapper.class,
                    mapper -> mapper.groupByType(table(), userId, menuId, keyword, userKeyword,
                            fromDate, fromTime, toDate, toTime));
            JSONArray types = new JSONArray();
            int total = 0;
            int login = 0;
            int logout = 0;
            int menu = 0;
            if (list != null) {
                for (Map<String, Object> row : list) {
                    String code = OaonesUtil.s(row.get("opType") == null ? "" : String.valueOf(row.get("opType")));
                    int count = OaonesUtil.i(row.get("totalCount") == null ? "" : String.valueOf(row.get("totalCount")), 0);
                    total += count;
                    if (OpLog.TYPE_LOGIN.equals(code)) {
                        login = count;
                    } else if (OpLog.TYPE_LOGOUT.equals(code)) {
                        logout = count;
                    } else if (OpLog.TYPE_MENU.equals(code)) {
                        menu = count;
                    }
                    JSONObject json = new JSONObject();
                    json.put("key", code);
                    json.put("keyText", OpLog.typeText(code));
                    json.put("total", Integer.valueOf(count));
                    types.add(json);
                }
            }
            stats.put("total", Integer.valueOf(total));
            stats.put("login", Integer.valueOf(login));
            stats.put("logout", Integer.valueOf(logout));
            stats.put("menu", Integer.valueOf(menu));
            stats.put("types", types);
        } catch (Throwable ignore) {
            stats.put("total", Integer.valueOf(0));
        }
        return stats;
    }

    private JSONObject rowJson(OpLog row) {
        JSONObject json = new JSONObject();
        json.put("id", row.getId());
        json.put("user_id", row.getUserId());
        json.put("login_name", OaonesUtil.s(row.getLoginName()));
        json.put("user_name", OaonesUtil.s(row.getUserName()));
        json.put("dept_name", OaonesUtil.s(row.getDeptName()));
        json.put("op_type", OaonesUtil.s(row.getOpType()));
        json.put("op_type_text", OpLog.typeText(row.getOpType()));
        json.put("menu_id", OaonesUtil.s(row.getMenuId()));
        json.put("menu_name", OaonesUtil.s(row.getMenuName()));
        json.put("menu_path", OaonesUtil.s(row.getMenuPath()));
        json.put("page_url", OaonesUtil.s(row.getPageUrl()));
        json.put("client_ip", OaonesUtil.s(row.getClientIp()));
        json.put("device", OaonesUtil.s(row.getDevice()));
        json.put("create_date", OaonesUtil.s(row.getModedatacreatedate()));
        json.put("create_time", OaonesUtil.s(row.getModedatacreatetime()));
        json.put("create_at", OaonesUtil.s(row.getModedatacreatedate())
                + " " + OaonesUtil.s(row.getModedatacreatetime()));
        return json;
    }

    private JSONObject typeOption(String code) {
        JSONObject json = new JSONObject();
        json.put("value", code);
        json.put("text", OpLog.typeText(code));
        return json;
    }

    /** 非查看人直接拒绝（接口层兜底）。 */
    private void requireViewer(String operatorId) {
        if (!isViewer(operatorId)) {
            throw new RuntimeException("操作监控平台仅对系统管理员或被授权人员开放");
        }
    }

    /** 查看人：系统管理员（user.id=1）、超管角色（total.admin.roleid）或 oaones.properties#opmonitor.admin.ids。 */
    private boolean isViewer(String operatorId) {
        int uid = OaonesUtil.i(operatorId, 0);
        if (OaonesAdmin.isTotalAdmin(operatorId)) {
            return true;
        }
        String ids = OaonesProps.opMonitorAdminIds();
        if (ids != null && ids.trim().length() > 0) {
            for (String item : ids.split(",")) {
                if (uid > 0 && item.trim().equals(String.valueOf(uid))) {
                    return true;
                }
            }
        }
        return false;
    }

    private Integer nullableInt(String value) {
        String s = OaonesUtil.s(value).trim();
        if (s.length() == 0) {
            return null;
        }
        Integer i = OaonesUtil.integer(s);
        return i;
    }
}
