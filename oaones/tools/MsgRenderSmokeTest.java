import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.msg.common.MsgConst;
import com.customization.oaones.appsyn.msg.dto.MsgReceiver;
import com.customization.oaones.appsyn.msg.entity.MsgApp;
import com.customization.oaones.appsyn.msg.entity.MsgTypeDef;
import com.customization.oaones.appsyn.msg.service.MsgLogService;
import com.customization.oaones.appsyn.msg.service.MsgRenderService;
import com.customization.oaones.appsyn.msg.service.MsgSendService;
import com.customization.oaones.appsyn.msg.service.MsgTaskService;
import com.customization.oaones.appsyn.msg.service.MsgTypeService;
import com.weaver.base.msgcenter.channel.DirectChannel;
import com.weaver.base.msgcenter.channel.IMessageReceive;
import weaver.interfaces.schedule.BaseCronJob;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一消息中心：本地冒烟测试（不依赖数据库与企业微信）。
 *
 * <p>覆盖：5 类企微消息体组装、模板占位符替换、task_id 生成规则、
 * 可重试错误码分类、标准消息订阅实现类的可加载性与空入参安全性。</p>
 *
 * <p>执行方式（在 ecology 目录下）：</p>
 * <pre>
 * mkdir -p /tmp/oaones-smoke
 * /Users/zhengli/weaver/jdk/bin/javac -encoding UTF-8 -nowarn \
 *     -cp "classbean:WEB-INF/lib/*" -d /tmp/oaones-smoke oaones/tools/MsgRenderSmokeTest.java
 * /Users/zhengli/weaver/jdk/bin/java -cp "/tmp/oaones-smoke:classbean:WEB-INF/lib/*" MsgRenderSmokeTest
 * </pre>
 *
 * <p>注意：本类属于开发自检工具，放在 `oaones/tools/` 下、不参与 classbean 编译，
 * 也不影响线上代码。</p>
 */
public class MsgRenderSmokeTest {

    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        MsgRenderService render = new MsgRenderService();
        // 独立 JVM 里没有 Resin 的连接池，读后台系统设置里的「OA访问地址」拿不到库，
        // 这里先按“未配置 OA 访问地址”注入（后半程再注入具体地址）：5 类消息体组装、
        // 模板渲染、多语言取值都能脱离数据库自检。
        MsgRenderService.setOaBaseForTest("");
        MsgApp app = new MsgApp();
        app.setId(1);
        app.setAppName("OA待办应用");
        app.setCorpId("ww1234567890");
        app.setAgentId("1000002");
        app.setAppSecret("secret");

        MsgTypeDef def = new MsgTypeDef();
        def.setId(1);
        def.setTypeName("流程待办提醒");
        def.setStdType(20);
        def.setWxMsgtype(MsgConst.WX_TASK_CARD);
        def.setCardAction(MsgConst.CARD_CREATE);
        def.setTitleTpl("${targetName}");
        def.setContextTpl("流程「${targetName}」由 ${creater} 于 ${date} ${time} 提交，请及时处理。");
        def.setBtnJson("[{\"key\":\"agree\",\"name\":\"同意\",\"replace_name\":\"已同意\",\"color\":\"blue\",\"is_bold\":true},"
                + "{\"key\":\"reject\",\"name\":\"驳回\",\"replace_name\":\"已驳回\",\"color\":\"red\"}]");
        // 用绝对地址，避免测试环境去读系统设置里的 OA 地址（生产由 oaBase() 缓存读取补全相对地址）
        def.setUrlTpl("https://oa.example.com/workflow/request/ViewRequest.jsp?requestid=8811");

        Map<String, String> vars = new HashMap<String, String>();
        vars.put("targetName", "供应商准入申请");
        vars.put("creater", "张三");
        vars.put("date", "2026-09-15");
        vars.put("time", "10:20:30");
        vars.put("receiver", "李四");
        vars.put("params.flowCode", "SUP-001");

        List<String> receivers = new ArrayList<String>();
        receivers.add("lisi");

