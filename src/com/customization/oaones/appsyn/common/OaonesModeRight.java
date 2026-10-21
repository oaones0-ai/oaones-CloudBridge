package com.customization.oaones.appsyn.common;

import com.customization.oaones.appsyn.dao.ModeDataMapper;
import com.customization.oaones.appsyn.dto.ModeAudit;
import weaver.formmode.setup.ModeRightInfo;
import weaver.general.BaseBean;

/**
 * E9 建模表数据权限维护。
 *
 * <p>业务表新增数据后按模块默认共享规则赋权，保证在建模引擎里能按创建人查到；
 * 修改数据后按原创建人重建单条数据共享权限。</p>
 */
public final class OaonesModeRight {

    private OaonesModeRight() {
    }

    /** 新增建模数据后，按模块默认共享规则为单条数据赋权。 */
    public static void shareNewData(String table, int modeId, String dataId, String operatorId) {
        int id = OaonesUtil.i(dataId, 0);
        if (table == null || table.length() == 0 || modeId <= 0 || id <= 0) {
            return;
        }
        try {
            int creator = OaonesUtil.i(operatorId, 0);
            if (creator <= 0) {
                creator = 1;
            }
            ModeRightInfo right = new ModeRightInfo();
            right.setNewRight(true);
            right.editModeDataShare(creator, modeId, id);
        } catch (Throwable t) {
            new BaseBean().writeLog("oaones share new mode data failed table=" + table
                    + " id=" + dataId + " modeId=" + modeId, t);
        }
    }

    /** 编辑建模数据后重建单条数据共享权限，沿用数据原创建人。 */
    public static void rebuildOnEdit(String table, int modeId, String dataId) {
        int id = OaonesUtil.i(dataId, 0);
        if (table == null || table.length() == 0 || modeId <= 0 || id <= 0) {
            return;
        }
        try {
            ModeAudit audit = OaonesMyBatis.call(ModeDataMapper.class,
                    mapper -> mapper.selectModeAudit(table, id));
            int actualModeId = modeId;
            int creator = 0;
            if (audit != null) {
                actualModeId = audit.getFormmodeid() == null ? modeId : audit.getFormmodeid();
                creator = audit.getModedatacreater() == null ? 0 : audit.getModedatacreater();
            }
            if (actualModeId <= 0) {
                return;
            }
            if (creator <= 0) {
                creator = 1;
            }
            ModeRightInfo right = new ModeRightInfo();
            right.rebuildModeDataShareByEdit(creator, actualModeId, id);
        } catch (Throwable t) {
            new BaseBean().writeLog("oaones rebuild mode data right failed table=" + table
                    + " id=" + dataId + " modeId=" + modeId, t);
        }
    }
}
