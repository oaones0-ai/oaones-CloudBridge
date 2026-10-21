package com.customization.oaones.appsyn.msg.dingtalk.service;

import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.msg.dingtalk.common.DingTalkMsgConst;
import com.customization.oaones.appsyn.msg.dingtalk.common.DingTalkMsgProps;
import com.customization.oaones.appsyn.msg.dingtalk.dao.MsgTypeMapper;
import com.customization.oaones.appsyn.msg.dingtalk.entity.MsgTypeDef;
import com.customization.oaones.appsyn.msg.std.entity.StdMsgGroup;
import com.customization.oaones.appsyn.msg.std.entity.StdMsgSource;
import com.customization.oaones.appsyn.msg.std.entity.StdMsgType;
import com.customization.oaones.appsyn.msg.std.service.StdMsgService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 消息类型推送设置服务（泛微标准「消息类型」→ 本模块的推送开关 / 推送目标应用 / 钉钉形态与模板）。
 *
 * <p><b>口径</b>：类型与来源都以泛微标准页（后台 应用中心 → 消息类型）为准——标准页的
 * 分组 / 消息来源 / 消息类型原样读进来（见 {@link StdMsgService}），本模块只在这些类型上补
 * “推不推 + 推到哪个绑定的第三方应用 + 用什么形态推”，<b>不再自己维护一份类型清单</b>：
 * 标准页新建类型后刷新本页即可看到，标准页改了来源归属也立即跟着变。</p>
 */
public class MsgTypeService {

    /** 任务卡片按钮：同意 / 驳回。 */
    private static final String BTN_AGREE_REJECT =
            "[{\"key\":\"agree\",\"name\":\"同意\",\"replace_name\":\"已同意\",\"color\":\"blue\",\"is_bold\":true},"
            + "{\"key\":\"reject\",\"name\":\"驳回\",\"replace_name\":\"已驳回\",\"color\":\"red\",\"is_bold\":false}]";

    /** 任务卡片按钮：处理。 */
    private static final String BTN_AGREE =
            "[{\"key\":\"agree\",\"name\":\"处理\",\"replace_name\":\"已处理\",\"color\":\"blue\",\"is_bold\":true}]";

    /** 多语言取值（把平台表里 ~`~`7 中文`~`8 English`~`~ 这类标记串取成纯文本）。 */
    private final MsgRenderService renderService = new MsgRenderService();

    /** 内置标准消息类型清单（平台表 ECOLOGY_MESSAGE_TYPE 读不到时的兜底，口径与泛微消息类型一致）。 */
    private static final int[] BUILTIN_TYPE_CODES = {
            20, 21, 22, 23, 24, 25, 27, 33, 34, 35, 38, 61, 62, 63, 64,
            81, 118, 119, 120, 124, 125, 126, 127
    };

    public List<MsgTypeDef> list() {
        return OaonesMyBatis.call(MsgTypeMapper.class, mapper -> mapper.list(DingTalkMsgProps.typeTable()));
    }

    public List<MsgTypeDef> listEnabled() {
        return OaonesMyBatis.call(MsgTypeMapper.class, mapper -> mapper.listEnabled(DingTalkMsgProps.typeTable()));
    }

    public MsgTypeDef getById(int id) {
        if (id <= 0) {
            return null;
        }
        return OaonesMyBatis.call(MsgTypeMapper.class, mapper -> mapper.getById(DingTalkMsgProps.typeTable(), id));
    }

    /**
     * 按标准消息来源编号（typecode）取启用中的推送设置；不该推送返回 null。
     *
     * <p>供“外部统一消息接口 / 页面直推”这类只给了 typecode 的场景使用
     * （标准消息订阅回调走 {@link #findForPush(int, int)}，口径一致，差别只在后者会按需自动登记）。</p>
     */
    public MsgTypeDef findEnabledByStdType(int stdType) {
        MsgTypeDef def = resolveByStandard(stdType, DingTalkMsgConst.LANG_ZH_CN);
        if (def == null) {
            // 未纳入任何标准消息类型的独立来源：退回本模块自己的行（不自动登记）
            def = getByStdType(stdType);
        }
        if (def == null) {
            return null;
        }
        Integer yx = def.getYx();
        return yx != null && yx == DingTalkMsgConst.STATE_ENABLE ? def : null;
    }

    /**
     * 按“未纳入标准消息类型的独立来源”编号取本模块的行（不论启用 / 停用）；没有返回 null。
     *
     * <p>已归入标准消息类型的行 {@code std_config_id} 非空、{@code std_type = 0}，
     * 不会在这里被匹配到（来源编号与名称都从标准侧实时读取）。</p>
     */
    public MsgTypeDef getByStdType(int stdType) {
        if (stdType <= 0) {
            return null;
        }
        return OaonesMyBatis.call(MsgTypeMapper.class,
                mapper -> mapper.getByStdType(DingTalkMsgProps.typeTable(), stdType));
    }

    /**
     * 按标准「消息类型」ID 取本模块的推送设置行；未配置返回 null。
     *
     * @param stdConfigId 标准消息类型ID（{@code ecology_biz_mobile_config.id}，<b>字符串</b>：
     *                    内置类型是 "20" 这类数字编号，建模 / 自定义类型是 "1LbVgIrt" 这类 8 位编号）
     */
    public MsgTypeDef getByStdConfigId(String stdConfigId) {
        String key = stdKey(stdConfigId);
        if (key.length() == 0) {
            return null;
        }
        return OaonesMyBatis.call(MsgTypeMapper.class,
                mapper -> mapper.getByStdConfigId(DingTalkMsgProps.typeTable(), key));
    }

    /**
     * 标准消息类型ID 归一：{@code null} / 空串 / {@code "0"} 一律返回空串
     * （空串 = 这一行不属于任何标准消息类型，按 {@code std_type} 来源编号匹配）。
     */
    private static String stdKey(String stdConfigId) {
        String key = stdConfigId == null ? "" : stdConfigId.trim();
        return "0".equals(key) ? "" : key;
    }

