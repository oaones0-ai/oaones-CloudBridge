package com.customization.oaones.appsyn.common;

/**
 * 图片文件头探测（应用中心的图标上传校验用）：只读文件头，得出「是不是图片 + 真实格式 + 长宽」。
 *
 * <p><b>为什么不用 ImageIO / AWT</b>：E9 的 JVM 往往不是 headless 启动的，实测在这种 JDK8 里
 * 创建 {@code BufferedImage} 会把进程直接打崩（见 {@link QrCodeImage} 的说明）；
 * 而上传校验只需要「确实是图片」和「长宽多少」，按文件头解析既够用，也省掉整张图片的解码开销。</p>
 *
 * <p>支持 PNG / JPG / GIF / BMP / WebP（与 {@code OaonesConst.APP_ICON_EXTENSIONS} 对齐）。
 * <b>不支持 SVG</b>：SVG 是同源可执行的脚本文件，直接访问上传后的地址会在 OA 域里执行脚本，
 * 因此不作为图标格式开放。</p>
 */
public final class OaonesImageSize {

    /** 探测结果：扩展名 / Content-Type / 长宽（长宽读不出时为 0，页面不显示尺寸即可）。 */
    public static final class Info {

        private final String extension;
        private final String contentType;
        private final int width;
        private final int height;

        Info(String extension, String contentType, int width, int height) {
            this.extension = extension;
            this.contentType = contentType;
            this.width = width;
            this.height = height;
        }

        /** 规范化扩展名（小写、不带点）：png / jpg / gif / bmp / webp。 */
        public String getExtension() {
            return extension;
        }

        /** 该格式的 Content-Type（落盘后由容器按扩展名返回，这里只用于页面显示与日志）。 */
        public String getContentType() {
            return contentType;
        }

        /** 宽度（像素）：读不出为 0。 */
        public int getWidth() {
            return width;
        }

        /** 高度（像素）：读不出为 0。 */
        public int getHeight() {
            return height;
        }
    }

    private OaonesImageSize() {
    }

    /**
     * 按文件头探测图片信息。
     *
     * @param data 上传文件的原始字节
     * @return 识别成功返回 {@link Info}；不是支持的图片格式时返回 {@code null}
     */
    public static Info probe(byte[] data) {
        if (data == null || data.length < 16) {
            return null;
        }
        if (isPng(data)) {
            return new Info("png", "image/png", beInt(data, 16), beInt(data, 20));
        }
        if (isJpeg(data)) {
            int[] size = jpegSize(data);
            return new Info("jpg", "image/jpeg", size[0], size[1]);
        }
        if (isGif(data)) {
            return new Info("gif", "image/gif", leShort(data, 6), leShort(data, 8));
        }
        if (isBmp(data)) {
            int[] size = bmpSize(data);
            return new Info("bmp", "image/bmp", size[0], size[1]);
        }
        if (isWebp(data)) {
            int[] size = webpSize(data);
            return new Info("webp", "image/webp", size[0], size[1]);
        }
        return null;
    }

    /* ---------------- PNG（IHDR 固定在前 24 字节里） ---------------- */

    private static boolean isPng(byte[] d) {
        return (d[0] & 0xFF) == 0x89 && d[1] == 0x50 && d[2] == 0x4E && d[3] == 0x47
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A;
    }

    /* ---------------- JPEG（扫段找 SOFn 帧头，宽高在帧头里） ---------------- */

    private static boolean isJpeg(byte[] d) {
        return (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8;
    }

    private static int[] jpegSize(byte[] d) {
        int pos = 2;
        while (pos + 4 <= d.length) {
            if ((d[pos] & 0xFF) != 0xFF) {
                pos++;
                continue;
            }
            int marker = d[pos + 1] & 0xFF;
            if (marker == 0xFF) {
                pos++;
                continue;
            }
            // 无长度字段的标记：SOI / TEM / RSTn，直接跳过
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                pos += 2;
                continue;
            }
            // 压缩数据开始（SOS）：后面不再有帧头
            if (marker == 0xDA) {
                return zero();
            }
            int segmentLength = beShort(d, pos + 2);
            if (segmentLength < 2) {
                return zero();
            }
            boolean sof = (marker >= 0xC0 && marker <= 0xCF)
                    && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
            if (sof) {
                // 帧头：长度(2) 精度(1) 高(2) 宽(2)
                return new int[]{beShort(d, pos + 7), beShort(d, pos + 5)};
            }
            pos += 2 + segmentLength;
        }
        return zero();
    }

