package com.customization.oaones.appsyn.common;

/**
 * OAones 业务常量。
 */
public final class OaonesConst {

    /** 平台类型：企业微信。 */
    public static final int TYPE_WECHAT = 0;
    /** 平台类型：钉钉。 */
    public static final int TYPE_DINGTALK = 1;
    /**
     * 平台类型：飞书（国内版 feishu.cn / 海外版 Lark）。
     *
     * <p>飞书连接器与企微、钉钉**共用同一批业务表**（设置类表结构可复用时不新建表），
     * 用 {@code uf_oaones_qywx_config.type} 区分租户平台；飞书的部门ID是字符串
     * （形如 {@code od-xxxxxxxx}），因此飞书侧的平台部门标识写在共用的
     * {@code wx_dept_key} 列（企微/钉钉仍用数值列 {@code wx_dept_id}），
     * 见 {@code sql/public/alter_oaones_platform_feishu.sql}。</p>
     */
    public static final int TYPE_FEISHU = 2;

    /** 快照状态：正常。 */
    public static final int STATUS_NORMAL = 0;
    /** 快照状态：关闭/封存/已删除。 */
    public static final int STATUS_CLOSED = 1;

    /** 同步方向：OA -> 企业微信。 */
    public static final int DIR_OA_TO_WX = 0;
    /** 同步方向：企业微信 -> OA。 */
    public static final int DIR_WX_TO_OA = 1;

    /** 绑定方式：手动绑定（管理员在人员绑定页人工选择成员保存）。 */
    public static final int BIND_SOURCE_MANUAL = 0;
    /** 绑定方式：系统绑定（默认规则自动批量绑定）。 */
    public static final int BIND_SOURCE_SYSTEM = 1;
    /** 绑定方式：自行绑定（本人在“个人账号中心”扫码绑定）。 */
    public static final int BIND_SOURCE_SELF = 2;

    /**
     * 绑定方式文案：0 手动绑定 / 1 系统绑定 / 2 自行绑定。
     *
     * <p>“OA数据基准”的“绑定方式”“绑定原因”与“个人账号中心”的绑定方式都按此口径显示。</p>
     */
    public static String bindSourceText(int bindSource) {
        if (bindSource == BIND_SOURCE_SYSTEM) {
            return "系统绑定";
        }
        if (bindSource == BIND_SOURCE_SELF) {
            return "自行绑定";
        }
        return "手动绑定";
    }

    /** 该绑定是否由本人扫码“自行绑定”（更换/解绑需二次确认）。 */
    public static boolean isSelfBind(int bindSource) {
        return bindSource == BIND_SOURCE_SELF;
    }

    /**
     * 分部绑定状态（`uf_oaones_sub_bind.yx`）：0 有效。
     *
     * <p>本表按建模“枚举序号从 0 开始”的习惯，取值与部门绑定表
     * （`uf_oaones_dept_bind.yx`：1 有效 / 0 失效）<b>相反</b>，两处不可混用。</p>
     */
    public static final int SUB_BIND_STATE_VALID = 0;
    /** 分部绑定状态：1 无效（见 {@link #SUB_BIND_STATE_VALID}）。 */
    public static final int SUB_BIND_STATE_INVALID = 1;

    /**
     * 免绑定标记值：0 —— 三处对象统一口径，只用 0 表达“免绑定”。
     *
     * <ul>
     *   <li>{@code uf_oaones_user_tmp.no_bind}（企微成员侧，“企微数据基准”页）：
     *       免绑定写 0，正常不写值（NULL），已取消 1 等其它状态值；</li>
     *   <li>{@code uf_oaones_dep_tmp.no_bind}（企微部门侧，“企微未绑定部门”页）：
     *       免绑定写 0，正常不写值（NULL），已取消 1 等其它状态值；</li>
     *   <li>{@code uf_oaones_user_bind.bind_state}（OA 人员侧，“OA数据基准”页）：
     *       0 = 免绑定 / 1 = 已绑定，本表已取消 no_bind 列。</li>
     * </ul>
     *
     * <p>三处语义一致：免绑定的对象不参与绑定选择与人员/部门同步，需先取消免绑定才能再绑定。</p>
     */
    public static final int NO_BIND_MARK = 0;

    /** 人员绑定原因：“同步人员”按部门绑定关系自动建号并绑定。 */
    public static final String SYNC_BIND_REMARK = "按部门绑定同步人员";

    /** 人员绑定原因：“个人账号中心”本人扫码绑定。 */
    public static final String SCAN_BIND_REMARK = "企业微信扫码绑定（本人操作）";

