package com.chattriggers.ctjs.internal;

import net.minecraft.text.Text;

import java.util.List;

public interface TooltipOverridable {
    void ctjs_setTooltip(List<Text> tooltip);
    void ctjs_setShouldOverrideTooltip(boolean shouldOverrideTooltip);
}
