package com.customization.oaones.appsyn.common;

/**
 * 默认人员绑定规则的字段口径。
 *
 * <p>rules 保存逗号分隔的匹配字段，优先级固定为
 * email（邮箱）→ mobile（手机号）→ name（姓名）→ workcode（工号）。</p>
 */
public final class OaonesRules {

    /** 匹配字段，数组顺序即匹配优先级。 */
    private static final String[] RULE_ORDER = {"email", "mobile", "name", "workcode"};

    private OaonesRules() {
    }

    /** 按固定优先级清洗并去重规则字段。 */
    public static String normalize(String value) {
        String source = OaonesUtil.s(value);
        StringBuilder sb = new StringBuilder();
        for (String rule : RULE_ORDER) {
            boolean hit = false;
            for (String token : source.split(",")) {
                if (rule.equals(token == null ? "" : token.trim())) {
                    hit = true;
                    break;
                }
            }
            if (hit) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(rule);
            }
        }
        return sb.toString();
    }

    /** 规则字段的中文名，用于拼接绑定原因。 */
    public static String label(String rule) {
        if ("email".equals(rule)) {
            return "邮箱";
        }
        if ("mobile".equals(rule)) {
            return "手机号";
        }
        if ("name".equals(rule)) {
            return "姓名";
        }
        if ("workcode".equals(rule)) {
            return "工号";
        }
        return rule;
    }
}
