package com.customization.oaones.appsyn.service;

import com.alibaba.fastjson.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * 应用中心服务（应用列表维护 + 桌面启动）。
 *
 * <p>「应用中心」是**没有租户维度**的公共菜单（与个人微信同口径）：
 * 一套部署只有一份应用列表，因此接口里没有 cfg_id，也不做租户分权，
 * 维护权限按“系统管理员 + {@code oaones.properties#appc.admin.ids}”判定。</p>
 */
public interface AppCenterService {

    /** 当前登录人能否进应用中心（系统管理员 / appc.admin.ids 名单 / 名下有应用的人）；具体某条应用能否改动见应用级校验。 */
    boolean canMaintain(String operatorId);

    /**
     * 当前登录人能否维护「启动台自动跳转」：系统管理员 + appc.admin.ids。
     * 名下有应用的普通维护人不在此列（该设置影响所有登录人的落点）。
     */
    boolean canMaintainGopage(String operatorId);

    /** 启动台自动跳转设置（配置值 / 实际生效值 / 默认值 / 范围）。 */
    JSONObject gopageSettings(String operatorId);

    /** 只保存启动台自动跳转秒数，不改动任何登录页设置。 */
    JSONObject saveGopageSeconds(String seconds, String operatorId);

    /** 应用列表（管理页）：含停用应用；keyword 为空表示不过滤。 */
    JSONObject list(String keyword, String operatorId);

    /**
     * 可见范围 · 角色候选：OA 标准角色（hrmroles）的 ID + 名称，供页面多选。
     * 只返回正整数ID（虚拟角色 / 负数ID不参与权限配置）。
     */
    List<Map<String, Object>> roles(String operatorId);

    /**
     * 桌面启动台用的启用中应用（任何登录人都可读）：
     * 只下发**可见范围命中**的应用（角色/分部/部门/人员四类满足其一），
     * 维护人 / 创建人 / 系统管理员不受可见范围限制（便于验证自己维护的应用）。
     */
    List<Map<String, Object>> desktopApps(String operatorId);

    /** 新建 / 编辑一条应用（id 为空 = 新建）。 */
    JSONObject save(JSONObject params, String operatorId);

    /** 启用 / 停用（enable：0 停用 / 1 启用）。 */
    JSONObject setState(String id, String enable, String operatorId);

    /** 删除一条应用。 */
    JSONObject delete(String id, String operatorId);

    /**
     * 图标上传（页面上传，不手填地址）：把上传的图片落到 {@code oaones/img/} 下，
     * 返回该图片的站内地址给页面回填到记录里。
     *
     * <p>只做“校验 + 落盘 + 回地址”，不写业务表：维护人上传完图标后按保存，
     * 地址才随 {@link #save} 一起写进 {@code uf_oaones_app.icon}。
     * 这也是本模块唯一的文件上传入口，因此：
     * 只有 {@code appc.admin.ids} 与系统管理员能调（{@code assertMaintain}），
     * 文件类型按**文件头**判定（客户改后缀名传上来的其它文件会被拒），
     * 落盘文件名由服务端生成（不采用上传上来的文件名，避免路径穿越与中文名乱码）。</p>
     *
     * @param originalName 上传时的原始文件名（只用于日志，不落盘）
     * @param content      文件内容
     * @param operatorId   当前登录人ID
     * @return {@code {url, file_name, width, height, size, suggest_size}} 等回显信息
     */
    JSONObject saveIcon(String originalName, byte[] content, String operatorId);

    /**
     * 校验“单点登录实现类”是否可用（类存在、实现 AppSsoHandler、可实例化）。
     *
     * @return {@code {ok:true, method:"loginUrl(...)"}} 或 {@code {ok:false, msg:"原因"}}
     */
    JSONObject checkClass(String className);

    /**
     * 打开应用：按应用的“单点登录实现类”生成最终访问地址。
     *
     * <p>没有配置实现类（或实现类不可用）时退回应用默认地址 {@code app_url}。</p>
     */
    JSONObject entry(String appId, String operatorId);
}
