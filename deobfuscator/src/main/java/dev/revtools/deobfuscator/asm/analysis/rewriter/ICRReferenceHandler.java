package dev.revtools.deobfuscator.asm.analysis.rewriter;

import dev.revtools.deobfuscator.asm.analysis.rewriter.value.CodeReferenceValue;
import dev.revtools.deobfuscator.asm.analysis.stack.IConstantReferenceHandler;
import org.objectweb.asm.tree.analysis.BasicValue;

import java.util.List;

/**
 * Same as {@link IConstantReferenceHandler} for CodeRewriter
 */
public interface ICRReferenceHandler {

  Object getFieldValueOrNull(BasicValue v, String owner, String name, String desc);

  Object getMethodReturnOrNull(BasicValue v, String owner, String name, String desc,
                               List<? extends CodeReferenceValue> values);

}
