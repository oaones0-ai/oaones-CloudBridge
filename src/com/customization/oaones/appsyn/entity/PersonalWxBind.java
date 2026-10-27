package com.customization.oaones.appsyn.entity;

/**
 * 个人微信绑定实体（对应业务表：表名取
 * oaones.appsyn.properties#oaones_personal_wx_bind_table）。
 *
 * <p>一个 OA 人员最多一条绑定（`oa_user_id` 唯一）：本人扫码绑定或管理员代为绑定；
 * 重复扫码即换绑（同一行覆盖 openid / 昵称 / 头像）。个人微信没有租户概念，
 * 因此本表不带 `cfg_id`，也不与企微 / 钉钉 / 飞书的人员绑定表混用。</p>
 */
public class PersonalWxBind {

    private Integer id;
    /** OA 人员ID（hrmresource.id），一个人员最多一条绑定。 */
    private Integer oaUserId;
    /** 微信 openid（同一个人在同一应用下唯一，扫码授权换回的稳定标识）。 */
    private String openid;
    /** 微信 unionid（微信开放平台同一主体下各应用的统一标识，可能为空）。 */
    private String unionid;
    /** 微信昵称快照（扫码时取回，便于管理员在列表里核对）。 */
    private String nickname;
    /** 微信头像地址快照。 */
    private String headimgurl;
    /** 绑定方式：0 手动绑定（管理员代为绑定）/ 2 自行绑定（本人在个人账号中心扫码），见 OaonesConst。 */
    private Integer bindSource;
    /** 绑定/换绑时间（yyyy-MM-dd HH:mm:ss）。 */
    private String bindTime;
    private String remark;
    private Integer formmodeid;
    private Integer modedatacreater;
    private String modedatacreatedate;
    private String modedatacreatetime;
    private Integer modedatamodifier;
    private String modedatamodifydatetime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getOaUserId() { return oaUserId; }
    public void setOaUserId(Integer oaUserId) { this.oaUserId = oaUserId; }

    public String getOpenid() { return openid; }
    public void setOpenid(String openid) { this.openid = openid; }

    public String getUnionid() { return unionid; }
    public void setUnionid(String unionid) { this.unionid = unionid; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getHeadimgurl() { return headimgurl; }
    public void setHeadimgurl(String headimgurl) { this.headimgurl = headimgurl; }

    public Integer getBindSource() { return bindSource; }
    public void setBindSource(Integer bindSource) { this.bindSource = bindSource; }

    public String getBindTime() { return bindTime; }
    public void setBindTime(String bindTime) { this.bindTime = bindTime; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

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
    public void setModedatamodifydatetime(String modedatamodifydatetime) {
        this.modedatamodifydatetime = modedatamodifydatetime;
    }
}
