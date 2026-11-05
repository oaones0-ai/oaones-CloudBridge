package com.customization.oaones.appsyn.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统设置 → 功能设置的开关目录。
 *
 * <p>代码只定义页面展示与菜单显隐所需的开关，不负责拦截业务接口。</p>
 */
public final class FeatureSettingCatalog {

    public static final String PERSON_SYNC_OA2THIRD = "person.sync.oa2third";
    public static final String PERSON_SYNC_THIRD2OA = "person.sync.third2oa";
    public static final String PERSON_SYNC_SCHEDULE = "person.sync.schedule";

    public static final String APP_BIND = "app.bind";

    public static final String USER_BIND_SYNC = "user.bind.sync";
    public static final String USER_BIND_SYNC_OA2THIRD = "user.bind.sync.oa2third";
    public static final String USER_BIND_SYNC_THIRD2OA = "user.bind.sync.third2oa";
    public static final String USER_BIND_SELF = "user.bind.self";
    public static final String USER_BIND_EXPORT = "user.bind.export";

    public static final String ATT_SYNC = "att.sync";
    public static final String ATT_SYNC_OA2THIRD = "att.sync.oa2third";
    public static final String ATT_SYNC_THIRD2OA = "att.sync.third2oa";

    public static final String MTG_SYNC = "mtg.sync";
    public static final String MTG_SYNC_OA2THIRD = "mtg.sync.oa2third";
    public static final String MTG_SYNC_THIRD2OA = "mtg.sync.third2oa";

    public static final String SCHEDULE_SYNC = "schedule.sync";
    public static final String SCHEDULE_SYNC_OA2THIRD = "schedule.sync.oa2third";
    public static final String SCHEDULE_SYNC_THIRD2OA = "schedule.sync.third2oa";

    private static final List<Definition> ALL;

    static {
        List<Definition> rows = new ArrayList<Definition>();
        rows.add(new Definition(PERSON_SYNC_OA2THIRD, "人员同步", "人员同步设置",
                "OA → 第三方同步", "关闭后隐藏同步中心中的 OA → 第三方人员同步入口。", null, ""));
        rows.add(new Definition(PERSON_SYNC_THIRD2OA, "人员同步", "人员同步设置",
                "第三方 → OA 同步", "关闭后隐藏同步中心中的第三方 → OA 人员同步入口。", null, ""));
        rows.add(new Definition(PERSON_SYNC_SCHEDULE, "人员同步", "人员同步设置",
                "定时同步", "关闭后不显示同步中心；已有启用中的同步任务时不能关闭，禁用任务不占用开关。",
                null, "sync_plan"));

        rows.add(new Definition(APP_BIND, "应用绑定", "应用绑定",
                "应用绑定", "关闭后不显示应用绑定菜单；存在启用中的应用绑定时不能关闭，禁用应用不占用开关。",
                null, "app_bind"));

        rows.add(new Definition(USER_BIND_SYNC, "人员绑定", "人员绑定",
                "同步人员", "关闭后在人员绑定页面隐藏同步人员按钮。", null, ""));
        rows.add(new Definition(USER_BIND_SYNC_OA2THIRD, "人员绑定", "人员绑定",
                "人员同步方向：OA → 第三方", "控制人员绑定同步按钮中的 OA → 第三方方向。",
                USER_BIND_SYNC, ""));
        rows.add(new Definition(USER_BIND_SYNC_THIRD2OA, "人员绑定", "人员绑定",
                "人员同步方向：第三方 → OA", "控制人员绑定同步按钮中的第三方 → OA 方向。",
                USER_BIND_SYNC, ""));
        rows.add(new Definition(USER_BIND_SELF, "人员绑定", "人员绑定",
                "个人自助绑定", "关闭后人员只能由管理员设置绑定，个人账号中心不显示扫码绑定入口。",
                null, ""));
        rows.add(new Definition(USER_BIND_EXPORT, "人员绑定", "人员绑定",
                "导出 Excel", "关闭后在人员绑定页面隐藏导出 Excel 按钮。", null, ""));

        rows.add(new Definition(ATT_SYNC, "考勤同步", "考勤同步",
                "考勤同步", "关闭后隐藏考勤同步菜单，不影响已有接口与历史数据。", null, ""));
        rows.add(new Definition(ATT_SYNC_OA2THIRD, "考勤同步", "考勤同步",
                "同步方向：OA → 第三方", "控制考勤同步页面是否显示 OA → 第三方方向。",
                ATT_SYNC, ""));
        rows.add(new Definition(ATT_SYNC_THIRD2OA, "考勤同步", "考勤同步",
                "同步方向：第三方 → OA", "控制考勤同步页面是否显示第三方 → OA 方向。",
                ATT_SYNC, ""));

        rows.add(new Definition(MTG_SYNC, "会议同步", "会议同步",
                "会议同步", "关闭后隐藏会议同步菜单，不影响已有接口与历史数据。", null, ""));
        rows.add(new Definition(MTG_SYNC_OA2THIRD, "会议同步", "会议同步",
                "同步方向：OA → 第三方", "控制会议同步页面是否显示 OA → 第三方方向。",
                MTG_SYNC, ""));
        rows.add(new Definition(MTG_SYNC_THIRD2OA, "会议同步", "会议同步",
                "同步方向：第三方 → OA", "控制会议同步页面是否显示第三方 → OA 方向。",
                MTG_SYNC, ""));

        rows.add(new Definition(SCHEDULE_SYNC, "日程同步", "日程同步",
                "日程同步", "关闭后隐藏日程同步菜单，不影响已有接口与历史数据。", null, ""));
        rows.add(new Definition(SCHEDULE_SYNC_OA2THIRD, "日程同步", "日程同步",
                "同步方向：OA → 第三方", "控制日程同步页面是否显示 OA → 第三方方向。",
                SCHEDULE_SYNC, ""));
        rows.add(new Definition(SCHEDULE_SYNC_THIRD2OA, "日程同步", "日程同步",
                "同步方向：第三方 → OA", "控制日程同步页面是否显示第三方 → OA 方向。",
                SCHEDULE_SYNC, ""));
        ALL = Collections.unmodifiableList(rows);
    }

    private FeatureSettingCatalog() {
    }

    public static List<Definition> all() {
        return ALL;
    }

    public static Definition find(String code) {
        String value = OaonesUtil.s(code).trim();
        for (Definition definition : ALL) {
            if (definition.getCode().equals(value)) {
                return definition;
            }
        }
        return null;
    }

    /** 没有配置行时的默认值：全部启用，保持升级前的页面行为。 */
    public static Map<String, Boolean> defaultEnabledMap() {
        Map<String, Boolean> map = new LinkedHashMap<String, Boolean>();
        for (Definition definition : ALL) {
            map.put(definition.getCode(), Boolean.TRUE);
        }
        return map;
    }

    public static final class Definition {
        private final String code;
        private final String group;
        private final String module;
        private final String name;
        private final String description;
        private final String parentCode;
        private final String dependency;

        public Definition(String code, String group, String module, String name, String description,
                          String parentCode, String dependency) {
            this.code = code;
            this.group = group;
            this.module = module;
            this.name = name;
            this.description = description;
            this.parentCode = parentCode;
            this.dependency = dependency;
        }

        public String getCode() {
            return code;
        }

        public String getGroup() {
            return group;
        }

        public String getModule() {
            return module;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getParentCode() {
            return parentCode;
        }

        public String getDependency() {
            return dependency;
        }
    }
}
