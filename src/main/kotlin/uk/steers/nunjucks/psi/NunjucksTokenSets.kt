package uk.steers.nunjucks.psi

import com.intellij.psi.tree.TokenSet
import uk.steers.nunjucks.psi.NunjucksTypes.*

object NunjucksTokenSets {
    @JvmField val COMMENTS = TokenSet.create(COMMENT)
    @JvmField val STRINGS = TokenSet.create(STRING)

    @JvmField val TAG_KEYWORDS = TokenSet.create(
        IF, ELIF, ELSEIF, ELSE, ENDIF, IFASYNC, FOR, ENDFOR, ASYNCEACH, ENDEACH, ASYNCALL, ENDALL,
        SET, ENDSET, MACRO, ENDMACRO, CALL, ENDCALL, FILTER, ENDFILTER, BLOCK, ENDBLOCK,
        EXTENDS, INCLUDE, IMPORT, FROM, RAW, ENDRAW, VERBATIM, ENDVERBATIM,
    )

    @JvmField val SOFT_KEYWORDS = TokenSet.create(AS, WITH, WITHOUT, CONTEXT, IGNORE, MISSING)

    @JvmField val OPERATOR_KEYWORDS = TokenSet.create(AND, OR, NOT, IN, IS)

    @JvmField val CONSTANTS = TokenSet.create(TRUE, FALSE, NONE, NULL)

    @JvmField val KEYWORDS = TokenSet.orSet(TAG_KEYWORDS, SOFT_KEYWORDS, OPERATOR_KEYWORDS)

    @JvmField val DELIMITERS = TokenSet.create(BLOCK_START, BLOCK_END, VAR_START, VAR_END)

    @JvmField val OPERATORS = TokenSet.create(
        EQEQEQ, NEQEQ, EQEQ, NEQ, LE, GE, LT, GT, POW, FLOORDIV, PLUS, MINUS, MUL, DIV, MOD, TILDE, ASSIGN, PIPE,
    )

    @JvmField val END_KEYWORDS = TokenSet.create(
        ENDIF, ENDFOR, ENDEACH, ENDALL, ENDSET, ENDMACRO, ENDCALL, ENDFILTER, ENDBLOCK, ENDRAW, ENDVERBATIM,
    )
}
