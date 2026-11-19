package uk.steers.nunjucks.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.parser.GeneratedParserUtilBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

import static uk.steers.nunjucks.psi.NunjucksTypes.*;

public class NunjucksParserUtil extends GeneratedParserUtilBase {
  private static final TokenSet TAG_BOUNDARIES =
      TokenSet.create(BLOCK_START, VAR_START, VAR_END, TEMPLATE_TEXT, COMMENT);

  public static boolean softAs(PsiBuilder b, int level) { return softKeyword(b, AS); }
  public static boolean softWith(PsiBuilder b, int level) { return softKeyword(b, WITH); }
  public static boolean softWithout(PsiBuilder b, int level) { return softKeyword(b, WITHOUT); }
  public static boolean softContext(PsiBuilder b, int level) { return softKeyword(b, CONTEXT); }
  public static boolean softIgnore(PsiBuilder b, int level) { return softKeyword(b, IGNORE); }
  public static boolean softMissing(PsiBuilder b, int level) { return softKeyword(b, MISSING); }
  public static boolean softImport(PsiBuilder b, int level) { return softKeyword(b, IMPORT); }

  /** Matches an IDENTIFIER whose text is the soft keyword's text, remapping it to that keyword. */
  private static boolean softKeyword(PsiBuilder b, IElementType keyword) {
    String text = keyword.toString();
    if (b.getTokenType() == keyword
        || (b.getTokenType() == IDENTIFIER && text.equals(b.getTokenText()))) {
      b.remapCurrentToken(keyword);
      b.advanceLexer();
      return true;
    }
    addVariant(b, text);
    return false;
  }

  /** Consumes the arguments of an extension tag, up to its closing %}. */
  public static boolean skipTagContent(PsiBuilder b, int level) {
    while (!b.eof() && b.getTokenType() != BLOCK_END && !TAG_BOUNDARIES.contains(b.getTokenType())) {
      b.advanceLexer();
    }
    return true;
  }

  public static boolean unexpectedToken(PsiBuilder b, int level) {
    if (b.eof()) return false;
    String text = b.getTokenText();
    PsiBuilder.Marker marker = b.mark();
    b.advanceLexer();
    marker.error("Unexpected '" + text + "'");
    return true;
  }
}
