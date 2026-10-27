package com.customization.oaones.appsyn.dto.wecom;

import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 同步中心的“同步范围”（同步中心页先选范围、再同步）。
 *
 * <p>范围由两侧节点组成，两侧都**含下级**：</p>
 * <ul>
 *   <li>OA 侧：页面上选择的 OA 分部 / OA 部门（分部自动含其下级分部与部门，部门自动含其下级部门），
 *       解析后落在 {@link #getOaDepts()}（按“父 → 子”层级排序）；</li>
 *   <li>企微侧：页面上选择的企微租户下部门（自动含其下级部门）；
 *       <b>不指定部门时取租户配置的 root 节点</b>（{@code uf_oaones_appsyn_qywx_config.root_dept_id}），
 *       解析后落在 {@link #getWxDepts()}（按“父 → 子”层级排序）。</li>
 * </ul>
 *
 * <p>方向决定两侧谁是来源、谁是落点：</p>
 * <ul>
 *   <li>OA → 企微：OA 侧范围是来源，企微侧节点是落点（新增部门的父部门 = 该企微节点）；</li>
 *   <li>企微 → OA：企微侧范围是来源，OA 侧范围是落点（新增部门的父部门 = OA 侧的落点部门）。</li>
 * </ul>
 */
public class SyncScope {

    /** 企微租户配置（含 corp_name / root_dept_id / sync_enable 等）。 */
    private JSONObject cfg;
    private String cfgId;
    /** 同步方向：0 OA→企微 / 1 企微→OA。 */
    private int direction;

    /** 页面上选择的 OA 分部（原始选择，不含下级展开）。 */
    private List<JSONObject> oaSubs = new ArrayList<JSONObject>();
    /** 页面上选择的 OA 部门（原始选择，不含下级展开）。 */
    private List<JSONObject> oaDeptsSelected = new ArrayList<JSONObject>();
    /** 解析后的 OA 部门（含下级，按“父 → 子”排序）。 */
    private List<JSONObject> oaDepts = new ArrayList<JSONObject>();

    /** 有效的企微侧节点ID：页面上没选部门时为租户 root 节点。 */
    private String wxDeptId = "";
    /** 解析后的企微部门（含下级，按“父 → 子”排序）。 */
    private List<JSONObject> wxDepts = new ArrayList<JSONObject>();

    /** 范围文案：如“OA 分部「研发中心」（含下级数据）”“OA 部门「质量部」（含下级数据）”。 */
    private String scopeText = "";
    /** 企微侧节点文案：如“企业微信「研发中心」（含下级部门）”“企业微信 root 节点”。 */
    private String wxScopeText = "";

    public JSONObject getCfg() { return cfg; }
    public void setCfg(JSONObject cfg) { this.cfg = cfg; }

    public String getCfgId() { return cfgId; }
    public void setCfgId(String cfgId) { this.cfgId = cfgId; }

    public int getDirection() { return direction; }
    public void setDirection(int direction) { this.direction = direction; }

    public List<JSONObject> getOaSubs() { return oaSubs; }
    public void setOaSubs(List<JSONObject> oaSubs) { this.oaSubs = oaSubs; }

    public List<JSONObject> getOaDeptsSelected() { return oaDeptsSelected; }
    public void setOaDeptsSelected(List<JSONObject> oaDeptsSelected) { this.oaDeptsSelected = oaDeptsSelected; }

    public List<JSONObject> getOaDepts() { return oaDepts; }
    public void setOaDepts(List<JSONObject> oaDepts) { this.oaDepts = oaDepts; }

    public String getWxDeptId() { return wxDeptId; }
    public void setWxDeptId(String wxDeptId) { this.wxDeptId = wxDeptId; }

    public List<JSONObject> getWxDepts() { return wxDepts; }
    public void setWxDepts(List<JSONObject> wxDepts) { this.wxDepts = wxDepts; }

    public String getScopeText() { return scopeText; }
    public void setScopeText(String scopeText) { this.scopeText = scopeText; }

    public String getWxScopeText() { return wxScopeText; }
    public void setWxScopeText(String wxScopeText) { this.wxScopeText = wxScopeText; }

    /** 该企微部门ID是否在本次企微侧范围内（含下级）。 */
    public boolean wxDeptInScope(String deptId) {
        String key = deptId == null ? "" : deptId.trim();
        for (int i = 0; i < wxDepts.size(); i++) {
            if (key.equals(String.valueOf(wxDepts.get(i).getLongValue("id")))) {
                return true;
            }
        }
        return false;
    }

    /** 该 OA 部门ID是否在本次 OA 侧范围内（含下级）。 */
    public boolean oaDeptInScope(String deptId) {
        String key = deptId == null ? "" : deptId.trim();
        for (int i = 0; i < oaDepts.size(); i++) {
            if (key.equals(String.valueOf(oaDepts.get(i).getLongValue("id")))) {
                return true;
            }
        }
        return false;
    }
}
