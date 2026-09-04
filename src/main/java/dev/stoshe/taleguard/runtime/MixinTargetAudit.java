package dev.stoshe.taleguard.runtime;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Checks, at startup, that every {@code @Redirect} still has something to bind to.
 *
 * <p>This exists because of how the guards fail. Each redirect names an exact
 * method descriptor in the server, and all but one carry {@code require = 0}: when
 * a server update changes that descriptor, Mixin skips the injection <em>in
 * silence</em>. TaleGuard then loads cleanly, reports itself healthy, and protects
 * nothing — the worst possible failure for a protection bridge, because the only
 * symptom is grief nobody can explain.</p>
 *
 * <p>The audit reads TaleGuard's own mixin classes with ASM, pulls the target
 * class, the target method and the redirected call out of each annotation, and
 * looks for that call in the server's bytecode. Anything it cannot find is a guard
 * that will not apply, named before anyone relies on it.</p>
 *
 * <p>It reports rather than refuses. Flipping the redirects to {@code require = 1}
 * would turn a silent hole into a server that will not boot, which is not an
 * improvement when only some guards are affected; and on Hytale 0.6, where a
 * failed core plugin stops the server, it would be worse still. Naming the dead
 * ones lets an operator decide.</p>
 */
public final class MixinTargetAudit {

    /** One redirect that could not be matched against the running server. */
    public record Broken(String mixin, String targetClass, String targetMethod, String reason) {
        @Override
        public String toString() {
            return mixin + " -> " + targetClass + "#" + targetMethod + ": " + reason;
        }
    }

    /** What the audit found. */
    public record Result(int checked, List<Broken> broken) {
        public boolean isClean() {
            return broken.isEmpty();
        }
    }

    private static final String MIXIN_ANNOTATION = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String REDIRECT_ANNOTATION = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    private static final String AT_ANNOTATION = "Lorg/spongepowered/asm/mixin/injection/At;";

    private final ClassLoader loader;

    public MixinTargetAudit(ClassLoader loader) {
        this.loader = loader;
    }

    /**
     * Audits every mixin class named, returning what could not be bound.
     *
     * <p>An unreadable mixin or an unreadable target is reported as broken rather
     * than skipped: "we could not check" and "it is fine" are different answers,
     * and only one of them is safe to assume.</p>
     */
    public Result audit(String mixinPackage, List<String> mixinNames) {
        List<Broken> broken = new ArrayList<>();
        int checked = 0;

        for (String simpleName : mixinNames) {
            String binary = mixinPackage + "." + simpleName;
            ClassNode mixin = read(binary);
            if (mixin == null) {
                broken.add(new Broken(simpleName, "?", "?", "mixin class could not be read"));
                continue;
            }

            String targetClass = targetOf(mixin);
            if (targetClass == null) {
                broken.add(new Broken(simpleName, "?", "?", "no @Mixin target"));
                continue;
            }

            ClassNode target = read(targetClass);
            if (target == null) {
                broken.add(new Broken(simpleName, targetClass, "?", "target class not on this server"));
                continue;
            }

            for (MethodNode method : mixin.methods) {
                for (AnnotationNode annotation : visible(method)) {
                    if (!REDIRECT_ANNOTATION.equals(annotation.desc)) {
                        continue;
                    }
                    checked++;
                    String problem = check(annotation, target);
                    if (problem != null) {
                        broken.add(new Broken(simpleName, targetClass, describeMethod(annotation), problem));
                    }
                }
            }
        }

        return new Result(checked, broken);
    }

