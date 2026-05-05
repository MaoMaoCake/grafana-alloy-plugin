package com.maomaocake.grafanaalloyplugin.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.maomaocake.grafanaalloyplugin.AlloyFileType
import com.maomaocake.grafanaalloyplugin.AlloyLanguage

class AlloyFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, AlloyLanguage) {
    override fun getFileType(): FileType = AlloyFileType
    override fun toString(): String = "Alloy File"
}