        // 1) 任务卡片（OA 待办）
        JSONObject taskCard = render.buildMessage(def, vars, app, receivers, "oaones_workflow_8811_lisi");
        check("任务卡片 msgtype=taskcard", "taskcard".equals(taskCard.getString("msgtype")), taskCard.toJSONString());
        check("任务卡片 agentid", "1000002".equals(taskCard.getString("agentid")), taskCard.toJSONString());
        check("任务卡片 touser", "lisi".equals(taskCard.getString("touser")), taskCard.toJSONString());
        JSONObject tc = taskCard.getJSONObject("taskcard");
        check("任务卡片 task_id", "oaones_workflow_8811_lisi".equals(tc.getString("task_id")), String.valueOf(tc));
        check("任务卡片标题替换占位符", "供应商准入申请".equals(tc.getString("title")), tc.getString("title"));
        check("任务卡片正文替换占位符",
                "流程「供应商准入申请」由 张三 于 2026-09-15 10:20:30 提交，请及时处理。".equals(tc.getString("description")),
                tc.getString("description"));
        check("任务卡片按钮数=2", tc.getJSONArray("btn") != null && tc.getJSONArray("btn").size() == 2,
                String.valueOf(tc.getJSONArray("btn")));
        check("任务卡片按钮 replace_name",
                "已同意".equals(tc.getJSONArray("btn").getJSONObject(0).getString("replace_name")),
                tc.getJSONArray("btn").toJSONString());
        check("跳转地址保留（绝对地址）",
                "https://oa.example.com/workflow/request/ViewRequest.jsp?requestid=8811".equals(tc.getString("url")),
                tc.getString("url"));
        // 相对地址：任务卡片 url 是可选项，绝不能把 "/wui/index.html" 发出去（企微 40127 不支持的uri schema）
        MsgTypeDef relDef = new MsgTypeDef();
        relDef.setId(11);
        relDef.setWxMsgtype(MsgConst.WX_TASK_CARD);
        relDef.setCardAction(MsgConst.CARD_CREATE);
        relDef.setTitleTpl("${targetName}");
        relDef.setContextTpl("${context}");
        relDef.setUrlTpl("/wui/index.html");
        JSONObject relBody = render.buildMessage(relDef, vars, app, receivers, "oaones_custom_TEST_1_lisi");
        check("相对跳转地址不下发给企微（任务卡片去掉 url）",
                !relBody.getJSONObject("taskcard").containsKey("url"), String.valueOf(relBody.getJSONObject("taskcard")));
        check("isAbsoluteUrl 判定", MsgRenderService.isAbsoluteUrl("https://oa.example.com/x")
                && MsgRenderService.isAbsoluteUrl("http://oa.example.com")
                && !MsgRenderService.isAbsoluteUrl("/wui/index.html")
                && !MsgRenderService.isAbsoluteUrl(""), "isAbsoluteUrl");

        // 2) 文本卡片
        def.setWxMsgtype(MsgConst.WX_TEXTCARD);
        def.setCardAction(MsgConst.CARD_NONE);
        def.setTitleTpl("流程抄送：${targetName}");
        JSONObject textCard = render.buildMessage(def, vars, app, receivers, null);
        check("文本卡片 msgtype=textcard", "textcard".equals(textCard.getString("msgtype")), textCard.toJSONString());
        check("文本卡片标题", "流程抄送：供应商准入申请".equals(textCard.getJSONObject("textcard").getString("title")),
                textCard.getJSONObject("textcard").toJSONString());

        // 3) 文本
        def.setWxMsgtype(MsgConst.WX_TEXT);
        JSONObject text = render.buildMessage(def, vars, app, receivers, null);
        check("文本消息 msgtype=text", "text".equals(text.getString("msgtype")), text.toJSONString());
        check("文本消息内容含标题与正文",
                text.getJSONObject("text").getString("content").contains("流程抄送：供应商准入申请"),
                text.getJSONObject("text").toJSONString());

        // 4) 图文
        def.setWxMsgtype(MsgConst.WX_NEWS);
        def.setCoverUrl("https://oa.example.com/cover.png");
        JSONObject news = render.buildMessage(def, vars, app, receivers, null);
        check("图文消息 msgtype=news", "news".equals(news.getString("msgtype")), news.toJSONString());
        check("图文消息文章数=1", news.getJSONObject("news").getJSONArray("articles").size() == 1, news.toJSONString());

        // 5) 模板卡片
        def.setWxMsgtype(MsgConst.WX_TEMPLATE_CARD);
        JSONObject tpl = render.buildMessage(def, vars, app, receivers, null);
        check("模板卡片 msgtype=template_card", "template_card".equals(tpl.getString("msgtype")), tpl.toJSONString());
        check("模板卡片 card_type=text_notice",
                "text_notice".equals(tpl.getJSONObject("template_card").getString("card_type")), tpl.toJSONString());

