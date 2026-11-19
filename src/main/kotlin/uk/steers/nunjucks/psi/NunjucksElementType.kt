package uk.steers.nunjucks.psi

import com.intellij.psi.tree.IElementType
import uk.steers.nunjucks.NunjucksLanguage

class NunjucksElementType(debugName: String) : IElementType(debugName, NunjucksLanguage)

class NunjucksTokenType(debugName: String) : IElementType(debugName, NunjucksLanguage)
