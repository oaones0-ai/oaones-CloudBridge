package com.customization.oaones.appsyn.service.impl.wecom;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesAdmin;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesImageSize;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dao.wecom.LoginConfigMapper;
import com.customization.oaones.appsyn.entity.wecom.LoginConfig;
import com.customization.oaones.appsyn.service.wecom.LoginConfigService;
import weaver.general.GCONST;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

/**
 * 统一登录设置实现（单行配置，表名取 oaones.properties#oaones_login_config_table）。
 *
 * <p>读取：取本表 id 最小的一条；没有数据（表还没建/还没保存过）时按默认值返回——
 * 默认账号密码 + 企业微信扫码 开、验证登录（手机号 / 邮箱）关，二维码有效期取 `oaones.properties#login.qr.ttl`，
 * 这样“没配过设置”与“配过设置但没改”表现一致，登录页不会因为少一行数据而不可用。</p>
 *
 * <p>“验证登录”是**登录页的第二个页签**（页签内可切手机号 / 邮箱），
 * 两个开关 <b>mobile_enable</b> / <b>email_enable</b> 决定该页签里出现哪些验证方式：
 * 只开手机号就只出现手机号，只开邮箱就只出现邮箱，两个都开时页签内可以切换。</p>
 *
 * <p>保存：校验通过后更新那一行，没有行则插入；个人微信只存开关不做登录实现，
 * 因此不参与“至少要有一路可用登录方式”的校验。</p>
 *
 * <p><b>登录页外观</b>有三项（都为空时登录页用系统默认外观）：
 * ① {@code logo}——登录框内部上方的小图；② {@code bg_image}——**整页背景图**（前端按窗口自动缩放）；
 * ③ {@code brand_html}——登录页**左侧的富文本**，“设置什么显示什么”。
 * 两个图片项都走页面上传：落到 {@code oaones/img/}（与「应用中心」的图标同一个目录、不同文件名前缀），
 * 记录里只存站内地址；上传（{@link #uploadImage}）与保存（{@link #save}）分两步——上传只落盘，
 * 点了“保存设置”才写库，与“先上传图标、再保存应用”同一口径。
 * 弹框模式（{@code login.jsp?popup=1}，超时后的“重新登录”）里三项都不显示（页面本来就是极简形态）。</p>
 *
 * <p>另有“登录框位置与尺寸”与“文字说明位置与尺寸”（各自四列都非空才启用，按视口左上角像素绝对定位，
 * 文字说明即登录页左侧文字区）与
 * “登录框背景透明度”与“左侧文字背景透明度”（均为 0 = 不透明，100 = 完全透明，只改白色底板）与
 * “桌面启动台自动跳转秒数”（{@link #gopageSeconds()}：0 = 不跳，空 = 回退 properties）。</p>
 *
 * <p><b>这是“统一登录设置”的公共实现</b>：统一登录页（{@code /oaones/login/login.jsp}）、
 * 登录设置页（{@code /oaones/loginConfig/config.jsp}）与 {@code /oaones/WeCom/api.jsp} 走的都是本类
 * （钉钉/飞书连接器下的同名类是它们各自的只读副本）。因此本类必须覆盖**全部平台**的开关，
 * 包括 {@code feishu_qr_enable}——否则设置页上的飞书开关存不进去。</p>
 */
public class LoginConfigServiceImpl implements LoginConfigService {

    /** 二维码有效期允许配置的范围（秒）：太短来不及扫、太长等于不过期（最长 180 秒 = 3 分钟）。 */
    private static final int MIN_TTL_SECONDS = 30;
    private static final int MAX_TTL_SECONDS = 180;

    /** 图片地址（列宽 varchar(500)）的最大长度：超出直接拒绝，不静默截断。 */
    private static final int LOGIN_URL_MAX = 500;

    /** 登录框布局与启动台秒数范围统一取 OaonesConst（保存校验与登录页渲染共用一组数）。 */
    private static final int MAX_BOX_POS = OaonesConst.LOGIN_BOX_MAX_POS;
    private static final int MIN_BOX_WIDTH = OaonesConst.LOGIN_BOX_MIN_WIDTH;
    private static final int MAX_BOX_WIDTH = OaonesConst.LOGIN_BOX_MAX_WIDTH;
    private static final int MIN_BOX_HEIGHT = OaonesConst.LOGIN_BOX_MIN_HEIGHT;
    private static final int MAX_BOX_HEIGHT = OaonesConst.LOGIN_BOX_MAX_HEIGHT;
    /** 登录页左侧文字布局范围（与登录框同一套视口像素口径，宽 / 高下限按文字区给得更小）。 */
    private static final int MAX_BRAND_POS = OaonesConst.LOGIN_BRAND_MAX_POS;
    private static final int MIN_BRAND_WIDTH = OaonesConst.LOGIN_BRAND_MIN_WIDTH;
    private static final int MAX_BRAND_WIDTH = OaonesConst.LOGIN_BRAND_MAX_WIDTH;
    private static final int MIN_BRAND_HEIGHT = OaonesConst.LOGIN_BRAND_MIN_HEIGHT;
    private static final int MAX_BRAND_HEIGHT = OaonesConst.LOGIN_BRAND_MAX_HEIGHT;
    /** 登录框背景透明度范围（百分比）：0 = 完全不透明，100 = 完全透明。 */
    private static final int MIN_BOX_OPACITY = OaonesConst.LOGIN_BOX_OPACITY_MIN;
    private static final int MAX_BOX_OPACITY = OaonesConst.LOGIN_BOX_OPACITY_MAX;
    /** 登录页左侧文字背景透明度：0 = 不透明，100 = 透明；默认 100 = 历史无底板外观。 */
    private static final int MIN_BRAND_OPACITY = OaonesConst.LOGIN_BRAND_OPACITY_MIN;
    private static final int MAX_BRAND_OPACITY = OaonesConst.LOGIN_BRAND_OPACITY_MAX;
    /** 桌面启动台自动跳转秒数范围：0 = 不自动跳，最长 120 秒（2 分钟）。 */
    private static final int MIN_GOPAGE_SECONDS = 0;
    private static final int MAX_GOPAGE_SECONDS = OaonesConst.LOGIN_GOPAGE_MAX_SECONDS;

    /** 生成图片落盘文件名用的字符集（与「应用中心」的图标同一口径：小写十六进制）。 */
    private static final String LOGIN_NAME_HEX = "0123456789abcdef";

    private String table() {
        return OaonesProps.loginConfigTable();
    }

    @Override
    public JSONObject get() {
        LoginConfig row = readRow();
        return toJson(row);
    }

