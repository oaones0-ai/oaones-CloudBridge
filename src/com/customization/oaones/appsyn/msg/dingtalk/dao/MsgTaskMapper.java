package com.customization.oaones.appsyn.msg.dingtalk.dao;

import com.customization.oaones.appsyn.msg.dingtalk.entity.MsgTask;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 卡片任务 Mapper（task_id 定位历史推送卡片）。
 */
public interface MsgTaskMapper {

    MsgTask getByTaskId(@Param("table") String table, @Param("taskId") String taskId);

    MsgTask getByBizUser(@Param("table") String table,
                         @Param("bizType") String bizType,
                         @Param("bizId") String bizId,
                         @Param("wxUserid") String wxUserid);

    int insert(@Param("table") String table, @Param("e") MsgTask entity);

    int updateState(@Param("table") String table,
                    @Param("taskId") String taskId,
                    @Param("taskState") String taskState,
                    @Param("modifyDatetime") String modifyDatetime);

    /** 修正历史 task_id（老版本把邮箱等钉钉不允许的字符原样拼进 task_id，需要按新口径改名）。 */
    int updateTaskId(@Param("table") String table,
                     @Param("oldTaskId") String oldTaskId,
                     @Param("newTaskId") String newTaskId,
                     @Param("modifyDatetime") String modifyDatetime);

    int deleteBefore(@Param("table") String table,
                     @Param("beforeDate") String beforeDate,
                     @Param("beforeTime") String beforeTime);

    List<MsgTask> listByBiz(@Param("table") String table,
                            @Param("bizType") String bizType,
                            @Param("bizId") String bizId);
}
