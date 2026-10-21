package com.customization.oaones.appsyn.common;

import com.swetake.util.Qrcode;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 自建二维码渲染（把文本画成二维码图片，不依赖任何外部服务）。
 *
 * <p>登录页的二维码是**本系统自己生成**的：内容指向本模块的统一扫码落地页
 * （{@code /oaones/login/scan.jsp?t=<ticket>}），扫码方（企业微信 / 钉钉 / 飞书）
 * 只是打开这个本站地址，平台判断与身份换取都在 OA 侧完成——
 * 因此同一张二维码既能服务多个租户，也能被三个平台的客户端扫。</p>
 *
 * <p><b>默认输出 SVG</b>（{@link #svg}）：纯字符串拼接，不用 AWT/ImageIO，
 * 也就不会依赖 JVM 的 headless 设置（实测非 headless 的 JDK8 里创建
 * {@link BufferedImage} 会让进程直接崩掉，退出码 134，因此不作为默认路径）。
 * PNG（{@link #png}）保留给“要下载图片/打印”的场景，仅在 JVM 以 headless 运行时可用。</p>
 *
 * <p>编码器用 E9 自带 classpath 里的 {@code com.swetake.util.Qrcode}（WEB-INF/lib/QRCode.jar），
 * 不引入新的第三方依赖；纠错级别固定 M（二维码上不叠 logo，M 级足够且密度低更好扫）。</p>
 */
public final class QrCodeImage {

    /** 纠错级别：L/M/Q/H。登录二维码内容短、不叠 logo，M 级兼顾容错与密度。 */
    private static final char ERROR_CORRECT = 'M';
    /** 默认每个码元的像素数（值越大图片越大越好扫）。 */
    public static final int DEFAULT_MODULE_SIZE = 6;
    /** 默认静默区宽度（码元数）：标准要求 4。 */
    public static final int DEFAULT_QUIET_ZONE = 4;
    /**
     * 各版本（1~40）在**纠错级别 M + 8bit 字节模式**下的容量（字节数，QR 标准表）。
     *
     * <p>必须按容量表**直接算出**版本，不能“从小版本开始试”：
     * 本工程用的 {@code com.swetake.util.Qrcode} 在内容装不进所选版本时不会正常抛出异常，
     * 而是打印 overflow 后把进程直接打崩（实测退出码 134），所以先算准版本再调用。</p>
     */
    private static final int[] BYTE_CAPACITY_M = {
        0, 14, 26, 42, 62, 84, 106, 122, 152, 180,
        213, 251, 287, 331, 362, 412, 450, 504, 560, 624,
        666, 711, 779, 857, 911, 997, 1059, 1125, 1190, 1264,
        1370, 1452, 1538, 1628, 1722, 1809, 1911, 1989, 2099, 2213, 2331
    };

    private QrCodeImage() {
    }

    /**
     * 渲染成 SVG（默认输出格式，页面 {@code qrImage.jsp} 直接把它当字符串写出去）。
     *
     * <p>每一行把连续的深色码元合并成一个 {@code <rect>}，比逐码元画省一大截体积。</p>
     */
    public static String svg(String text, int moduleSize, int quietZone) {
        boolean[][] matrix = encodeMatrix(text);
        int modules = matrix.length;
        int module = moduleSize <= 0 ? DEFAULT_MODULE_SIZE : moduleSize;
        int quiet = quietZone < 0 ? DEFAULT_QUIET_ZONE : quietZone;
        int size = (modules + quiet * 2) * module;
        StringBuilder svg = new StringBuilder(2048);
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(size)
           .append("\" height=\"").append(size)
           .append("\" viewBox=\"0 0 ").append(size).append(' ').append(size)
           .append("\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"扫码登录二维码\">");
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>");
        svg.append("<g fill=\"#000000\">");
        for (int y = 0; y < modules; y++) {
            int x = 0;
            while (x < modules) {
                if (!matrix[y][x]) {
                    x++;
                    continue;
                }
                int start = x;
                while (x < modules && matrix[y][x]) {
                    x++;
                }
                svg.append("<rect x=\"").append((start + quiet) * module)
                   .append("\" y=\"").append((y + quiet) * module)
                   .append("\" width=\"").append((x - start) * module)
                   .append("\" height=\"").append(module).append("\"/>");
            }
        }
        svg.append("</g></svg>");
        return svg.toString();
    }

    /**
     * 渲染成 PNG 字节。
     *
     * <p>注意：需要 JVM 以 {@code -Djava.awt.headless=true} 运行（E9 一般已开启）；
     * 非 headless 的 JDK8 里创建 {@link BufferedImage} 可能让进程直接退出，
     * 所以页面默认用 {@link #svg}，只有显式要 PNG 时才走这里。</p>
     */
    public static byte[] png(String text, int moduleSize, int quietZone) throws Exception {
        boolean[][] matrix = encodeMatrix(text);
        int modules = matrix.length;
        int module = moduleSize <= 0 ? DEFAULT_MODULE_SIZE : moduleSize;
        int quiet = quietZone < 0 ? DEFAULT_QUIET_ZONE : quietZone;
        int size = (modules + quiet * 2) * module;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, size, size);
            g.setColor(Color.BLACK);
            for (int y = 0; y < modules; y++) {
                for (int x = 0; x < modules; x++) {
                    if (matrix[y][x]) {
                        g.fillRect((x + quiet) * module, (y + quiet) * module, module, module);
                    }
                }
            }
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** 编码成码元矩阵（内容为空或过长时抛异常）。 */
    public static boolean[][] encodeMatrix(String text) {
        String value = OaonesUtil.s(text);
        if (value.length() == 0) {
            throw new RuntimeException("二维码内容为空，无法生成二维码");
        }
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        // 版本号按容量表算出再编码；万一算出的版本仍装不下，才逐级往上试（绝不从更小版本试）
        for (int version = versionOf(data.length); version <= 40; version++) {
            boolean[][] matrix = tryEncode(data, version);
            if (matrix != null && matrix.length > 0) {
                return matrix;
            }
        }
        throw new RuntimeException("二维码内容过长，无法生成二维码（" + data.length + " 字节）");
    }

    /** 按容量表取能装下该字节数的**最小**版本；超出 40 版容量时返回 40（由编码阶段报错）。 */
    private static int versionOf(int bytes) {
        for (int version = 1; version <= 40; version++) {
            if (bytes <= BYTE_CAPACITY_M[version]) {
                return version;
            }
        }
        return 40;
    }

    /** 用指定版本试编码；失败返回 null（不抛出，由调用方决定是否升版重试）。 */
    private static boolean[][] tryEncode(byte[] data, int version) {
        try {
            Qrcode qrcode = new Qrcode();
            qrcode.setQrcodeErrorCorrect(ERROR_CORRECT);
            // 'B' = 8bit byte 模式：中文/URL 里的 UTF-8 字节按原样编码
            qrcode.setQrcodeEncodeMode('B');
            qrcode.setQrcodeVersion(version);
            return qrcode.calQrcode(data);
        } catch (Throwable t) {
            return null;
        }
    }
}