        // 6) 未知占位符清理
        String rendered = render.render("A${title}B${notExist}C", vars);
        check("未知占位符被清空", "ABC".equals(rendered), rendered);

        // 7) task_id 生成规则
        MsgTaskService taskService = new MsgTaskService();
        String taskId = taskService.buildTaskId("workflow", "8811", "lisi");
        check("task_id 规则", "oaones_workflow_8811_lisi".equals(taskId), taskId);
        String longTaskId = taskService.buildTaskId("workflow", repeat("9", 200), repeat("u", 200));
        check("超长 task_id 截断在 128 字节内",
                longTaskId.getBytes("UTF-8").length <= 128 && longTaskId.length() <= 128,
                String.valueOf(longTaskId.length()));
        // 业务单号/账号带邮箱（含点号）时，企微只允许 数字/字母/_-@，必须替换掉否则报 42014
        String mailTaskId = taskService.buildTaskId("workflow", "20260915_abc123xyz",
                "zhengli.wang@ghedu.com");
        check("邮箱型账号生成的 task_id 只含企微允许字符",
                MsgConst.isWxTokenSafe(mailTaskId) && mailTaskId.indexOf('.') < 0
                        && mailTaskId.getBytes("UTF-8").length <= 128, mailTaskId);
        check("邮箱型账号生成的 task_id 稳定可复现",
                mailTaskId.equals(taskService.buildTaskId("workflow", "20260915_abc123xyz",
                        "zhengli.wang@ghedu.com")), mailTaskId);
        check("替换后不同业务单号不会归一成同一个 task_id",
                !mailTaskId.equals(taskService.buildTaskId("workflow", "20260915/abc123xyz",
                        "zhengli.wang@ghedu.com")), "点号与斜杠必须区分");
        check("中文业务单号也能生成合法 task_id",
                MsgConst.isWxTokenSafe(taskService.buildTaskId("workflow", "流程标题（测试）", "lisi")),
                taskService.buildTaskId("workflow", "流程标题（测试）", "lisi"));
        // 现场实际失败报文（2026-09-15 待办测试推送）：bizType=custom / bizId 带测试流水 / 账号是邮箱
        String realTaskId = taskService.buildTaskId("custom", "task_20260915_abc123xyz",
                "zhengli.wang@ghedu.com");
        System.out.println("         历史报文 task_id: oaones_custom_task_20260915_abc123xyz_zhengli.wang@ghedu.com"
                + " (企微 42014 拒收)");
        System.out.println("         修正后 task_id  : " + realTaskId);
        check("现场失败报文修正后 task_id 合法",
                MsgConst.isWxTokenSafe(realTaskId) && realTaskId.indexOf('.') < 0
                        && realTaskId.getBytes("UTF-8").length <= 128, realTaskId);
        check("历史非法 task_id 会被识别为需要修正",
                !MsgConst.isWxTokenSafe("oaones_custom_task_20260915_abc123xyz_zhengli.wang@ghedu.com"),
                "含点号");
        check("safeWxToken 保留 _-@ 并替换其它字符",
                "a_b-c@d".equals(MsgConst.safeWxToken("a.b-c@d")), MsgConst.safeWxToken("a.b-c@d"));

        // 7.1) 卡片按钮 key / agentid 归一（企微要求 key 只含 数字/字母/_-@、agentid 为整型）
        MsgTypeDef btnDef = new MsgTypeDef();
        btnDef.setId(9);
        btnDef.setWxMsgtype(MsgConst.WX_TASK_CARD);
        btnDef.setTitleTpl("标题");
        btnDef.setContextTpl("内容");
        btnDef.setUrlTpl("https://oa.example.com/wui/index.html");
        btnDef.setBtnJson("[{\"key\":\"agree.now\",\"name\":\"同意\"},{\"key\":\"\",\"name\":\"驳回\"}]");
        JSONObject btnBody = render.buildMessage(btnDef, vars, app, receivers, "oaones_workflow_1_lisi");
        JSONObject taskcard = btnBody.getJSONObject("taskcard");
        check("按钮 key 非法字符被替换", "agree_now".equals(taskcard.getJSONArray("btn")
                .getJSONObject(0).getString("key")), String.valueOf(taskcard));
        check("按钮 key 为空时补默认值", "key2".equals(taskcard.getJSONArray("btn")
                .getJSONObject(1).getString("key")), String.valueOf(taskcard));
        check("agentid 按整型下发", Integer.valueOf(1000002).equals(render.agentIdValue(app)),
                String.valueOf(render.agentIdValue(app)));
        MsgApp nonNumApp = new MsgApp();
        nonNumApp.setAgentId("ww-agent");
        check("agentid 非数字时按原样下发", "ww-agent".equals(render.agentIdValue(nonNumApp)),
                String.valueOf(render.agentIdValue(nonNumApp)));

