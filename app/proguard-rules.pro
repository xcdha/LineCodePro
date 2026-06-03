# Release obfuscation policy. The default optimize file enables R8 shrinking
# and optimization; these rules make naming and debug metadata stricter.
-allowaccessmodification
-overloadaggressively
-repackageclasses ''
-adaptclassstrings

-obfuscationdictionary build/generated/r8/obfuscation-dictionary.txt
-classobfuscationdictionary build/generated/r8/obfuscation-dictionary.txt
-packageobfuscationdictionary build/generated/r8/obfuscation-dictionary.txt

# Do not keep SourceFile or LineNumberTable. Keep only metadata commonly needed
# by Java libraries and runtime annotations.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
