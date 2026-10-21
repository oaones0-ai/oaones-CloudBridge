package com.customization.oaones.appsyn.entity;

/**
 * 操作监控日志（表名取 oaones.properties#oaones_op_log_table）。
 *
 * <p>一行 = 一次“操作监控平台”关心的事件：</p>
 * <ul>
 *   <li>{@code login} —— 某人什么时候登录（会话里第一次出现登录态的页面请求）；</li>
 *   <li>{@code logout} —— 某人什么时候退出（点到退出地址）；</li>
 *   <li>{@code menu} —— 某人访问了哪个菜单（请求地址命中 E9 菜单表 LeftMenuInfo 配置的菜单）。</li>
 * </ul>
 *
 * <p>姓名 / 部门都写<b>快照</b>，人员改部门或改名不影响已经落库的历史记录。
 * 本表<b>不做权限重构</b>：只记 {@code formmodeid}（按表名反查，便于在建模引擎里查日志）。</p>
 */
public class OpLog {

    /** 事件类型：登录。 */
    public static final String TYPE_LOGIN = "login";
    /** 事件类型：退出。 */
    public static final String TYPE_LOGOUT = "logout";
    /** 事件类型：菜单访问。 */
    public static final String TYPE_MENU = "menu";

    /** 事件类型文案：登录 / 退出 / 菜单访问。 */
    public static String typeText(String type) {
        if (TYPE_LOGIN.equals(type)) {
            return "登录";
        }
        if (TYPE_LOGOUT.equals(type)) {
            return "退出";
        }
        if (TYPE_MENU.equals(type)) {
            return "菜单访问";
        }
        return "其他";
    }

    private Long id;
    /** OA 人员ID（HrmResource.id）；取不到登录态时为 0。 */
    private Integer userId;
    /** 登录名（HrmResource.loginid）。 */
    private String loginName;
    /** 姓名快照。 */
    private String userName;
    /** 主部门名称快照。 */
    private String deptName;
    /** 事件类型：login / logout / menu。 */
    private String opType;
    /** 命中的菜单ID（LeftMenuInfo.id）；登录 / 退出为空。 */
    private String menuId;
    /** 菜单名称（customName 或平台菜单标签）。 */
    private String menuName;
    /** 菜单配置地址（linkAddress / fullrouteurl 等原样）。 */
    private String menuPath;
    /** 本次实际请求地址（站内路径 + 查询串）。 */
    private String pageUrl;
    /** 客户端 IP。 */
    private String clientIp;
    /** 客户端：PC / MOBILE。 */
    private String device;
    /** 会话ID（登录 / 退出按它配对）。 */
    private String sessionId;
    private Integer formmodeid;
    /** 事件日期（建模系统标准字段）：yyyy-MM-dd。 */
    private String modedatacreatedate;
    /** 事件时间（建模系统标准字段）：HH:mm:ss。 */
    private String modedatacreatetime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public String getLoginName() { return loginName; }
    public void setLoginName(String loginName) { this.loginName = loginName; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }

    public String getOpType() { return opType; }
    public void setOpType(String opType) { this.opType = opType; }

    public String getMenuId() { return menuId; }
    public void setMenuId(String menuId) { this.menuId = menuId; }

    public String getMenuName() { return menuName; }
    public void setMenuName(String menuName) { this.menuName = menuName; }

    public String getMenuPath() { return menuPath; }
    public void setMenuPath(String menuPath) { this.menuPath = menuPath; }

    public String getPageUrl() { return pageUrl; }
    public void setPageUrl(String pageUrl) { this.pageUrl = pageUrl; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public String getDevice() { return device; }
    public void setDevice(String device) { this.device = device; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Integer getFormmodeid() { return formmodeid; }
    /** 建模模块ID按表名反查；反查不到（表不是建模表）时不写值（留 NULL，不写 0）。 */
    public void setFormmodeid(Integer formmodeid) {
        this.formmodeid = (formmodeid == null || formmodeid.intValue() <= 0) ? null : formmodeid;
    }

    public String getModedatacreatedate() { return modedatacreatedate; }
    public void setModedatacreatedate(String modedatacreatedate) {
        this.modedatacreatedate = modedatacreatedate;
    }

    public String getModedatacreatetime() { return modedatacreatetime; }
    public void setModedatacreatetime(String modedatacreatetime) {
        this.modedatacreatetime = modedatacreatetime;
    }
}
