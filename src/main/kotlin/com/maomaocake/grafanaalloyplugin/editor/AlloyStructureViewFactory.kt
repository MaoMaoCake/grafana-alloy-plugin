package com.maomaocake.grafanaalloyplugin.editor

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.maomaocake.grafanaalloyplugin.AlloyIcons
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlock
import com.maomaocake.grafanaalloyplugin.psi.AlloyFile
import com.maomaocake.grafanaalloyplugin.psi.AlloyPsiUtil

class AlloyStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? {
        if (psiFile !is AlloyFile) return null
        return object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel =
                AlloyStructureViewModel(psiFile, editor)
        }
    }
}

private class AlloyStructureViewModel(file: AlloyFile, editor: Editor?) :
    StructureViewModelBase(file, editor, AlloyFileTreeElement(file)),
    StructureViewModel.ElementInfoProvider {

    init { withSorters(Sorter.ALPHA_SORTER) }

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean = element is AlloyFileTreeElement
    override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean = false
    override fun getSuitableClasses(): Array<Class<*>> = arrayOf(AlloyBlock::class.java)
}

private class AlloyFileTreeElement(private val file: AlloyFile) : StructureViewTreeElement, SortableTreeElement {
    override fun getValue(): Any = file
    override fun getPresentation(): ItemPresentation =
        PresentationData(file.name, null, AlloyIcons.FILE, null)

    override fun getChildren(): Array<StructureViewTreeElement> =
        file.children.filterIsInstance<com.maomaocake.grafanaalloyplugin.psi.AlloyStatement>()
            .mapNotNull { PsiTreeUtil.findChildOfType(it, AlloyBlock::class.java) }
            .map { AlloyBlockTreeElement(it) }
            .toTypedArray()

    override fun getAlphaSortKey(): String = file.name
    override fun navigate(requestFocus: Boolean) = file.navigate(requestFocus)
    override fun canNavigate(): Boolean = file.canNavigate()
    override fun canNavigateToSource(): Boolean = file.canNavigateToSource()
}

private class AlloyBlockTreeElement(private val block: AlloyBlock) : StructureViewTreeElement, SortableTreeElement {
    override fun getValue(): Any = block

    override fun getPresentation(): ItemPresentation {
        val nameIdents = AlloyPsiUtil.blockNameIdents(block.blockName)
        val label = block.blockLabel?.let { AlloyPsiUtil.unquoteLabel(it) }
        val main = nameIdents.joinToString(".")
        val trailer = if (label != null) "\"$label\"" else null
        return PresentationData(main, trailer, null, null)
    }

    override fun getChildren(): Array<StructureViewTreeElement> {
        val body = block.blockBody
        val nested = body.children.filterIsInstance<com.maomaocake.grafanaalloyplugin.psi.AlloyStatement>()
            .mapNotNull { PsiTreeUtil.findChildOfType(it, AlloyBlock::class.java) }
        return nested.map { AlloyBlockTreeElement(it) }.toTypedArray()
    }

    override fun getAlphaSortKey(): String {
        val nameIdents = AlloyPsiUtil.blockNameIdents(block.blockName)
        val label = block.blockLabel?.let { AlloyPsiUtil.unquoteLabel(it) } ?: ""
        return nameIdents.joinToString(".") + " " + label
    }

    override fun navigate(requestFocus: Boolean) {
        val nav = block as? PsiElement
        if (nav is com.intellij.pom.Navigatable && nav.canNavigate()) nav.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = (block as? com.intellij.pom.Navigatable)?.canNavigate() ?: false
    override fun canNavigateToSource(): Boolean = (block as? com.intellij.pom.Navigatable)?.canNavigateToSource() ?: false
}