    @Override
    public JSONObject publicSettings() {
        JSONObject all = get();
        JSONObject data = new JSONObject();
        data.put("password_enable", all.getIntValue("password_enable"));
        data.put("mobile_enable", all.getIntValue("mobile_enable"));
        data.put("email_enable", all.getIntValue("email_enable"));
        data.put("wecom_qr_enable", all.getIntValue("wecom_qr_enable"));
        data.put("dingtalk_qr_enable", all.getIntValue("dingtalk_qr_enable"));
        data.put("feishu_qr_enable", all.getIntValue("feishu_qr_enable"));
        data.put("personal_wx_enable", all.getIntValue("personal_wx_enable"));
        data.put("qr_ttl_seconds", all.getIntValue("qr_ttl_seconds"));
        // 登录页右侧登录框上方的 logo（空 = 不显示；弹框模式里不显示，见 login.jsp）
        data.put("logo", OaonesUtil.s(all.getString("logo")));
        // 登录页的整页背景图与左侧富文本（都为空时登录页用系统默认外观）
        data.put("bg_image", OaonesUtil.s(all.getString("bg_image")));
        data.put("brand_html", OaonesUtil.s(all.getString("brand_html")));
        // 登录框自定义位置与尺寸（四列都有值才算启用；未启用时页面走默认居中布局）
        boolean boxCustom = all.getBooleanValue("login_box_custom");
        data.put("login_box_custom", boxCustom);
        data.put("login_box_left", boxCustom ? all.getIntValue("login_box_left") : 0);
        data.put("login_box_top", boxCustom ? all.getIntValue("login_box_top") : 0);
        data.put("login_box_width", boxCustom ? all.getIntValue("login_box_width") : 0);
        data.put("login_box_height", boxCustom ? all.getIntValue("login_box_height") : 0);
        // 左侧文字自定义位置与尺寸（四列都有值才算启用；未启用时跟随系统默认版式）
        boolean brandCustom = all.getBooleanValue("brand_custom");
        data.put("brand_custom", brandCustom);
        data.put("brand_left", brandCustom ? all.getIntValue("brand_left") : 0);
        data.put("brand_top", brandCustom ? all.getIntValue("brand_top") : 0);
        data.put("brand_width", brandCustom ? all.getIntValue("brand_width") : 0);
        data.put("brand_height", brandCustom ? all.getIntValue("brand_height") : 0);
        // 登录框背景透明度（0 = 不透明）：只改白色底色，文字 / 输入框保持清晰
        data.put("login_box_opacity", all.getIntValue("login_box_opacity"));
        // 左侧文字背景透明度（100 = 透明）：只改文字区白色底板，文字保持不透明
        data.put("brand_opacity", all.getIntValue("brand_opacity"));
        // 桌面启动台实际生效的跳转秒数（0 = 不自动跳转）
        data.put("gopage_seconds", all.getIntValue("gopage_effective"));
        // 实际生效的二维码有效期（秒）：登录页倒计时的起始值按它显示（留空回退 properties，最长 180 秒）
        data.put("qr_ttl_effective", all.getIntValue("qr_ttl_effective"));
        // 扫码登录是同一张二维码：任何一个平台开着，登录页就有扫码登录可用
        boolean qr = all.getIntValue("wecom_qr_enable") == 1
                || all.getIntValue("dingtalk_qr_enable") == 1
                || all.getIntValue("feishu_qr_enable") == 1;
        data.put("qr_enable", qr);
        // 登录页只用“已实现的登录方式”算可用组合，个人微信单开不算；
        // “验证登录”页签在手机号 / 邮箱任一开启时出现
        boolean password = all.getIntValue("password_enable") == 1;
        boolean mobile = all.getIntValue("mobile_enable") == 1;
        boolean email = all.getIntValue("email_enable") == 1;
        data.put("verify_enable", mobile || email);
        data.put("available", password || mobile || email || qr);
        return data;
    }