        // 7.2) 常见错误码运维提示
        check("42014 有运维提示", MsgConst.errorHint(42014).length() > 0, MsgConst.errorHint(42014));
        check("42018 有运维提示", MsgConst.errorHint(42018).length() > 0, MsgConst.errorHint(42018));
        check("未知错误码无提示", MsgConst.errorHint(-8888).length() == 0, "无提示");

        // 7.3) 业务单号推送流水：标准消息 业务ID + "-" + 12 位uuid（同一业务可重复推送）
        String flowBizId = MsgConst.withBizSeq("8811");
        check("标准消息业务单号 = 业务ID + '-' + 12位uuid",
                flowBizId.matches("8811-[0-9a-f]{" + MsgConst.BIZ_SEQ_LEN + "}"), flowBizId);
        check("同一业务两次推送的业务单号不同（不被去重窗口拦掉）",
                !flowBizId.equals(MsgConst.withBizSeq("8811")), flowBizId);
        String meetingBizId = MsgConst.withBizSeq("1234");
        check("会议等其它标准消息同样追加流水",
                meetingBizId.matches("1234-[0-9a-f]{" + MsgConst.BIZ_SEQ_LEN + "}")
                        && "1234".equals(MsgConst.baseBizId(meetingBizId)), meetingBizId);
        check("业务单号去掉流水得到稳定业务键（requestid）",
                "8811".equals(MsgConst.baseBizId(flowBizId)), MsgConst.baseBizId(flowBizId));
        check("历史业务单号（无流水）原样作为稳定业务键",
                "20260915_abc123xyz".equals(MsgConst.baseBizId("20260915_abc123xyz")),
                MsgConst.baseBizId("20260915_abc123xyz"));
        check("尾部不是 12 位十六进制时不当成流水去掉",
                "8811-abcdefghijkl".equals(MsgConst.baseBizId("8811-abcdefghijkl")),
                MsgConst.baseBizId("8811-abcdefghijkl"));
        check("空业务单号不追加流水",
                "".equals(MsgConst.withBizSeq(null)) && "".equals(MsgConst.withBizSeq("  ")),
                "[" + MsgConst.withBizSeq(null) + "]");
        MsgSendService.MsgPushRequest flowRequest = new MsgSendService.MsgPushRequest();
        flowRequest.setBizId(flowBizId);
        check("未显式给稳定业务键时按业务单号去掉流水回退",
                "8811".equals(flowRequest.getBizKey()), flowRequest.getBizKey());
        flowRequest.setBizKey("8811");
        check("显式给了稳定业务键时优先用它",
                "8811".equals(flowRequest.getBizKey()), flowRequest.getBizKey());

        // 7.4) 跳转地址：模板留空时用标准消息自带链接（流程即流程详情页），
        //      相对路径按后台系统设置里的「OA访问地址」补成完整网址（这里注入模拟已配置）
        MsgRenderService.setOaBaseForTest("https://oa.example.com");
        MsgApp linkApp = new MsgApp();
        linkApp.setAgentId("1000002");
        Map<String, String> linkVars = new HashMap<String, String>();
        linkVars.put("title", "流程待办");
        linkVars.put("context", "请及时处理");
        linkVars.put("linkUrl", "/workflow/request/ViewRequestForwardSPA.jsp?requestid=8811");
        String expectFlowUrl = "https://oa.example.com/workflow/request/ViewRequestForwardSPA.jsp?requestid=8811";

        MsgTypeDef linkCardDef = new MsgTypeDef();
        linkCardDef.setId(11);
        linkCardDef.setWxMsgtype(MsgConst.WX_TEXTCARD);
        linkCardDef.setTitleTpl("${title}");
        linkCardDef.setContextTpl("${context}");
        JSONObject linkCardBody = render.buildMessage(linkCardDef, linkVars, linkApp, receivers, null);
        check("跳转地址模板留空时用标准消息自带链接并补成完整网址",
                expectFlowUrl.equals(linkCardBody.getJSONObject("textcard").getString("url")),
                String.valueOf(linkCardBody.getJSONObject("textcard").getString("url")));