    /**
     * 个人微信绑定原因：“个人账号中心”本人扫码绑定个人微信。
     *
     * <p>“个人微信”是与企微 / 钉钉 / 飞书并列的**第四个平台**，但它没有“企业 / 租户”概念：
     * 一套部署只有一份微信授权密钥（见 {@code uf_oaones_personal_wx_config}），
     * 因此绑定表按 OA 人员一对一存一条，不按 cfg_id 分租户。</p>
     */
    public static final String PERSONAL_WX_BIND_REMARK_SELF = "个人微信扫码绑定（本人操作）";

    /** 个人微信绑定原因：管理员在「微信设置」页为员工扫码绑定。 */
    public static final String PERSONAL_WX_BIND_REMARK_ADMIN = "个人微信扫码绑定（管理员操作）";

    /** 人员/部门绑定原因：“同步中心”按指定范围同步（OA 分部/部门 ⇄ 企微部门）时自动建立。 */
    public static final String SCOPE_SYNC_BIND_REMARK = "同步中心按范围同步";

    /* ---------- 同步日志明细（uf_oaones_sync_log_item） ---------- */

    /** 同步明细对象类型：部门。 */
    public static final int SYNC_ITEM_DEPT = 0;
    /** 同步明细对象类型：人员。 */
    public static final int SYNC_ITEM_USER = 1;
    /**
     * 同步明细对象类型：OA 分部。
     *
     * <p>企微里只有“部门”概念、没有“分部”：OA 分部同步到企微时落成一个部门，
     * 明细里单独标出“分部”，便于在预览/日志里看出这一层是怎么来的。</p>
     */
    public static final int SYNC_ITEM_SUB = 2;

    /** 同步明细动作：新增（目标侧原本没有该对象）。 */
    public static final int SYNC_OP_CREATE = 0;
    /** 同步明细动作：更新（目标侧已有该对象）。 */
    public static final int SYNC_OP_UPDATE = 1;
    /** 同步明细动作：跳过（按约束不处理，如免绑定、范围外、缺少必要信息）。 */
    public static final int SYNC_OP_SKIP = 2;
    /** 同步明细动作：失败（调用目标侧接口或写库异常）。 */
    public static final int SYNC_OP_FAIL = 3;
    /** 同步明细动作：删除（对方平台删除 / 取消后，本侧标记删除，不物理删）。 */
    public static final int SYNC_OP_DELETE = 4;

    /** 同步明细对象类型文案：0 部门 / 1 人员。 */
    public static String syncItemTypeText(int itemType) {
        if (itemType == SYNC_ITEM_USER) {
            return "人员";
        }
        if (itemType == SYNC_ITEM_SUB) {
            return "分部";
        }
        return "部门";
    }

    /** 同步明细动作文案：0 新增 / 1 更新 / 2 跳过 / 3 失败。 */
    public static String syncOpText(int op) {
        if (op == SYNC_OP_CREATE) {
            return "新增";
        }
        if (op == SYNC_OP_UPDATE) {
            return "更新";
        }
        if (op == SYNC_OP_SKIP) {
            return "跳过";
        }
        if (op == SYNC_OP_FAIL) {
            return "失败";
        }
        if (op == SYNC_OP_DELETE) {
            return "删除";
        }
        return "";
    }

    /* ---------- 会议 / 日程同步（uf_oaones_mtg_sync / uf_oaones_mtg_sync_log） ---------- */

    /**
     * 会议 / 日程同步的业务类型：会议（一级菜单「会议同步」）。
     *
     * <p>会议同步与日程同步是两条独立的一级菜单，但共用同一张设置表与记录表，
     * 用本列区分：0 会议（来源 E9 {@code Meeting}）/ 1 日程（来源 E9 {@code WorkPlan}）。</p>
     */
    public static final int MTG_BIZ_MEETING = 0;
    /** 会议 / 日程同步的业务类型：日程（一级菜单「日程同步」）。 */
    public static final int MTG_BIZ_SCHEDULE = 1;

    /** 会议 / 日程同步业务文案：0 会议 / 1 日程。 */
    public static String mtgBizText(int biz) {
        return biz == MTG_BIZ_SCHEDULE ? "日程" : "会议";
    }

    /* ---------- 会议 / 日程同步的同步方向（租户级 + 个人级） ---------- */

    /** 个人同步方向口径：0 = 遵循系统设置（默认，用租户级方向）。 */
    public static final int MTG_DIR_MODE_FOLLOW = 0;
    /** 个人同步方向口径：1 = 指定同步方向（用个人自己的两个勾选）。 */
    public static final int MTG_DIR_MODE_CUSTOM = 1;