    @Override
    public JSONObject save(JSONObject params, String operatorId) {
        if (!canMaintain(operatorId)) {
            throw new RuntimeException("你不是统一登录设置的维护人，无权修改（仅系统管理员与 login.admin.ids 中的人员可维护）");
        }
        int password = flag(params, "password_enable");
        int mobile = flag(params, "mobile_enable");
        int email = flag(params, "email_enable");
        int wecom = flag(params, "wecom_qr_enable");
        int dingtalk = flag(params, "dingtalk_qr_enable");
        int feishu = flag(params, "feishu_qr_enable");
        int personalWx = flag(params, "personal_wx_enable");
        // 登录设置是**一条独立一级菜单里的共享配置**（三个平台的扫码共用同一行、同一张二维码）：
        // 可用性按“账号密码 / 验证登录（手机号或邮箱）/ 扫码登录（企业微信 / 钉钉 / 飞书任一平台）”一起判断。
        if (password + mobile + email + wecom + dingtalk + feishu == 0) {
            throw new RuntimeException("账号密码登录、验证登录（手机号 / 邮箱）、扫码登录至少要开启一种"
                    + "（扫码登录要至少允许一个平台；个人微信尚未接入登录，单独开启会导致没人能登录）");
        }
        int ttl = OaonesUtil.i(OaonesUtil.nullToEmpty(params.getString("qr_ttl_seconds")), 0);
        if (ttl <= 0) {
            ttl = 0; // 留空 = 用 properties 默认值
        } else if (ttl < MIN_TTL_SECONDS || ttl > MAX_TTL_SECONDS) {
            throw new RuntimeException("扫码二维码有效期必须在 " + MIN_TTL_SECONDS + " ~ "
                    + MAX_TTL_SECONDS + " 秒之间（留空表示使用系统默认值）");
        }
        // 登录框位置与尺寸是一个整体：不带这四个参数 = 保持原值（别的入口只改开关时不会丢布局），
        // 四个都带空串 = 恢复默认居中布局，四个都有值 = 自定义绝对定位；
        // 只填一部分（例如只填 left / top 没填 width / height）直接拒绝，避免存出半套配置。
        boolean boxProvided = params != null && (params.containsKey("login_box_left")
                || params.containsKey("login_box_top") || params.containsKey("login_box_width")
                || params.containsKey("login_box_height"));
        Integer boxLeft = null;
        Integer boxTop = null;
        Integer boxWidth = null;
        Integer boxHeight = null;
        if (boxProvided) {
            boxLeft = optionalInt(params, "login_box_left", "登录框左侧位置", MAX_BOX_POS);
            boxTop = optionalInt(params, "login_box_top", "登录框顶部位置", MAX_BOX_POS);
            boxWidth = optionalInt(params, "login_box_width", "登录框宽度", MAX_BOX_WIDTH);
            boxHeight = optionalInt(params, "login_box_height", "登录框高度", MAX_BOX_HEIGHT);
            int provided = (boxLeft == null ? 0 : 1) + (boxTop == null ? 0 : 1)
                    + (boxWidth == null ? 0 : 1) + (boxHeight == null ? 0 : 1);
            if (provided != 0 && provided != 4) {
                throw new RuntimeException("登录框位置与尺寸要一起设置：左侧、顶部、宽度、高度四项都填，"
                        + "或四项都留空（留空 = 恢复系统默认居中布局）");
            }
            if (provided == 4) {
                if (boxWidth < MIN_BOX_WIDTH || boxWidth > MAX_BOX_WIDTH) {
                    throw new RuntimeException("登录框宽度必须在 " + MIN_BOX_WIDTH + " ~ "
                            + MAX_BOX_WIDTH + " 像素之间");
                }
                if (boxHeight < MIN_BOX_HEIGHT || boxHeight > MAX_BOX_HEIGHT) {
                    throw new RuntimeException("登录框高度必须在 " + MIN_BOX_HEIGHT + " ~ "
                            + MAX_BOX_HEIGHT + " 像素之间");
                }
                if ((long) boxLeft + boxWidth > MAX_BOX_POS) {
                    throw new RuntimeException("登录框右侧位置超出允许范围（左侧位置 + 宽度需小于 "
                            + MAX_BOX_POS + " 像素）");
                }
                if ((long) boxTop + boxHeight > MAX_BOX_POS) {
                    throw new RuntimeException("登录框底部位置超出允许范围（顶部位置 + 高度需小于 "
                            + MAX_BOX_POS + " 像素）");
                }
            }
        }
        // 登录页左侧文字的位置与尺寸也是一个整体：不带参数 = 保持原值，四个都带空串 = 恢复默认版式，
        // 四个都有值 = 按视口左上角像素绝对定位 + 固定宽高；只填一部分直接拒绝。
        boolean brandLayoutProvided = params != null && (params.containsKey("brand_left")
                || params.containsKey("brand_top") || params.containsKey("brand_width")
                || params.containsKey("brand_height"));
        Integer brandLeft = null;
        Integer brandTop = null;
        Integer brandWidth = null;
        Integer brandHeight = null;
        if (brandLayoutProvided) {
            brandLeft = optionalInt(params, "brand_left", "左侧文字左侧位置", MAX_BRAND_POS);
            brandTop = optionalInt(params, "brand_top", "左侧文字顶部位置", MAX_BRAND_POS);
            brandWidth = optionalInt(params, "brand_width", "左侧文字宽度", MAX_BRAND_WIDTH);
            brandHeight = optionalInt(params, "brand_height", "左侧文字高度", MAX_BRAND_HEIGHT);
            int provided = (brandLeft == null ? 0 : 1) + (brandTop == null ? 0 : 1)
                    + (brandWidth == null ? 0 : 1) + (brandHeight == null ? 0 : 1);
            if (provided != 0 && provided != 4) {
                throw new RuntimeException("文字说明位置与尺寸要一起设置：左侧、顶部、宽度、高度四项都填，"
                        + "或四项都留空（留空 = 恢复系统默认版式）");
            }
            if (provided == 4) {
                if (brandWidth < MIN_BRAND_WIDTH || brandWidth > MAX_BRAND_WIDTH) {
                    throw new RuntimeException("文字说明宽度必须在 " + MIN_BRAND_WIDTH + " ~ "
                            + MAX_BRAND_WIDTH + " 像素之间");
                }
                if (brandHeight < MIN_BRAND_HEIGHT || brandHeight > MAX_BRAND_HEIGHT) {
                    throw new RuntimeException("文字说明高度必须在 " + MIN_BRAND_HEIGHT + " ~ "
                            + MAX_BRAND_HEIGHT + " 像素之间");
                }
                if ((long) brandLeft + brandWidth > MAX_BRAND_POS) {
                    throw new RuntimeException("文字说明右侧位置超出允许范围（左侧位置 + 宽度需小于 "
                            + MAX_BRAND_POS + " 像素）");
                }
                if ((long) brandTop + brandHeight > MAX_BRAND_POS) {
                    throw new RuntimeException("文字说明底部位置超出允许范围（顶部位置 + 高度需小于 "
                            + MAX_BRAND_POS + " 像素）");
                }
            }
        }
        // 登录框背景透明度：不带参数 = 保持原值；空串 = 回到默认 0（不透明）；
        // 只允许 0~100 的整数百分比，超出直接拒绝，避免存出登录框“消失”或负透明度。
        boolean opacityProvided = params != null && params.containsKey("login_box_opacity");
        Integer boxOpacity = null;
        if (opacityProvided) {
            boxOpacity = optionalInt(params, "login_box_opacity", "登录框透明度", MAX_BOX_OPACITY);
            if (boxOpacity == null) {
                boxOpacity = Integer.valueOf(OaonesConst.LOGIN_BOX_OPACITY_DEFAULT);
            } else if (boxOpacity < MIN_BOX_OPACITY || boxOpacity > MAX_BOX_OPACITY) {
                throw new RuntimeException("登录框透明度必须在 " + MIN_BOX_OPACITY + " ~ "
                        + MAX_BOX_OPACITY + " 之间（0 = 不透明，100 = 完全透明）");
            }
        }
        // 左侧文字背景透明度：不带参数 = 保持原值；空串 = 回到默认 100（历史无底板外观）；
        // 只允许 0~100 的整数百分比。
        boolean brandOpacityProvided = params != null && params.containsKey("brand_opacity");
        Integer brandOpacity = null;
        if (brandOpacityProvided) {
            brandOpacity = optionalInt(params, "brand_opacity", "左侧文字背景透明度",
                    MAX_BRAND_OPACITY);
            if (brandOpacity == null) {
                brandOpacity = Integer.valueOf(OaonesConst.LOGIN_BRAND_OPACITY_DEFAULT);
            } else if (brandOpacity < MIN_BRAND_OPACITY || brandOpacity > MAX_BRAND_OPACITY) {
                throw new RuntimeException("左侧文字背景透明度必须在 " + MIN_BRAND_OPACITY + " ~ "
                        + MAX_BRAND_OPACITY + " 之间（0 = 不透明，100 = 完全透明）");
            }
        }
        // 桌面启动台自动跳转秒数：不带参数 = 保持原值；空串 = 用 properties 默认值；0 = 不自动跳
        boolean gopageProvided = params != null && params.containsKey("gopage_seconds");
        Integer gopageSeconds = null;
        if (gopageProvided) {
            gopageSeconds = optionalInt(params, "gopage_seconds", "启动台自动跳转秒数",
                    MAX_GOPAGE_SECONDS);
            if (gopageSeconds != null && (gopageSeconds < MIN_GOPAGE_SECONDS
                    || gopageSeconds > MAX_GOPAGE_SECONDS)) {
                throw new RuntimeException("启动台自动跳转秒数必须在 " + MIN_GOPAGE_SECONDS + " ~ "
                        + MAX_GOPAGE_SECONDS + " 秒之间（0 = 不自动跳转；留空 = 用系统默认值）");
            }
        }
        LoginConfig row = readRow();
        LoginConfig entity = row == null ? new LoginConfig() : row;
        // 登录页外观（logo / 整页背景图 / 左侧富文本）：**只有请求里带了对应参数才改动**——
        // 别的入口（如只改开关的调用）不带它们时保持原值，不会把已上传的图或已写好的文字抹掉；
        // 带空串 = 清除该项（回到系统默认：不显示 logo / 用默认底色 / 用默认左侧说明）。
        String oldLogo = OaonesUtil.s(row == null ? "" : row.getLogo());
        boolean logoProvided = params != null && params.containsKey("logo");
        String newLogo = logoProvided
                ? normalizeImageUrl(LOGIN_LOGO, OaonesUtil.s(params.getString("logo"))) : oldLogo;
        String oldBg = OaonesUtil.s(row == null ? "" : row.getBgImage());
        boolean bgProvided = params != null && params.containsKey("bg_image");
        String newBg = bgProvided
                ? normalizeImageUrl(LOGIN_BG, OaonesUtil.s(params.getString("bg_image"))) : oldBg;
        String oldBrand = OaonesUtil.s(row == null ? "" : row.getBrandHtml());
        boolean brandProvided = params != null && params.containsKey("brand_html");
        String newBrand = brandProvided ? normalizeBrandHtml(params.getString("brand_html")) : oldBrand;
        entity.setPasswordEnable(password);
        entity.setMobileEnable(mobile);
        entity.setEmailEnable(email);
        entity.setWecomQrEnable(wecom);
        entity.setDingtalkQrEnable(dingtalk);
        entity.setFeishuQrEnable(feishu);
        entity.setPersonalWxEnable(personalWx);
        entity.setQrTtlSeconds(ttl);
        entity.setLogo(newLogo);
        entity.setBgImage(newBg);
        entity.setBrandHtml(newBrand);
        if (boxProvided) {
            entity.setLoginBoxLeft(boxLeft);
            entity.setLoginBoxTop(boxTop);
            entity.setLoginBoxWidth(boxWidth);
            entity.setLoginBoxHeight(boxHeight);
        }
        if (brandLayoutProvided) {
            entity.setBrandLeft(brandLeft);
            entity.setBrandTop(brandTop);
            entity.setBrandWidth(brandWidth);
            entity.setBrandHeight(brandHeight);
        }
        if (opacityProvided) {
            entity.setLoginBoxOpacity(boxOpacity);
        }
        if (brandOpacityProvided) {
            entity.setBrandOpacity(brandOpacity);
        }
        if (gopageProvided) {
            entity.setGopageSeconds(gopageSeconds);
        }
        entity.setRemark(OaonesUtil.s(params.getString("remark")));
        entity.setModedatamodifier(OaonesUtil.i(operatorId, 0));
        entity.setModedatamodifydatetime(OaonesUtil.now());
        if (row == null) {
            entity.setFormmodeid(OaonesProps.loginConfigModeId());
            entity.setModedatacreater(OaonesUtil.i(operatorId, 0));
            entity.setModedatacreatedate(OaonesUtil.nowDate());
            entity.setModedatacreatetime(OaonesUtil.nowTime());
            try {
                OaonesMyBatis.run(LoginConfigMapper.class, mapper -> mapper.insert(table(), entity));
            } catch (Throwable t) {
                throw new RuntimeException(writeError(t));
            }
        } else {
            try {
                OaonesMyBatis.run(LoginConfigMapper.class, mapper -> mapper.update(table(), entity));
            } catch (Throwable t) {
                throw new RuntimeException(writeError(t));
            }
        }
        // 保存成功后清理被换掉 / 清掉的旧图片：本表只有一行共享配置，旧地址不再被任何地方引用
        // （只删本模块自己生成的 loginlogo_ / loginbg_ 前缀文件，见 deleteImageFile）
        if (logoProvided && !newLogo.equals(oldLogo)) {
            deleteImageFile(LOGIN_LOGO, oldLogo);
        }
        if (bgProvided && !newBg.equals(oldBg)) {
            deleteImageFile(LOGIN_BG, oldBg);
        }
        OaonesLog.step("保存统一登录设置 operatorId=" + operatorId
                + " password=" + password + " mobile=" + mobile + " email=" + email + " wecomQr=" + wecom
                + " dingtalkQr=" + dingtalk + " feishuQr=" + feishu
                + " personalWx=" + personalWx + " qrTtl=" + ttl
                + " logo=" + (logoProvided ? (newLogo.length() == 0 ? "(已清除)" : newLogo) : "(未改动)")
                + " bg=" + (bgProvided ? (newBg.length() == 0 ? "(已清除)" : newBg) : "(未改动)")
                + " brandHtml=" + (brandProvided ? (newBrand.length() == 0 ? "(已清除)" : newBrand.length() + "字符")
                    : "(未改动)")
                + " loginBox=" + (boxProvided
                    ? (boxLeft == null ? "(已恢复默认布局)" : boxLeft + "," + boxTop + " "
                        + boxWidth + "×" + boxHeight)
                    : "(未改动)")
                + " brandLayout=" + (brandLayoutProvided
                    ? (brandLeft == null ? "(已恢复默认版式)" : brandLeft + "," + brandTop + " "
                        + brandWidth + "×" + brandHeight)
                    : "(未改动)")
                + " loginBoxOpacity=" + (opacityProvided
                    ? String.valueOf(boxOpacity) + "%" : "(未改动)")
                + " brandOpacity=" + (brandOpacityProvided
                    ? String.valueOf(brandOpacity) + "%" : "(未改动)")
                + " gopage=" + (gopageProvided
                    ? (gopageSeconds == null ? "(用默认值)" : String.valueOf(gopageSeconds))
                    : "(未改动)"));
        return toJson(readRow());
    }

