package com.customization.oaones.appsyn.dto.feishu;

import com.customization.oaones.appsyn.common.OaonesConst;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次同步的完整计划：范围 + 逐条对象（部门 / 人员）+ 统计。
 *
 * <p>同步前的预览与实际执行共用本对象：预览只读它生成弹窗文案与清单，
 * 执行时逐条按 {@link #getItems()} 落库并回填结果。</p>
 */
public class SyncPlan {

    private SyncScope scope;
    private List<SyncPlanItem> items = new ArrayList<SyncPlanItem>();

    public SyncScope getScope() { return scope; }
    public void setScope(SyncScope scope) { this.scope = scope; }

    public List<SyncPlanItem> getItems() { return items; }
    public void setItems(List<SyncPlanItem> items) { this.items = items; }

    public void addItem(SyncPlanItem item) {
        if (item != null) {
            item.setSeq(items.size() + 1);
            items.add(item);
        }
    }

    /** 按对象类型与动作统计条数。 */
    public int count(int itemType, int op) {
        int total = 0;
        for (int i = 0; i < items.size(); i++) {
            SyncPlanItem item = items.get(i);
            if (item.getItemType() == itemType && item.getOp() == op) {
                total++;
            }
        }
        return total;
    }

    /**
     * “部门类”对象（部门 + 分部）按动作统计条数。
     *
     * <p>飞书里只有部门概念：A = OA 分部时该分部会落成一个部门，
     * 因此预览/统计里的“部门”要同时算上“分部”这一类明细。</p>
     */
    public int countDeptLike(int op) {
        return count(OaonesConst.SYNC_ITEM_DEPT, op) + count(OaonesConst.SYNC_ITEM_SUB, op);
    }

    /** 该对象类型是否属于“部门类”（部门 / 分部）。 */
    public static boolean isDeptLike(int itemType) {
        return itemType == OaonesConst.SYNC_ITEM_DEPT || itemType == OaonesConst.SYNC_ITEM_SUB;
    }

    /** 部门类对象（部门 + 分部）的明细清单，按处理顺序返回。 */
    public List<SyncPlanItem> ofDeptLike() {
        List<SyncPlanItem> list = new ArrayList<SyncPlanItem>();
        for (int i = 0; i < items.size(); i++) {
            if (isDeptLike(items.get(i).getItemType())) {
                list.add(items.get(i));
            }
        }
        return list;
    }

    /** 某类型（部门/人员）的明细清单，按处理顺序返回。 */
    public List<SyncPlanItem> of(int itemType) {
        List<SyncPlanItem> list = new ArrayList<SyncPlanItem>();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getItemType() == itemType) {
                list.add(items.get(i));
            }
        }
        return list;
    }

    /** 部门需新增的条数。 */
    public int deptCreateCount() { return countDeptLike(OaonesConst.SYNC_OP_CREATE); }
    /** 部门需更新的条数。 */
    public int deptUpdateCount() { return countDeptLike(OaonesConst.SYNC_OP_UPDATE); }
    /** 人员需新增的条数。 */
    public int userCreateCount() { return count(OaonesConst.SYNC_ITEM_USER, OaonesConst.SYNC_OP_CREATE); }
    /** 人员需更新的条数。 */
    public int userUpdateCount() { return count(OaonesConst.SYNC_ITEM_USER, OaonesConst.SYNC_OP_UPDATE); }
}