    /* ---------------- GIF（逻辑屏幕描述符：宽高各 2 字节小端） ---------------- */

    private static boolean isGif(byte[] d) {
        return d[0] == 'G' && d[1] == 'I' && d[2] == 'F' && d[3] == '8'
                && (d[4] == '7' || d[4] == '9') && d[5] == 'a';
    }

    /* ---------------- BMP（DIB 头，宽高可能是负数表示自上而下） ---------------- */

    private static boolean isBmp(byte[] d) {
        return d[0] == 'B' && d[1] == 'M';
    }

    private static int[] bmpSize(byte[] d) {
        int dibHeaderSize = leInt(d, 14);
        if (dibHeaderSize == 12) {
            return new int[]{Math.abs(leShort(d, 18)), Math.abs(leShort(d, 20))};
        }
        return new int[]{Math.abs(leInt(d, 18)), Math.abs(leInt(d, 22))};
    }

    /* ---------------- WebP（RIFF 容器里的三种编码） ---------------- */

    private static boolean isWebp(byte[] d) {
        return d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P';
    }

    private static int[] webpSize(byte[] d) {
        String chunk = "" + (char) d[12] + (char) d[13] + (char) d[14] + (char) d[15];
        if ("VP8X".equals(chunk) && d.length >= 30) {
            // 扩展头：画布宽高各 3 字节小端，存的是边长减一
            return new int[]{1 + le24(d, 24), 1 + le24(d, 27)};
        }
        if ("VP8 ".equals(chunk) && d.length >= 30) {
            // 有损：帧头里的低 14 位分别是宽与高
            return new int[]{leShort(d, 26) & 0x3FFF, leShort(d, 28) & 0x3FFF};
        }
        if ("VP8L".equals(chunk) && d.length >= 25 && (d[20] & 0xFF) == 0x2F) {
            // 无损：14 位宽、14 位高按位打包（同样各存边长减一）
            int b0 = d[21] & 0xFF;
            int b1 = d[22] & 0xFF;
            int b2 = d[23] & 0xFF;
            int b3 = d[24] & 0xFF;
            int width = 1 + (((b1 & 0x3F) << 8) | b0);
            int height = 1 + (((b3 & 0x0F) << 10) | (b2 << 2) | ((b1 & 0xC0) >> 6));
            return new int[]{width, height};
        }
        return zero();
    }

    /* ---------------- 字节读取（越界一律返回 0，不抛异常） ---------------- */

    private static int[] zero() {
        return new int[]{0, 0};
    }

    private static int beShort(byte[] d, int offset) {
        if (offset < 0 || offset + 2 > d.length) {
            return 0;
        }
        return ((d[offset] & 0xFF) << 8) | (d[offset + 1] & 0xFF);
    }

    private static int beInt(byte[] d, int offset) {
        if (offset < 0 || offset + 4 > d.length) {
            return 0;
        }
        return ((d[offset] & 0xFF) << 24) | ((d[offset + 1] & 0xFF) << 16)
                | ((d[offset + 2] & 0xFF) << 8) | (d[offset + 3] & 0xFF);
    }

    private static int leShort(byte[] d, int offset) {
        if (offset < 0 || offset + 2 > d.length) {
            return 0;
        }
        return ((d[offset + 1] & 0xFF) << 8) | (d[offset] & 0xFF);
    }

    private static int leInt(byte[] d, int offset) {
        if (offset < 0 || offset + 4 > d.length) {
            return 0;
        }
        return ((d[offset + 3] & 0xFF) << 24) | ((d[offset + 2] & 0xFF) << 16)
                | ((d[offset + 1] & 0xFF) << 8) | (d[offset] & 0xFF);
    }

    private static int le24(byte[] d, int offset) {
        if (offset < 0 || offset + 3 > d.length) {
            return 0;
        }
        return ((d[offset + 2] & 0xFF) << 16) | ((d[offset + 1] & 0xFF) << 8) | (d[offset] & 0xFF);
    }
}