        MsgTypeDef linkTextDef = new MsgTypeDef();
        linkTextDef.setId(12);
        linkTextDef.setWxMsgtype(MsgConst.WX_TEXT);
        linkTextDef.setTitleTpl("${title}");
        linkTextDef.setContextTpl("${context}");
        JSONObject linkTextBody = render.buildMessage(linkTextDef, linkVars, linkApp, receivers, null);
        String linkTextContent = linkTextBody.getJSONObject("text").getString("content");
        check("文本消息把跳转地址拼进正文（企业微信客户端可点）",
                linkTextContent.indexOf(expectFlowUrl) > 0, linkTextContent);
        check("文本消息正文里已有该网址时不重复拼接",
                linkTextContent.indexOf(expectFlowUrl) == linkTextContent.lastIndexOf(expectFlowUrl),
                linkTextContent);
        MsgTypeDef linkTextTplDef = new MsgTypeDef();
        linkTextTplDef.setId(13);
        linkTextTplDef.setWxMsgtype(MsgConst.WX_TEXT);
        linkTextTplDef.setTitleTpl("${title}");
        linkTextTplDef.setContextTpl("${context}\n详情：${linkUrl}");
        String tplTextContent = render.buildMessage(linkTextTplDef, linkVars, linkApp, receivers, null)
                .getJSONObject("text").getString("content");
        check("正文模板已经写了链接时不重复拼接",
                tplTextContent.indexOf(expectFlowUrl) == tplTextContent.lastIndexOf(expectFlowUrl),
                tplTextContent);
        Map<String, String> junkVars = new HashMap<String, String>();
        junkVars.put("title", "流程待办");
        junkVars.put("linkUrl", "12345");
        String junkContent = render.buildMessage(linkTextDef, junkVars, linkApp, receivers, null)
                .getJSONObject("text").getString("content");
        check("不像链接的值不会被当成跳转地址拼进正文",
                junkContent.indexOf("12345") < 0, junkContent);
        String junkCardUrl = render.buildMessage(linkTextDef, junkVars, linkApp, receivers, null)
                .getJSONObject("text").getString("content");
        check("没有业务链接时文本消息只发标题与正文",
                junkCardUrl.indexOf("详情：") < 0, junkCardUrl);

        // 7.5) 推送日志的“跳转地址”解析（页面直接展示成超链接）
        check("文本报文里的跳转地址可解析",
                expectFlowUrl.equals(MsgLogService.jumpUrl(linkTextBody.toJSONString())),
                MsgLogService.jumpUrl(linkTextBody.toJSONString()));
        check("文本卡片报文的跳转地址可解析",
                "https://oa.example.com/a".equals(MsgLogService.jumpUrl(
                        "{\"msgtype\":\"textcard\",\"textcard\":{\"url\":\"https://oa.example.com/a\"}}")), "");
        check("模板卡片报文的跳转地址可解析",
                "https://oa.example.com/b".equals(MsgLogService.jumpUrl(
                        "{\"msgtype\":\"template_card\",\"template_card\":{\"card_action\":"
                                + "{\"url\":\"https://oa.example.com/b\"}}}")), "");
        check("任务卡片报文的跳转地址可解析",
                "https://oa.example.com/c".equals(MsgLogService.jumpUrl(
                        "{\"msgtype\":\"taskcard\",\"taskcard\":{\"url\":\"https://oa.example.com/c\"}}")), "");
        check("报文里没有链接时返回空",
                "".equals(MsgLogService.jumpUrl("{\"msgtype\":\"text\",\"text\":{\"content\":\"没有链接\"}}")), "");
        check("报文为空或损坏时不抛异常",
                "".equals(MsgLogService.jumpUrl(null)) && "".equals(MsgLogService.jumpUrl("{oops")), "");