    @Override
    public JSONObject uploadImage(String kind, String originalName, byte[] content, String operatorId) {
        if (!canMaintain(operatorId)) {
            throw new RuntimeException("你不是统一登录设置的维护人，无权上传登录页图片"
                    + "（仅系统管理员与 login.admin.ids 中的人员可维护）");
        }
        LoginImage spec = loginImageSpec(kind);
        if (content == null || content.length == 0) {
            throw new RuntimeException("没有收到图片文件，请重新选择图片上传");
        }
        if (content.length > spec.maxBytes) {
            throw new RuntimeException(spec.label + "不能超过 " + (spec.maxBytes / 1024)
                    + "KB，请压缩后再上传");
        }
        // 类型按文件头判定：改了后缀名的其它文件（含 SVG，见 OaonesImageSize 类注释）在这里被拒
        OaonesImageSize.Info info = OaonesImageSize.probe(content);
        if (info == null) {
            throw new RuntimeException(spec.label + "只支持 " + spec.extensions
                    + " 格式的图片（按文件内容判定，改后缀名不算）");
        }
        if (info.getWidth() > spec.maxSize || info.getHeight() > spec.maxSize) {
            throw new RuntimeException(spec.label + "长宽不能超过 " + spec.maxSize
                    + " 像素（当前 " + describeSize(info) + "）");
        }
        File dir = loginImageDir();
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new RuntimeException(spec.label + "目录不可用：" + dir.getPath()
                    + "（请检查 E9 部署目录的写权限）");
        }
        File target = newImageFile(dir, spec.filePrefix, info.getExtension());
        OutputStream out = null;
        try {
            out = new FileOutputStream(target);
            out.write(content);
            out.flush();
        } catch (IOException e) {
            OaonesLog.error("登录页" + spec.label + "写入失败 " + target.getPath(), e);
            throw new RuntimeException(spec.label + "保存失败：" + (e.getMessage() == null
                    ? e.getClass().getSimpleName() : e.getMessage()));
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignore) {
                    // 关闭失败不影响已写入的内容
                }
            }
        }
        String url = OaonesConst.LOGIN_IMAGE_URL_PREFIX + target.getName();
        OaonesLog.step("上传登录页" + spec.label + " kind=" + spec.kind
                + " originalName=" + OaonesUtil.s(originalName)
                + " saved=" + url + " size=" + content.length + "px=" + describeSize(info));
        JSONObject data = new JSONObject();
        // 回的是“待保存”的地址：页面把它放进表单，点“保存设置”后才写进配置行
        data.put("url", url);
        data.put("kind", spec.kind);
        data.put("file_name", target.getName());
        data.put("original_name", OaonesUtil.s(originalName));
        data.put("content_type", info.getContentType());
        data.put("width", Integer.valueOf(info.getWidth()));
        data.put("height", Integer.valueOf(info.getHeight()));
        data.put("size", Integer.valueOf(content.length));
        data.put("max_size", Long.valueOf(spec.maxBytes));
        data.put("max_side", Integer.valueOf(spec.maxSize));
        return data;
    }

    /**
     * 登录页外观图片的用途口径（kind）：文件名前缀 / 体积与长宽上限 / 提示文案。
     * 两类图片同目录（{@code oaones/img/}）、同一套校验与落盘口径，只是前缀与上限不同。
     */
    private static final class LoginImage {

        private final String kind;
        private final String label;
        private final String filePrefix;
        private final long maxBytes;
        private final int maxSize;
        private final String extensions;

        LoginImage(String kind, String label, String filePrefix, long maxBytes, int maxSize, String extensions) {
            this.kind = kind;
            this.label = label;
            this.filePrefix = filePrefix;
            this.maxBytes = maxBytes;
            this.maxSize = maxSize;
            this.extensions = extensions;
        }
    }

    /** 登录框内部上方的 logo（1MB / 单边 2048 像素）。 */
    private static final LoginImage LOGIN_LOGO = new LoginImage("logo", "logo",
            OaonesConst.LOGIN_LOGO_FILE_PREFIX, OaonesConst.LOGIN_LOGO_MAX_BYTES,
            OaonesConst.LOGIN_LOGO_MAX_SIZE, OaonesConst.LOGIN_LOGO_EXTENSIONS);

    /** 整页背景图（2MB / 单边 8192 像素：背景图要铺满整屏，天然比 logo 大）。 */
    private static final LoginImage LOGIN_BG = new LoginImage("bg", "背景图",
            OaonesConst.LOGIN_BG_FILE_PREFIX, OaonesConst.LOGIN_BG_MAX_BYTES,
            OaonesConst.LOGIN_BG_MAX_SIZE, OaonesConst.LOGIN_BG_EXTENSIONS);

    /** 按 kind 取用途口径：不认识的 kind 直接拒绝（页面只会传 logo / bg）。 */
    private LoginImage loginImageSpec(String kind) {
        String value = OaonesUtil.s(kind).trim();
        if (LOGIN_LOGO.kind.equals(value)) {
            return LOGIN_LOGO;
        }
        if (LOGIN_BG.kind.equals(value)) {
            return LOGIN_BG;
        }
        throw new RuntimeException("未知的登录页图片用途：" + (value.length() == 0 ? "(空)" : value)
                + "（只支持 logo / bg）");
    }

    /**
     * 页面提交的图片地址：只接受**空值**或本页上传后产生的站内地址
     * （{@code /oaones/img/loginlogo_xxx.png} / {@code /oaones/img/loginbg_xxx.png}）。
     *
     * <p>与「应用中心」的图标同一口径：不接受外部图片地址、也不接受带子目录 / 回退段的地址，
     * 既避免把图指到别的路径，也保证清理旧文件时删的一定是本模块上传的图片。</p>
     *
     * @param spec  用途口径（logo / 背景图）
     * @param value 页面提交的地址（空串 = 清除该项）
     * @return 可落库的地址（空串表示清除）
     */
    private String normalizeImageUrl(LoginImage spec, String value) {
        String url = OaonesUtil.s(value).trim();
        if (url.length() == 0) {
            return "";
        }
        if (url.length() > LOGIN_URL_MAX) {
            throw new RuntimeException(spec.label + "地址不能超过 " + LOGIN_URL_MAX + " 个字符");
        }
        String illegal = null;
        if (!url.startsWith(OaonesConst.LOGIN_IMAGE_URL_PREFIX)) {
            illegal = "只能填本站上传后产生的地址";
        } else {
            String name = url.substring(OaonesConst.LOGIN_IMAGE_URL_PREFIX.length());
            if (!name.startsWith(spec.filePrefix)) {
                illegal = "只能填本站上传后产生的地址";
            } else if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf("..") >= 0
                    || name.indexOf('?') >= 0 || name.indexOf('#') >= 0
                    || name.indexOf(' ') >= 0 || name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0) {
                illegal = "地址里不能带子目录或其它字符";
            }
        }
        if (illegal != null) {
            throw new RuntimeException("登录页" + spec.label + illegal
                    + "（请用页面上的「上传」按钮上传，上传后保存在 " + OaonesConst.LOGIN_IMAGE_DIR
                    + "/ 下，地址形如 " + OaonesConst.LOGIN_IMAGE_URL_PREFIX + spec.filePrefix
                    + "xxx.png；不填则" + (spec == LOGIN_BG ? "用系统默认底色" : "登录页不显示 logo") + "）");
        }
        return url;
    }

    /**
     * 登录页左侧富文本（列 {@code brand_html}）：**设置什么存什么**——“设置什么显示什么”是这条需求的核心，
     * 所以这里**不做 HTML 转义、也不过滤标签**（登录页原样输出），只有两条边界：
     * ① 长度上限 {@link OaonesConst#LOGIN_BRAND_HTML_MAX}，超了直接拒绝而**不截断**（截断会把 HTML 截成坏标记）；
     * ② 只有空白 / 空标记（{@code <p><br></p>}、{@code &nbsp;} 之类）的内容按“没设置”处理，
     * 登录页回到系统默认的左侧说明，避免左侧留一块空白。
     *
     * @param value 编辑器提交的 HTML（空串 = 恢复系统默认）
     */
    private String normalizeBrandHtml(String value) {
        String html = OaonesUtil.s(value);
        if (html.length() == 0) {
            return "";
        }
        if (html.length() > OaonesConst.LOGIN_BRAND_HTML_MAX) {
            throw new RuntimeException("登录页左侧文字不能超过 " + OaonesConst.LOGIN_BRAND_HTML_MAX
                    + " 个字符（当前 " + html.length() + "），请精简后再保存");
        }
        String text = html.replace("&nbsp;", "").replace("\u00a0", "")
                .replace("&#160;", "").replace("&#xa0;", "").replace("&#XA0;", "")
                .replaceAll("<[^>]*>", "").trim();
        return text.length() == 0 ? "" : html;
    }

    /** 登录页外观图片的上传目录（与「应用中心」的图标同目录：E9 站点根下的 {@code oaones/img}）。 */
    private File loginImageDir() {
        return new File(OaonesUtil.s(GCONST.getRootPath()), OaonesConst.LOGIN_IMAGE_DIR);
    }

    /**
     * 生成本次上传的落盘文件：文件名由服务端拼（时间戳 + 随机串），
     * 不使用上传上来的文件名——既避免路径穿越，也避免中文名在各系统的乱码问题。
     */
    private File newImageFile(File dir, String filePrefix, String extension) {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        Random random = new Random();
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append(LOGIN_NAME_HEX.charAt(random.nextInt(LOGIN_NAME_HEX.length())));
            }
            File file = new File(dir, filePrefix + stamp + "_" + hex + "." + extension);
            if (!file.exists()) {
                return file;
            }
        }
        // 极端情况下（同一秒内同名）直接用毫秒兜底，保证一定拿到一个新名字
        return new File(dir, filePrefix + System.currentTimeMillis() + "." + extension);
    }

    /**
     * 删除不再被引用的旧图片（换图 / 清除后调用）。
     *
     * <p>只处理 {@code oaones/img/} 下、且文件名带**本用途前缀**（logo {@code loginlogo_} /
     * 背景图 {@code loginbg_}）的文件——「应用中心」的图标与手工放进该目录的图片都不动；
     * 删不掉只记日志，不让保存失败（脏文件可手工清理）。</p>
     */
    private void deleteImageFile(LoginImage spec, String url) {
        String path = OaonesUtil.s(url).trim();
        if (!path.startsWith(OaonesConst.LOGIN_IMAGE_URL_PREFIX)) {
            return;
        }
        String name = path.substring(OaonesConst.LOGIN_IMAGE_URL_PREFIX.length());
        if (!name.startsWith(spec.filePrefix)
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf("..") >= 0) {
            return;
        }
        try {
            File file = new File(loginImageDir(), name);
            if (file.isFile() && !file.delete()) {
                OaonesLog.step("旧登录页" + spec.label + "文件删除失败（可手工清理）：" + file.getPath());
            }
        } catch (Throwable t) {
            OaonesLog.error("清理旧登录页" + spec.label + "文件失败：" + path, t);
        }
    }

    /** 图片尺寸文案（读不出长宽时给“尺寸未知”，页面/日志不出现 0×0）。 */
    private String describeSize(OaonesImageSize.Info info) {
        if (info.getWidth() <= 0 || info.getHeight() <= 0) {
            return "尺寸未知";
        }
        return info.getWidth() + "×" + info.getHeight();
    }

    @Override
    public boolean canMaintain(String operatorId) {
        String uid = OaonesUtil.s(operatorId).trim();
        if (uid.length() == 0) {
            return false;
        }
        if (OaonesAdmin.isTotalAdmin(uid)) {
            return true;
        }
        String admins = OaonesUtil.s(OaonesProps.loginAdminIds());
        String[] parts = admins.split(",");
        for (int i = 0; i < parts.length; i++) {
            if (uid.equals(parts[i].trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 实际生效的二维码有效期（秒）：设置页配了就用它、留空回退 properties 的默认值，
     * 两种情况都按 {@code 30~180} 秒截断（历史数据可能存过旧上限 3600，这里一律按 180 走）。
     */
    private int effectiveTtl(int configured) {
        int seconds = configured > 0 ? configured : Math.max(1, OaonesProps.loginQrTtl() / 1000);
        return Math.min(MAX_TTL_SECONDS, Math.max(MIN_TTL_SECONDS, seconds));
    }

    /**
     * 实际生效的桌面启动台自动跳转秒数：设置页留空（null）回退 properties 的默认值，
     * 两种来源都按 0~120 秒截断（0 = 不自动跳转）。
     */
    private int effectiveGopageSeconds(Integer configured) {
        int seconds = configured == null
                ? OaonesProps.loginLandingGopageSeconds() : configured.intValue();
        return Math.min(MAX_GOPAGE_SECONDS, Math.max(MIN_GOPAGE_SECONDS, seconds));
    }

    @Override
    public int qrTtlSeconds() {
        LoginConfig row = readRow();
        int configured = row == null || row.getQrTtlSeconds() == null ? 0 : row.getQrTtlSeconds();
        // 设置页配了就用它（按 30~180 秒截断），留空回退 properties 的默认值（默认 120 秒）
        return effectiveTtl(configured);
    }

    @Override
    public int gopageSeconds() {
        LoginConfig row = readRow();
        return effectiveGopageSeconds(row == null ? null : row.getGopageSeconds());
    }

    /**
     * 「应用中心 → 应用列表」维护启动台自动跳转秒数的人：系统管理员 + appc.admin.ids。
     *
     * <p>不把“名下有某个应用”的普通维护人也放进来：这是对全局登录落点生效的一项设置，
     * 与维护单个应用不是同一件事。</p>
     */
    public boolean canMaintainGopage(String operatorId) {
        String uid = OaonesUtil.s(operatorId).trim();
        if (uid.length() == 0) {
            return false;
        }
        if (OaonesAdmin.isTotalAdmin(uid)) {
            return true;
        }
        String[] parts = OaonesUtil.s(OaonesProps.appcAdminIds()).split(",");
        for (int i = 0; i < parts.length; i++) {
            if (uid.equals(parts[i].trim())) {
                return true;
            }
        }
        return false;
    }

    /** 应用列表页回显启动台跳转设置（只含这一项及其范围）。 */
    @Override
    public JSONObject gopageSettings() {
        JSONObject all = toJson(readRow());
        JSONObject data = new JSONObject();
        data.put("gopage_seconds", all.get("gopage_seconds"));
        data.put("gopage_effective", all.get("gopage_effective"));
        data.put("gopage_default", all.get("gopage_default"));
        data.put("gopage_min", MIN_GOPAGE_SECONDS);
        data.put("gopage_max", MAX_GOPAGE_SECONDS);
        return data;
    }

    /**
     * 只写 {@code gopage_seconds} 一列：读取整行 → 改这一列 → 原样写回，
     * 因此不会碰到登录方式、二维码有效期、Logo / 背景图 / 左侧文字、登录框布局等登录页设置。
     */
    @Override
    public JSONObject saveGopageSeconds(String seconds, String operatorId) {
        if (!canMaintainGopage(operatorId)) {
            throw new RuntimeException("你不是「应用中心」的管理员，无权修改启动台自动跳转设置"
                    + "（仅系统管理员与 appc.admin.ids 中的人员可维护）");
        }
        String raw = OaonesUtil.s(seconds).trim();
        Integer value = null;
        if (raw.length() > 0) {
            if (!raw.matches("\\d+")) {
                throw new RuntimeException("启动台自动跳转秒数请填整数（0 = 不自动跳转；留空 = 用系统默认值）");
            }
            long parsed;
            try {
                parsed = Long.parseLong(raw);
            } catch (NumberFormatException e) {
                throw new RuntimeException("启动台自动跳转秒数数值过大（当前：" + raw + "）");
            }
            if (parsed < MIN_GOPAGE_SECONDS || parsed > MAX_GOPAGE_SECONDS) {
                throw new RuntimeException("启动台自动跳转秒数必须在 " + MIN_GOPAGE_SECONDS + " ~ "
                        + MAX_GOPAGE_SECONDS + " 秒之间（0 = 不自动跳转；留空 = 用系统默认值）");
            }
            value = Integer.valueOf((int) parsed);
        }

        LoginConfig row = readRow();
        LoginConfig entity = row == null ? new LoginConfig() : row;
        if (row == null) {
            // 首次保存时按登录页的默认口径建行，避免 appc 保存顺带把登录方式写成全关
            entity.setPasswordEnable(Integer.valueOf(1));
            entity.setMobileEnable(Integer.valueOf(0));
            entity.setEmailEnable(Integer.valueOf(0));
            entity.setWecomQrEnable(Integer.valueOf(1));
            entity.setDingtalkQrEnable(Integer.valueOf(0));
            entity.setFeishuQrEnable(Integer.valueOf(0));
            entity.setPersonalWxEnable(Integer.valueOf(0));
            entity.setQrTtlSeconds(Integer.valueOf(0));
            entity.setLoginBoxOpacity(Integer.valueOf(OaonesConst.LOGIN_BOX_OPACITY_DEFAULT));
            entity.setFormmodeid(OaonesProps.loginConfigModeId());
            entity.setModedatacreater(OaonesUtil.i(operatorId, 0));
            entity.setModedatacreatedate(OaonesUtil.nowDate());
            entity.setModedatacreatetime(OaonesUtil.nowTime());
        }
        entity.setGopageSeconds(value);
        entity.setModedatamodifier(OaonesUtil.i(operatorId, 0));
        entity.setModedatamodifydatetime(OaonesUtil.now());
        try {
            if (row == null) {
                OaonesMyBatis.run(LoginConfigMapper.class, mapper -> mapper.insert(table(), entity));
            } else {
                OaonesMyBatis.run(LoginConfigMapper.class, mapper -> mapper.update(table(), entity));
            }
        } catch (Throwable t) {
            throw new RuntimeException(writeError(t));
        }
        OaonesLog.step("保存启动台自动跳转秒数 operatorId=" + operatorId
                + " gopage=" + (value == null ? "(用默认值)" : String.valueOf(value)));
        return gopageSettings();
    }

    /** 读当前设置行（表不存在/没数据时返回 null，由调用方按默认值兜底）。 */
    private LoginConfig readRow() {
        try {
            return OaonesMyBatis.call(LoginConfigMapper.class, mapper -> mapper.findFirst(table()));
        } catch (Throwable t) {
            // 表还没建（没跑升级脚本/没建模）时不能让整个登录页打不开：
            // 按默认值继续（默认开了账号密码 + 企业微信扫码），并在日志里说明原因
            OaonesLog.error("读取统一登录设置失败，按默认值继续（请确认已执行 "
                    + "sql/create_oaones_login_config.sql 且 oaones.properties 的 oaones_login_config_table 指向该表）", t);
            return null;
        }
    }

    /** 请求参数里的开关：1/true/on 视为开，其余视为关。 */
    private int flag(JSONObject params, String key) {
        String value = OaonesUtil.s(params == null ? "" : params.getString(key)).trim();
        return ("1".equals(value) || "true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value)) ? 1 : 0;
    }

    /**
     * 可选整数参数：空串 / 缺省 = {@code null}（表示“未设置 / 用默认”），非数字直接拒绝；
     * 上限先在这里挡一次，具体下限 / 上限由调用方按字段含义校验。
     */
    private Integer optionalInt(JSONObject params, String key, String label, int max) {
        String raw = OaonesUtil.s(params == null ? "" : params.getString(key)).trim();
        if (raw.length() == 0) {
            return null;
        }
        if (!raw.matches("-?\\d+")) {
            throw new RuntimeException(label + "请填整数（当前：" + raw + "）");
        }
        long value;
        try {
            value = Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new RuntimeException(label + "数值过大（当前：" + raw + "）");
        }
        if (value < 0 || value > max) {
            throw new RuntimeException(label + "必须在 0 ~ " + max + " 之间（当前：" + raw + "）");
        }
        return Integer.valueOf((int) value);
    }

    /** 写入失败时的可执行提示：最常见的原因就是没跑升级脚本/没建建模表。 */
    private String writeError(Throwable t) {
        OaonesLog.error("保存统一登录设置失败", t);
        return "保存统一登录设置失败：请确认已执行 sql/create_oaones_login_config.sql 建好 "
                + "uf_oaones_login_config 表、且 oaones.properties 的 oaones_login_config_table 指向该表"
                + "（老库还要执行 oaones0006.sql 补 email_enable 列、执行 oaones0007.sql 补 logo 列、"
                + "执行 oaones0009.sql 补登录框布局与启动台跳转秒数列、"
                + "执行 oaones0010.sql 补登录框透明度列、执行 oaones0011.sql 补左侧文字布局列、"
                + "执行 oaones0012.sql 补左侧文字背景透明度列）"
                + (t.getMessage() == null ? "" : "（数据库返回：" + t.getMessage() + "）");
    }

    /** 配置行转 JSON（无数据时给默认值：账号密码 + 企业微信扫码 开，其余关）。 */
    private JSONObject toJson(LoginConfig row) {
        JSONObject json = new JSONObject();
        json.put("id", row == null || row.getId() == null ? "" : String.valueOf(row.getId()));
        json.put("password_enable", row == null || row.getPasswordEnable() == null ? 1 : row.getPasswordEnable());
        json.put("mobile_enable", row == null || row.getMobileEnable() == null ? 0 : row.getMobileEnable());
        json.put("email_enable", row == null || row.getEmailEnable() == null ? 0 : row.getEmailEnable());
        json.put("wecom_qr_enable", row == null || row.getWecomQrEnable() == null ? 1 : row.getWecomQrEnable());
        json.put("dingtalk_qr_enable", row == null || row.getDingtalkQrEnable() == null ? 0 : row.getDingtalkQrEnable());
        json.put("feishu_qr_enable", row == null || row.getFeishuQrEnable() == null ? 0 : row.getFeishuQrEnable());
        json.put("personal_wx_enable", row == null || row.getPersonalWxEnable() == null ? 0 : row.getPersonalWxEnable());
        // 二维码有效期：配了就用它、留空回退 properties，超出 30~180 秒按边界截断，
        // 回给页面的是**实际生效**的秒数（页面上不会出现越界数字）
        int configuredTtl = row == null || row.getQrTtlSeconds() == null ? 0 : row.getQrTtlSeconds();
        json.put("qr_ttl_seconds", configuredTtl > 0 ? effectiveTtl(configuredTtl) : 0);
        json.put("qr_ttl_effective", effectiveTtl(configuredTtl));
        json.put("qr_ttl_min", MIN_TTL_SECONDS);
        json.put("qr_ttl_max", MAX_TTL_SECONDS);
        // 登录页 logo（站内地址，空 = 不显示）：设置页用它回显预览，登录页用它渲染
        json.put("logo", OaonesUtil.s(row == null ? "" : row.getLogo()));
        // 登录页外观的另外两项：整页背景图（空 = 默认底色）与左侧富文本（空 = 系统默认左侧说明）
        json.put("bg_image", OaonesUtil.s(row == null ? "" : row.getBgImage()));
        json.put("brand_html", OaonesUtil.s(row == null ? "" : row.getBrandHtml()));
        // 登录框布局：四列都非空才算启用；否则回空串，设置页按默认布局回显
        boolean boxCustom = row != null && row.getLoginBoxLeft() != null && row.getLoginBoxTop() != null
                && row.getLoginBoxWidth() != null && row.getLoginBoxHeight() != null;
        json.put("login_box_custom", boxCustom);
        json.put("login_box_left", boxCustom ? row.getLoginBoxLeft() : "");
        json.put("login_box_top", boxCustom ? row.getLoginBoxTop() : "");
        json.put("login_box_width", boxCustom ? row.getLoginBoxWidth() : "");
        json.put("login_box_height", boxCustom ? row.getLoginBoxHeight() : "");
        json.put("login_box_min_width", MIN_BOX_WIDTH);
        json.put("login_box_max_width", MAX_BOX_WIDTH);
        json.put("login_box_min_height", MIN_BOX_HEIGHT);
        json.put("login_box_max_height", MAX_BOX_HEIGHT);
        json.put("login_box_max_pos", MAX_BOX_POS);
        // 登录页左侧文字布局：同样四列都非空才算启用；否则回空串，设置页按默认版式回显
        boolean brandCustom = row != null && row.getBrandLeft() != null && row.getBrandTop() != null
                && row.getBrandWidth() != null && row.getBrandHeight() != null;
        json.put("brand_custom", brandCustom);
        json.put("brand_left", brandCustom ? row.getBrandLeft() : "");
        json.put("brand_top", brandCustom ? row.getBrandTop() : "");
        json.put("brand_width", brandCustom ? row.getBrandWidth() : "");
        json.put("brand_height", brandCustom ? row.getBrandHeight() : "");
        json.put("brand_min_width", MIN_BRAND_WIDTH);
        json.put("brand_max_width", MAX_BRAND_WIDTH);
        json.put("brand_min_height", MIN_BRAND_HEIGHT);
        json.put("brand_max_height", MAX_BRAND_HEIGHT);
        json.put("brand_max_pos", MAX_BRAND_POS);
        // 登录框背景透明度：NULL / 历史数据 = 0（不透明），页面按 0~100 的滑块回显
        int boxOpacity = row == null || row.getLoginBoxOpacity() == null
                ? OaonesConst.LOGIN_BOX_OPACITY_DEFAULT : row.getLoginBoxOpacity().intValue();
        json.put("login_box_opacity", Math.min(MAX_BOX_OPACITY, Math.max(MIN_BOX_OPACITY, boxOpacity)));
        json.put("login_box_opacity_min", MIN_BOX_OPACITY);
        json.put("login_box_opacity_max", MAX_BOX_OPACITY);
        // 左侧文字背景透明度：NULL / 历史数据 = 100（无底板），页面按 0~100 的滑块回显
        int brandOpacity = row == null || row.getBrandOpacity() == null
                ? OaonesConst.LOGIN_BRAND_OPACITY_DEFAULT : row.getBrandOpacity().intValue();
        json.put("brand_opacity", Math.min(MAX_BRAND_OPACITY,
                Math.max(MIN_BRAND_OPACITY, brandOpacity)));
        json.put("brand_opacity_min", MIN_BRAND_OPACITY);
        json.put("brand_opacity_max", MAX_BRAND_OPACITY);
        // 启动台自动跳转：配了就用它，空 = properties 默认值；页面同时显示实际生效值
        Integer gopage = row == null ? null : row.getGopageSeconds();
        json.put("gopage_seconds", gopage == null ? "" : gopage);
        json.put("gopage_effective", effectiveGopageSeconds(gopage));
        json.put("gopage_default", effectiveGopageSeconds(null));
        json.put("gopage_min", MIN_GOPAGE_SECONDS);
        json.put("gopage_max", MAX_GOPAGE_SECONDS);
        json.put("remark", OaonesUtil.s(row == null ? "" : row.getRemark()));
        json.put("update_time", OaonesUtil.s(row == null ? "" : row.getModedatamodifydatetime()));
        json.put("update_by", row == null || row.getModedatamodifier() == null
                ? "" : String.valueOf(row.getModedatamodifier()));
        return json;
    }

}