    /**
     * 推送入口用的“取推送设置”口径。
     *
     * <p>① 消息来源已被某个标准「消息类型」包含（标准页里配过）：按那个类型的设置决定——
     * 本模块单独配过就按配置（“不推送”就是不推），没配过按<b>默认口径推送</b>（形态取平台默认，
     * 应用按接收人租户自动匹配），因此标准页新建的类型天然即可推送，不必先在本页“补齐”。</p>
     *
     * <p>② 消息来源没有被任何标准「消息类型」包含（平台自建来源、建模“自定义提醒”等还没在标准页
     * 归类的来源）：退回老口径——本模块有该来源的行就按行（停用即不推），从未配置过则按
     * {@code msg.type.auto.register} 决定是否自动登记一行默认设置后推送。</p>
     *
     * @param stdType    标准消息来源编号（{@code MessageBean#getMessageType()#getCode()}）
     * @param operatorId 自动登记行的创建人（取消息发送人；取不到传 0）
     * @return 启用中的推送设置；不该推送（人工停用 / 自动登记已关闭 / 在排除清单 / 登记失败）返回 null
     */
    public MsgTypeDef findForPush(int stdType, int operatorId) {
        MsgTypeDef standard = resolveByStandard(stdType, DingTalkMsgConst.LANG_ZH_CN);
        if (standard != null) {
            Integer yx = standard.getYx();
            return yx != null && yx == DingTalkMsgConst.STATE_ENABLE ? standard : null;
        }
        MsgTypeDef def = getByStdType(stdType);
        if (def != null) {
            Integer yx = def.getYx();
            return yx != null && yx == DingTalkMsgConst.STATE_ENABLE ? def : null;
        }
        if (DingTalkMsgProps.autoRegisterTypes() != 1) {
            return null;
        }
        if (DingTalkMsgProps.autoRegisterExcluded(stdType)) {
            OaonesLog.step("[msg] 标准消息类型 " + stdType + " 在自动登记排除清单里，跳过推送");
            return null;
        }
        return autoRegister(stdType, operatorId);
    }

    // ------------------------------------------------------------------ 标准消息类型（泛微侧）→ 本模块推送设置

    /**
     * 按标准口径解析一个消息来源（typecode）该用哪份推送设置。
     *
     * <p>同一个来源可能被多个标准「消息类型」包含（标准页允许来源重复挂在多个类型上）：
     * <b>本页显式配置过的类型优先</b>（显式“不推送”就算不推送），都没配置时取标准列表里
     * 最靠前的那个（{@code ecology_biz_mobile_config.id} 最小），保证同一来源的推送口径稳定可解释。</p>
     *
     * @return 可用的推送设置（含未配置时的默认口径）；该来源没被任何标准类型包含时返回 null
     */
    private MsgTypeDef resolveByStandard(int stdType, int lang) {
        if (stdType <= 0) {
            return null;
        }
        List<StdMsgType> matches = StdMsgService.snapshot(lang).typesOfTypeCode(stdType);
        if (matches.isEmpty()) {
            return null;
        }
        StdMsgType chosen = null;
        MsgTypeDef row = null;
        for (StdMsgType type : matches) {
            MsgTypeDef found = getByStdConfigId(type.getId());
            if (found != null) {
                chosen = type;
                row = found;
                break;
            }
        }
        if (chosen == null) {
            chosen = matches.get(0);
        }
        if (row == null) {
            return defaultForStdType(chosen, stdType);
        }
        // 行上的形态/模板留空时补默认形态，避免历史行（只配了开关）推不出内容
        return fillDefaults(row, chosen, stdType);
    }

    /** 标准消息类型未单独配置时的默认推送口径（默认形态/模板取 {@link #defaultDef(int, String)}）。 */
    private MsgTypeDef defaultForStdType(StdMsgType std, int stdType) {
        MsgTypeDef def = defaultDef(stdType, std.getName());
        def.setStdConfigId(std.getId());
        def.setStdConfigName(std.getName());
        def.setStdSourceText(std.sourceNameText());
        // 归入标准消息类型的行不冗余来源编号（来源从标准侧实时读，避免标准页改了之后两边不一致）
        def.setStdType(0);
        def.setSortNo(0);
        def.setYx(DingTalkMsgConst.STATE_ENABLE);
        def.setRemark("标准消息类型「" + std.getName() + "」未单独配置，按默认口径推送，可在本页按类型细化");
        return def;
    }

    /** 给已配置的行补上标准侧的展示信息与缺省形态（不改库，只补这一次推送用的对象）。 */
    private MsgTypeDef fillDefaults(MsgTypeDef row, StdMsgType std, int stdType) {
        if (std != null) {
            row.setStdConfigId(std.getId());
            row.setStdConfigName(std.getName());
            row.setStdSourceText(std.sourceNameText());
            // 名称以标准页为准：标准页改了类型名，本模块的日志 / 提示立即跟着变
            if (std.getName().length() > 0) {
                row.setTypeName(std.getName());
            }
        }
        String wxMsgtype = row.getWxMsgtype();
        if (wxMsgtype == null || wxMsgtype.trim().length() == 0) {
            row.setWxMsgtype(defaultDef(stdType, std == null ? "" : std.getName()).getWxMsgtype());
        }
        if (row.getCardAction() == null || row.getCardAction().trim().length() == 0) {
            row.setCardAction(DingTalkMsgConst.CARD_NONE);
        }
        if (row.getTitleTpl() == null || row.getTitleTpl().trim().length() == 0) {
            row.setTitleTpl("${title}");
        }
        if (row.getContextTpl() == null || row.getContextTpl().trim().length() == 0) {
            row.setContextTpl("${context}");
        }
        if (row.getAppId() == null) {
            row.setAppId(0);
        }
        return row;
    }