    /**
     * 同步方向文案：两个方向可双选（列 {@code dir_oa2third} / {@code dir_third2oa}）。
     *
     * @param oa2Third OA 同步到第三方（1 选中 / 0 不选）
     * @param third2Oa 第三方同步到 OA（1 选中 / 0 不选）
     */
    public static String mtgDirText(boolean oa2Third, boolean third2Oa) {
        if (oa2Third && third2Oa) {
            return "双向（OA→第三方 / 第三方→OA）";
        }
        if (oa2Third) {
            return "仅 OA→第三方";
        }
        if (third2Oa) {
            return "仅 第三方→OA";
        }
        return "未选择同步方向";
    }

    /* ---------- 同步中心“同步设置”（uf_oaones_sync_plan） ---------- */

    /**
     * 同步成员时“没有找到部门”（所在部门未绑定 / 目标侧部门不存在）的口径：不同步。
     *
     * <p>默认口径，保持历史约束：不自动创建部门、该成员本次跳过并记入同步日志。</p>
     */
    public static final int SYNC_NODEPT_SKIP = 0;

    /**
     * 同步成员时“没有找到部门”的口径：直接挂在租户下。
     *
     * <p>OA → 平台方向使用：该成员落到本租户配置的 root 节点（企业微信/钉钉取
     * {@code root_dept_id}，飞书取 {@code root_dept_key}）；平台 → OA 方向不使用本值
     * （该方向在同步设置里指定“默认 OA 部门”）。</p>
     */
    public static final int SYNC_NODEPT_ATTACH_TENANT = 1;

    /**
     * 同步中心「计划任务」的方式（{@code uf_oaones_sync_plan.plan_mode}）：
     * 0 = 轮询间隔（分钟，见 {@code interval_min}，<b>页面上是“轮询间隔（分钟）”一档</b>）——
     * 按“每 N 分钟轮询一次”执行，现场不需要懂 cron；
     * 1 = 高级模式（直接写 cron 表达式，Quartz 口径，见 {@code cron}）。
     *
     * <p>老库兼容：{@code plan_mode} 为空的行按“有 cron 就算高级模式、没有 cron 就算轮询间隔”判断，
     * 因此升级后原有 cron 的节奏不变（见 {@code SyncPlanServiceImpl#planModeOf}）。</p>
     */
    public static final int SYNC_PLAN_MODE_INTERVAL = 0;
    /** 同步中心「计划任务」的方式：1 = 高级模式（cron 表达式），见 {@link #SYNC_PLAN_MODE_INTERVAL}。 */
    public static final int SYNC_PLAN_MODE_CRON = 1;
    /** 同步中心「计划任务」轮询间隔的下限（分钟，与页面输入框的 min 一致）。 */
    public static final int SYNC_PLAN_MIN_MINUTES = 5;
    /** 同步中心「计划任务」轮询间隔的上限（分钟，= 24 小时；再长就没有“轮询”的意义了）。 */
    public static final int SYNC_PLAN_MAX_MINUTES = 24 * 60;
    /** 同步中心「计划任务」轮询间隔的默认值（分钟）：新增规则或老库缺值时用它。 */
    public static final int SYNC_PLAN_DEF_MINUTES = 30;

    /** 系统管理员账号ID：可查看与维护全部企微租户。 */
    public static final int SYS_ADMIN_USER_ID = 1;

    /**
     * 会议 / 日程同步：**非本平台创建**的数据在描述里追加的提示语。
     *
     * <p>一条数据只有“创建方”（OA 或第三方平台）能改：把它**初次**同步到对方时，
     * 在对方的描述（企微日程 {@code description}、OA 会议 {@code desc_n} / 日程 {@code description}）
     * 里追加这句话，提醒对方人员“要改请回原平台改”；创建方后续更新时这句保留、不重复追加。</p>
     */
    public static final String MTG_FOREIGN_HINT = "（非本平台创建，请在原平台修改）";

    /** 会议 / 日程同步·对象映射来源：0 = 由「OA→第三方」推送建立（**OA 是创建方**）。 */
    public static final int MTG_LINK_SOURCE_OA = 0;
    /** 会议 / 日程同步·对象映射来源：1 = 由「第三方→OA」拉取建立（**平台是创建方**）。 */
    public static final int MTG_LINK_SOURCE_PLATFORM = 1;

    /* ---------- 会议 / 日程同步的「计划任务」（uf_oaones_mtg_cron）与变更快照 ---------- */

