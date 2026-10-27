/**
 * OAones Excel 导出（全模块唯一实现）：把当前列表/树的数据导出为真正的 .xlsx 文件。
 *
 * 用法：
 *   OaonesExcel.export({
 *       filename: "OA数据基准_20260911_1030.xlsx",   // 可省略，省略时自动生成
 *       sheet:    "OA数据基准",                        // 工作表名（可省略）
 *       columns:  [{ title: "OA 人员", width: 18 }, ...],  // width 为字符宽度，可省略
 *       rows:     [["张三", "1001", 3], ...]               // 单元格：字符串按文本、数字按数值写入
 *   });
 *
 * 口径：
 *   - 纯前端生成 OOXML(.xlsx)，不依赖第三方库、不经过后端：zip 用 store 方式打包，
 *     字符串单元格用 inlineStr，所以工号/手机号/ID 等长数字串不会被 Excel 变成科学计数法；
 *     数字类型（人数、条数）按数值写入，打开即可求和；
 *   - 表头加粗并有底色、首行冻结、自动筛选，列宽按 columns[].width；
 *   - 下载用 Blob + a[download]，IE/旧 Edge 走 navigator.msSaveBlob；
 *   - 文件名统一过滤非法字符，避免浏览器拒绝下载。
 */
(function (window, document) {
    "use strict";

    var XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /* ==================================================================
     * 一、文本、编码与 CRC32
     * ================================================================== */

    function text(value) { return value == null ? "" : String(value); }

    /** XML 文本/属性转义；同时去掉 XML 不允许的控制字符 */
    function xmlEsc(value) {
        return text(value)
            .replace(/[\x00-\x08\x0B\x0C\x0E-\x1F]/g, "")
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&apos;");
    }

    /** 字符串 → UTF-8 字节数组（走 encodeURIComponent，不依赖 TextEncoder，兼容老浏览器） */
    function utf8Bytes(str) {
        var out = [];
        var enc = encodeURIComponent(text(str));
        for (var i = 0; i < enc.length; i++) {
            var ch = enc.charAt(i);
            if (ch === "%") {
                out.push(parseInt(enc.substr(i + 1, 2), 16));
                i += 2;
            } else {
                out.push(enc.charCodeAt(i) & 0xff);
            }
        }
        return out;
    }

    var crcTable = null;

    function crc32(bytes) {
        if (!crcTable) {
            crcTable = [];
            for (var n = 0; n < 256; n++) {
                var c = n;
                for (var k = 0; k < 8; k++) {
                    c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
                }
                crcTable[n] = c >>> 0;
            }
        }
        var crc = 0xFFFFFFFF;
        for (var i = 0; i < bytes.length; i++) {
            crc = (crc >>> 8) ^ crcTable[(crc ^ bytes[i]) & 0xFF];
        }
        return (crc ^ 0xFFFFFFFF) >>> 0;
    }

    /* ==================================================================
     * 二、zip 打包（store，不压缩，xlsx 本身已足够小）
     * ================================================================== */

    function pushU16(out, value) {
        out.push(value & 0xff, (value >>> 8) & 0xff);
    }

    function pushU32(out, value) {
        out.push(value & 0xff, (value >>> 8) & 0xff, (value >>> 16) & 0xff, (value >>> 24) & 0xff);
    }

    function pushBytes(out, bytes) {
        for (var i = 0; i < bytes.length; i++) { out.push(bytes[i]); }
    }

    /** DOS 时间/日期（zip 头用） */
    function dosDateTime(date) {
        var d = date || new Date();
        return {
            time: ((d.getHours() & 31) << 11) | ((d.getMinutes() & 63) << 5) | ((d.getSeconds() >> 1) & 31),
            date: ((((d.getFullYear() - 1980) & 127) << 9) | (((d.getMonth() + 1) & 15) << 5) | (d.getDate() & 31))
        };
    }

    /** files: [{ name: "xl/workbook.xml", bytes: [..] }] → Uint8Array */
    function zipStore(files) {
        var out = [];
        var central = [];
        var offset = 0;
        var dt = dosDateTime();
        for (var i = 0; i < files.length; i++) {
            var nameBytes = utf8Bytes(files[i].name);
            var data = files[i].bytes;
            var crc = crc32(data);
            // 本地文件头
            pushU32(out, 0x04034b50);
            pushU16(out, 20);
            pushU16(out, 0x0800);
            pushU16(out, 0);
            pushU16(out, dt.time);
            pushU16(out, dt.date);
            pushU32(out, crc);
            pushU32(out, data.length);
            pushU32(out, data.length);
            pushU16(out, nameBytes.length);
            pushU16(out, 0);
            pushBytes(out, nameBytes);
            pushBytes(out, data);
            // 中央目录项
            pushU32(central, 0x02014b50);
            pushU16(central, 20);
            pushU16(central, 20);
            pushU16(central, 0x0800);
            pushU16(central, 0);
            pushU16(central, dt.time);
            pushU16(central, dt.date);
            pushU32(central, crc);
            pushU32(central, data.length);
            pushU32(central, data.length);
            pushU16(central, nameBytes.length);
            pushU16(central, 0);
            pushU16(central, 0);
            pushU16(central, 0);
            pushU16(central, 0);
            pushU32(central, 0);
            pushU32(central, offset);
            pushBytes(central, nameBytes);
            offset += 30 + nameBytes.length + data.length;
        }
        var centralOffset = offset;
        pushBytes(out, central);
        // 中央目录结束记录
        pushU32(out, 0x06054b50);
        pushU16(out, 0);
        pushU16(out, 0);
        pushU16(out, files.length);
        pushU16(out, files.length);
        pushU32(out, central.length);
        pushU32(out, centralOffset);
        pushU16(out, 0);
        return new Uint8Array(out);
    }

    /* ==================================================================
     * 三、xlsx（OOXML）内容
     * ================================================================== */

    var HEADER_STYLE = 1;

    function contentTypesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/xl/workbook.xml\""
            + " ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
            + "<Override PartName=\"/xl/worksheets/sheet1.xml\""
            + " ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
            + "<Override PartName=\"/xl/styles.xml\""
            + " ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
            + "</Types>";
    }

    function rootRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\""
            + " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\""
            + " Target=\"xl/workbook.xml\"/>"
            + "</Relationships>";
    }

    function workbookRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\""
            + " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\""
            + " Target=\"worksheets/sheet1.xml\"/>"
            + "<Relationship Id=\"rId2\""
            + " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\""
            + " Target=\"styles.xml\"/>"
            + "</Relationships>";
    }

    function workbookXml(sheetName) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
            + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
            + "<sheets><sheet name=\"" + xmlEsc(sheetName) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
            + "</workbook>";
    }

    function stylesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
            + "<fonts count=\"2\">"
            + "<font><sz val=\"11\"/><name val=\"\u5b8b\u4f53\"/></font>"
            + "<font><b/><sz val=\"11\"/><name val=\"\u5b8b\u4f53\"/></font>"
            + "</fonts>"
            + "<fills count=\"3\">"
            + "<fill><patternFill patternType=\"none\"/></fill>"
            + "<fill><patternFill patternType=\"gray125\"/></fill>"
            + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFF2F6FC\"/><bgColor indexed=\"64\"/></patternFill></fill>"
            + "</fills>"
            + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
            + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
            + "<cellXfs count=\"2\">"
            + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
            + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"
            + "</cellXfs>"
            + "<cellStyles count=\"1\"><cellStyle name=\"\u5e38\u89c4\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
            + "</styleSheet>";
    }

    /** 0 → A、25 → Z、26 → AA …… */
    function colRef(index) {
        var i = index + 1;
        var ref = "";
        while (i > 0) {
            var m = (i - 1) % 26;
            ref = String.fromCharCode(65 + m) + ref;
            i = Math.floor((i - 1) / 26);
        }
        return ref;
    }

    /** 单元格 XML：空值省略单元格；数字按数值、其余按文本（inlineStr）写入 */
    function cellXml(ref, value, styleId) {
        var style = styleId ? " s=\"" + styleId + "\"" : "";
        if (value === null || value === undefined || value === "") { return ""; }
        if (typeof value === "number" && isFinite(value)) {
            return "<c r=\"" + ref + "\"" + style + "><v>" + value + "</v></c>";
        }
        return "<c r=\"" + ref + "\"" + style + " t=\"inlineStr\"><is><t xml:space=\"preserve\">"
            + xmlEsc(value) + "</t></is></c>";
    }

    function sheetXml(opt) {
        var columns = opt.columns || [];
        var rows = opt.rows || [];
        var xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
            + "<sheetViews><sheetView workbookViewId=\"0\">"
            + "<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>"
            + "<selection pane=\"bottomLeft\" activeCell=\"A2\" sqref=\"A2\"/>"
            + "</sheetView></sheetViews>"
            + "<sheetFormatPr defaultRowHeight=\"15\"/>";
        if (columns.length) {
            xml += "<cols>";
            for (var c = 0; c < columns.length; c++) {
                var width = columns[c] && columns[c].width ? columns[c].width : 16;
                xml += "<col min=\"" + (c + 1) + "\" max=\"" + (c + 1) + "\" width=\"" + width
                    + "\" customWidth=\"1\"/>";
            }
            xml += "</cols>";
        }
        xml += "<sheetData>";
        var head = "<row r=\"1\">";
        for (var h = 0; h < columns.length; h++) {
            head += cellXml(colRef(h) + "1", columns[h] ? columns[h].title : "", HEADER_STYLE);
        }
        xml += head + "</row>";
        for (var r = 0; r < rows.length; r++) {
            var line = rows[r] || [];
            var rowXml = "<row r=\"" + (r + 2) + "\">";
            for (var i = 0; i < columns.length; i++) {
                rowXml += cellXml(colRef(i) + (r + 2), line[i], 0);
            }
            xml += rowXml + "</row>";
        }
        xml += "</sheetData>";
        if (rows.length && columns.length) {
            xml += "<autoFilter ref=\"A1:" + colRef(columns.length - 1) + (rows.length + 1) + "\"/>";
        }
        return xml + "</worksheet>";
    }

    /** 工作表名：长度 ≤31，去掉 Excel 不接受的字符 */
    function safeSheetName(name) {
        var n = text(name).replace(/[\\\/\?\*\[\]:]/g, " ").replace(/\s+/g, " ").trim();
        if (!n) { n = "数据"; }
        return n.length > 31 ? n.substring(0, 31) : n;
    }

    /** 文件名：去掉各系统不接受的字符 */
    function safeFilename(name) {
        var n = text(name).replace(/[\\\/:*?"<>|\r\n\t]/g, "_").replace(/\s+/g, " ").trim();
        return n || ("导出_" + stamp() + ".xlsx");
    }

    /** 时间戳：20260911_1030（导出文件名用） */
    function stamp(date) {
        var d = date || new Date();
        function pad(v) { return (v < 10 ? "0" : "") + v; }
        return "" + d.getFullYear() + pad(d.getMonth() + 1) + pad(d.getDate())
            + "_" + pad(d.getHours()) + pad(d.getMinutes());
    }

    /* ==================================================================
     * 四、对外 API
     * ================================================================== */

    /** 生成 xlsx 字节（Uint8Array）；自测与调试用，正常导出走 export() */
    function build(opt) {
        opt = opt || {};
        var sheetName = safeSheetName(opt.sheet);
        var files = [
            { name: "[Content_Types].xml", bytes: utf8Bytes(contentTypesXml()) },
            { name: "_rels/.rels", bytes: utf8Bytes(rootRelsXml()) },
            { name: "xl/workbook.xml", bytes: utf8Bytes(workbookXml(sheetName)) },
            { name: "xl/_rels/workbook.xml.rels", bytes: utf8Bytes(workbookRelsXml()) },
            { name: "xl/styles.xml", bytes: utf8Bytes(stylesXml()) },
            { name: "xl/worksheets/sheet1.xml", bytes: utf8Bytes(sheetXml(opt)) }
        ];
        return zipStore(files);
    }

    /** 触发浏览器下载；返回是否成功（false 表示当前浏览器不支持） */
    function download(filename, bytes) {
        var name = safeFilename(filename);
        var blob = new window.Blob([bytes], { type: XLSX_MIME });
        var nav = window.navigator || {};
        // IE / 旧 Edge：没有 a[download]，走 msSaveBlob
        if (nav.msSaveBlob || nav.msSaveOrOpenBlob) {
            (nav.msSaveBlob || nav.msSaveOrOpenBlob).call(nav, blob, name);
            return true;
        }
        var urlApi = window.URL || window.webkitURL;
        if (!urlApi || !urlApi.createObjectURL) { return false; }
        var url = urlApi.createObjectURL(blob);
        var a = document.createElement("a");
        a.href = url;
        a.download = name;
        a.style.display = "none";
        document.body.appendChild(a);
        a.click();
        // 立即回收在部分浏览器里会让下载中断，这里延迟回收
        window.setTimeout(function () {
            if (a.parentNode) { a.parentNode.removeChild(a); }
            urlApi.revokeObjectURL(url);
        }, 2000);
        return true;
    }

    /**
     * 导出并下载：opt = { filename, sheet, columns: [{title, width}], rows: [[...]] }
     * 返回 true 表示已触发下载。
     */
    function exportFile(opt) {
        opt = opt || {};
        var filename = opt.filename || ((opt.sheet || "导出") + "_" + stamp() + ".xlsx");
        return download(filename, build(opt));
    }

    window.OaonesExcel = {
        build: build,
        export: exportFile,
        stamp: stamp,
        safeSheetName: safeSheetName
    };
})(window, document);
