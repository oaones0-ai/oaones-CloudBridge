package com.customization.oaones.appsyn.msg.std.service;

import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.msg.std.dao.StdMsgMapper;
import com.customization.oaones.appsyn.msg.std.entity.StdMsgGroup;
import com.customization.oaones.appsyn.msg.std.entity.StdMsgSource;
import com.customization.oaones.appsyn.msg.std.entity.StdMsgType;
import weaver.general.Util;
import weaver.systeminfo.SystemEnv;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 泛微标准「消息类型设置」只读服务（分组 / 消息来源 / 消息类型）。
 *
 * <p><b>口径</b>：本模块的“消息类型”完全引用泛微标准页
 * （后台 应用中心 → 消息类型，页面 {@code /spa/smallApp/static4engine/engine.html#/ecAndEM/messagetypeconfig}）
 * 里的设置——标准页的<b>分组</b>（{@code ecology_biz_mobile_group}）、
 * <b>消息来源</b>（{@code ecology_message_type}）与<b>消息类型</b>
 * （{@code ecology_biz_mobile_config}，用 {@code messagetypeid} 把若干来源打包成一个类型）
 * 原样读进来，本模块只补“推不推 + 推到哪个绑定的第三方应用”等自己的设置，
 * <b>不在本模块重复维护一份类型 / 来源清单</b>，也不改标准表。</p>
 *
 * <p>推送时按标准口径对应：标准消息回调带 {@code typecode}（来源编号）→ 找到包含该来源的
 * 标准消息类型（可能有多个）→ 用该类型在本模块的配置决定怎么推、推到哪个应用。</p>
 *
 * <p><b>缓存</b>：推送路径每条消息都要用这份清单，标准表按 60 秒缓存（页面读的是实时数据，
 * 不受缓存影响）；标准侧改完最迟 1 分钟生效，与 {@code OaonesAdmin} 的缓存口径一致。</p>
 */
public class StdMsgService {

    /** 默认语言（简体中文，与 {@code MsgConst.LANG_ZH_CN} 同口径；本类不依赖各平台的常量类）。 */
    public static final int LANG_ZH_CN = 7;

    /** 标准清单缓存时长（毫秒）：推送路径用。 */
    private static final long CACHE_MS = 60 * 1000L;
    /** 读取失败时的缓存时长（毫秒）：避免一次库抖动导致后续 1 分钟都取不到清单。 */
    private static final long FAIL_CACHE_MS = 30 * 1000L;

    /** 按语言缓存（名称已按语言取成纯文本）。 */
    private static final Map<Integer, CachedSnapshot> CACHE = new HashMap<Integer, CachedSnapshot>();

    /** 标准清单快照：分组 / 来源 / 模块 / 消息类型，外加按 typecode 反查的索引。 */
    public static final class Snapshot {
        private final List<StdMsgGroup> groups = new ArrayList<StdMsgGroup>();
        private final List<StdMsgSource> sources = new ArrayList<StdMsgSource>();
        private final List<StdMsgType> types = new ArrayList<StdMsgType>();
        /** 读取过程中的告警（某张标准表读不到时记一条，页面提示用；推送路径只记日志）。 */
        private final List<String> warnings = new ArrayList<String>();
        private final Map<Integer, StdMsgSource> sourceById = new HashMap<Integer, StdMsgSource>();
        private final Map<String, StdMsgType> typeById = new HashMap<String, StdMsgType>();
        private final Map<Integer, StdMsgGroup> groupById = new HashMap<Integer, StdMsgGroup>();
        private final Map<Integer, List<StdMsgType>> typesByTypeCode = new HashMap<Integer, List<StdMsgType>>();

        public List<StdMsgGroup> getGroups() { return groups; }

        public List<StdMsgSource> getSources() { return sources; }

        public List<StdMsgType> getTypes() { return types; }

        public List<String> getWarnings() { return warnings; }

        public StdMsgSource sourceById(int id) { return sourceById.get(id); }

        /** 按标准消息类型ID（{@code ecology_biz_mobile_config.id}，<b>字符串</b>）取类型。 */
        public StdMsgType typeById(String id) {
            return id == null ? null : typeById.get(id.trim());
        }

        public StdMsgGroup groupById(int id) { return groupById.get(id); }

        /** 包含该来源编号（typecode）的标准消息类型，按标准列表顺序（ID 升序）。 */
        public List<StdMsgType> typesOfTypeCode(int typeCode) {
            List<StdMsgType> list = typesByTypeCode.get(typeCode);
            return list == null ? new ArrayList<StdMsgType>() : list;
        }

        /** 该来源编号是否已被某个标准消息类型包含（没有包含时本模块只能按“独立来源”处理）。 */
        public boolean covered(int typeCode) {
            return typeCode > 0 && typesByTypeCode.containsKey(typeCode);
        }

        /** 没被任何标准消息类型包含的标准来源（页面提示用：这类来源在标准页里还没配到类型上）。 */
        public List<StdMsgSource> uncoveredSources() {
            List<StdMsgSource> list = new ArrayList<StdMsgSource>();
            for (StdMsgSource source : sources) {
                if (source != null && source.getTypeCode() > 0 && !covered(source.getTypeCode())) {
                    list.add(source);
                }
            }
            return list;
        }

        /** 标准类型总数（页面提示用）。 */
        public int typeCount() { return types.size(); }
    }

    private static final class CachedSnapshot {
        private final Snapshot snapshot;
        private final long expireAt;

        private CachedSnapshot(Snapshot snapshot, long expireAt) {
            this.snapshot = snapshot;
            this.expireAt = expireAt;
        }
    }

    // ------------------------------------------------------------------ 读取

    /** 推送路径用：取标准清单（按语言缓存 60 秒；读失败缓存 30 秒后重试）。 */
    public static Snapshot snapshot(int lang) {
        int key = lang <= 0 ? LANG_ZH_CN : lang;
        CachedSnapshot cached = CACHE.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expireAt > now) {
            return cached.snapshot;
        }
        Snapshot snapshot = null;
        boolean failed = false;
        try {
            snapshot = load(key);
        } catch (Throwable t) {
            failed = true;
            OaonesLog.error("[msg] 读取泛微标准消息类型设置失败（推送按“未配置”处理）", t);
        }
        if (snapshot == null) {
            snapshot = new Snapshot();
        }
        CACHE.put(key, new CachedSnapshot(snapshot, now + (failed ? FAIL_CACHE_MS : CACHE_MS)));
        return snapshot;
    }

    /** 页面用：实时读取标准清单（不走缓存，标准页改完刷新页面即可看到）。 */
    public static Snapshot load(int lang) {
        int language = lang <= 0 ? LANG_ZH_CN : lang;
        Snapshot snapshot = new Snapshot();

        // 所属模块（ecology_message_group）：名称是标签索引，取标签名，取不到退化成 groupcode
        Map<Integer, String> moduleNames = new HashMap<Integer, String>();
        List<Map<String, Object>> modules = quiet(new Loader<List<Map<String, Object>>>() {
            @Override
            public List<Map<String, Object>> get() {
                return OaonesMyBatis.call(StdMsgMapper.class, mapper -> mapper.listModules(language));
            }
        }, snapshot, "所属模块（ecology_message_group）");
        if (modules != null) {
            for (Map<String, Object> row : modules) {
                int id = OaonesUtil.i(field(row, "id"), 0);
                if (id <= 0) {
                    continue;
                }
                moduleNames.put(id, moduleNameOf(field(row, "groupname"), field(row, "groupcode"), language, id));
            }
        }

        // 分组设置（ecology_biz_mobile_group）
        List<Map<String, Object>> groups = quiet(new Loader<List<Map<String, Object>>>() {
            @Override
            public List<Map<String, Object>> get() {
                return OaonesMyBatis.call(StdMsgMapper.class, mapper -> mapper.listGroups());
            }
        }, snapshot, "分组设置（ecology_biz_mobile_group）");
        if (groups != null) {
            for (Map<String, Object> row : groups) {
                StdMsgGroup group = new StdMsgGroup();
                group.setId(OaonesUtil.i(field(row, "id"), 0));
                group.setName(text(field(row, "name"), language));
                group.setShowNum(OaonesUtil.i(field(row, "shownum"), 0));
                group.setStatus(field(row, "status"));
                snapshot.groups.add(group);
                snapshot.groupById.put(group.getId(), group);
            }
        }

        // 消息来源（ecology_message_type）
        List<Map<String, Object>> sources = quiet(new Loader<List<Map<String, Object>>>() {
            @Override
            public List<Map<String, Object>> get() {
                return OaonesMyBatis.call(StdMsgMapper.class, mapper -> mapper.listSources(language));
            }
        }, snapshot, "消息来源（ecology_message_type）");
        if (sources != null) {
            for (Map<String, Object> row : sources) {
                StdMsgSource source = new StdMsgSource();
                source.setId(OaonesUtil.i(field(row, "id"), 0));
                source.setTypeCode(OaonesUtil.i(field(row, "typecode"), 0));
                source.setName(sourceNameOf(field(row, "typename"), field(row, "sourcename"),
                        language, source.getTypeCode()));
                source.setModuleId(OaonesUtil.i(field(row, "moduleid"), 0));
                String moduleName = moduleNames.get(source.getModuleId());
                source.setModuleName(moduleName == null ? "" : moduleName);
                source.setDefault("y".equalsIgnoreCase(field(row, "isdefault")));
                snapshot.sources.add(source);
                if (source.getId() > 0) {
                    snapshot.sourceById.put(source.getId(), source);
                }
            }
        }

        // 消息类型（ecology_biz_mobile_config）
        List<Map<String, Object>> types = quiet(new Loader<List<Map<String, Object>>>() {
            @Override
            public List<Map<String, Object>> get() {
                return OaonesMyBatis.call(StdMsgMapper.class, mapper -> mapper.listTypes());
            }
        }, snapshot, "消息类型（ecology_biz_mobile_config）");
        if (types != null) {
            for (Map<String, Object> row : types) {
                StdMsgType type = new StdMsgType();
                // id 是 varchar：既可能是 "20" 这种数字编号，也可能是自定义类型的 "1LbVgIrt"，一律按原文取
                type.setId(field(row, "id"));
                type.setName(text(field(row, "name"), language));
                type.setGroupId(OaonesUtil.i(field(row, "groupid"), 0));
                StdMsgGroup group = snapshot.groupById.get(type.getGroupId());
                type.setGroupName(group == null ? "" : group.getName());
                type.setModuleId(OaonesUtil.i(field(row, "moduleid"), 0));
                String moduleName = moduleNames.get(type.getModuleId());
                type.setModuleName(moduleName == null ? "" : moduleName);
                type.setEnable(field(row, "enable"));
                type.setEnablePc(field(row, "enablepc"));
                type.setEnableEm(field(row, "enableem"));
                type.setSendMobile(field(row, "sendmobile"));
                type.setDefault("y".equalsIgnoreCase(field(row, "isdefault")));
                type.setAgentId(field(row, "agentid"));
                type.setImgUrl(field(row, "imgurl"));
                type.setPicUrl(field(row, "picture_url"));
                type.setPcUrl(field(row, "pc_url"));
                type.setDialogUrl(field(row, "dialogurl"));
                type.setEmAppUrl(field(row, "em_app_url"));
                // messagetypeid 存的是 ecology_message_type.id 列表（,12,13,），逐个换成来源
                List<StdMsgSource> typeSources = new ArrayList<StdMsgSource>();
                for (Integer sourceId : splitInt(field(row, "messagetypeid"))) {
                    StdMsgSource source = snapshot.sourceById.get(sourceId);
                    if (source != null) {
                        typeSources.add(source);
                    }
                }
                type.setSources(typeSources);
                snapshot.types.add(type);
                if (type.getId().length() > 0) {
                    snapshot.typeById.put(type.getId(), type);
                }
                for (StdMsgSource source : typeSources) {
                    List<StdMsgType> bucket = snapshot.typesByTypeCode.get(source.getTypeCode());
                    if (bucket == null) {
                        bucket = new ArrayList<StdMsgType>();
                        snapshot.typesByTypeCode.put(source.getTypeCode(), bucket);
                    }
                    bucket.add(type);
                }
            }
        }
        return snapshot;
    }

    /** 清缓存（标准页改完立刻要生效时用；正常情况下 60 秒自动过期）。 */
    public static void invalidate() {
        CACHE.clear();
    }

    // ------------------------------------------------------------------ 名称取值

    /** 单张标准表的读取回调（读失败只记告警，不影响其余表）。 */
    private interface Loader<T> {
        T get();
    }

    /**
     * 容错读取一张标准表：读不到（没装消息中心 / 表不存在 / 库异常）时记一条告警并返回 null，
     * 保证“标准页的某一部分读不出来”不会让整个消息类型页面或推送挂掉。
     */
    private static <T> T quiet(Loader<T> loader, Snapshot snapshot, String what) {
        try {
            return loader.get();
        } catch (Throwable t) {
            String message = t.getMessage() == null ? t.getClass().getName() : t.getMessage();
            snapshot.warnings.add("读取 " + what + " 失败：" + message);
            OaonesLog.error("[msg] 读取 " + what + " 失败（该项按空处理）", t);
            return null;
        }
    }

    /**
     * 标准侧文本取值（分组名 / 类型名 / 来源名统一走这里）：
     * ① {@code base64_} 前缀的值先解码（前端多语言组件口径）；
     * ② 多语言标记串 {@code ~`~`7 中文`~`8 English`~`~} 按当前语言取成纯文本；
     * ③ 普通文本原样返回。
     */
    public static String text(String raw, int lang) {
        String value = OaonesUtil.s(raw);
        if (value.length() == 0) {
            return "";
        }
        if (value.startsWith("base64_")) {
            value = decodeBase64(value.substring("base64_".length()));
        }
        return Util.formatMultiLang(value, String.valueOf(lang <= 0 ? LANG_ZH_CN : lang));
    }

    /** 标签索引名称（{@code ecology_message_group.groupname} 这类“标签索引”列）：取标签名，取不到退化成原始值。 */
    public static String labelName(String raw, int lang) {
        String value = text(raw, lang);
        int index = OaonesUtil.i(value, 0);
        if (index <= 0) {
            return "";
        }
        try {
            String label = SystemEnv.getHtmlLabelName(index, lang <= 0 ? LANG_ZH_CN : lang);
            return label == null ? "" : label.trim();
        } catch (Throwable t) {
            return "";
        }
    }

    /** 来源名称：标签名（typename 是标签索引）→ 来源名文本（sourcename 多语言）→ “消息来源 <typecode>”。 */
    private static String sourceNameOf(String typename, String sourcename, int lang, int typeCode) {
        String label = labelName(typename, lang);
        if (label.length() > 0) {
            return label;
        }
        String name = text(sourcename, lang);
        if (name.length() > 0) {
            return name;
        }
        return typeCode > 0 ? "消息来源 " + typeCode : "";
    }

    /** 所属模块名称：标签名 → groupcode → “模块 <id>”。 */
    private static String moduleNameOf(String groupname, String groupcode, int lang, int id) {
        String label = labelName(groupname, lang);
        if (label.length() > 0) {
            return label;
        }
        String code = text(groupcode, lang);
        if (code.length() > 0) {
            return code;
        }
        return "模块 " + id;
    }

    private static String decodeBase64(String value) {
        if (value == null || value.length() == 0) {
            return "";
        }
        try {
            byte[] bytes = java.util.Base64.getMimeDecoder().decode(value.trim());
            return new String(bytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        } catch (Throwable t) {
            return value;
        }
    }

    // ------------------------------------------------------------------ 小工具

    /** 逗号 / 顿号 / 空格分隔的数字串 → 去重后的整数列表（类型里的 messagetypeid 形如 ,12,13,）。 */
    public static List<Integer> splitInt(String value) {
        List<Integer> list = new ArrayList<Integer>();
        String raw = OaonesUtil.s(value);
        if (raw.trim().length() == 0) {
            return list;
        }
        for (String item : raw.split("[,，\\s]+")) {
            int number = OaonesUtil.i(item.trim(), 0);
            if (number > 0 && !list.contains(number)) {
                list.add(number);
            }
        }
        return list;
    }

    /** MyBatis 返回的 Map 在别名大小写上可能有差异，统一按小写再按大写取一次。 */
    private static String field(Map<String, Object> row, String key) {
        if (row == null) {
            return "";
        }
        Object value = row.get(key);
        if (value == null) {
            value = row.get(key.toUpperCase());
        }
        return value == null ? "" : OaonesUtil.s(String.valueOf(value));
    }

    /** 展示用：来源名称列表（顿号分隔）。 */
    public static String sourceNames(List<StdMsgSource> sources) {
        StringBuilder sb = new StringBuilder();
        for (StdMsgSource source : sources) {
            if (source == null) {
                continue;
            }
            String name = source.getName();
            if (name.length() == 0) {
                name = "消息来源 " + source.getTypeCode();
            }
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(name);
        }
        return sb.toString();
    }

    /** 页面接口通用的“空对象”兜底（避免调用方到处判空）。 */
    public static Map<String, Object> emptyRow() {
        return new LinkedHashMap<String, Object>();
    }
}
