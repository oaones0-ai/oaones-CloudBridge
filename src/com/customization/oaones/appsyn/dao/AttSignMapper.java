package com.customization.oaones.appsyn.dao;

import com.customization.oaones.appsyn.dto.AttOaPunch;

import java.util.List;
import java.util.Map;

/**
 * E9 标准考勤打卡表（HrmScheduleSign）的读写 Mapper
 * （表名由 {@code oaones.properties#att.sign.table} 配置，经 {@code ${attSignTable}} 传入）。
 *
 * <p>口径与 E9 自己的“考勤数据导入”完全一致（见平台源码
 * {@code com.engine.kq.biz.KQScheduleSignImport#ExcelToDB} 与
 * {@code com.engine.kq.cmd.schedulesignimport.SynDingTalkDataCmd}）：</p>
 *
 * <ul>
 *   <li><b>先删后插</b>：先按“来源 + 人员 + 打卡日期”删掉上次导入的同一批，再插入本次数据，
 *       因此同一时间窗反复执行不会产生重复打卡（需求 3.6.6 / Q7）；</li>
 *   <li><b>导入标记</b>：isimport = 1（与标准导入同一口径）；</li>
 *   <li><b>来源标识</b>：signfrom 写平台对应值（企微 Wechat / 钉钉 DingTalk / 飞书 Feishu，可配），
 *       补签卡写 E9 标准补卡来源（以 {@code card:} 开头）；</li>
 *   <li>写入后由 Service 调 E9 标准格式化入口 {@code com.engine.kq.biz.KQFormatBiz#format}
 *       刷新 kq_format_detail / kq_format_total，不直接改报表表（需求 3.6.4）。</li>
 * </ul>
 *
 * <p>反向同步（OA → 第三方）的取数也在这里：只取 OA 侧真实产生的打卡
 * （按来源排除本功能导入回来的记录 + 映射表二次排除，需求 3.9.1 / Q9）。</p>
 */
public interface AttSignMapper {

    /**
     * 插入一条打卡记录（isimport = 1），插入后回填自增主键（{@code e.oaSignId}）。
     *
     * <p>字段口径：userid OA 人员ID、usertype = 1（人员）、signtype 1 签到 / 2 签退、
     * signdate {@code yyyy-MM-dd}、signtime {@code HH:mm:ss}、isincom = 1、signfrom 来源标识。</p>
     */
    int insertSign(Map<String, Object> params);

    /** 「先删后插」的那一步：按“来源 + 人员 + 打卡日期”删掉上次导入的同一批。 */
    int deleteSignByUserDate(Map<String, Object> params);

    /**
     * 「先删后插」的批量口径：按“来源集合 + 打卡日期区间 + 人员集合”删掉本平台历史导入记录，
     * 严格限定为本平台来源 + 本次时间窗（不会碰到其它来源的打卡）。
     */
    int deleteSignByWindow(Map<String, Object> params);

    /** 该人员该日期是否已有本来源的导入记录（幂等判断 / 跳过文案“已同步”用）。 */
    int countSignByKey(Map<String, Object> params);

    /** 按“人员 + 打卡时间 + 类型”找 OA 侧打卡记录ID（反向同步幂等用，未找到返回 null）。 */
    String findSignIdByKey(Map<String, Object> params);

    /** 反向同步取数：时间窗内、范围内人员的 OA 打卡记录（排除 excludeSources 里的来源）。 */
    List<AttOaPunch> listOaPunch(Map<String, Object> params);

    /** 反向同步取数（单条）：按 OA 打卡记录ID 取一条（「重推」用，不受时间窗限制）。 */
    AttOaPunch getOaPunch(Map<String, Object> params);
}
