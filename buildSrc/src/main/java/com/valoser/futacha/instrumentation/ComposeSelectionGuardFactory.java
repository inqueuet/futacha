package com.valoser.futacha.instrumentation;

import com.android.build.api.instrumentation.AsmClassVisitorFactory;
import com.android.build.api.instrumentation.ClassContext;
import com.android.build.api.instrumentation.ClassData;
import com.android.build.api.instrumentation.InstrumentationParameters;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Two narrowly scoped call-site patches for Foundation Android 1.13.0-alpha02.
 * Keep the original concrete class: Compose checks its type to populate smart menu actions.
 * No UI, IME, selection gesture, timeout or classifier implementation is replaced.
 * Remove after adopting an upstream fix; review the pinned dependency before upgrading.
 */
public abstract class ComposeSelectionGuardFactory
        implements AsmClassVisitorFactory<InstrumentationParameters.None> {
    private static final String IMPL =
            "androidx.compose.foundation.text.selection.PlatformSelectionBehaviorsImpl";
    private static final String SUGGEST = IMPL + "$suggestSelectionForLongPressOrDoubleClick$2";
    private static final String GUARD = "com/valoser/futacha/text/SafeTextClassification";
    private static final String WITH_CONTEXT =
            "(Lkotlin/coroutines/CoroutineContext;Lkotlin/jvm/functions/Function2;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;";

    @Override public boolean isInstrumentable(ClassData data) {
        return IMPL.equals(data.getClassName()) || SUGGEST.equals(data.getClassName());
    }

    @Override public ClassVisitor createClassVisitor(ClassContext context, ClassVisitor next) {
        String className = context.getCurrentClassData().getClassName();
        return new ClassVisitor(Opcodes.ASM9, next) {
            private int replacements;

            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor downstream = super.visitMethod(access, name, descriptor, signature, exceptions);
                boolean session = IMPL.equals(className) && name.equals("requireTextClassificationSession");
                boolean suggestion = SUGGEST.equals(className) && name.equals("invokeSuspend")
                        && descriptor.equals("(Ljava/lang/Object;)Ljava/lang/Object;");
                if (!session && !suggestion) return downstream;
                return new MethodVisitor(Opcodes.ASM9, downstream) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method,
                            String desc, boolean isInterface) {
                        if (session && opcode == Opcodes.INVOKESTATIC
                                && owner.equals("kotlinx/coroutines/BuildersKt")
                                && method.equals("withContext") && desc.equals(WITH_CONTEXT)) {
                            replacements++;
                            super.visitMethodInsn(opcode, GUARD, "withContext", desc, false);
                        } else if (suggestion && opcode == Opcodes.INVOKESTATIC
                                && owner.equals("androidx/compose/ui/text/TextRangeKt")
                                && method.equals("TextRange") && desc.equals("(II)J")) {
                            replacements++;
                            // Stack is [start, end]. Validate against the exact request's text
                            // before either API 31 classification reuse or fallback classification.
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitFieldInsn(Opcodes.GETFIELD, className.replace('.', '/'),
                                    "$text", "Ljava/lang/CharSequence;");
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, GUARD, "checkedSelection",
                                    "(IILjava/lang/CharSequence;)J", false);
                        } else {
                            super.visitMethodInsn(opcode, owner, method, desc, isInterface);
                        }
                    }
                };
            }

            @Override public void visitEnd() {
                if (replacements != 1) {
                    throw new IllegalStateException("Compose selection guard expected one call site in "
                            + className + ", found " + replacements + ". Review Foundation dependency.");
                }
                super.visitEnd();
            }
        };
    }
}
