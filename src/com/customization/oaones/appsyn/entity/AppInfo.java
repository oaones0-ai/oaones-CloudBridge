package com.customization.oaones.appsyn.entity;

/**
 * 应用中心实体：一个「应用」（第三方系统 / 外部页面）。
 *
 * <p>对应业务表：表名取 {@code oaones.appsyn.properties#oaones_app_table}（默认 {@code uf_oaones_appsyn_app}），
 * 建模模块ID按表名反查，配置里不出现。</p>
 *
 * <p>一行 = 桌面上（macOS 风格启动台）的一个应用图标，三个关键字段：</p>
 * <ul>
 *   <li>{@link #appName}  应用名称（图标下方的名字、列表里的标识）；</li>
 *   <li>{@link #appUrl}   应用默认访问地址（没有配置单点登录实现类时直接打开它）；</li>
 *   <li>{@link #ssoClass} 单点登录实现类全限定名 —— 指向“对方身份认证”的具体实现类
 *       （必须实现 {@code com.customization.oaones.appsyn.appsso.AppSsoHandler}），
 *       类里实现的方法负责把 OA 登录人换成对方系统认可的身份凭据，并返回最终访问地址；
 *       留空 = 不做单点登录，直接打开 {@link #appUrl}。</li>
 * </ul>
 *
 * <p>{@link #ssoMethod} 只是给维护人看的“方法说明”（例如方法签名或备注），
 * 不参与运行；真正被调用的是 {@code AppSsoHandler#loginUrl}。</p>
 *
 * <p>{@link #iconSeed} 是**默认文字图标**（{@link #icon} 为空时按首字生成的渐变色块）
 * 的“取字 / 取色基准”：首次以默认图标保存时固化为当时的 {@link #appName}，
 * 之后改名不跟随（列表与桌面都用它算首字与底色，见 AppCenterServiceImpl#resolveIconSeed）。</p>
 *
 * <p>{@link #openMode} 是桌面启动台点击图标时的**打开方式**：
 * 0 默认（在当前页面 / 框架内打开，外层 OA 框架不动）、1 新标签页、2 原标签页（整个页面 reload）。</p>
 *
 * <p>{@link #maintainerIds} 是**维护人**（逗号分隔的 OA 人员ID，可多选）：
 * 应用由“创建人（{@link #modedatacreater}）+ 维护人”维护；创建人天然可维护，不用重复填进维护人；
 * 系统管理员（user.id=1）可查看与维护全部应用。</p>
 *
 * <p>{@link #visAll} 与 {@link #visRoleIds} / {@link #visSubIds} / {@link #visDeptIds} / {@link #visUserIds}
 * 是**可见范围**：
 * {@link #visAll} = 1 表示**全员可见**（不控制权限，工作台上所有登录人都能看到这个应用）；
 * = 0（默认）时按角色 / 分部 / 部门 / 人员四类范围判定，四类**满足其一**即在工作台（桌面启动台）可见，
 * 四类都为空 = **无权限**（除创建人 / 维护人 / 系统管理员外都看不到）；
 * 分部 / 部门范围**只按当前分部 / 当前部门判定，不含下级组织**；角色按登录人的角色成员归属判定
 * （见 AppCenterServiceImpl#visibleTo）。</p>
 */
public class AppInfo {

