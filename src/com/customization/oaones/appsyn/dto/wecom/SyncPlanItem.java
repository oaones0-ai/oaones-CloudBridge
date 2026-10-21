package com.customization.oaones.appsyn.dto.wecom;

/**
 * 同步计划 / 同步明细中的一行：一个同步对象（单条部门或单条人员）。
 *
 * <p>同一结构用于三处，保证“预览内容”与“实际执行内容”一致：</p>
 * <ol>
 *   <li>同步前的预览弹窗（本次将更新/新增哪些部门、哪些人员）；</li>
 *   <li>实际同步时逐条执行与结果回填（成功/跳过/失败原因）；</li>
 *   <li>同步日志明细表（uf_oaones_sync_log_item）逐条留痕。</li>
 * </ol>
 */
public class SyncPlanItem {

    /** 对象类型：见 {@code OaonesConst#SYNC_ITEM_DEPT / SYNC_ITEM_USER}。 */
    private int itemType;
    /** 动作：见 {@code OaonesConst#SYNC_OP_CREATE / SYNC_OP_UPDATE / SYNC_OP_SKIP / SYNC_OP_FAIL}。 */
    private int op;

    private String oaId = "";
    private String oaName = "";
    private String wxId = "";
    private String wxName = "";

    /** 目标侧父节点（部门同步时的挂靠位置）。 */
    private String parentId = "";
    private String parentName = "";

    /**
     * 父节点解析方式（目标侧父节点本次还没确定时使用）：
     *
     * <ul>
     *   <li>{@code "dept"}：父节点 = 源侧父部门本次在目标侧对应的部门
     *       （OA→企微 是 OA 父部门本次对应的企微部门；企微→OA 是企微父部门本次对应的 OA 部门，
     *       父部门本次一起新建时才算得出来）；</li>
     *   <li>{@code "sub"}：父节点 = 分部（OA→企微 是 A 选中的 OA 分部在企微侧对应的部门，
     *       分部在企微下落成一个部门；企微→OA 是本次的 OA 落点分部，
     *       新建部门成为该分部的顶级部门）；</li>
     *   <li>{@code "bindRoot"}：该行只负责建立“企微范围根部门 ⇄ OA 落点部门”的绑定（企微 → OA 方向）；</li>
     *   <li>空：直接用 {@link #getParentId()}（已知的真实ID，或同步落点）。</li>
     * </ul>
     */
    private String parentRef = "";

    /**
     * OA 分部ID：OA→企微 是 A 选中的 OA 分部（用于把该分部下的顶层部门挂到分部对应的企微部门下）；
     * 企微→OA 是本次的 OA 落点分部（落点只选分部时，新建部门作为该分部的顶级部门）。
     */
    private String oaSubId = "";

    /**
     * 源侧父节点ID：用于执行阶段把“本次一起新建的父节点”解析成真实ID。
     *
     * <p>OA→企微方向是 OA 父部门ID（它的企微部门可能本次才新建）；
     * 企微→OA 方向是企微父部门ID（它的 OA 部门可能本次才新建）。
     * 目标侧父节点在预览阶段取不到（父节点本次新建）时 {@link #getParentId()} 为空，
     * 执行时按本字段从“本次已处理映射”里解析。</p>
     */
    private String srcParentId = "";

    /** 过程说明（写入日志明细、预览弹窗列表右侧）。 */
    private String message = "";
    /** 本次同步内的处理顺序。 */
    private int seq;

    public int getItemType() { return itemType; }
    public void setItemType(int itemType) { this.itemType = itemType; }

    public int getOp() { return op; }
    public void setOp(int op) { this.op = op; }

    public String getOaId() { return oaId; }
    public void setOaId(String oaId) { this.oaId = oaId == null ? "" : oaId; }

    public String getOaName() { return oaName; }
    public void setOaName(String oaName) { this.oaName = oaName == null ? "" : oaName; }

    public String getWxId() { return wxId; }
    public void setWxId(String wxId) { this.wxId = wxId == null ? "" : wxId; }

    public String getWxName() { return wxName; }
    public void setWxName(String wxName) { this.wxName = wxName == null ? "" : wxName; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId == null ? "" : parentId; }

    public String getParentName() { return parentName; }
    public void setParentName(String parentName) { this.parentName = parentName == null ? "" : parentName; }

    public String getParentRef() { return parentRef; }
    public void setParentRef(String parentRef) { this.parentRef = parentRef == null ? "" : parentRef; }

    public String getOaSubId() { return oaSubId; }
    public void setOaSubId(String oaSubId) { this.oaSubId = oaSubId == null ? "" : oaSubId; }

    public String getSrcParentId() { return srcParentId; }
    public void setSrcParentId(String srcParentId) { this.srcParentId = srcParentId == null ? "" : srcParentId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message == null ? "" : message; }

    public int getSeq() { return seq; }
    public void setSeq(int seq) { this.seq = seq; }
}
