package com.customization.oaones.appsyn.dto;

import com.customization.oaones.appsyn.entity.AppInfo;

/**
 * 应用中心列表行（应用表 + 创建人姓名 关联结果）。
 *
 * <p>在 {@link AppInfo} 之外多带两个展示字段：创建人姓名与最后修改人姓名。</p>
 */
public class AppInfoRow extends AppInfo {

    private String creatorName;
    private String modifierName;

    public String getCreatorName() { return creatorName; }
    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }

    public String getModifierName() { return modifierName; }
    public void setModifierName(String modifierName) { this.modifierName = modifierName; }
}