    /** 内容快照（映射表 {@code oa_hash} / {@code wx_hash}）的字符长度：MD5 十六进制，32 位。 */
    public static final int MTG_HASH_LENGTH = 32;
    /**
     * 计划任务频率下限（分钟）：页面填的是 Quartz cron 表达式，相邻两次执行的间隔不得小于它
     * （保存与「预测后 10 次」都会校验，与建表注释里的“频率下限 5 分钟”一致）。
     */
    public static final int MTG_PLAN_MIN_MINUTES = 5;
    /**
     * 计划任务（{@code uf_oaones_mtg_cron.plan_mode}）的**同步方式**：
     * 0 = 按时间段（明细表 {@code uf_oaones_mtg_plan_item}，页面上自由配多个时段 + 每段一个轮询间隔，
     * <b>默认</b>）；1 = 高级（直接写 cron 表达式，白天 / 夜间两档，见 {@code day_cron} / {@code night_cron}）。
     */
    public static final int MTG_PLAN_MODE_ITEM = 0;
    /** 计划任务的同步方式：1 = 高级（cron 表达式），见 {@link #MTG_PLAN_MODE_ITEM}。 */
    public static final int MTG_PLAN_MODE_CRON = 1;
    /**
     * 计划任务的**适用日**（明细表每一行的 {@code day_type}，以及高级模式下的
     * {@code uf_oaones_mtg_cron.cron_day_type}）：0 = 每天。
     */
    public static final int MTG_PLAN_DAY_ALL = 0;
    /** 计划任务的适用日：1 = 仅工作日（按泛微标准考勤口径，见 {@link com.customization.oaones.appsyn.common.OaonesWorkday}）。 */
    public static final int MTG_PLAN_DAY_WORK = 1;
    /** 计划任务的适用日：2 = 仅非工作日（周末 / 法定节假日、扣掉“调配工作日”）。 */
    public static final int MTG_PLAN_DAY_OFF = 2;
    /**
     * 计划任务时间段明细的**轮询间隔上限**（分钟，= 24 小时）：
     * 间隔比时段本身还长时没有任何意义，保存时直接拦住。
     */
    public static final int MTG_PLAN_MAX_MINUTES = 24 * 60;
    /** 一份计划最多配多少条时间段明细（页面上可自由加减，这里只防呆）。 */
    public static final int MTG_PLAN_ITEM_LIMIT = 20;
    /**
     * 平台侧删除失败后的重试节流（小时）：同一对象在这个时间内只再试一次，
     * 避免“对象其实已被平台删掉、我们一直在删”这种场景每轮刷一条失败记录。
     */
    public static final int MTG_DELETE_RETRY_HOURS = 6;

    /**
     * 应用绑定状态（`uf_oaones_app_bind.yx`）：0 有效。
     *
     * <p>与分部绑定表同口径（建模枚举序号从 0 开始），查询一律带 {@code yx = 0}；
     * 与部门绑定表 `yx` 的 1 有效 / 0 失效相反，两处不可混用。</p>
     */
    public static final int APP_BIND_STATE_VALID = 0;
    /** 应用绑定状态：1 无效（停用，见 {@link #APP_BIND_STATE_VALID}）。 */
    public static final int APP_BIND_STATE_INVALID = 1;

    /**
     * 企微工作台应用的最终落地页解析顺序：入口参数 state → 绑定配置的“OA 页面地址”
     * → 绑定配置的“OA 主页地址” → 本默认地址（E9 门户首页）。
     */
    public static final String DEFAULT_OA_HOME_URL = "/wui/index.html";

    /* ---------- 应用中心：应用图标（页面上传，落到 oaones/img/ 下） ---------- */

    /**
     * 图标上传目录（站点根下的相对路径）。
     *
     * <p>E9 部署即 {@code ecology/oaones/img/}；目录随 {@code oaones/} 一起同步，
     * 应用列表页上传的图标都落在这里，页面/桌面按 {@link #APP_ICON_URL_PREFIX} 引用。</p>
     */
    public static final String APP_ICON_DIR = "oaones/img";

    /** 图标地址前缀：记录里的 {@code icon} 只存这个前缀下的站内地址（不上传则留空）。 */
    public static final String APP_ICON_URL_PREFIX = "/oaones/img/";

    /** 单个图标文件大小上限（1MB）：图标是桌面/Dock 上的小图，超过说明没压过。 */
    public static final long APP_ICON_MAX_BYTES = 1024L * 1024L;

