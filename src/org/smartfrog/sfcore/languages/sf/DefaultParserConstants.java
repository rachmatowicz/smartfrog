/* Generated By:JavaCC: Do not edit this line. DefaultParserConstants.java */
package org.smartfrog.sfcore.languages.sf;


/**
 * Token literal values and constants.
 * Generated by org.javacc.parser.OtherFilesGen#start()
 */
public interface DefaultParserConstants {

  /** End of File. */
  int EOF = 0;
  /** RegularExpression Id. */
  int FORMAL_COMMENT = 10;
  /** RegularExpression Id. */
  int MULTI_LINE_COMMENT = 11;
  /** RegularExpression Id. */
  int APPLY = 13;
  /** RegularExpression Id. */
  int ASSERT = 14;
  /** RegularExpression Id. */
  int ATTRIB = 15;
  /** RegularExpression Id. */
  int CODEBASE = 16;
  /** RegularExpression Id. */
  int COMMA = 17;
  /** RegularExpression Id. */
  int DATA = 18;
  /** RegularExpression Id. */
  int EXTENDS = 19;
  /** RegularExpression Id. */
  int FALSE = 20;
  /** RegularExpression Id. */
  int HERE = 21;
  /** RegularExpression Id. */
  int HOST = 22;
  /** RegularExpression Id. */
  int INCLUDE = 23;
  /** RegularExpression Id. */
  int INCLUDEOPTIONALLY = 24;
  /** RegularExpression Id. */
  int PROPERTY = 25;
  /** RegularExpression Id. */
  int IPROPERTY = 26;
  /** RegularExpression Id. */
  int ENVPROPERTY = 27;
  /** RegularExpression Id. */
  int IENVPROPERTY = 28;
  /** RegularExpression Id. */
  int CONST = 29;
  /** RegularExpression Id. */
  int LAZY = 30;
  /** RegularExpression Id. */
  int LBRACE = 31;
  /** RegularExpression Id. */
  int LBRACKET = 32;
  /** RegularExpression Id. */
  int NULL = 33;
  /** RegularExpression Id. */
  int OPTIONAL = 34;
  /** RegularExpression Id. */
  int PARENT = 35;
  /** RegularExpression Id. */
  int PROCESS = 36;
  /** RegularExpression Id. */
  int RBRACE = 37;
  /** RegularExpression Id. */
  int RBRACKET = 38;
  /** RegularExpression Id. */
  int REFPARTSEP = 39;
  /** RegularExpression Id. */
  int ROOT = 40;
  /** RegularExpression Id. */
  int SEMICOLON = 41;
  /** RegularExpression Id. */
  int TBD = 42;
  /** RegularExpression Id. */
  int THIS = 43;
  /** RegularExpression Id. */
  int TRUE = 44;
  /** RegularExpression Id. */
  int VECTOREND = 45;
  /** RegularExpression Id. */
  int VECTORSTART = 46;
  /** RegularExpression Id. */
  int UNIQUE = 47;
  /** RegularExpression Id. */
  int OPSTART = 48;
  /** RegularExpression Id. */
  int OPEND = 49;
  /** RegularExpression Id. */
  int SUM = 50;
  /** RegularExpression Id. */
  int MINUS = 51;
  /** RegularExpression Id. */
  int TIMES = 52;
  /** RegularExpression Id. */
  int DIV = 53;
  /** RegularExpression Id. */
  int MOD = 54;
  /** RegularExpression Id. */
  int CONCAT = 55;
  /** RegularExpression Id. */
  int APPEND = 56;
  /** RegularExpression Id. */
  int EQ = 57;
  /** RegularExpression Id. */
  int NE = 58;
  /** RegularExpression Id. */
  int GE = 59;
  /** RegularExpression Id. */
  int LE = 60;
  /** RegularExpression Id. */
  int GT = 61;
  /** RegularExpression Id. */
  int LT = 62;
  /** RegularExpression Id. */
  int AND = 63;
  /** RegularExpression Id. */
  int OR = 64;
  /** RegularExpression Id. */
  int XOR = 65;
  /** RegularExpression Id. */
  int NOT = 66;
  /** RegularExpression Id. */
  int IMPLIES = 67;
  /** RegularExpression Id. */
  int IF = 68;
  /** RegularExpression Id. */
  int THEN = 69;
  /** RegularExpression Id. */
  int ELSE = 70;
  /** RegularExpression Id. */
  int FI = 71;
  /** RegularExpression Id. */
  int SWITCH = 72;
  /** RegularExpression Id. */
  int ENDSWITCH = 73;
  /** RegularExpression Id. */
  int VAR = 74;
  /** RegularExpression Id. */
  int WORD = 75;
  /** RegularExpression Id. */
  int SPECIAL = 76;
  /** RegularExpression Id. */
  int LETTER = 77;
  /** RegularExpression Id. */
  int DIGIT = 78;
  /** RegularExpression Id. */
  int STRING = 79;
  /** RegularExpression Id. */
  int MULTILINESTRING = 80;
  /** RegularExpression Id. */
  int INTEGER = 81;
  /** RegularExpression Id. */
  int FLOAT_BASE = 82;
  /** RegularExpression Id. */
  int EXPONENT = 83;
  /** RegularExpression Id. */
  int DOUBLE = 84;
  /** RegularExpression Id. */
  int LONG = 85;
  /** RegularExpression Id. */
  int FLOAT = 86;
  /** RegularExpression Id. */
  int BYTEARRAY = 87;