    private Integer id;
    /** 应用名称（必填，同名校验由 Service 做）。 */
    private String appName;
    /** 应用默认访问地址（必填；可填 http(s) 绝对地址，也可填站内相对地址）。 */
    private String appUrl;
    /**
     * 单点登录实现类（全限定名，例如 {@code com.xxx.sso.JdySsoHandler}）。
     *
     * <p>必须是 {@code AppSsoHandler} 的实现类；留空表示不使用单点登录。</p>
     */
    private String ssoClass;
    /** 对方身份认证方法的说明（选填，纯展示：方法名 / 签名 / 备注）。 */
    private String ssoMethod;
    /** 图标地址（http(s) 或站内相对地址，选填；留空时桌面用应用名首字生成色块图标）。 */
    private String icon;
    /**
     * 默认文字图标的取字 / 取色基准（选填，与 {@link #icon} 互斥使用）。
     *
     * <p>{@link #icon} 为空时，桌面与列表按这个基准生成渐变色块图标（首字 + 底色都由它算出）；
     * 基准在首次以默认图标保存时固化为当时的应用名，**之后改名不跟随**——
     * 维护人在页面上点「清除图标」时才按当前应用名重新固化。</p>
     */
    private String iconSeed;
    /**
     * 打开方式：0 默认 / 1 新标签页 / 2 原标签页（整个页面 reload）。
     *
     * <p>0 默认 = 只把**当前页面 / 框架**换成应用地址，外层 OA 框架不动（不是整个页面刷新）；
     * 1 = 浏览器新开一个标签页打开；
     * 2 = 把**整个页面**（最外层窗口，含外层 OA 框架）换成应用地址。
     * 库里为 NULL（老库补列后的历史行）或非法值时按 0 默认处理。</p>
     */
    private Integer openMode;
    /**
     * 维护人（OA 人员ID，逗号分隔，可多选；留空 = 没有额外维护人）。
     *
     * <p>应用由**创建人 + 维护人**维护：创建人记在 {@link #modedatacreater}，天然可维护（不用重复填）；
     * 维护人字段用于把维护权追加给别人；系统管理员（user.id=1）可查看与维护全部应用。
     * 页面上的口径见 AppCenterServiceImpl#normalizeMaintainers / #belongsTo。</p>
     */
    private String maintainerIds;
    /**
     * 可见范围·全员可见：1 = 不控制权限（工作台上所有登录人都能看到这个应用）/ 0 = 按四类范围判定。
     *
     * <p>页面上这个开关在「角色 / 分部 / 部门 / 人员」四类之上（见 appCenter/appListPanel.jsp）：
     * 勾选后四类选择框禁用，**四类范围不再参与判定**；已选的四类内容仍会照原样保存，
     * 取消勾选后立即恢复生效。库里为 NULL（老库补列前的历史行）时按 0 处理。</p>
     */
    private Integer visAll;
    /** 可见范围·角色（OA 角色ID，逗号分隔，可多选；空 = 该类别不限制，未勾选全员可见且四类都空 = 无权限）。 */
    private String visRoleIds;
    /** 可见范围·分部（OA 分部ID，逗号分隔，可多选；空 = 该类别不限制，未勾选全员可见且四类都空 = 无权限）。 */
    private String visSubIds;
    /** 可见范围·部门（OA 部门ID，逗号分隔，可多选；空 = 该类别不限制，未勾选全员可见且四类都空 = 无权限）。 */
    private String visDeptIds;
    /** 可见范围·人员（OA 人员ID，逗号分隔，可多选；空 = 该类别不限制，未勾选全员可见且四类都空 = 无权限）。 */
    private String visUserIds;
    /** 桌面 / 列表排序号（小的排前面，默认 0）。 */
    private Integer sortNo;
    /** 启用状态：0 停用 / 1 启用（停用后不出现在桌面上，也不能跳转）。 */
    private Integer enable;
    private String remark;
    private Integer formmodeid;
    private Integer modedatacreater;
    private String modedatacreatedate;
    private String modedatacreatetime;
    private Integer modedatamodifier;
    private String modedatamodifydatetime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public String getAppUrl() { return appUrl; }
    public void setAppUrl(String appUrl) { this.appUrl = appUrl; }

    public String getSsoClass() { return ssoClass; }
    public void setSsoClass(String ssoClass) { this.ssoClass = ssoClass; }

    public String getSsoMethod() { return ssoMethod; }
    public void setSsoMethod(String ssoMethod) { this.ssoMethod = ssoMethod; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getIconSeed() { return iconSeed; }
    public void setIconSeed(String iconSeed) { this.iconSeed = iconSeed; }

    public Integer getOpenMode() { return openMode; }
    public void setOpenMode(Integer openMode) { this.openMode = openMode; }

    public String getMaintainerIds() { return maintainerIds; }
    public void setMaintainerIds(String maintainerIds) { this.maintainerIds = maintainerIds; }

    public Integer getVisAll() { return visAll; }
    public void setVisAll(Integer visAll) { this.visAll = visAll; }

    public String getVisRoleIds() { return visRoleIds; }
    public void setVisRoleIds(String visRoleIds) { this.visRoleIds = visRoleIds; }

    public String getVisSubIds() { return visSubIds; }
    public void setVisSubIds(String visSubIds) { this.visSubIds = visSubIds; }

    public String getVisDeptIds() { return visDeptIds; }
    public void setVisDeptIds(String visDeptIds) { this.visDeptIds = visDeptIds; }

    public String getVisUserIds() { return visUserIds; }
    public void setVisUserIds(String visUserIds) { this.visUserIds = visUserIds; }

    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }

    public Integer getEnable() { return enable; }
    public void setEnable(Integer enable) { this.enable = enable; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Integer getFormmodeid() { return formmodeid; }
    /** 建模模块ID按表名反查；反查不到（表不是建模表）时不写值（留 NULL，不写 0）。 */
    public void setFormmodeid(Integer formmodeid) {
        this.formmodeid = (formmodeid == null || formmodeid.intValue() <= 0) ? null : formmodeid;
    }

    public Integer getModedatacreater() { return modedatacreater; }
    public void setModedatacreater(Integer modedatacreater) { this.modedatacreater = modedatacreater; }

    public String getModedatacreatedate() { return modedatacreatedate; }
    public void setModedatacreatedate(String modedatacreatedate) {
        this.modedatacreatedate = modedatacreatedate;
    }

    public String getModedatacreatetime() { return modedatacreatetime; }
    public void setModedatacreatetime(String modedatacreatetime) {
        this.modedatacreatetime = modedatacreatetime;
    }

    public Integer getModedatamodifier() { return modedatamodifier; }
    public void setModedatamodifier(Integer modedatamodifier) { this.modedatamodifier = modedatamodifier; }

    public String getModedatamodifydatetime() { return modedatamodifydatetime; }
    public void setModedatamodifydatetime(String modedatamodifydatetime) {
        this.modedatamodifydatetime = modedatamodifydatetime;
    }
}