    /**
     * 图标长宽建议值（正方形边长，单位像素）：**页面上的建议尺寸就是这个值**。
     *
     * <p>桌面页的图标显示 144px（Dock 里 54px、列表 34px），512 在 2 倍屏上也清晰
     * （144px × 2 = 288px，512 留足余量），同时纯色图标体积仍然很小。</p>
     */
    public static final int APP_ICON_SUGGEST_SIZE = 512;

    /** 图标最小边长（像素）：低于桌面图标显示尺寸（144px）放大后就会发虚（页面会提醒，不拦截）。 */
    public static final int APP_ICON_MIN_SIZE = 144;

    /** 图标最大边长（像素）：超过这个尺寸说明传错了图（页面拦截）。 */
    public static final int APP_ICON_MAX_SIZE = 2048;

    /**
     * 图标允许的图片格式（按**文件头**判定，不看后缀名）：png / jpg / gif / bmp / webp。
     *
     * <p>不开放 SVG：SVG 是同源可执行的脚本文件，直接访问上传后的地址会在 OA 域里执行脚本。</p>
     */
    public static final String APP_ICON_EXTENSIONS = "png / jpg / gif / bmp / webp";

    /** 上传图标落盘时的文件名前缀（清理旧图标时按它判定“这个文件是本模块生成的”）。 */
    public static final String APP_ICON_FILE_PREFIX = "appicon_";

    /* ---------- 统一登录设置：登录页外观（logo / 整页背景图，与图标同一个 oaones/img/ 目录） ----------
       两类图片都走页面上的「上传」（见 service.impl.wecom.LoginConfigServiceImpl#uploadImage），
       落盘前缀不同：清理旧图时只删自己前缀的文件，图标与手工放进该目录的图片都不会被误删。 */

    /** 登录页 logo 落盘时的文件名前缀（显示在登录框内部上方的那张小图）。 */
    public static final String LOGIN_LOGO_FILE_PREFIX = "loginlogo_";

    /** 登录页整页背景图落盘时的文件名前缀。 */
    public static final String LOGIN_BG_FILE_PREFIX = "loginbg_";

    /** 登录页外观图片（logo / 整页背景图）的站内地址前缀（与图标同目录：{@code /oaones/img/}）。 */
    public static final String LOGIN_IMAGE_URL_PREFIX = APP_ICON_URL_PREFIX;

    /** 登录页外观图片（logo / 整页背景图）的上传目录（与图标同目录，见 {@link #APP_ICON_DIR}）。 */
    public static final String LOGIN_IMAGE_DIR = APP_ICON_DIR;

    /** 登录页 logo 文件大小上限（与图标同一口径 1MB；登录页上只是一张窄图，通常几十 KB）。 */
    public static final long LOGIN_LOGO_MAX_BYTES = APP_ICON_MAX_BYTES;

    /**
     * 登录页整页背景图大小上限（2MB）。
     *
     * <p>比 logo 放宽一倍：背景图要铺满整屏（常见 1920×1080），压到 1MB 以内往往要降质很明显；
     * 但仍然卡在 2MB，超过会直接拒绝并提示压缩。</p>
     */
    public static final long LOGIN_BG_MAX_BYTES = 2L * 1024L * 1024L;

    /** 登录页 logo 单边最大像素（与图标同一口径，超过说明传错了图）。 */
    public static final int LOGIN_LOGO_MAX_SIZE = APP_ICON_MAX_SIZE;

    /**
     * 登录页整页背景图单边最大像素（8192）。
     *
     * <p>背景图天然比 logo 大（2K / 4K 屏的壁纸就是 2000~4000 像素），这里只作“显然传错图”的上限，
     * 不去限制 1920、2560 这类正常尺寸。</p>
     */
    public static final int LOGIN_BG_MAX_SIZE = 8192;

    /** 登录页 logo 允许的图片格式（与图标同一口径，按**文件头**判定，不收 SVG）。 */
    public static final String LOGIN_LOGO_EXTENSIONS = APP_ICON_EXTENSIONS;

    /** 登录页整页背景图允许的图片格式（与 logo 同一口径，按**文件头**判定，不收 SVG）。 */
    public static final String LOGIN_BG_EXTENSIONS = APP_ICON_EXTENSIONS;

    /**
     * 登录页外观图片的**上传解析上限**（取两类图片里较大的那一档 = 背景图 2MB）。
     *
     * <p>multipart 解析要先给一个上限（超过直接拒绝、不把整段读进内存），所以这里用 2MB；
     * 真正的**按用途**校验在 Service 里（logo 仍然只允许 1MB，见
     * {@code LoginConfigServiceImpl#uploadImage}），解析上限放宽不会让 logo 变大。</p>
     */
    public static final long LOGIN_IMAGE_MAX_BYTES =
            LOGIN_BG_MAX_BYTES > LOGIN_LOGO_MAX_BYTES ? LOGIN_BG_MAX_BYTES : LOGIN_LOGO_MAX_BYTES;

