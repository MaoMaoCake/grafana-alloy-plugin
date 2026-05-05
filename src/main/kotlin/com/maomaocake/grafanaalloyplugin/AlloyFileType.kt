package com.maomaocake.grafanaalloyplugin

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object AlloyFileType : LanguageFileType(AlloyLanguage) {
    override fun getName(): String = "Alloy"
    override fun getDescription(): String = "Grafana Alloy configuration"
    override fun getDefaultExtension(): String = "alloy"
    override fun getIcon(): Icon? = AlloyIcons.FILE
}
