package com.customization.oaones.appsyn.common;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 上传请求解析（本模块唯一的文件上传——应用中心的图标）。
 *
 * <p><b>为什么不复用 {@code api.jsp}</b>：{@code api.jsp} 是 query string 口径的 AJAX 入口
 * （{@code js/api.js} 用 GET 调），收不了 {@code multipart/form-data}。
 * 因此图标上传另开一个专用入口页 {@code /oaones/appCenter/iconUpload.jsp}，
 * 由它调用同一个控制器 action（{@code appc.uploadIcon}），页面用 {@code fetch} 以
 * {@code FormData} 提交——与平台自带的其它上传页（{@code docs/ofd/OfdUpload.jsp} 等）同一口径。</p>
 *
 * <p>解析用 E9 classpath 里自带的 commons-fileupload（平台自带的上传页也用它），
 * 不引入新的第三方依赖；大小上限交给解析器，超限时直接给中文提示（不把英文异常抛给页面）。</p>
 */
public final class OaonesUpload {

    /** 上传文件（读进内存的原始字节 + 原始文件名）。 */
    public static final class Item {

        private final String name;
        private final String contentType;
        private final byte[] bytes;

        Item(String name, String contentType, byte[] bytes) {
            this.name = name;
            this.contentType = contentType;
            this.bytes = bytes;
        }

        /** 浏览器提交的原始文件名（只用于日志：落盘文件名由服务端生成）。 */
        public String getName() {
            return name;
        }

        /** 浏览器提交的 Content-Type（不可信，服务端按文件头重新判定）。 */
        public String getContentType() {
            return contentType;
        }

        /** 文件内容。 */
        public byte[] getBytes() {
            return bytes;
        }
    }

    private OaonesUpload() {
    }

    /**
     * 取请求里的第一个上传文件。
     *
     * @param request  当前请求（必须是 {@code multipart/form-data}）
     * @param maxBytes 单个文件大小上限（超出直接抛中文提示）
     * @return 上传的文件；不是 multipart 请求或没有选文件时返回 {@code null}
     */
    public static Item read(HttpServletRequest request, long maxBytes) {
        if (request == null || !ServletFileUpload.isMultipartContent(request)) {
            return null;
        }
        DiskFileItemFactory factory = new DiskFileItemFactory();
        // 小文件（图标）直接放内存，不落临时目录
        factory.setSizeThreshold(64 * 1024);
        ServletFileUpload upload = new ServletFileUpload(factory);
        upload.setHeaderEncoding("UTF-8");
        if (maxBytes > 0) {
            upload.setSizeMax(maxBytes);
        }
        List<?> items;
        try {
            items = upload.parseRequest(request);
        } catch (FileUploadBase.SizeLimitExceededException e) {
            throw new RuntimeException("图标文件不能超过 " + ((maxBytes + 1023) / 1024)
                    + "KB（建议 200KB 以内），请压缩后再上传");
        } catch (Exception e) {
            OaonesLog.error("解析上传请求失败", e);
            throw new RuntimeException("图标上传失败，请重新选择图片再试");
        }
        if (items == null) {
            return null;
        }
        for (Object object : items) {
            if (!(object instanceof FileItem)) {
                continue;
            }
            FileItem item = (FileItem) object;
            if (item.isFormField()) {
                continue;
            }
            try {
                return new Item(OaonesUtil.s(item.getName()), OaonesUtil.s(item.getContentType()),
                        item.get());
            } catch (Exception e) {
                OaonesLog.error("读取上传文件内容失败", e);
                throw new RuntimeException("图标读取失败，请重新选择图片再试");
            }
        }
        return null;
    }
}