    /**
     * 登录页左侧富文本（列 {@code brand_html}）允许的最大长度（字符）。
     *
     * <p>富文本存的是 HTML（编辑器产生的标记 + 正文），十几二十行文字通常只有几百字符；
     * 20000 已经够写一大篇说明，超过直接拒绝并提示（不静默截断，避免把 HTML 截断成坏标记）。</p>
     */
    public static final int LOGIN_BRAND_HTML_MAX = 20000;

    /**
     * 登录框自定义布局的范围（CSS 像素）：
     * 坐标相对浏览器可视区域左上角，允许 0；宽高下限保证表单与二维码放得下，
     * 高度不够时登录框内部滚动。保存校验与登录页渲染共用这一组数。
     */
    public static final int LOGIN_BOX_MAX_POS = 20000;
    public static final int LOGIN_BOX_MIN_WIDTH = 320;
    public static final int LOGIN_BOX_MAX_WIDTH = 1600;
    public static final int LOGIN_BOX_MIN_HEIGHT = 320;
    public static final int LOGIN_BOX_MAX_HEIGHT = 1600;

    /**
     * 登录页左侧文字（{@code .login-brand}）自定义布局的范围（CSS 像素）：
     * 与登录框用同一套“视口左上角坐标 + 固定宽高”口径，但文字区可以比登录框窄 / 矮，
     * 因此下限单独给（宽 160、高 60）；高度固定时内容超出在文字区内滚动。
     */
    public static final int LOGIN_BRAND_MAX_POS = 20000;
    public static final int LOGIN_BRAND_MIN_WIDTH = 160;
    public static final int LOGIN_BRAND_MAX_WIDTH = 1600;
    public static final int LOGIN_BRAND_MIN_HEIGHT = 60;
    public static final int LOGIN_BRAND_MAX_HEIGHT = 1600;

    /**
     * 登录框背景透明度（百分比）：0 = 完全不透明，100 = 完全透明。
     * 只作用于登录框的白色底色，文字 / 输入框 / 按钮不受影响。
     */
    public static final int LOGIN_BOX_OPACITY_MIN = 0;
    public static final int LOGIN_BOX_OPACITY_MAX = 100;
    public static final int LOGIN_BOX_OPACITY_DEFAULT = 0;

    /**
     * 登录页左侧文字背景透明度（百分比）：0 = 完全不透明，100 = 完全透明。
     * 只作用于左侧文字区的白色底板，文字本身不受影响；默认 100 用于兼容历史“无底板”外观。
     */
    public static final int LOGIN_BRAND_OPACITY_MIN = 0;
    public static final int LOGIN_BRAND_OPACITY_MAX = 100;
    public static final int LOGIN_BRAND_OPACITY_DEFAULT = 100;

    /** 桌面启动台自动跳转秒数上限（0 = 不自动跳转，最长 120 秒）。 */
    public static final int LOGIN_GOPAGE_MAX_SECONDS = 120;

    /* ==================================================================
       考勤同步（独立一级菜单「考勤同步」，页面 /oaones/attSync/attSync.jsp）
       一行设置 = 平台 + 租户 + 方向（同一平台租户同方向可多条），
       把第三方平台（企微 / 钉钉 / 飞书）的打卡数据写进 OA 的 E9 标准考勤打卡表，
       并支持反向（OA 的打卡写到第三方平台）；两个方向各自独立配置、执行与记日志。
       ================================================================== */

    /** 考勤同步设置表的行类型：0 = 普通同步设置（一行一条规则）。 */
    public static final int ATT_ROW_CFG = 0;
    /**
     * 考勤同步设置表的行类型：1 = **租户级开关**（平台 + 租户这一级整体启停）。
     *
     * <p>租户级开关与同步设置放同一张表：这样“平台 + 租户”的启停只有一个落点，
     * 页面上租户那一段的下拉就是它；同步设置行不受它影响（历史日志保留）。</p>
     */
    public static final int ATT_ROW_TENANT = 1;

    /** 考勤同步方向：0 = 两个方向都跑（默认）。 */
    public static final int ATT_DIR_BOTH = 0;
    /** 考勤同步方向：1 = 仅「第三方 → OA」（把平台打卡写进 OA）。 */
    public static final int ATT_DIR_THIRD2OA = 1;
    /** 考勤同步方向：2 = 仅「OA → 第三方」（把 OA 打卡写到平台）。 */
    public static final int ATT_DIR_OA2THIRD = 2;

