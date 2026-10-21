package com.customization.oaones.appsyn.service.impl.feishu;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesAdmin;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesImageSize;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dao.feishu.LoginConfigMapper;
import com.customization.oaones.appsyn.entity.feishu.LoginConfig;
import com.customization.oaones.appsyn.service.feishu.LoginConfigService;
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
 * 默认账号密码 + 飞书扫码 开、二维码有效期取 `oaones.properties#login.qr.ttl`，
 * 这样“没配过设置”与“配过设置但没改”表现一致，登录页不会因为少一行数据而不可用。</p>
 *
 * <p>保存：校验通过后更新那一行，没有行则插入；飞书/个人微信只存开关不做登录实现，
 * 因此不参与“至少要有一路可用登录方式”的校验。</p>
 *
 * <p><b>登录页外观</b>三项（列 {@code logo} / {@code bg_image} / {@code brand_html}）：
 * 与统一登录设置页共用一行配置，本类同样读写它们——两个图片项页面上传后落到
 * {@code oaones/img/}（前缀 loginlogo_ / loginbg_），记录里只存站内地址；
 * 上传（{@link #uploadImage}）只落盘，点“保存设置”才写库；请求里不带对应参数时保持原值，
 * 这样从飞书入口只改开关不会把图或左侧文字抹掉。</p>
 */
public class LoginConfigServiceImpl implements LoginConfigService {

    /** 二维码有效期允许配置的范围（秒）：太短来不及扫、太长等于不过期（最长 180 秒 = 3 分钟）。 */
    private static final int MIN_TTL_SECONDS = 30;
    private static final int MAX_TTL_SECONDS = 180;

    /** 图片地址（列宽 varchar(500)）的最大长度：超出直接拒绝，不静默截断。 */
    private static final int LOGIN_URL_MAX = 500;

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
        data.put("feishu_qr_enable", all.getIntValue("feishu_qr_enable"));
        data.put("personal_wx_enable", all.getIntValue("personal_wx_enable"));
        data.put("qr_ttl_seconds", all.getIntValue("qr_ttl_seconds"));
        // 登录页右侧登录框上方的 logo（空 = 不显示；弹框模式里不显示，见 login.jsp）
        data.put("logo", OaonesUtil.s(all.getString("logo")));
        // 登录页的整页背景图与左侧富文本（都为空时登录页用系统默认外观）
        data.put("bg_image", OaonesUtil.s(all.getString("bg_image")));
        data.put("brand_html", OaonesUtil.s(all.getString("brand_html")));
        // 本页服务飞书登录页：可用性只看“账号密码 / 验证登录（手机号或邮箱）/ 飞书扫码”这三种
        // （企微扫码登录有自己的登录页，个人微信仍未接入，都不参与这里的判断）
        boolean password = all.getIntValue("password_enable") == 1;
        boolean mobile = all.getIntValue("mobile_enable") == 1;
        boolean email = all.getIntValue("email_enable") == 1;
        boolean feishu = all.getIntValue("feishu_qr_enable") == 1;
        data.put("verify_enable", mobile || email);
        data.put("available", password || mobile || email || feishu);
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
        // 钉钉扫码登录开关由钉钉连接器维护：本服务只读它做“至少开一种”的判断，不写库
        int dingtalk = flag(params, "dingtalk_qr_enable");
        int feishu = flag(params, "feishu_qr_enable");
        int personalWx = flag(params, "personal_wx_enable");
        // 登录设置与企微共用同一行配置：可用性按四种方式一起判断
        if (password + mobile + email + wecom + dingtalk + feishu == 0) {
            throw new RuntimeException("账号密码登录、验证登录（手机号 / 邮箱）、企业微信扫码登录、钉钉扫码登录、"
                    + "飞书扫码登录至少要开启一种"
                    + "（个人微信尚未接入登录，单独开启会导致没人能登录）");
        }
        int ttl = OaonesUtil.i(OaonesUtil.nullToEmpty(params.getString("qr_ttl_seconds")), 0);
        if (ttl <= 0) {
            ttl = 0; // 留空 = 用 properties 默认值
        } else if (ttl < MIN_TTL_SECONDS || ttl > MAX_TTL_SECONDS) {
            throw new RuntimeException("扫码二维码有效期必须在 " + MIN_TTL_SECONDS + " ~ "
                    + MAX_TTL_SECONDS + " 秒之间（留空表示使用系统默认值）");
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
        entity.setFeishuQrEnable(feishu);
        entity.setPersonalWxEnable(personalWx);
        entity.setQrTtlSeconds(ttl);
        entity.setLogo(newLogo);
        entity.setBgImage(newBg);
        entity.setBrandHtml(newBrand);
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
        if (logoProvided && !newLogo.equals(oldLogo)) {
            deleteImageFile(LOGIN_LOGO, oldLogo);
        }
        if (bgProvided && !newBg.equals(oldBg)) {
            deleteImageFile(LOGIN_BG, oldBg);
        }
        OaonesLog.step("保存统一登录设置 operatorId=" + operatorId
                + " password=" + password + " mobile=" + mobile + " email=" + email + " wecomQr=" + wecom
                + " feishuQr=" + feishu + " personalWx=" + personalWx + " qrTtl=" + ttl
                + " logo=" + (logoProvided ? (newLogo.length() == 0 ? "(已清除)" : newLogo) : "(未改动)")
                + " bg=" + (bgProvided ? (newBg.length() == 0 ? "(已清除)" : newBg) : "(未改动)")
                + " brandHtml=" + (brandProvided ? (newBrand.length() == 0 ? "(已清除)" : newBrand.length() + "字符")
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
     * （{@code /oaones/img/loginlogo_xxx.png} / {@code /oaones/img/loginbg_xxx.png}）；
     * 其它值一律拒绝（与「应用中心」的图标同一口径）。
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
     * 登录页左侧富文本（列 {@code brand_html}）：**设置什么存什么**（登录页原样输出），
     * 只有两条边界：长度上限（超了直接拒绝而不截断，截断会把 HTML 截成坏标记）；
     * 只有空白 / 空标记的内容按“没设置”处理，登录页回到系统默认的左侧说明。
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
                .replaceAll("<[^>]*>", "").trim();
        return text.length() == 0 ? "" : html;
    }

    /** 登录页外观图片的上传目录（与「应用中心」的图标同目录：E9 站点根下的 {@code oaones/img}）。 */
    private File loginImageDir() {
        return new File(OaonesUtil.s(GCONST.getRootPath()), OaonesConst.LOGIN_IMAGE_DIR);
    }

    /** 生成本次上传的落盘文件（时间戳 + 随机串，不使用上传的文件名）。 */
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
        return new File(dir, filePrefix + System.currentTimeMillis() + "." + extension);
    }

    /** 删除不再被引用的旧图片（只删本用途前缀的文件，删不掉只记日志）。 */
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

    @Override
    public int qrTtlSeconds() {
        LoginConfig row = readRow();
        int configured = row == null || row.getQrTtlSeconds() == null ? 0 : row.getQrTtlSeconds();
        // 设置页配了就用它（按 30~180 秒截断），留空回退 properties 的默认值（默认 120 秒）
        return effectiveTtl(configured);
    }

    /** 读当前设置行（表不存在/没数据时返回 null，由调用方按默认值兜底）。 */
    private LoginConfig readRow() {
        try {
            return OaonesMyBatis.call(LoginConfigMapper.class, mapper -> mapper.findFirst(table()));
        } catch (Throwable t) {
            // 表还没建（没跑升级脚本/没建模）时不能让整个登录页打不开：
            // 按默认值继续（默认开了账号密码 + 飞书扫码），并在日志里说明原因
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

    /** 写入失败时的可执行提示：最常见的原因就是没跑升级脚本/没建建模表。 */
    private String writeError(Throwable t) {
        OaonesLog.error("保存统一登录设置失败", t);
        return "保存统一登录设置失败：请确认已执行 sql/create_oaones_login_config.sql 建好 "
                + "uf_oaones_login_config 表、且 oaones.properties 的 oaones_login_config_table 指向该表"
                + "（老库还要执行 oaones0006.sql 补 email_enable 列、执行 oaones0007.sql 补 logo 列）"
                + (t.getMessage() == null ? "" : "（数据库返回：" + t.getMessage() + "）");
    }

    /** 配置行转 JSON（无数据时给默认值：账号密码 + 飞书扫码 开，其余关）。 */
    private JSONObject toJson(LoginConfig row) {
        JSONObject json = new JSONObject();
        json.put("id", row == null || row.getId() == null ? "" : String.valueOf(row.getId()));
        json.put("password_enable", row == null || row.getPasswordEnable() == null ? 1 : row.getPasswordEnable());
        json.put("mobile_enable", row == null || row.getMobileEnable() == null ? 0 : row.getMobileEnable());
        json.put("email_enable", row == null || row.getEmailEnable() == null ? 0 : row.getEmailEnable());
        json.put("wecom_qr_enable", row == null || row.getWecomQrEnable() == null ? 1 : row.getWecomQrEnable());
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
        json.put("remark", OaonesUtil.s(row == null ? "" : row.getRemark()));
        json.put("update_time", OaonesUtil.s(row == null ? "" : row.getModedatamodifydatetime()));
        json.put("update_by", row == null || row.getModedatamodifier() == null
                ? "" : String.valueOf(row.getModedatamodifier()));
        return json;
    }

}
