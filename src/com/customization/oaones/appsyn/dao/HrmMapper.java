package com.customization.oaones.appsyn.dao;

import com.customization.oaones.appsyn.dto.HrmResourceBasic;
import com.customization.oaones.appsyn.dto.HrmResourceCreate;
import com.customization.oaones.appsyn.dto.HrmDepartmentCreate;
import com.customization.oaones.appsyn.dto.OaAccountRow;
import com.customization.oaones.appsyn.entity.HrmDepartment;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * E9 标准组织表 Mapper（hrmdepartment / hrmsubcompany / hrmresource）。
 *
 * <p>只读查询 OA 部门；人员侧仅按需回写基础信息、
 * 以及在“企微数据基准”页同步人员时新建账号，不修改 OA 标准表结构。</p>
 */
public interface HrmMapper {

    List<HrmDepartment> listDepartments();

    HrmDepartment getDepartment(@Param("deptId") int deptId);

    /** 动态更新非空字段，仅姓名/手机/邮箱/部门/分部。 */
    int updateResourceBasic(@Param("e") HrmResourceBasic entity);

    /** 按登录名查询 OA 人员ID（登录名唯一，不存在返回 null）。 */
    Integer findResourceIdByLoginId(@Param("loginid") String loginid);

    /**
     * 按手机号查询有效 OA 人员ID（统一登录设置的“手机号登录”用）。
     *
     * <p>只返回状态 0-3 且登录名非空的账号；同一手机号可能对应多人，返回集合由调用方判断唯一性。</p>
     */
    List<Integer> listResourceIdsByMobile(@Param("mobile") String mobile);

    /**
     * 按邮箱查询有效 OA 人员ID（统一登录设置的“邮箱验证登录”用）。
     *
     * <p>口径与 {@link #listResourceIdsByMobile} 完全一致（状态 0-3、登录名非空、
     * 返回集合由调用方判断唯一性），只是匹配列换成 {@code hrmresource.email}。</p>
     */
    List<Integer> listResourceIdsByEmail(@Param("email") String email);

    /**
     * 个人账号中心：按 OA 人员ID读取本人账号信息（含头像文件ID）。
     *
     * <p>不做「有效账号 / 排除 sysadmin」过滤——本人查看自己的账号中心时
     * 必须能看到自己，账号状态由页面单独展示。</p>
     */
    OaAccountRow getResourceAccount(@Param("id") int id);

    /**
     * 个人账号中心：读取“我的账户”（同一主账户下的主账户 + 全部子账户）。
     *
     * <p>E9 标准口径：{@code hrmresource.accounttype} 0 主账号 / 1 次账号，
     * 次账号通过 {@code belongto} 指向主账号的人员ID；传入主账号ID时用
     * {@code id = mainId or belongto = mainId} 一次取全，主账号排在前面。</p>
     */
    List<OaAccountRow> listAccountFamily(@Param("mainId") int mainId);

    /** 新建 OA 账号；id 由平台过程 HrmResourceMaxId_Get 生成后显式写入。 */
    int insertResource(@Param("e") HrmResourceCreate entity);

    /**
     * 新建 OA 部门（同步中心“企微 → OA”按范围同步用）。
     *
     * <p>hrmdepartment.id 是自增列，这里不写 id，插入后用
     * {@link #findLatestDepartmentId} 取回本次生成的部门ID。</p>
     */
    int insertDepartment(@Param("e") HrmDepartmentCreate entity);

    /** 取“同一上级、同一分部、同一名称”的最新部门ID（配合 {@link #insertDepartment} 取回自增ID）。 */
    Integer findLatestDepartmentId(@Param("name") String name,
                                   @Param("subCompanyId") Integer subCompanyId,
                                   @Param("supDepId") Integer supDepId);

    /** 更新 OA 部门名称（同步中心“企微 → OA”按范围同步用）。 */
    int updateDepartmentName(@Param("deptId") int deptId,
                             @Param("name") String name,
                             @Param("mark") String mark);

    /**
     * 调整已有部门的挂靠（同步中心“企微 → OA”按范围同步用）。
     *
     * <p>只改 {@code supdepid} / {@code subcompanyid1} / {@code allsupdepid}，
     * 部门层级由平台 {@code OrgOperationUtil#updateDepartmentLevel} 重算。</p>
     */
    int moveDepartment(@Param("deptId") int deptId,
                       @Param("supDepId") int supDepId,
                       @Param("subCompanyId") Integer subCompanyId,
                       @Param("allSupDepId") String allSupDepId);
}