    /** 考勤同步方向文案：0 双向 / 1 第三方→OA / 2 OA→第三方。 */
    public static String attDirText(int dir) {
        if (dir == ATT_DIR_THIRD2OA) {
            return "第三方 → OA";
        }
        if (dir == ATT_DIR_OA2THIRD) {
            return "OA → 第三方";
        }
        return "双向（第三方 → OA、OA → 第三方）";
    }

    /** 考勤数据类型位：1 = 正常打卡（平台正常产生的打卡，含外勤 / 出差）。 */
    public static final int ATT_DATA_NORMAL = 1;
    /** 考勤数据类型位：2 = 补签卡（补卡）。 */
    public static final int ATT_DATA_REMEDY = 2;

    /** 考勤数据类型文案：位值 → 中文（逗号分隔）。 */
    public static String attDataText(int bits) {
        StringBuilder sb = new StringBuilder();
        if ((bits & ATT_DATA_NORMAL) != 0) {
            sb.append("正常打卡");
        }
        if ((bits & ATT_DATA_REMEDY) != 0) {
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append("补签卡（补卡）");
        }
        return sb.length() == 0 ? "（未勾选）" : sb.toString();
    }

    /** 打卡时段位：1 = 签到（上班卡）。 */
    public static final int ATT_PERIOD_SIGN_IN = 1;
    /** 打卡时段位：2 = 签退（下班卡）。 */
    public static final int ATT_PERIOD_SIGN_OUT = 2;
    /** 打卡时段默认（两种都同步）。 */
    public static final int ATT_PERIOD_BOTH = ATT_PERIOD_SIGN_IN | ATT_PERIOD_SIGN_OUT;

    /** 打卡时段文案：1 签到 / 2 签退 / 3 两种。 */
    public static String attPeriodText(int bits) {
        if (bits == ATT_PERIOD_SIGN_IN) {
            return "仅签到（上班卡）";
        }
        if (bits == ATT_PERIOD_SIGN_OUT) {
            return "仅签退（下班卡）";
        }
        return "签到 + 签退";
    }

    /** 考勤时间口径：0 = 当天数据（执行时刻所在自然日）。 */
    public static final int ATT_WIN_TODAY = 0;
    /** 考勤时间口径：1 = 前一天数据。 */
    public static final int ATT_WIN_PREV_DAY = 1;
    /** 考勤时间口径：2 = 任意时间范围（页面手工指定起止，一次性 / 定时共用）。 */
    public static final int ATT_WIN_RANGE = 2;
    /** 考勤时间口径：3 = 最近 N 天（补跑漏数据用，N 可配）。 */
    public static final int ATT_WIN_LAST_DAYS = 3;

    /** 考勤时间口径文案。 */
    public static String attWinText(int mode) {
        if (mode == ATT_WIN_PREV_DAY) {
            return "前一天数据";
        }
        if (mode == ATT_WIN_RANGE) {
            return "任意时间范围";
        }
        if (mode == ATT_WIN_LAST_DAYS) {
            return "最近 N 天";
        }
        return "当天数据";
    }

    /** 考勤批次 / 明细的触发方式：0 = 计划任务（内置调度器到点）。 */
    public static final int ATT_TRIGGER_PLAN = 0;
    /** 考勤批次 / 明细的触发方式：1 = 手工「立即执行一次」。 */
    public static final int ATT_TRIGGER_MANUAL = 1;
    /** 考勤批次 / 明细的触发方式：2 = 「重推」（对失败 / 跳过的明细重推）。 */
    public static final int ATT_TRIGGER_RETRY = 2;

    /** 考勤触发方式文案：0 计划任务 / 1 手工 / 2 重推。 */
    public static String attTriggerText(int trigger) {
        if (trigger == ATT_TRIGGER_MANUAL) {
            return "手工执行";
        }
        if (trigger == ATT_TRIGGER_RETRY) {
            return "重推";
        }
        return "计划任务";
    }

