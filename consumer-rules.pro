# Preserve Joni's enum-based quantifier table. R8 otherwise rejects the rewritten
# QuantifierNode.<clinit>, which is required when compiling syntax grammars.
-keep class org.joni.ast.QuantifierNode { *; }
-keep class org.joni.ast.QuantifierNode$ReduceType { *; }