        // 7.6) 任务卡片 task_id：企微要求发送时唯一，同一业务重复推送必须换新 task_id
        String pushOne = taskService.buildTaskId("workflow",
                MsgConst.withBizSeq("1993997"), "zhengli.wang@ghedu.com");
        String pushTwo = taskService.buildTaskId("workflow",
                MsgConst.withBizSeq("1993997"), "zhengli.wang@ghedu.com");
        check("同一流程重复推送生成不同的 task_id（避免企微 42014 taskid has existed）",
                !pushOne.equals(pushTwo), pushOne + " / " + pushTwo);
        check("带推送流水的 task_id 仍满足企微字符与长度限制",
                MsgConst.isWxTokenSafe(pushOne) && pushOne.getBytes("UTF-8").length <= 128, pushOne);
        String renewed = taskService.renewTaskId("workflow", MsgConst.withBizSeq("1993997"),
                "zhengli.wang@ghedu.com", pushOne);
        check("重推换新 task_id 可用且与原值不同",
                !renewed.equals(pushOne) && MsgConst.isWxTokenSafe(renewed)
                        && renewed.getBytes("UTF-8").length <= 128, renewed);

        // 7.7) 跳转地址包一层企微免登录入口（企业微信里直接打开站内页面会“登录超时”）
        // 链接前缀只取后台系统设置里的「OA访问地址」，这里注入一个地址模拟已配置
        MsgRenderService.setOaBaseForTest("https://oatest.ghedu.com");
        MsgApp entryApp = new MsgApp();
        entryApp.setId(7);
        entryApp.setCfgId(2);
        entryApp.setAgentId("1000069");
        entryApp.setOaPageUrl("https://oatest.ghedu.com/oaones/appBind/WeCom/appEntry.jsp");
        Map<String, String> entryVars = new HashMap<String, String>();
        entryVars.put("title", "流程待办");
        entryVars.put("context", "请及时处理");
        entryVars.put("linkUrl", "/common/chatResource/view.html?resourcetype=0&resourceid=1993997&isFormMsg=1");
        MsgTypeDef entryCardDef = new MsgTypeDef();
        entryCardDef.setId(21);
        entryCardDef.setWxMsgtype(MsgConst.WX_TASK_CARD);
        entryCardDef.setTitleTpl("${title}");
        entryCardDef.setContextTpl("${context}");
        String entryUrl = render.buildMessage(entryCardDef, entryVars, entryApp, receivers, "oaones_workflow_1_lisi")
                .getJSONObject("taskcard").getString("url");
        check("站内跳转地址被换成企微免登录入口",
                entryUrl.startsWith("https://oatest.ghedu.com/oaones/appBind/WeCom/appEntry.jsp?")
                        && entryUrl.indexOf("b=7") > 0 && entryUrl.indexOf("cfg_id=2") > 0
                        && entryUrl.indexOf("agent_id=1000069") > 0 && entryUrl.indexOf("state=") > 0,
                entryUrl);
        String entryState = "";
        int stateAt = entryUrl.indexOf("state=");
        if (stateAt > 0) {
            entryState = entryUrl.substring(stateAt + "state=".length());
            int amp = entryState.indexOf('&');
            if (amp >= 0) { entryState = entryState.substring(0, amp); }
        }
        String entryTarget = "";
        if (entryState.startsWith("/")) {
            entryTarget = entryState;
        }
        if (entryTarget.length() == 0 && entryState.indexOf('%') >= 0) {
            try {
                String decoded = java.net.URLDecoder.decode(entryState, "UTF-8");
                entryTarget = decoded.startsWith("/") ? decoded : "";
            } catch (Exception ignore) {
                entryTarget = "";
            }
        }
        if (entryTarget.length() == 0) {
            try {
                String decoded = new String(java.util.Base64.getUrlDecoder().decode(entryState), "UTF-8");
                entryTarget = decoded.startsWith("/") ? decoded : "";
            } catch (Exception ignore) {
                entryTarget = "";
            }
        }
        check("免登录入口 state 能还原出原来的业务页面",
                "/common/chatResource/view.html?resourcetype=0&resourceid=1993997&isFormMsg=1"
                        .equals(entryTarget), entryTarget);
        String external = render.resolveUrl("https://www.example.com/x", entryVars, null, entryApp);
        check("外部链接不会被包进免登录入口",
                "https://www.example.com/x".equals(external), external);

        // 8) 错误码分类（可重试 / 不可重试 / token 失效）
        check("45009 限流可重试", MsgConst.isRetryable(45009), "45009");
        check("-1 系统繁忙可重试", MsgConst.isRetryable(-1), "-1");
        check("81013 用户不存在不重试", !MsgConst.isRetryable(81013), "81013");
        check("60011 无权限不重试", !MsgConst.isRetryable(60011), "60011");
        check("42001 判定为 token 失效", MsgConst.isTokenError(42001), "42001");
        check("卡片更新动作判定", MsgConst.isCardUpdateAction(MsgConst.CARD_DONE)
                && MsgConst.isCardUpdateAction(MsgConst.CARD_INVALID)
                && !MsgConst.isCardUpdateAction(MsgConst.CARD_NONE), "card action");