    /**
     * 自动登记一行默认映射：口径与 {@link #defaultDef(int, String)} 一致（默认启用）。
     *
     * <p>并发（同一类型多条消息同时到达）时以“插入后回读”为准：先插入再按 stdType 回读，
     * 拿到哪一行就用哪一行，避免同一类型推两份。</p>
     */
    private MsgTypeDef autoRegister(int stdType, int operatorId) {
        MsgTypeDef def = defaultDef(stdType, standardTypeNameOf(stdType));
        def.setRemark("收到未配置的标准消息自动登记，可在本页调整或停用");
        def.setModedatacreater(operatorId);
        def.setModedatacreatedate(OaonesUtil.nowDate());
        def.setModedatacreatetime(OaonesUtil.nowTime());
        def.setModedatamodifier(operatorId);
        def.setModedatamodifydatetime(OaonesUtil.now());
        try {
            OaonesMyBatis.run(MsgTypeMapper.class, mapper -> mapper.insert(DingTalkMsgProps.typeTable(), def));
        } catch (Throwable t) {
            // 并发下可能已被另一条消息插入：不当作错误，下面回读已有行
            OaonesLog.error("[msg] 自动登记消息类型映射失败（将回读已有行）stdType=" + stdType, t);
        }
        MsgTypeDef saved = getByStdType(stdType);
        if (saved == null) {
            return null;
        }
        OaonesLog.step("[msg] 自动登记消息类型映射 stdType=" + stdType
                + " name=" + saved.getTypeName() + " wxMsgtype=" + saved.getWxMsgtype()
                + " operatorId=" + operatorId);
        Integer yx = saved.getYx();
        return yx != null && yx == DingTalkMsgConst.STATE_ENABLE ? saved : null;
    }

    /**
     * 单个标准消息类型的名称（口径与 {@link #standardTypes(int)} 一致：
     * 平台标签 → {@code sourcename} 多语言串 → 内置清单 → “消息类型 N”），取不到返回空串。
     */
    private String standardTypeNameOf(int stdType) {
        for (Map<String, Object> item : standardTypes(DingTalkMsgConst.LANG_ZH_CN)) {
            if (OaonesUtil.i(String.valueOf(item.get("code")), 0) == stdType) {
                return OaonesUtil.s(String.valueOf(item.get("name")));
            }
        }
        return "";
    }

    /**
     * 保存一个标准消息类型（或一个未归类的独立来源）在本模块的推送设置。
     *
     * <p>唯一性按“标准消息类型 + 平台”判重（独立来源按“来源编号 + 平台”）：同一标准类型只能有一行设置，
     * 重复新增会被拦截，请在原行上编辑。</p>
     */
    public void save(MsgTypeDef def, int operatorId) {
        validate(def);
        String stdConfigId = def.hasStdConfig() ? def.getStdConfigId() : "";
        int stdType = def.getStdType() == null ? 0 : def.getStdType();
        int selfId = def.getId() == null ? 0 : def.getId();
        Integer dup = stdConfigId.length() > 0
                ? OaonesMyBatis.call(MsgTypeMapper.class, mapper -> mapper.findIdByStdConfigId(
                        DingTalkMsgProps.typeTable(), stdConfigId, selfId))
                : OaonesMyBatis.call(MsgTypeMapper.class, mapper -> mapper.findIdByStdType(
                        DingTalkMsgProps.typeTable(), stdType, selfId));
        if (dup != null) {
            throw new RuntimeException("该消息类型已配置推送设置，请直接编辑原记录");
        }
        def.setModedatamodifier(operatorId);
        def.setModedatamodifydatetime(OaonesUtil.now());
        if (def.getId() == null) {
            def.setModedatacreater(operatorId);
            def.setModedatacreatedate(OaonesUtil.nowDate());
            def.setModedatacreatetime(OaonesUtil.nowTime());
            OaonesMyBatis.run(MsgTypeMapper.class, mapper -> mapper.insert(DingTalkMsgProps.typeTable(), def));
            // 回读新增行的主键：页面保存时随行提交的条件规则要按它落库（规则表按 msg_type_id 挂）
            Integer newId = stdConfigId.length() > 0
                    ? OaonesMyBatis.call(MsgTypeMapper.class, mapper -> mapper.findIdByStdConfigId(
                            DingTalkMsgProps.typeTable(), stdConfigId, 0))
                    : OaonesMyBatis.call(MsgTypeMapper.class, mapper -> mapper.findIdByStdType(
                            DingTalkMsgProps.typeTable(), def.getStdType() == null ? 0 : def.getStdType(), 0));
            if (newId != null) {
                def.setId(newId);
            }
        } else {
            OaonesMyBatis.run(MsgTypeMapper.class, mapper -> mapper.update(DingTalkMsgProps.typeTable(), def));
        }
        OaonesLog.step("[msg] 保存消息类型推送设置 stdConfigId=" + stdConfigId + " stdType=" + stdType
                + " wxMsgtype=" + def.getWxMsgtype() + " operatorId=" + operatorId);
    }

    /**
     * 删除某个标准消息类型（或独立来源）在本模块的推送设置：
     * 删除后该类型回到<b>默认口径</b>（按平台默认形态、默认应用推送）。
     *
     * <p>只想“不推送”请不要删除，改用列表里的推送开关（{@code yx = 1}）。</p>
     */
    public void delete(int id) {
        OaonesMyBatis.run(MsgTypeMapper.class, mapper -> mapper.deleteById(DingTalkMsgProps.typeTable(), id));
        OaonesLog.step("[msg] 删除消息类型推送设置 id=" + id);
    }

    // ------------------------------------------------------------------ 页面用：标准「分组 / 消息来源 / 消息类型」清单 + 本模块推送设置