    /** Whether the redirect's target method exists and still makes the call it redirects. */
    private String check(AnnotationNode redirect, ClassNode target) {
        String methodSpec = value(redirect, "method") instanceof String s ? s
                : firstString(value(redirect, "method"));
        if (methodSpec == null) {
            return "no method named";
        }

        List<MethodNode> candidates = matching(target, methodSpec);
        if (candidates.isEmpty()) {
            return "no method '" + methodSpec + "' on the target";
        }

        AnnotationNode at = firstAnnotation(value(redirect, "at"));
        if (at == null || !AT_ANNOTATION.equals(at.desc)) {
            return null; // Nothing further we can verify.
        }

        String callTarget = value(at, "target") instanceof String s ? s : null;
        String kind = value(at, "value") instanceof String s ? s : null;
        if (callTarget == null || !"INVOKE".equals(kind)) {
            return null; // Only INVOKE targets are checkable this cheaply.
        }

        Call call = Call.parse(callTarget);
        if (call == null) {
            return null;
        }

        for (MethodNode candidate : candidates) {
            if (containsCall(candidate, call)) {
                return null;
            }
        }
        return "the redirected call " + call.name + call.desc + " is gone from " + methodSpec;
    }

    private record Call(String owner, String name, String desc) {
        /** Parses Mixin's {@code Lowner;name(desc)ret} member descriptor. */
        static Call parse(String spec) {
            int semi = spec.indexOf(';');
            int paren = spec.indexOf('(');
            if (semi < 0 || paren < 0 || paren < semi) {
                return null;
            }
            return new Call(spec.substring(1, semi), spec.substring(semi + 1, paren), spec.substring(paren));
        }
    }

    private boolean containsCall(MethodNode method, Call call) {
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode invoke
                    && invoke.name.equals(call.name)
                    && invoke.desc.equals(call.desc)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Methods matching Mixin's {@code method} spec, which is either a bare name or
     * a name with a descriptor appended.
     */
    private List<MethodNode> matching(ClassNode target, String spec) {
        int paren = spec.indexOf('(');
        String name = paren < 0 ? spec : spec.substring(0, paren);
        String desc = paren < 0 ? null : spec.substring(paren);

        List<MethodNode> found = new ArrayList<>();
        for (MethodNode method : target.methods) {
            if (method.name.equals(name) && (desc == null || method.desc.equals(desc))) {
                found.add(method);
            }
        }
        return found;
    }

    private String describeMethod(AnnotationNode redirect) {
        Object method = value(redirect, "method");
        String spec = method instanceof String s ? s : firstString(method);
        return spec != null ? spec : "?";
    }

    private String targetOf(ClassNode mixin) {
        for (AnnotationNode annotation : mixin.invisibleAnnotations == null
                ? List.<AnnotationNode>of() : mixin.invisibleAnnotations) {
            if (!MIXIN_ANNOTATION.equals(annotation.desc)) {
                continue;
            }
            Object value = value(annotation, "value");
            if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Type type) {
                return type.getClassName();
            }
            // Mixins may also name their target as a string.
            String named = firstString(value(annotation, "targets"));
            if (named != null) {
                return named.replace('/', '.');
            }
        }
        return null;
    }

    /** Mixin annotations are CLASS-retention, so they land in the invisible set. */
    private List<AnnotationNode> visible(MethodNode method) {
        List<AnnotationNode> all = new ArrayList<>();
        if (method.invisibleAnnotations != null) {
            all.addAll(method.invisibleAnnotations);
        }
        if (method.visibleAnnotations != null) {
            all.addAll(method.visibleAnnotations);
        }
        return all;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values == null) {
            return null;
        }
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) {
                return annotation.values.get(i + 1);
            }
        }
        return null;
    }

    private static String firstString(Object value) {
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof String s) {
            return s;
        }
        return null;
    }

    private static AnnotationNode firstAnnotation(Object value) {
        if (value instanceof AnnotationNode node) {
            return node;
        }
        if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof AnnotationNode node) {
            return node;
        }
        return null;
    }

    private ClassNode read(String binaryName) {
        String resource = binaryName.replace('.', '/') + ".class";
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
            return node;
        } catch (Exception e) {
            return null;
        }
    }

    /** A one-line summary suitable for the console, and for a status property. */
    public static String summarise(Result result) {
        if (result.isClean()) {
            return "all " + result.checked() + " redirect(s) match this server";
        }
        return result.broken().size() + " of " + result.checked()
                + " redirect(s) will NOT apply on this server".toLowerCase(Locale.ROOT);
    }
}