        // 9) 外部入参构造模板变量
        Map<String, String> receiverVars = render.variablesFromParams(single("context", "会议提醒内容"), receiver());
        check("外部入参可构造模板变量",
                "会议提醒内容".equals(receiverVars.get("context")) && "李四".equals(receiverVars.get("receiver")),
                receiverVars.toString());

        // 10) 时间字段拆分（日志时间用建模标准字段：日期 + 时间两列）
        String[] full = MsgLogService.splitDateTime("2026-09-15 10:20:30", "00:00:00");
        check("完整时间拆分", "2026-09-15".equals(full[0]) && "10:20:30".equals(full[1]),
                full[0] + "|" + full[1]);
        String[] dateOnly = MsgLogService.splitDateTime("2026-09-15", "23:59:59");
        check("只填日期按默认时间补齐", "2026-09-15".equals(dateOnly[0]) && "23:59:59".equals(dateOnly[1]),
                dateOnly[0] + "|" + dateOnly[1]);
        String[] empty = MsgLogService.splitDateTime("  ", "00:00:00");
        check("空时间表示不限制", empty[0] == null && empty[1] == null, String.valueOf(empty[0]));
        String[] shortTime = MsgLogService.splitDateTime("2026-09-15 10:20", "00:00:00");
        check("时间位数不足按左取", "10:20".equals(shortTime[1]), shortTime[1]);

        // 11) 标准消息订阅实现类与计划任务类可加载（企业微信/消息中心集成前提）
        Class<?> subClass = Class.forName("com.customization.oaones.appsyn.msg.sub.QywxMsgReceiveSub");
        IMessageReceive sub = (IMessageReceive) subClass.newInstance();
        check("推送实现类可实例化且实现 IMessageReceive", sub != null, subClass.getName());
        check("DirectChannel 可实例化", new DirectChannel() != null, "DirectChannel");
        sub.onMessage(null);
        sub.onMessage("");
        sub.onMessage("[]");
        check("空消息安全返回", true, "onMessage(null/\"\" /\"[]\")");
        check("重试任务类是 BaseCronJob",
                Class.forName("com.customization.oaones.appsyn.msg.job.OaonesMsgRetryCronJob").newInstance()
                        instanceof BaseCronJob, "OaonesMsgRetryCronJob");
        check("清理任务类是 BaseCronJob",
                Class.forName("com.customization.oaones.appsyn.msg.job.OaonesMsgCleanCronJob").newInstance()
                        instanceof BaseCronJob, "OaonesMsgCleanCronJob");
        // 11.1) 标准消息类型的内置名称清单（不触库：标准表读不到时展示 / 自动登记就靠它兜底）
        check("消息类型服务可实例化", new MsgTypeService() != null, "MsgTypeService");
        check("内置标准类型名称：20 新到达流程",
                "新到达流程".equals(MsgTypeService.builtinTypeName(20)),
                MsgTypeService.builtinTypeName(20));
        check("内置标准类型名称：27 流程删除",
                "流程删除".equals(MsgTypeService.builtinTypeName(27)),
                MsgTypeService.builtinTypeName(27));
        check("内置标准类型名称：63 新到达任务",
                "新到达任务".equals(MsgTypeService.builtinTypeName(63)),
                MsgTypeService.builtinTypeName(63));
        check("内置标准类型名称：未收录编号有兜底文案",
                MsgTypeService.builtinTypeName(9999).startsWith("消息类型"),
                MsgTypeService.builtinTypeName(9999));

        // 12) OA 访问地址自动取值：读后台系统设置（wechat_Set）失败也不能拖住推送
        long begin = System.currentTimeMillis();
        JSONObject oaInfo = new MsgRenderService().oaBaseInfo();
        long cost = System.currentTimeMillis() - begin;
        check("oaBaseInfo 自动取值不阻塞（<8 秒）", cost < 8000,
                cost + "ms base=" + oaInfo.getString("base") + " from=" + oaInfo.getString("from"));

