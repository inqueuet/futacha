package com.valoser.futacha.instrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.build.api.instrumentation.ClassContext;
import com.android.build.api.instrumentation.ClassData;
import com.android.build.api.instrumentation.InstrumentationContext;
import com.android.build.api.instrumentation.InstrumentationParameters;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.gradle.api.provider.Property;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Runs the build-time patch over the exact pinned Foundation Android bytecode. */
public class ComposeSelectionGuardFactoryTest {
    private static final String IMPL =
            "androidx.compose.foundation.text.selection.PlatformSelectionBehaviorsImpl";
    private static final String SUGGEST = IMPL + "$suggestSelectionForLongPressOrDoubleClick$2";
    private static final String GUARD = "com/valoser/futacha/text/SafeTextClassification";

    @Test public void pinnedFoundationSessionCallIsRoutedThroughGuard() throws IOException {
        List<String> calls = guardCalls(instrument(IMPL, pinnedClass(IMPL)), "requireTextClassificationSession");
        assertEquals(List.of("withContext"), calls);
    }

    @Test public void pinnedFoundationSuggestionRangeIsCheckedAgainstRequestText() throws IOException {
        List<String> calls = guardCalls(instrument(SUGGEST, pinnedClass(SUGGEST)), "invokeSuspend");
        assertEquals(List.of("checkedSelection"), calls);
    }

    @Test public void targetClassWithoutTheCallSiteFailsTheBuild() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, IMPL.replace('.', '/'), null, "java/lang/Object", null);
        writer.visitEnd();
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> instrument(IMPL, writer.toByteArray()));
        assertTrue(failure.getMessage().contains("found 0"));
    }

    @Test public void registrationIsBoundToTheVerifiedPin() {
        ComposeSelectionGuardFactory.requireVerifiedFoundation(
                ComposeSelectionGuardFactory.VERIFIED_FOUNDATION_ANDROID_VERSION);
        assertThrows(IllegalStateException.class,
                () -> ComposeSelectionGuardFactory.requireVerifiedFoundation("1.13.0-alpha03"));
        assertThrows(IllegalStateException.class,
                () -> ComposeSelectionGuardFactory.requireVerifiedFoundation(""));
    }

    @Test public void phoneAndWearGuardsStayIdentical() throws IOException {
        Path root = Path.of(System.getProperty("futacha.repositoryRoot"));
        String guard = "src/main/java/com/valoser/futacha/text/SafeTextClassification.kt";
        assertEquals(guardBody(root.resolve("app-android").resolve(guard)),
                guardBody(root.resolve("app-wear").resolve(guard)));
    }

    private static String guardBody(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        return text.substring(text.indexOf("object SafeTextClassification"));
    }

    private static byte[] pinnedClass(String className) throws IOException {
        String entry = className.replace('.', '/') + ".class";
        try (ZipFile aar = new ZipFile(System.getProperty("futacha.guardedFoundationAar"));
             ZipInputStream classes = new ZipInputStream(aar.getInputStream(aar.getEntry("classes.jar")))) {
            for (ZipEntry next; (next = classes.getNextEntry()) != null; ) {
                if (next.getName().equals(entry)) return classes.readAllBytes();
            }
        }
        throw new AssertionError(entry + " is missing from the pinned Foundation Android AAR");
    }

    private static byte[] instrument(String className, byte[] original) {
        ComposeSelectionGuardFactory factory = new ComposeSelectionGuardFactory() {
            @Override public Property<InstrumentationParameters.None> getParameters() { return null; }
            @Override public InstrumentationContext getInstrumentationContext() { return null; }
        };
        ClassData data = (ClassData) Proxy.newProxyInstance(ClassData.class.getClassLoader(),
                new Class<?>[] {ClassData.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getClassName")) return className;
                    if (method.getReturnType() == List.class) return List.of();
                    throw new UnsupportedOperationException(method.getName());
                });
        ClassContext context = (ClassContext) Proxy.newProxyInstance(ClassContext.class.getClassLoader(),
                new Class<?>[] {ClassContext.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getCurrentClassData")) return data;
                    throw new UnsupportedOperationException(method.getName());
                });
        assertTrue(factory.isInstrumentable(data));
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(original).accept(factory.createClassVisitor(context, writer), 0);
        return writer.toByteArray();
    }

    /** Guard methods called from methods named [methodName] of the instrumented class. */
    private static List<String> guardCalls(byte[] instrumented, String methodName) {
        List<String> calls = new ArrayList<>();
        new ClassReader(instrumented).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                if (!name.equals(methodName)) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method,
                            String desc, boolean isInterface) {
                        if (owner.equals(GUARD)) calls.add(method);
                    }
                };
            }
        }, 0);
        return calls;
    }
}