    /**
     * 页面「消息设置 → 消息类型」用：泛微标准页的分组 / 消息来源 / 消息类型清单，
     * 合并本模块对每个类型的推送设置（未配置的类型按默认口径展示，即“默认推送 + 默认形态”）。
     *
     * <p>返回结构：</p>
     * <ul>
     *   <li>{@code groups}：标准分组清单（页面按分组分段展示与筛选）；</li>
     *   <li>{@code types}：标准「消息类型」一行一条，含所属分组 / 所属模块 / 包含的来源，
     *       以及本模块的推送开关（{@code yx}）、推送目标应用（{@code appId}）、形态模板；</li>
     *   <li>{@code orphans}：<b>未被任何标准消息类型包含</b>的消息来源（按来源编号 typecode 匹配），两类都列：
     *       ① 本模块已配置过的行（{@code configured=true}；其中已被标准类型覆盖、本行不再生效的历史行会标注出来）；
     *       ② 标准页里有、本模块还没配过的来源（{@code configured=false} / {@code rowId=0}，按默认口径推送，
     *       页面同样能启用 / 停用 / 设置 / 测试，保存时按来源编号建行）；</li>
     *   <li>{@code summary}：标准类型 / 来源数量、已配置条数、以及“还没配到任何消息类型上的来源”提示。</li>
     * </ul>
     */
    public Map<String, Object> standardView(int lang) {
        int language = lang <= 0 ? DingTalkMsgConst.LANG_ZH_CN : lang;
        StdMsgService.Snapshot snapshot = StdMsgService.load(language);
        List<MsgTypeDef> ours = list();
        // 消息类型「推送目标应用」明细（一行 = 一个消息类型 + 一个租户 + 一个应用）：
        // 整表读一次，按类型挂到列表行上（页面显示“已设置目标应用的数量”并按行预填明细弹框）
        Map<String, Map<Integer, Integer>> appBinds = new MsgTypeAppService().allBinds();
        Map<String, MsgTypeDef> byConfig = new HashMap<String, MsgTypeDef>();
        for (MsgTypeDef def : ours) {
            if (def != null && def.hasStdConfig() && !byConfig.containsKey(def.getStdConfigId())) {
                byConfig.put(def.getStdConfigId(), def);
            }
        }

        List<Map<String, Object>> types = new ArrayList<Map<String, Object>>();
        for (StdMsgType type : snapshot.getTypes()) {
            MsgTypeDef row = byConfig.get(type.getId());
            int firstCode = firstTypeCode(type);
            // 未配置的类型按默认口径展示（默认推送 + 平台默认形态），保证与推送口径一致
            MsgTypeDef effective = row == null ? defaultForStdType(type, firstCode)
                    : fillDefaults(row, type, firstCode);
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("stdConfigId", type.getId());
            item.put("name", type.getName());
            item.put("groupId", type.getGroupId());
            item.put("groupName", type.getGroupName());
            item.put("moduleId", type.getModuleId());
            item.put("moduleName", type.getModuleName());
            item.put("sourceNames", type.sourceNameText());
            item.put("typeCodes", type.typeCodeText());
            // 列表里“消息来源”按「系统ID#名称」展示：ID 取来源编号 typecode（与标准页 / 推送匹配口径一致）
            item.put("sources", sourceItems(type));
            item.put("sourceCount", type.getSources().size());
            item.put("stdEnable", type.getEnable());
            item.put("stdEnableEm", type.getEnableEm());
            item.put("stdSendMobile", type.getSendMobile());
            item.put("stdAgentId", type.getAgentId());
            item.put("stdDefault", type.isDefault());
            // 本模块的推送设置（configured=false 表示还没单独配过，按默认口径推送）
            item.put("configured", row != null);
            fillPushFields(item, effective);
            fillAppBinds(item, appBinds, type.getId(), 0);
            types.add(item);
        }

        List<Map<String, Object>> orphans = new ArrayList<Map<String, Object>>();
        Set<Integer> orphanCodes = new HashSet<Integer>();
        for (MsgTypeDef def : ours) {
            if (def == null || def.hasStdConfig()) {
                continue;
            }
            int code = def.getStdType() == null ? 0 : def.getStdType();
            List<StdMsgType> coveredBy = snapshot.typesOfTypeCode(code);
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("rowId", def.getId());
            item.put("stdType", code);
            item.put("name", sourceNameOf(code, snapshot, language));
            item.put("covered", !coveredBy.isEmpty());
            item.put("coveredBy", coveredBy.isEmpty() ? "" : coveredBy.get(0).getName());
            // 本模块已经有这一行（configured=true：页面显示“删除”，删掉即回到默认口径）
            item.put("configured", true);
            fillPushFields(item, fillDefaults(def, null, code));
            fillAppBinds(item, appBinds, "", code);
            orphans.add(item);
            orphanCodes.add(code);
        }
        // 标准页里有、但没挂到任何消息类型上的来源：本模块从未配置过时也列出来（configured=false / rowId=0），
        // 页面据此同样能启用 / 停用 / 设置 / 测试——保存时按来源编号建行（quickSave / save 都支持），
        // 免得这类来源只能在边上提示一行、看不到也配不了。
        for (StdMsgSource source : snapshot.uncoveredSources()) {
            if (source == null) {
                continue;
            }
            int code = source.getTypeCode();
            if (code <= 0 || orphanCodes.contains(code)) {
                continue;
            }
            String name = source.getName();
            if (name == null || name.trim().length() == 0) {
                name = "消息来源 " + code;
            }
            orphanCodes.add(code);
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("rowId", 0);
            item.put("stdType", code);
            item.put("name", name);
            item.put("covered", false);
            item.put("coveredBy", "");
            item.put("configured", false);
            fillPushFields(item, fillDefaults(defaultDef(code, name), null, code));
            fillAppBinds(item, appBinds, "", code);
            orphans.add(item);
        }
        // 排序：没挂到标准消息类型的按来源编号升序在前；“已被标准类型包含”的历史行（本行不再生效）排最后
        Collections.sort(orphans, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> a, Map<String, Object> b) {
                boolean ca = Boolean.TRUE.equals(a.get("covered"));
                boolean cb = Boolean.TRUE.equals(b.get("covered"));
                if (ca != cb) {
                    return ca ? 1 : -1;
                }
                return OaonesUtil.i(String.valueOf(a.get("stdType")), 0)
                        - OaonesUtil.i(String.valueOf(b.get("stdType")), 0);
            }
        });

        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("groupCount", snapshot.getGroups().size());
        summary.put("typeCount", snapshot.getTypes().size());
        summary.put("sourceCount", snapshot.getSources().size());
        summary.put("configured", byConfig.size());
        List<StdMsgSource> uncovered = snapshot.uncoveredSources();
        summary.put("uncoveredCount", uncovered.size());
        List<Map<String, Object>> uncoveredItems = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < uncovered.size() && i < 20; i++) {
            StdMsgSource source = uncovered.get(i);
            Map<String, Object> one = new LinkedHashMap<String, Object>();
            one.put("typeCode", source.getTypeCode());
            one.put("name", source.getName());
            uncoveredItems.add(one);
        }
        summary.put("uncoveredSources", uncoveredItems);
        // 读取标准表过程中的告警（没装消息中心 / 表不存在时提示出来，页面顶部红字）
        summary.put("warnings", snapshot.getWarnings());

        List<Map<String, Object>> groups = new ArrayList<Map<String, Object>>();
        for (StdMsgGroup one : snapshot.getGroups()) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("id", one.getId());
            item.put("name", one.getName());
            item.put("showNum", one.getShowNum());
            groups.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("groups", groups);
        result.put("types", types);
        result.put("orphans", orphans);
        result.put("summary", summary);
        return result;
    }

    /**
     * 把“租户 → 应用”明细挂到列表行上（页面据此显示“已设置目标应用的数量”与明细弹框的预填值）。
     *
     * @param stdConfigId 标准消息类型ID（独立来源传空串）
     * @param stdType     独立来源编号 typecode（标准类型传 0）
     */
    private void fillAppBinds(Map<String, Object> item, Map<String, Map<Integer, Integer>> all,
                              String stdConfigId, int stdType) {
        Map<Integer, Integer> binds = all.get(MsgTypeAppService.keyOf(stdConfigId, stdType));
        // 交给前端的必须是**字符串键**：本项目用的 fastjson 对 Map<Integer,?> 会输出 {3:7}
        // 这种“键不带引号”的非法 JSON，前端 JSON.parse 直接报
        // “Expected property name or '}' in JSON at position ...”，整页接口都拿不到数据。
        Map<String, Integer> json = new LinkedHashMap<String, Integer>();
        if (binds != null) {
            for (Map.Entry<Integer, Integer> entry : binds.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                json.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        item.put("appBinds", json);
        item.put("appCount", json.size());
    }

    /**
     * 列表里“消息来源”要展示的「系统ID#名称」清单：ID 取来源编号 typecode
     * （{@code ecology_message_type.typecode}，就是推送时用来匹配的来源编号）。
     *
     * <p>名称取不到时退化成“消息来源 &lt;typecode&gt;”，口径与 {@link StdMsgType#sourceNameText()} 一致。</p>
     */
    private List<Map<String, Object>> sourceItems(StdMsgType type) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        if (type == null) {
            return list;
        }
        for (StdMsgSource source : type.getSources()) {
            if (source == null) {
                continue;
            }
            int code = source.getTypeCode();
            String name = source.getName();
            Map<String, Object> one = new LinkedHashMap<String, Object>();
            one.put("code", code);
            one.put("name", name.length() == 0 ? ("消息来源 " + code) : name);
            list.add(one);
        }
        return list;
    }

    /**
     * 列表内联“状态 / 推送目标应用”的快速保存：没有配置行时按默认口径建一行再改，
     * 已配置的行就地改（只动 {@code yx} 与 {@code appId}，不动形态与模板）。
     *
     * @param stdConfigId 标准消息类型ID（&gt; 0 时按它定位；独立来源传 0）
     * @param stdType     独立来源编号 typecode（{@code stdConfigId = 0} 时使用）
     * @param yx          {@link DingTalkMsgConst#STATE_ENABLE} 推送 / {@link DingTalkMsgConst#STATE_DISABLE} 不推送；null = 不改
     * @param appId       推送目标应用（绑定的第三方应用ID，0 = 按接收人租户自动匹配）；null = 不改
     * @param operatorId  操作人（OA 人员ID）
     * @return 保存后的设置行
     */
    public MsgTypeDef quickSave(String stdConfigId, int stdType, Integer yx, Integer appId,
                                int operatorId, int lang) {
        int language = lang <= 0 ? DingTalkMsgConst.LANG_ZH_CN : lang;
        String stdKey = stdKey(stdConfigId);

        MsgTypeDef def = stdKey.length() > 0 ? getByStdConfigId(stdKey) : getByStdType(stdType);
        if (def == null) {
            // 新建：按默认口径生成完整配置行（形态 / 模板 / 应用 / 状态一次写全）
            if (stdKey.length() > 0) {
                StdMsgType type = StdMsgService.snapshot(language).typeById(stdKey);
                if (type == null) {
                    throw new RuntimeException("标准消息类型不存在（可能已在标准页删除），请刷新后重试");
                }
                def = defaultForStdType(type, firstTypeCode(type));
                def.setRemark("由「消息设置 → 消息类型」按默认口径创建，可在本页细化");
            } else {
                if (stdType <= 0) {
                    throw new RuntimeException("缺少消息来源编号（或标准消息类型编号）");
                }
                def = defaultDef(stdType, standardTypeNameOf(stdType));
                def.setRemark("由「消息设置 → 消息类型」按默认口径创建，可在本页细化");
            }
            def.setStdConfigId(stdKey);
            // 新建行：没传的项取默认口径（启用 + 按接收人租户自动匹配应用）
            def.setYx(yx == null ? DingTalkMsgConst.STATE_ENABLE : stateOf(yx));
            def.setAppId(appId == null ? 0 : (appId > 0 ? appId : 0));
            def.setModedatacreater(operatorId);
            def.setModedatacreatedate(OaonesUtil.nowDate());
            def.setModedatacreatetime(OaonesUtil.nowTime());
            def.setModedatamodifier(operatorId);
            def.setModedatamodifydatetime(OaonesUtil.now());
            final MsgTypeDef entity = def;
            OaonesMyBatis.run(MsgTypeMapper.class, mapper -> mapper.insert(DingTalkMsgProps.typeTable(), entity));
            MsgTypeDef saved = stdKey.length() > 0 ? getByStdConfigId(stdKey) : getByStdType(stdType);
            if (saved != null) {
                OaonesLog.step("[msg] 新建消息类型推送设置 stdConfigId=" + stdKey
                        + " stdType=" + stdType + " yx=" + saved.getYx()
                        + " appId=" + saved.getAppId());
                return saved;
            }
            return def;
        }
        // 已有行：只改传来的那一项（null = 保持不变，避免“改状态时把推送目标应用重置为默认”）
        if (yx != null) {
            def.setYx(stateOf(yx));
        }
        if (appId != null) {
            def.setAppId(appId > 0 ? appId : 0);
        }
        def.setModedatamodifier(operatorId);
        def.setModedatamodifydatetime(OaonesUtil.now());
        final MsgTypeDef entity = def;
        OaonesMyBatis.run(MsgTypeMapper.class, mapper -> mapper.update(DingTalkMsgProps.typeTable(), entity));
        OaonesLog.step("[msg] 快速设置消息类型推送 stdConfigId=" + stdKey + " stdType=" + stdType
                + " yx=" + def.getYx() + " appId=" + def.getAppId());
        return def;
    }

    /** 状态取值归一：{@link DingTalkMsgConst#STATE_DISABLE} 之外一律按启用。 */
    private static int stateOf(int yx) {
        return yx == DingTalkMsgConst.STATE_DISABLE ? DingTalkMsgConst.STATE_DISABLE : DingTalkMsgConst.STATE_ENABLE;
    }

    /**
     * 按标准「消息类型」ID 取<b>生效</b>的推送设置（页面“测试推送 / 详情”用）：
     * 本模块配过就返回配置行，没配过返回默认口径（默认推送 + 平台默认形态）。
     *
     * @return 该标准类型已被标准页删除时返回 null
     */
    public MsgTypeDef effectiveForStdConfig(String stdConfigId, int lang) {
        String key = stdKey(stdConfigId);
        if (key.length() == 0) {
            return null;
        }
        int language = lang <= 0 ? DingTalkMsgConst.LANG_ZH_CN : lang;
        StdMsgType type = StdMsgService.snapshot(language).typeById(key);
        if (type == null) {
            return null;
        }
        MsgTypeDef row = getByStdConfigId(key);
        return row == null ? defaultForStdType(type, firstTypeCode(type))
                : fillDefaults(row, type, firstTypeCode(type));
    }

    /**
     * 按“未纳入标准消息类型的独立来源”编号取<b>生效</b>的推送设置：
     * 本模块配过就返回配置行，没配过返回默认口径。
     */
    public MsgTypeDef effectiveForStdType(int stdType) {
        if (stdType <= 0) {
            return null;
        }
        MsgTypeDef row = getByStdType(stdType);
        return row == null ? defaultDef(stdType, standardTypeNameOf(stdType)) : row;
    }

    /** 一行推送设置的公共字段（页面直接序列化成 JSON）。 */
    private void fillPushFields(Map<String, Object> item, MsgTypeDef def) {
        if (def == null) {
            return;
        }
        item.put("rowId", def.getId() == null ? 0 : def.getId());
        item.put("yx", def.getYx() == null ? DingTalkMsgConst.STATE_ENABLE : def.getYx());
        item.put("appId", def.getAppId() == null ? 0 : def.getAppId());
        item.put("wxMsgtype", def.getWxMsgtype());
        item.put("cardAction", def.getCardAction());
        item.put("titleTpl", def.getTitleTpl());
        item.put("contextTpl", def.getContextTpl());
        item.put("urlTpl", def.getUrlTpl());
        item.put("mobileUrlTpl", def.getMobileUrlTpl());
        item.put("coverUrl", def.getCoverUrl());
        item.put("btnJson", def.getBtnJson());
        item.put("sortNo", def.getSortNo());
        item.put("remark", def.getRemark());
    }

    /** 标准消息类型里第一个来源编号（默认形态按来源编号区分流程 / 会议 / 任务）；没有来源返回 0。 */
    private int firstTypeCode(StdMsgType type) {
        if (type == null) {
            return 0;
        }
        for (StdMsgSource source : type.getSources()) {
            if (source != null && source.getTypeCode() > 0) {
                return source.getTypeCode();
            }
        }
        return 0;
    }

    /** 独立来源行的名称：标准来源清单里按 typecode 找 → 标准类型清单名称 → 内置兜底名。 */
    private String sourceNameOf(int typeCode, StdMsgService.Snapshot snapshot, int lang) {
        if (typeCode <= 0) {
            return "";
        }
        for (StdMsgSource source : snapshot.getSources()) {
            if (source != null && source.getTypeCode() == typeCode && source.getName().length() > 0) {
                return source.getName();
            }
        }
        for (Map<String, Object> row : standardTypes(lang)) {
            if (OaonesUtil.i(String.valueOf(row.get("code")), 0) == typeCode) {
                return OaonesUtil.s(String.valueOf(row.get("name")));
            }
        }
        return builtinTypeName(typeCode);
    }

    // ------------------------------------------------------------------ 标准消息类型（泛微侧）

    /**
     * 泛微标准消息中心里的全部消息类型（平台后台 应用中心 → 消息类型 的那一份清单）。
     *
     * <p>返回项固定三个键：{@code code}（标准 typecode）/ {@code name}（名称）/
     * {@code group}（分组名称，可为空串）；平台表读不到（未装消息中心 / 库异常）时
     * 回落到 {@link #BUILTIN_TYPE_CODES} 内置清单，保证页面下拉与“补齐”功能始终可用。</p>
     */
    public List<Map<String, Object>> standardTypes(int lang) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        try {
            List<Map<String, Object>> rows = OaonesMyBatis.call(MsgTypeMapper.class,
                    mapper -> mapper.listStandardTypes(lang));
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    int code = OaonesUtil.i(field(row, "typecode"), 0);
                    if (code <= 0) {
                        continue;
                    }
                    String name = field(row, "typename");
                    // 平台表里的 sourcename 存的是多语言编码串（~`~`7 中文~`8 English~`~`），
                    // 当该语言没有 HtmlLabelInfo 标签时会兜底到它；这里按当前语言取成纯文本，
                    // 避免页面下拉里出现 “~`~`7 …” 这种标记串（不含标记的值原样返回）。
                    name = multiLangName(name, lang);
                    if (name.length() == 0) {
                        name = builtinTypeName(code);
                    }
                    list.add(standardTypeItem(code, name, field(row, "groupname")));
                }
            }
        } catch (Throwable t) {
            OaonesLog.error("[msg] 读取泛微标准消息类型失败，回落到内置清单", t);
            list.clear();
        }
        if (list.isEmpty()) {
            for (int code : BUILTIN_TYPE_CODES) {
                list.add(standardTypeItem(code, builtinTypeName(code), ""));
            }
        }
        return list;
    }

    /**
     * 默认推送口径（标准消息类型没单独配置、或来源没纳入标准类型时按它推送）：待办 / 卡片失效类给任务卡片（口径与
     * {@code sql/create_oaones_msg_center.sql} 的初始化行一致），其余标准类型
     * 统一“文本卡片 + 不处理卡片 + ${title}/${context}”兜底，之后在页面上按需细化。
     */
    private MsgTypeDef defaultDef(int code, String stdName) {
        MsgTypeDef def = new MsgTypeDef();
        // 未纳入标准消息类型的独立来源行：std_config_id 落 "0"（列是 varchar NOT NULL）
        def.setStdConfigId("0");
        def.setStdType(code);
        String name = stdName == null ? "" : stdName.trim();
        def.setTypeName(name.length() == 0 ? builtinTypeName(code) : name);
        def.setWxMsgtype(DingTalkMsgConst.DD_MARKDOWN);
        def.setCardAction(DingTalkMsgConst.CARD_NONE);
        def.setTitleTpl("${title}");
        def.setContextTpl("${context}");
        def.setAppId(0);
        def.setYx(DingTalkMsgConst.STATE_ENABLE);
        def.setSortNo(code);
        def.setRemark("按默认口径初始化，可在本页调整");
        switch (code) {
            case 20:
                taskCard(def, "${targetName}",
                        "流程「${targetName}」由 ${creater} 于 ${date} ${time} 提交，请及时处理。",
                        BTN_AGREE_REJECT, DingTalkMsgConst.CARD_CREATE);
                break;
            case 21:
                taskCard(def, "${targetName}", "流程「${targetName}」被退回，请重新处理。",
                        BTN_AGREE, DingTalkMsgConst.CARD_CREATE);
                break;
            case 63:
                taskCard(def, "${targetName}", "任务「${targetName}」已到达，请及时处理。",
                        BTN_AGREE_REJECT, DingTalkMsgConst.CARD_CREATE);
                break;
            case 25:
                taskCard(def, "流程已办结", "流程「${targetName}」已办结。", null, DingTalkMsgConst.CARD_DONE);
                break;
            case 38:
                taskCard(def, "流程已收回", "流程「${targetName}」已被收回/撤销，待办已失效。",
                        null, DingTalkMsgConst.CARD_INVALID);
                break;
            case 27:
                taskCard(def, "流程已删除", "流程「${targetName}」已删除，待办已失效。", null, DingTalkMsgConst.CARD_INVALID);
                break;
            case 34:
                taskCard(def, "流程已暂停", "流程「${targetName}」已暂停，待办已失效。", null, DingTalkMsgConst.CARD_INVALID);
                break;
            case 35:
                taskCard(def, "流程已撤销", "流程「${targetName}」已撤销，待办已失效。", null, DingTalkMsgConst.CARD_INVALID);
                break;
            default:
                break;
        }
        return def;
    }

    /** 把默认映射改成任务卡片口径（标题 / 正文 / 按钮 / 卡片动作）。 */
    private void taskCard(MsgTypeDef def, String title, String context, String btnJson, String action) {
        def.setWxMsgtype(DingTalkMsgConst.DD_TASK_CARD);
        def.setCardAction(action);
        def.setTitleTpl(title);
        def.setContextTpl(context);
        def.setBtnJson(btnJson);
    }
    private Map<String, Object> standardTypeItem(int code, String name, String group) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("code", code);
        item.put("name", name == null ? "" : name);
        item.put("group", group == null ? "" : group);
        return item;
    }

    /** MyBatis 返回的 Map 在别名大小写上可能有差异，统一按小写再按大写取一次。 */
    private String field(Map<String, Object> row, String key) {
        if (row == null) {
            return "";
        }
        Object value = row.get(key);
        if (value == null) {
            value = row.get(key.toUpperCase());
        }
        return value == null ? "" : OaonesUtil.s(String.valueOf(value));
    }

    /**
     * 标准消息类型名称的语言取值。
     *
     * <p>平台表 {@code ECOLOGY_MESSAGE_TYPE} 没有为某个标签准备当前语言的
     * {@code HtmlLabelInfo} 行时，SQL 会兜底返回 {@code SOURCENAME}（“来源名”文本），
     * 而多语言站点里它存的是 {@code ~`~`7 中文`~`8 English`~`~} 这种标记串；
     * 这里按当前语言取成纯文本（不含标记的值原样返回），避免下拉里出现标记串。</p>
     */
    private String multiLangName(String value, int lang) {
        String text = OaonesUtil.s(value).trim();
        if (text.length() == 0 || text.indexOf("~`~`") < 0) {
            return text;
        }
        return renderService.multiLang(text, lang);
    }

    /**
     * 内置标准消息类型名称（平台表读不到时的兜底，口径与泛微 {@code MessageType} 一致）。
     */
    public static String builtinTypeName(int code) {
        switch (code) {
            case 20: return "新到达流程";
            case 21: return "退回流程";
            case 22: return "转发流程";
            case 23: return "抄送流程";
            case 24: return "意见征询流程";
            case 25: return "已完成流程";
            case 27: return "流程删除";
            case 33: return "流程交流提醒";
            case 34: return "流程暂停";
            case 35: return "流程撤销";
            case 38: return "流程收回";
            case 61: return "日程提醒";
            case 62: return "协作提醒";
            case 63: return "新到达任务";
            case 64: return "会议提醒";
            case 81: return "会议交流提醒";
            case 118: return "流程退回提醒";
            case 119: return "流程即将超时提醒";
            case 120: return "流程已超时提醒";
            case 124: return "新会议提醒";
            case 125: return "会议开始前提醒";
            case 126: return "会议结束前提醒";
            case 127: return "会议取消提醒";
            default: return "消息类型 " + code;
        }
    }

    /**
     * 名称快照补齐：类型清单以泛微标准页为准，页面**不提交** type_name，
     * 落库前按标准侧取一次（标准类型取标准页的类型名，未归类的独立来源取来源名）。
     */
    private void fillTypeName(MsgTypeDef def) {
        if (def == null) {
            return;
        }
        if (def.hasStdConfig()) {
            StdMsgType type = StdMsgService.snapshot(DingTalkMsgConst.LANG_ZH_CN).typeById(def.getStdConfigId());
            if (type != null && type.getName().length() > 0) {
                def.setTypeName(type.getName());
                return;
            }
        }
        int stdType = def.getStdType() == null ? 0 : def.getStdType();
        if (stdType > 0) {
            // 口径与列表里显示的名称一致（来源表 → 标准类型清单 → 内置清单 → “消息类型 <code>”）
            def.setTypeName(sourceNameOf(stdType, StdMsgService.snapshot(DingTalkMsgConst.LANG_ZH_CN),
                    DingTalkMsgConst.LANG_ZH_CN));
        }
    }

    /** 校验：企微消息形态、卡片动作必须与形态匹配（非卡片类消息禁止配置卡片动作）。 */
    private void validate(MsgTypeDef def) {
        // 名称快照：消息类型清单来自泛微标准页，页面**不提交** type_name（列表里显示的也是标准侧实时名称），
        // 所以这里必须自己按标准侧补齐——否则页面点“设置 → 保存”会被下面的名称校验挡回去
        // （老口径“在一个输入框里手填类型名”的入口已经没有了）。
        if (def.getTypeName() == null || def.getTypeName().trim().length() == 0) {
            fillTypeName(def);
        }
        // 一行要么挂在标准「消息类型」上（stdConfigId 非空），要么是未归类的独立来源（stdType = typecode）
        int stdType = def.getStdType() == null ? 0 : def.getStdType();
        if (!def.hasStdConfig() && stdType <= 0) {
            throw new RuntimeException("请选择泛微标准消息类型（或在标准页新建消息类型后刷新重试）");
        }
        if (def.hasStdConfig()) {
            // 归入标准消息类型的行不冗余来源编号（来源从标准侧实时读）
            def.setStdType(0);
        }
        String wxMsgtype = def.getWxMsgtype();
        if (wxMsgtype == null || wxMsgtype.trim().length() == 0) {
            throw new RuntimeException("请选择钉钉消息形态");
        }
        boolean valid = false;
        for (String item : DingTalkMsgConst.DD_MSGTYPES) {
            if (item.equals(wxMsgtype)) {
                valid = true;
                break;
            }
        }
        if (!valid) {
            throw new RuntimeException("钉钉消息形态取值非法：" + wxMsgtype);
        }
        String action = def.getCardAction();
        if (action == null || action.trim().length() == 0) {
            action = DingTalkMsgConst.CARD_NONE;
            def.setCardAction(action);
        }
        boolean cardMsgtype = DingTalkMsgConst.DD_TASK_CARD.equals(wxMsgtype)
                || DingTalkMsgConst.DD_ACTION_CARD.equals(wxMsgtype);
        if (!cardMsgtype && !DingTalkMsgConst.CARD_NONE.equals(action)) {
            throw new RuntimeException("文本/图文/文本卡片消息不支持卡片动作，请把卡片动作改为“不处理卡片”");
        }
        if (DingTalkMsgConst.CARD_CREATE.equals(action) && !DingTalkMsgConst.DD_TASK_CARD.equals(wxMsgtype)) {
            throw new RuntimeException("只有任务卡片（OA 待办）支持“新建待办卡片”动作");
        }
        if (def.getTypeName() == null || def.getTypeName().trim().length() == 0) {
            throw new RuntimeException("取不到这个消息类型的名称（标准页里可能已删除），请刷新后重试");
        }
        if (def.getYx() == null) {
            def.setYx(DingTalkMsgConst.STATE_ENABLE);
        }
        if (def.getAppId() == null) {
            def.setAppId(0);
        }
        if (def.getSortNo() == null) {
            def.setSortNo(0);
        }
    }
}
