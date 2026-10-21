package com.customization.oaones.appsyn.msg.dingtalk.dao;

import com.customization.oaones.appsyn.msg.dingtalk.dto.MsgReceiver;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 消息接收人 Mapper：OA 账号 <-> 钉钉 userid 映射（复用组织同步的绑定关系）。
 */
public interface MsgReceiverMapper {

    /** 按 OA 人员ID批量取绑定关系（bind_state = 1 才算绑定）。 */
    List<MsgReceiver> listByOaUsers(@Param("userBindTable") String userBindTable,
                                    @Param("cfgTable") String cfgTable,
                                    @Param("cfgId") Integer cfgId,
                                    @Param("oaUserIds") List<Integer> oaUserIds);

    /** 按钉钉 userid 批量反查绑定关系（外部系统直接传钉钉账号时定位租户）。 */
    List<MsgReceiver> listByWxUserids(@Param("userBindTable") String userBindTable,
                                      @Param("cfgTable") String cfgTable,
                                      @Param("wxUserids") List<String> wxUserids);

    /** 按登录名 / 工号查 OA 人员ID（页面测试推送、统一消息接口按账号传入时使用）。 */
    List<Integer> findOaUserIdsByAccounts(@Param("accounts") List<String> accounts);

    /** 按 OA 人员ID取姓名 / 登录名（日志与页面展示）。 */
    List<MsgReceiver> listOaUsers(@Param("oaUserIds") List<Integer> oaUserIds);
}