  /** Lexical state. */
  int DEFAULT = 0;
  /** Lexical state. */
  int IN_SINGLE_LINE_COMMENT = 1;
  /** Lexical state. */
  int IN_FORMAL_COMMENT = 2;
  /** Lexical state. */
  int IN_MULTI_LINE_COMMENT = 3;

  /** Literal token values. */
  String[] tokenImage = {
    "<EOF>",
    "\" \"",
    "\"\\t\"",
    "\"\\n\"",
    "\"\\r\"",
    "\"\\f\"",
    "\"//\"",
    "<token of kind 7>",
    "\"/*\"",
    "<token of kind 9>",
    "\"*/\"",
    "\"*/\"",
    "<token of kind 12>",
    "\"APPLY\"",
    "\"ASSERT\"",
    "\"ATTRIB\"",
    "\"#codebase\"",
    "\",\"",
    "\"DATA\"",
    "\"extends\"",
    "\"false\"",
    "\"HERE\"",
    "\"HOST\"",
    "\"#include\"",
    "\"#include?\"",
    "\"PROPERTY\"",
    "\"IPROPERTY\"",
    "\"ENVPROPERTY\"",
    "\"IENVPROPERTY\"",
    "\"CONSTANT\"",
    "\"LAZY\"",
    "\"{\"",
    "\"[\"",
    "\"NULL\"",
    "\"OPTIONAL\"",
    "\"PARENT\"",
    "\"PROCESS\"",
    "\"}\"",
    "\"]\"",
    "\":\"",
    "\"ROOT\"",
    "\";\"",
    "\"TBD\"",
    "\"THIS\"",
    "\"true\"",
    "\"|]\"",
    "\"[|\"",
    "\"--\"",
    "\"(\"",
    "\")\"",
    "\"+\"",
    "\"-\"",
    "\"*\"",
    "\"/\"",
    "\"%\"",
    "\"++\"",
    "\"<>\"",
    "\"==\"",
    "\"!=\"",
    "\">=\"",
    "\"<=\"",
    "\">\"",
    "\"<\"",
    "\"&&\"",
    "\"||\"",
    "\"x|\"",
    "\"!\"",
    "\"->\"",
    "\"IF\"",
    "\"THEN\"",
    "\"ELSE\"",
    "\"FI\"",
    "\"SWITCH\"",
    "\"ENDSWITCH\"",
    "\"VAR\"",
    "<WORD>",
    "<SPECIAL>",
    "<LETTER>",
    "<DIGIT>",
    "<STRING>",
    "<MULTILINESTRING>",
    "<INTEGER>",
    "<FLOAT_BASE>",
    "<EXPONENT>",
    "<DOUBLE>",
    "<LONG>",
    "<FLOAT>",
    "<BYTEARRAY>",
  };

}