        // 13) 日志列长度兜底（表列建成 nvarchar(n) 时按档截断，不能整条日志都写不进去）
        String longJson = "{\"bizTitle\":\"" + repeat("x", 20000) + "\"}";
        check("日志超长内容按档截断", MsgLogService.limit(longJson, 2000).length() == 2000,
                String.valueOf(MsgLogService.limit(longJson, 2000).length()));
        check("日志内容未超长时不截断", "abc".equals(MsgLogService.limit("abc", 2000)),
                MsgLogService.limit("abc", 2000));
        check("日志内容为 null 安全", MsgLogService.limit(null, 2000) == null, "null");

        // 14) 多语言取值：E9 多语言标记（~`~`7 中文`~`8 English`~`9 繁體`~`~）按接收人语言取成纯文本
        String multi = "~`~`7 系统管理员`~`8 system administrator`~`9 系統管理員`~`~";
        check("多语言取值-简体(7)", "系统管理员".equals(render.multiLang(multi, 7)), render.multiLang(multi, 7));
        check("多语言取值-英文(8)", "system administrator".equals(render.multiLang(multi, 8)),
                render.multiLang(multi, 8));
        check("多语言取值-繁体(9)", "系統管理員".equals(render.multiLang(multi, 9)), render.multiLang(multi, 9));
        check("多语言取值-未配该语言时回落到简体",
                "系统管理员".equals(render.multiLang(multi, 11)), render.multiLang(multi, 11));
        check("不含多语言标记的文本原样返回", "普通文本".equals(render.multiLang("普通文本", 7)),
                render.multiLang("普通文本", 7));
        check("系统默认语言可读", render.defaultLanguage() > 0, String.valueOf(render.defaultLanguage()));
        // 真实形态：多语言标记嵌在流程提醒正文中间（流程名 / 提交人姓名是 OA 多语言值）
        Map<String, String> multiVars = new HashMap<String, String>();
        multiVars.put("targetName", "测试3-系统管理员-2026-09-15");
        multiVars.put("context", "流程「测试3-系统管理员-2026-09-15」由" + multi + "于2026-09-17 11:39:34提交，请及时处理。");
        MsgTypeDef multiDef = new MsgTypeDef();
        multiDef.setId(14);
        multiDef.setTypeName("流程待办提醒");
        multiDef.setWxMsgtype(MsgConst.WX_TASK_CARD);
        multiDef.setCardAction(MsgConst.CARD_CREATE);
        multiDef.setTitleTpl("${targetName}");
        multiDef.setContextTpl("${context}");
        multiDef.setUrlTpl("https://oa.example.com/workflow/request/ViewRequest.jsp?requestid=8811");
        String zhDesc = render.buildMessage(multiDef, multiVars, app, receivers,
                "oaones_workflow_8811_lisi", 7).getJSONObject("taskcard").getString("description");
        check("任务卡片正文按简体取值（企微报文里不再有多语言标记）",
                zhDesc.indexOf("~`~`") < 0 && zhDesc.contains("由系统管理员于"), zhDesc);
        String enDesc = render.buildMessage(multiDef, multiVars, app, receivers,
                "oaones_workflow_8811_lisi", 8).getJSONObject("taskcard").getString("description");
        check("任务卡片正文按英文取值",
                enDesc.indexOf("~`~`") < 0 && enDesc.contains("system administrator"), enDesc);
        String textDesc = render.buildMessage(multiDef, multiVars, app, receivers, null)
                .getJSONObject("taskcard").getString("description");
        check("未传语言时按系统默认语言取值", textDesc.indexOf("~`~`") < 0, textDesc);

        System.out.println();
        if (failed == 0) {
            System.out.println("冒烟测试全部通过 ✔（共 " + passed + " 项）");
        } else {
            System.out.println("冒烟测试失败 " + failed + " 项，通过 " + passed + " 项");
        }
        // 明确退出：读“系统设置 OA 地址”的超时线程可能拖着 JDBC 的非守护线程不退出
        System.exit(failed == 0 ? 0 : 1);
    }

    private static int passed = 0;

    private static MsgReceiver receiver() {
        MsgReceiver r = new MsgReceiver();
        r.setOaUserId(101);
        r.setOaName("李四");
        r.setWxUserid("lisi");
        return r;
    }

    private static Map<String, Object> single(String k, Object v) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(k, v);
        return map;
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.println("  [OK]   " + name);
        } else {
            failed++;
            System.out.println("  [FAIL] " + name + "  -> " + detail);
        }
    }
}
