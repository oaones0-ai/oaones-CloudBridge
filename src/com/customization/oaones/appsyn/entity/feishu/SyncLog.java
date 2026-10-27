package com.customization.oaones.appsyn.entity.feishu;

/**
 * 同步日志实体（对应业务表：表名取 oaones.appsyn.properties#oaones_sync_log_table，
 * 建模模块ID按表名反查，配置里不出现）。
 */
public class SyncLog {

    private Integer id;
    private Integer cfgid;
    private String corpname;
    /** 同步方向：0 OA->飞书 / 1 飞书->OA。 */
    private Integer direction;
    private Integer totalcount;
    private Integer successcount;
    private Integer failcount;
    private Integer skipcount;
    /** 本次同步的部门（含分部）条数。 */
    private Integer deptcount;
    /** 本次同步的人员条数。 */
    private Integer usercount;
    /** 其中“没有找到部门”（部门未绑定 / 目标侧不存在）的人员条数。 */
    private Integer nodeptcount;
    /** 上述人员本次挂靠的部门名称（口径为“不同步”时为其它部门的默认值/空）。 */
    private String nodeptdept;
    private String starttime;
    private String endtime;
    /** 执行结果：0 成功 / 1 部分成功 / 2 失败。 */
    private Integer runstatus;
    private String detail;
    private String modeuuid;
    private Integer formmodeid;
    private Integer modedatacreater;
    private String modedatacreatedate;
    private String modedatacreatetime;
    private Integer modedatamodifier;
    private String modedatamodifydatetime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getCfgid() { return cfgid; }
    public void setCfgid(Integer cfgid) { this.cfgid = cfgid; }

    public String getCorpname() { return corpname; }
    public void setCorpname(String corpname) { this.corpname = corpname; }

    public Integer getDirection() { return direction; }
    public void setDirection(Integer direction) { this.direction = direction; }

    public Integer getTotalcount() { return totalcount; }
    public void setTotalcount(Integer totalcount) { this.totalcount = totalcount; }

    public Integer getSuccesscount() { return successcount; }
    public void setSuccesscount(Integer successcount) { this.successcount = successcount; }

    public Integer getFailcount() { return failcount; }
    public void setFailcount(Integer failcount) { this.failcount = failcount; }

    public Integer getSkipcount() { return skipcount; }
    public void setSkipcount(Integer skipcount) { this.skipcount = skipcount; }

    public Integer getDeptcount() { return deptcount; }
    public void setDeptcount(Integer deptcount) { this.deptcount = deptcount; }

    public Integer getUsercount() { return usercount; }
    public void setUsercount(Integer usercount) { this.usercount = usercount; }

    public Integer getNodeptcount() { return nodeptcount; }
    public void setNodeptcount(Integer nodeptcount) { this.nodeptcount = nodeptcount; }

    public String getNodeptdept() { return nodeptdept; }
    public void setNodeptdept(String nodeptdept) { this.nodeptdept = nodeptdept; }

    public String getStarttime() { return starttime; }
    public void setStarttime(String starttime) { this.starttime = starttime; }

    public String getEndtime() { return endtime; }
    public void setEndtime(String endtime) { this.endtime = endtime; }

    public Integer getRunstatus() { return runstatus; }
    public void setRunstatus(Integer runstatus) { this.runstatus = runstatus; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public String getModeuuid() { return modeuuid; }
    public void setModeuuid(String modeuuid) { this.modeuuid = modeuuid; }

    public Integer getFormmodeid() { return formmodeid; }
    /** 建模模块ID按表名反查；反查不到（表不是建模表）时不写值（留 NULL，不写 0）。 */
    public void setFormmodeid(Integer formmodeid) {
        this.formmodeid = (formmodeid == null || formmodeid.intValue() <= 0) ? null : formmodeid;
    }

    public Integer getModedatacreater() { return modedatacreater; }
    public void setModedatacreater(Integer modedatacreater) { this.modedatacreater = modedatacreater; }

    public String getModedatacreatedate() { return modedatacreatedate; }
    public void setModedatacreatedate(String modedatacreatedate) { this.modedatacreatedate = modedatacreatedate; }

    public String getModedatacreatetime() { return modedatacreatetime; }
    public void setModedatacreatetime(String modedatacreatetime) { this.modedatacreatetime = modedatacreatetime; }

    public Integer getModedatamodifier() { return modedatamodifier; }
    public void setModedatamodifier(Integer modedatamodifier) { this.modedatamodifier = modedatamodifier; }

    public String getModedatamodifydatetime() { return modedatamodifydatetime; }
    public void setModedatamodifydatetime(String modedatamodifydatetime) { this.modedatamodifydatetime = modedatamodifydatetime; }
}
