import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.dto.AttMember;
import com.customization.oaones.appsyn.entity.AttSyncCfg;
import com.customization.oaones.appsyn.service.impl.AttSyncServiceImpl;

import java.lang.reflect.Method;

/**
 * 考勤同步 OA 多账户规则：本地冒烟测试（不依赖数据库 / 平台接口）。
 *
 * <p>核对 E9 主账户判定、历史空值兼容，以及设置级“只同步主账户 / 所有账户均同步”
 * 对主账户和子账户的放行结果。</p>
 *
 * <pre>
 * mkdir -p /tmp/oaones-att-rule
 * /Users/zhengli/weaver/jdk/bin/javac -encoding UTF-8 -nowarn \
 *     -cp "classbean:WEB-INF/lib/*" -d /tmp/oaones-att-rule \
 *     oaones/appsyn/tools/AttSyncAccountRuleSmokeTest.java
 * /Users/zhengli/weaver/jdk/bin/java \
 *     -cp "/tmp/oaones-att-rule:classbean:WEB-INF/lib/*" AttSyncAccountRuleSmokeTest
 * </pre>
 */
public class AttSyncAccountRuleSmokeTest {

    private static int failed;

    public static void main(String[] args) throws Exception {
        AttMember legacy = new AttMember();
        check("历史查询未带 accounttype 时按主账户处理", legacy.isPrimaryAccount());

        AttMember main = new AttMember();
        main.setAccountType(Integer.valueOf(0));
        main.setPrimaryAccount(Integer.valueOf(1));
        check("accounttype=0 是主账户", main.isPrimaryAccount());

        AttMember sub = new AttMember();
        sub.setAccountType(Integer.valueOf(1));
        sub.setPrimaryAccount(Integer.valueOf(0));
        check("accounttype=1 是子账户", !sub.isPrimaryAccount());

        AttSyncServiceImpl service = new AttSyncServiceImpl();
        Method allowed = AttSyncServiceImpl.class.getDeclaredMethod(
                "oaAccountAllowed", AttSyncCfg.class, AttMember.class);
        allowed.setAccessible(true);

        AttSyncCfg primaryOnly = new AttSyncCfg();
        primaryOnly.setOaAccountRule(Integer.valueOf(OaonesConst.ATT_OA_ACCOUNT_PRIMARY));
        check("只同步主账户：放行主账户",
                Boolean.TRUE.equals(allowed.invoke(service, primaryOnly, main)));
        check("只同步主账户：跳过子账户",
                Boolean.FALSE.equals(allowed.invoke(service, primaryOnly, sub)));

        AttSyncCfg all = new AttSyncCfg();
        all.setOaAccountRule(Integer.valueOf(OaonesConst.ATT_OA_ACCOUNT_ALL));
        check("所有账户均同步：放行主账户",
                Boolean.TRUE.equals(allowed.invoke(service, all, main)));
        check("所有账户均同步：放行子账户",
                Boolean.TRUE.equals(allowed.invoke(service, all, sub)));

        AttSyncCfg legacyCfg = new AttSyncCfg();
        check("设置行规则为空时按只同步主账户处理",
                Boolean.FALSE.equals(allowed.invoke(service, legacyCfg, sub)));

        check("规则文案",
                "只同步主账户".equals(OaonesConst.attOaAccountRuleText(OaonesConst.ATT_OA_ACCOUNT_PRIMARY))
                        && "所有账户均同步".equals(
                                OaonesConst.attOaAccountRuleText(OaonesConst.ATT_OA_ACCOUNT_ALL)));

        if (failed > 0) {
            throw new AssertionError("AttSyncAccountRuleSmokeTest failed: " + failed);
        }
        System.out.println("AttSyncAccountRuleSmokeTest passed ✓");
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            System.out.println("  ✓ " + name);
        } else {
            failed++;
            System.out.println("  ✗ " + name);
        }
    }
}
