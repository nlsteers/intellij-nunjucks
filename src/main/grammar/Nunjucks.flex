package uk.steers.nunjucks.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static uk.steers.nunjucks.psi.NunjucksTypes.*;

%%

%class _NunjucksLexer
%implements FlexLexer
%public
%unicode
%function advance
%type IElementType

%{
  private static IElementType tagKeyword(CharSequence text) {
    switch (text.toString()) {
      case "if": return IF;
      case "elif": return ELIF;
      case "elseif": return ELSEIF;
      case "else": return ELSE;
      case "endif": return ENDIF;
      case "ifAsync": return IFASYNC;
      case "for": return FOR;
      case "endfor": return ENDFOR;
      case "asyncEach": return ASYNCEACH;
      case "endeach": return ENDEACH;
      case "asyncAll": return ASYNCALL;
      case "endall": return ENDALL;
      case "set": return SET;
      case "endset": return ENDSET;
      case "macro": return MACRO;
      case "endmacro": return ENDMACRO;
      case "call": return CALL;
      case "endcall": return ENDCALL;
      case "filter": return FILTER;
      case "endfilter": return ENDFILTER;
      case "block": return BLOCK;
      case "endblock": return ENDBLOCK;
      case "extends": return EXTENDS;
      case "include": return INCLUDE;
      case "import": return IMPORT;
      case "from": return FROM;
      case "endraw": return ENDRAW;
      case "endverbatim": return ENDVERBATIM;
      default: return TAG_NAME;
    }
  }

  private static IElementType expressionKeyword(CharSequence text) {
    switch (text.toString()) {
      case "and": return AND;
      case "or": return OR;
      case "not": return NOT;
      case "in": return IN;
      case "is": return IS;
      case "if": return IF;
      case "else": return ELSE;
      case "true": return TRUE;
      case "false": return FALSE;
      case "none": return NONE;
      case "null": return NULL;
      default: return IDENTIFIER;
    }
  }
%}

WS = [ \t\f\r\n]+
IDENT = [\p{L}_$] [\p{L}\p{N}_$]*
NUMBER = [0-9]+ ("." [0-9]+)?
// strings stop before }} or %} so an unterminated string can't swallow the rest of the template
DQ_CHAR = [^\"\\}%] | \\[^] | [}%] [^\"\\}%]
SQ_CHAR = [^\'\\}%] | \\[^] | [}%] [^\'\\}%]
DQ_STRING = \" {DQ_CHAR}* [}%]? \" | \" {DQ_CHAR}*
SQ_STRING = \' {SQ_CHAR}* [}%]? \' | \' {SQ_CHAR}*
REGEX = "r/" ([^/\\\r\n] | \\[^\r\n])* "/" [gimy]*
COMMENT_BODY = ([^#] | "#"+ [^#}])*
END_RAW = "{%" "-"? {WS}? "endraw" {WS}? "-"? "%}"
END_VERBATIM = "{%" "-"? {WS}? "endverbatim" {WS}? "-"? "%}"

%state S_TAG_START
%state S_BLOCK
%state S_VAR
%state S_RAW_TAG
%state S_VERBATIM_TAG
%state S_RAW_BODY
%state S_VERBATIM_BODY

%%

<YYINITIAL> {
  "{%" "-"?                         { yybegin(S_TAG_START); return BLOCK_START; }
  "{{" "-"?                         { yybegin(S_VAR); return VAR_START; }
  "{#" {COMMENT_BODY} "#"+ "}"      { return COMMENT; }
  "{#" {COMMENT_BODY} "#"*          { return COMMENT; }
  [^{]+                             { return TEMPLATE_TEXT; }
  "{"                               { return TEMPLATE_TEXT; }
}

<S_TAG_START> {
  {WS}                              { return WHITE_SPACE; }
  "raw"                             { yybegin(S_RAW_TAG); return RAW; }
  "verbatim"                        { yybegin(S_VERBATIM_TAG); return VERBATIM; }
  {IDENT}                           { yybegin(S_BLOCK); return tagKeyword(yytext()); }
  [^]                               { yypushback(1); yybegin(S_BLOCK); }
}

<S_RAW_TAG> {
  {WS}                              { return WHITE_SPACE; }
  "-"? "%}"                         { yybegin(S_RAW_BODY); return BLOCK_END; }
  [^]                               { yypushback(1); yybegin(S_BLOCK); }
}

<S_VERBATIM_TAG> {
  {WS}                              { return WHITE_SPACE; }
  "-"? "%}"                         { yybegin(S_VERBATIM_BODY); return BLOCK_END; }
  [^]                               { yypushback(1); yybegin(S_BLOCK); }
}

<S_RAW_BODY> {
  {END_RAW}                         { yypushback(yylength()); yybegin(YYINITIAL); }
  [^{]+                             { return TEMPLATE_TEXT; }
  "{"                               { return TEMPLATE_TEXT; }
}

<S_VERBATIM_BODY> {
  {END_VERBATIM}                    { yypushback(yylength()); yybegin(YYINITIAL); }
  [^{]+                             { return TEMPLATE_TEXT; }
  "{"                               { return TEMPLATE_TEXT; }
}

<S_BLOCK> {
  "-"? "%}"                         { yybegin(YYINITIAL); return BLOCK_END; }
}

<S_VAR> {
  "-"? "}}"                         { yybegin(YYINITIAL); return VAR_END; }
}

<S_BLOCK, S_VAR> {
  {WS}                              { return WHITE_SPACE; }
  {REGEX}                           { return REGEX; }
  {DQ_STRING}                       { return STRING; }
  {SQ_STRING}                       { return STRING; }
  {NUMBER}                          { return NUMBER; }
  {IDENT}                           { return expressionKeyword(yytext()); }

  "==="                             { return EQEQEQ; }
  "!=="                             { return NEQEQ; }
  "=="                              { return EQEQ; }
  "!="                              { return NEQ; }
  "<="                              { return LE; }
  ">="                              { return GE; }
  "<"                               { return LT; }
  ">"                               { return GT; }
  "**"                              { return POW; }
  "//"                              { return FLOORDIV; }
  "+"                               { return PLUS; }
  "-"                               { return MINUS; }
  "*"                               { return MUL; }
  "/"                               { return DIV; }
  "%"                               { return MOD; }
  "~"                               { return TILDE; }
  "="                               { return ASSIGN; }
  "|"                               { return PIPE; }
  "."                               { return DOT; }
  ","                               { return COMMA; }
  ":"                               { return COLON; }
  "("                               { return LPAREN; }
  ")"                               { return RPAREN; }
  "["                               { return LBRACKET; }
  "]"                               { return RBRACKET; }
  "{"                               { return LBRACE; }
  "}"                               { return RBRACE; }
}

[^]                                 { return BAD_CHARACTER; }