    /**
     * 考勤同步「跳过」原因的**固定文案**（需求 3.8.4：跳过与失败分开，跳过原因要文案化，
     * 不允许只写“失败”或留空；这里集中定义，页面 / 日志 / 重推共用一套措辞）。
     */
    public static final String ATT_SKIP_SYNCED = "已同步";
    /** 跳过：OA 人员没有绑定平台账号（人员绑定表里没有有效绑定）。 */
    public static final String ATT_SKIP_NO_BIND = "未绑定平台账号，跳过";
    /** 跳过：OA 侧人员已离职 / 已删除（status ≥ 4），或平台侧成员已离职。 */
    public static final String ATT_SKIP_LEFT = "人员已离职，跳过";
    /** 跳过：该条打卡的类型（正常 / 补卡，签到 / 签退）没有勾选。 */
    public static final String ATT_SKIP_TYPE_OFF = "类型未勾选，跳过";
    /** 跳过：人员不在本设置的同步范围内。 */
    public static final String ATT_SKIP_OUT_OF_SCOPE = "不在同步范围，跳过";
    /** 跳过：打卡时间不在本次时间窗内。 */
    public static final String ATT_SKIP_OUT_OF_WINDOW = "不在时间窗内，跳过";
    /** 跳过：该平台该方向暂不支持写入（需求 3.9 的兜底口径，不做“假成功”）。 */
    public static final String ATT_SKIP_PUSH_UNSUPPORTED = "该平台该方向暂不支持写入打卡（未实测开通），跳过";

    /**
     * 考勤同步「打卡记录 ⇄ 平台记录」映射表的来源：
     * 0 = 由「第三方 → OA」导入建立（OA 侧的这条打卡是平台来的）。
     */
    public static final int ATT_LINK_SOURCE_IMPORT = 0;
    /** 考勤同步映射来源：1 = 由「OA → 第三方」推送建立（平台侧的那条记录是 OA 推过去的）。 */
    public static final int ATT_LINK_SOURCE_PUSH = 1;

    /** 反向写入方式：direct = 平台开放「写入 / 导入打卡记录」接口，直接写入。 */
    public static final String ATT_PUSH_MODE_DIRECT = "direct";
    /** 反向写入方式：card = 平台不开放打卡写入，走平台的补卡 / 申诉类审批接口（等价于代员工提交补卡）。 */
    public static final String ATT_PUSH_MODE_CARD = "card";
    /**
     * 反向写入方式：none = 该平台该方向**显式标记为不支持**（页面上说明原因，不做“看起来在同步”）。
     *
     * <p>默认值就是 {@code none}：三个平台到底落到 direct / card / none 需要**首次上线实测**后回填
     * （需求 Q13），在此之前不发未经确认的写请求。</p>
     */
    public static final String ATT_PUSH_MODE_NONE = "none";

    /** 考勤同步「计划任务」频率下限（分钟）：与会议 / 日程同步同一口径（{@link #MTG_PLAN_MIN_MINUTES}）。 */
    public static final int ATT_PLAN_MIN_MINUTES = MTG_PLAN_MIN_MINUTES;
    /** 考勤同步计划任务方式：0 = 按时间段（默认），1 = 高级（Quartz cron）；与会议同步同构。 */
    public static final int ATT_PLAN_MODE_ITEM = MTG_PLAN_MODE_ITEM;
    /** 考勤同步计划任务方式：1 = 高级（cron 表达式）。 */
    public static final int ATT_PLAN_MODE_CRON = MTG_PLAN_MODE_CRON;
    /** 考勤同步计划任务的适用日：0 每天 / 1 工作日 / 2 非工作日（与会议同步同口径）。 */
    public static final int ATT_PLAN_DAY_ALL = MTG_PLAN_DAY_ALL;
    /** 考勤同步计划任务适用日：1 = 仅工作日。 */
    public static final int ATT_PLAN_DAY_WORK = MTG_PLAN_DAY_WORK;
    /** 考勤同步计划任务适用日：2 = 仅非工作日。 */
    public static final int ATT_PLAN_DAY_OFF = MTG_PLAN_DAY_OFF;
    /** 一条设置的**计划任务**最多配多少条时间段明细（防呆，与会议同步同一量级）。 */
    public static final int ATT_PLAN_ITEM_LIMIT = MTG_PLAN_ITEM_LIMIT;
    /** 计划任务时间段明细的轮询间隔上限（分钟）= 24 小时。 */
    public static final int ATT_PLAN_MAX_MINUTES = MTG_PLAN_MAX_MINUTES;

    /** 考勤同步设置行的「校验状态」：0 = 未校验。 */
    public static final int ATT_CHECK_NONE = 0;
    /** 考勤同步设置行的「校验状态」：1 = 校验通过（凭据与考勤数据读取权限都正常）。 */
    public static final int ATT_CHECK_OK = 1;
    /** 考勤同步设置行的「校验状态」：2 = 校验失败（应用不可用 / 无考勤权限 / Secret 变更）。 */
    public static final int ATT_CHECK_FAIL = 2;

    private OaonesConst() {
    }
}
